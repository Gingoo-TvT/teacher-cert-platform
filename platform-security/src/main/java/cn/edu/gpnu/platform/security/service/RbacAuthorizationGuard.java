package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.SysRolePermission;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.entity.SysUserDataScope;
import cn.edu.gpnu.platform.system.entity.SysUserRole;
import cn.edu.gpnu.platform.system.mapper.SysMajorMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysRolePermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserDataScopeMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces the RBAC delegation ceiling for security-administration writes.
 *
 * <p>The caller's grants and concrete data range are always reloaded from the database. Roles
 * being inspected deliberately use their potential grants even while disabled, so a dormant role
 * cannot be populated with privileges that become effective when it is enabled later.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class RbacAuthorizationGuard {

    private static final String ROLE_MANAGE_PERMISSION = "system:role:manage";
    private static final String DENIED_MESSAGE = "当前操作无权限";

    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserDataScopeMapper userDataScopeMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysMajorMapper majorMapper;

    /**
     * Acquires the transaction-scoped serialization lock before any RBAC state snapshot is read.
     */
    public void lockAuthorizationState() {
        if (rolePermissionMapper.lockAuthorizationState() == null) {
            deny();
        }
    }

    /** Requires an effective, exact {@code system:role:manage@SYSTEM} database grant. */
    public void requireSystemRoleManagement() {
        Operator operator = loadOperator();
        List<String> scopes = rolePermissionMapper.selectScopeTypes(operator.userId(), ROLE_MANAGE_PERMISSION);
        if (scopes == null || scopes.stream().noneMatch("SYSTEM"::equals)) {
            deny();
        }
    }

    /** Checks the abstract permission/scope vector of roles before assigning them to a user. */
    public void assertCanGrantRoles(Collection<Long> roleIds) {
        Operator operator = loadOperator();
        assertDominates(operator, potentialGrantsForRoles(roleIds, true), null);
    }

    /** Checks an abstract permission/scope vector without applying a concrete user's range. */
    public void assertCanGrantPermissions(Map<Long, String> scopeByPermission) {
        Operator operator = loadOperator();
        assertDominates(operator, requestedGrants(scopeByPermission), null);
    }

    /** Checks the target user's current potential grants and concrete database range. */
    public void assertCanManageUser(Long targetUserId) {
        Operator operator = loadOperator();
        assertCanManageUser(operator, targetUserId);
    }

    /**
     * Checks a user's complete authorization after-state. A non-null target id also causes the
     * current state to be checked first, so lowering a higher-privileged or incomparable user is
     * not a way around the ceiling. Pass {@code null} only while creating a new user.
     */
    public void assertCanSetUserAuthorization(Long targetUserId,
                                               Collection<Long> roleIds,
                                               Long homeCollegeId,
                                               Collection<Long> assignedCollegeIds,
                                               Collection<Long> assignedMajorIds) {
        Operator operator = loadOperator();
        if (targetUserId != null) {
            assertCanManageUser(operator, targetUserId);
        }
        TargetContext afterState = targetContext(
                targetUserId, homeCollegeId, assignedCollegeIds, assignedMajorIds);
        assertDominates(operator, potentialGrantsForRoles(roleIds, true), afterState);
    }

    /**
     * Checks a role's dormant/current grants and their concrete effect on every existing member.
     */
    public void assertCanManageRole(Long roleId) {
        Operator operator = loadOperator();
        requireRole(roleId);
        List<Grant> grants = potentialGrantsForRole(roleId);
        assertRoleStateForMembers(
                operator, grants, existingMemberAuthorizations(roleId), new HashMap<>());
    }

    /**
     * Checks both a role's current authorization and the requested after-state, including the
     * concrete range through which every existing member would receive COLLEGE grants.
     */
    public void assertCanSetRolePermissions(Long roleId, Map<Long, String> scopeByPermission) {
        Operator operator = loadOperator();
        requireRole(roleId);
        List<MemberAuthorization> members = existingMemberAuthorizations(roleId);
        Map<Long, Long> majorCollegeCache = new HashMap<>();

        List<Grant> current = potentialGrantsForRole(roleId);
        assertRoleStateForMembers(operator, current, members, majorCollegeCache);

        List<Grant> requested = requestedGrants(scopeByPermission);
        assertRoleStateForMembers(operator, requested, members, majorCollegeCache);
    }

    private void assertCanManageUser(Operator operator, Long targetUserId) {
        SysUser target = requireUser(targetUserId);
        List<Long> targetRoleIds = safeList(userRoleMapper.selectRoleIds(targetUserId));
        List<Grant> targetGrants = potentialGrantsForRoles(targetRoleIds, false);
        assertDominates(operator, targetGrants, currentTargetContext(target));
    }

    private void assertRoleStateForMembers(Operator operator,
                                           List<Grant> roleGrants,
                                           List<MemberAuthorization> members,
                                           Map<Long, Long> majorCollegeCache) {
        assertDominates(operator, roleGrants, null, majorCollegeCache);
        for (MemberAuthorization member : members) {
            List<Grant> completeGrants = new ArrayList<>(
                    member.otherRoleGrants().size() + roleGrants.size());
            completeGrants.addAll(member.otherRoleGrants());
            completeGrants.addAll(roleGrants);
            assertDominates(operator, completeGrants, member.context(), majorCollegeCache);
        }
    }

    private List<MemberAuthorization> existingMemberAuthorizations(Long roleId) {
        List<TargetContext> memberContexts = existingMemberContexts(roleId);
        if (memberContexts.isEmpty()) {
            return List.of();
        }

        List<Long> memberIds = memberContexts.stream().map(TargetContext::userId).toList();
        Set<Long> memberIdSet = Set.copyOf(memberIds);
        Map<Long, LinkedHashSet<Long>> otherRoleIdsByUser = new LinkedHashMap<>();
        LinkedHashSet<Long> otherRoleIds = new LinkedHashSet<>();
        for (SysUserRole relation : safeList(
                userRoleMapper.selectByUserIdsExcludingRole(memberIds, roleId))) {
            if (relation == null || relation.getUserId() == null || relation.getRoleId() == null
                    || !memberIdSet.contains(relation.getUserId())
                    || roleId.equals(relation.getRoleId())) {
                deny();
            }
            otherRoleIdsByUser
                    .computeIfAbsent(relation.getUserId(), ignored -> new LinkedHashSet<>())
                    .add(relation.getRoleId());
            otherRoleIds.add(relation.getRoleId());
        }

        Map<Long, List<Grant>> grantsByRole = new LinkedHashMap<>();
        if (!otherRoleIds.isEmpty()) {
            for (SysRolePermission relation : safeList(
                    rolePermissionMapper.selectByRoleIds(List.copyOf(otherRoleIds)))) {
                if (relation == null || relation.getRoleId() == null
                        || relation.getPermissionId() == null
                        || !otherRoleIds.contains(relation.getRoleId())) {
                    deny();
                }
                ScopeType scopeType = parseScope(relation.getScopeType());
                if (scopeType == null) {
                    deny();
                }
                grantsByRole
                        .computeIfAbsent(relation.getRoleId(), ignored -> new ArrayList<>())
                        .add(new Grant(relation.getPermissionId(), scopeType));
            }
        }

        List<MemberAuthorization> members = new ArrayList<>(memberContexts.size());
        for (TargetContext context : memberContexts) {
            List<Grant> otherGrants = new ArrayList<>();
            for (Long otherRoleId : otherRoleIdsByUser.getOrDefault(
                    context.userId(), new LinkedHashSet<>())) {
                otherGrants.addAll(grantsByRole.getOrDefault(otherRoleId, List.of()));
            }
            members.add(new MemberAuthorization(context, List.copyOf(otherGrants)));
        }
        return members;
    }

    private List<TargetContext> existingMemberContexts(Long roleId) {
        List<Long> memberIds = safeList(userRoleMapper.selectUserIdsByRoleId(roleId));
        if (memberIds.stream().anyMatch(id -> id == null)) {
            deny();
        }
        if (memberIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> memberIdSet = new LinkedHashSet<>(memberIds);
        List<Long> uniqueMemberIds = List.copyOf(memberIdSet);
        Map<Long, SysUser> usersById = new LinkedHashMap<>();
        for (SysUser user : safeList(userMapper.selectBatchIds(uniqueMemberIds))) {
            if (user == null || user.getId() == null || !memberIdSet.contains(user.getId())
                    || usersById.put(user.getId(), user) != null) {
                deny();
            }
        }

        Map<Long, LinkedHashSet<Long>> collegesByUser = new LinkedHashMap<>();
        Map<Long, LinkedHashSet<Long>> majorsByUser = new LinkedHashMap<>();
        for (SysUserDataScope relation : safeList(userDataScopeMapper.selectByUserIds(uniqueMemberIds))) {
            if (relation == null || relation.getUserId() == null
                    || !memberIdSet.contains(relation.getUserId())) {
                deny();
            }
            if (relation.getCollegeId() != null) {
                collegesByUser.computeIfAbsent(relation.getUserId(), ignored -> new LinkedHashSet<>())
                        .add(relation.getCollegeId());
            }
            if (relation.getMajorId() != null) {
                majorsByUser.computeIfAbsent(relation.getUserId(), ignored -> new LinkedHashSet<>())
                        .add(relation.getMajorId());
            }
        }

        List<TargetContext> contexts = new ArrayList<>(uniqueMemberIds.size());
        for (Long memberId : uniqueMemberIds) {
            SysUser member = usersById.get(memberId);
            if (member == null) {
                deny();
            }
            contexts.add(targetContext(
                    memberId,
                    member.getCollegeId(),
                    collegesByUser.getOrDefault(memberId, new LinkedHashSet<>()),
                    majorsByUser.getOrDefault(memberId, new LinkedHashSet<>())));
        }
        return contexts;
    }

    private Operator loadOperator() {
        lockAuthorizationState();
        Long operatorId = UserContext.getUserId();
        if (operatorId == null) {
            deny();
        }
        SysUser operatorUser = userMapper.selectById(operatorId);
        if (operatorUser == null || !"ENABLED".equals(operatorUser.getStatus())) {
            deny();
        }

        Map<Long, EnumSet<ScopeType>> grants = new LinkedHashMap<>();
        for (SysRolePermission relation : safeList(rolePermissionMapper.selectEffectiveByUserId(operatorId))) {
            ScopeType scopeType = parseScope(relation == null ? null : relation.getScopeType());
            Long permissionId = relation == null ? null : relation.getPermissionId();
            if (permissionId != null && scopeType != null) {
                grants.computeIfAbsent(permissionId, ignored -> EnumSet.noneOf(ScopeType.class)).add(scopeType);
            }
        }
        return new Operator(operatorId, grants, currentTargetContext(operatorUser).range());
    }

    private TargetContext currentTargetContext(SysUser user) {
        return targetContext(
                user.getId(),
                user.getCollegeId(),
                userDataScopeMapper.selectCollegeIds(user.getId()),
                userDataScopeMapper.selectMajorIds(user.getId()));
    }

    private TargetContext targetContext(Long userId,
                                        Long homeCollegeId,
                                        Collection<Long> assignedCollegeIds,
                                        Collection<Long> assignedMajorIds) {
        LinkedHashSet<Long> collegeIds = normalizedIds(assignedCollegeIds);
        if (homeCollegeId != null) {
            collegeIds.add(homeCollegeId);
        }
        UserRange range = new UserRange(Set.copyOf(collegeIds), Set.copyOf(normalizedIds(assignedMajorIds)));
        return new TargetContext(userId, homeCollegeId, range);
    }

    private LinkedHashSet<Long> normalizedIds(Collection<Long> ids) {
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        if (ids == null) {
            return normalized;
        }
        for (Long id : ids) {
            if (id == null) {
                deny();
            }
            normalized.add(id);
        }
        return normalized;
    }

    private List<Grant> potentialGrantsForRoles(Collection<Long> roleIds, boolean requireExistingRoles) {
        if (roleIds == null) {
            deny();
        }
        List<Grant> grants = new ArrayList<>();
        for (Long roleId : new LinkedHashSet<>(roleIds)) {
            if (roleId == null) {
                deny();
            }
            if (requireExistingRoles) {
                requireRole(roleId);
            }
            grants.addAll(potentialGrantsForRole(roleId));
        }
        return grants;
    }

    private List<Grant> potentialGrantsForRole(Long roleId) {
        List<Grant> grants = new ArrayList<>();
        for (SysRolePermission relation : safeList(rolePermissionMapper.selectByRoleId(roleId))) {
            if (relation == null || relation.getPermissionId() == null) {
                deny();
            }
            ScopeType scopeType = parseScope(relation.getScopeType());
            if (scopeType == null) {
                deny();
            }
            grants.add(new Grant(relation.getPermissionId(), scopeType));
        }
        return grants;
    }

    private List<Grant> requestedGrants(Map<Long, String> scopeByPermission) {
        if (scopeByPermission == null) {
            deny();
        }
        List<Grant> grants = new ArrayList<>(scopeByPermission.size());
        for (Map.Entry<Long, String> entry : scopeByPermission.entrySet()) {
            ScopeType scopeType = parseScope(entry.getValue());
            if (entry.getKey() == null || scopeType == null) {
                deny();
            }
            grants.add(new Grant(entry.getKey(), scopeType));
        }
        return grants;
    }

    private void assertDominates(Operator operator, List<Grant> requested, TargetContext target) {
        assertDominates(operator, requested, target, new HashMap<>());
    }

    private void assertDominates(Operator operator,
                                 List<Grant> requested,
                                 TargetContext target,
                                 Map<Long, Long> majorCollegeCache) {
        for (Grant grant : requested) {
            Set<ScopeType> operatorScopes = operator.grants().get(grant.permissionId());
            if (operatorScopes == null) {
                operatorScopes = Collections.emptySet();
            }
            if (operatorScopes.stream().noneMatch(scope -> dominates(scope, grant.scopeType()))) {
                deny();
            }
            if (target != null && operatorScopes.stream()
                    .noneMatch(scope -> contextuallyDominates(
                            operator, scope, grant.scopeType(), target, majorCollegeCache))) {
                deny();
            }
        }
    }

    private boolean contextuallyDominates(Operator operator,
                                           ScopeType operatorScope,
                                           ScopeType requestedScope,
                                           TargetContext target,
                                           Map<Long, Long> majorCollegeCache) {
        if (!dominates(operatorScope, requestedScope)) {
            return false;
        }
        return switch (requestedScope) {
            case COLLEGE -> operatorScope == ScopeType.SYSTEM
                    || operatorScope == ScopeType.SCHOOL
                    || (operatorScope == ScopeType.COLLEGE
                        && rangeWithin(target.range(), operator.range(), majorCollegeCache));
            case SELF -> operatorScope == ScopeType.SYSTEM
                    || operatorScope == ScopeType.SCHOOL
                    || (operatorScope == ScopeType.COLLEGE
                        && target.homeCollegeId() != null
                        && operator.range().collegeIds().contains(target.homeCollegeId()))
                    || (operatorScope == ScopeType.SELF
                        && target.userId() != null
                        && target.userId().equals(operator.userId()));
            case ASSIGNED -> operatorScope == ScopeType.SYSTEM
                    || operatorScope == ScopeType.SCHOOL
                    || (operatorScope == ScopeType.ASSIGNED
                        && target.userId() != null
                        && target.userId().equals(operator.userId()));
            default -> true;
        };
    }

    private boolean rangeWithin(UserRange requested,
                                UserRange operator,
                                Map<Long, Long> majorCollegeCache) {
        if (!operator.collegeIds().containsAll(requested.collegeIds())) {
            return false;
        }
        if (!operator.majorIds().isEmpty()) {
            if (requested.majorIds().isEmpty()) {
                // Empty majors make DataScopeSqlHandler fall back to all majors in the colleges.
                return requested.collegeIds().isEmpty();
            }
            return operator.majorIds().containsAll(requested.majorIds());
        }
        for (Long majorId : requested.majorIds()) {
            if (!majorCollegeCache.containsKey(majorId)) {
                majorCollegeCache.put(majorId, majorMapper.selectCollegeIdForUpdate(majorId));
            }
            Long collegeId = majorCollegeCache.get(majorId);
            if (collegeId == null || !operator.collegeIds().contains(collegeId)) {
                return false;
            }
        }
        return true;
    }

    private boolean dominates(ScopeType operator, ScopeType requested) {
        return switch (operator) {
            case SYSTEM -> true;
            case SCHOOL -> EnumSet.of(
                    ScopeType.SCHOOL, ScopeType.COLLEGE, ScopeType.SELF,
                    ScopeType.ASSIGNED, ScopeType.NONE).contains(requested);
            case LOGIN_ALL -> requested == ScopeType.LOGIN_ALL || requested == ScopeType.NONE;
            case COLLEGE -> requested == ScopeType.COLLEGE
                    || requested == ScopeType.SELF
                    || requested == ScopeType.NONE;
            case SELF -> requested == ScopeType.SELF || requested == ScopeType.NONE;
            case ASSIGNED -> requested == ScopeType.ASSIGNED || requested == ScopeType.NONE;
            case NONE -> requested == ScopeType.NONE;
        };
    }

    private ScopeType parseScope(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ScopeType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private SysUser requireUser(Long userId) {
        if (userId == null) {
            deny();
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            deny();
        }
        return user;
    }

    private void requireRole(Long roleId) {
        if (roleId == null || roleMapper.selectById(roleId) == null) {
            deny();
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void deny() {
        throw new BizException(ResultCode.FORBIDDEN.getCode(), DENIED_MESSAGE);
    }

    private enum ScopeType {
        SYSTEM, SCHOOL, LOGIN_ALL, COLLEGE, SELF, ASSIGNED, NONE
    }

    private record Grant(Long permissionId, ScopeType scopeType) {
    }

    private record UserRange(Set<Long> collegeIds, Set<Long> majorIds) {
    }

    private record TargetContext(Long userId, Long homeCollegeId, UserRange range) {
    }

    private record MemberAuthorization(TargetContext context, List<Grant> otherRoleGrants) {
    }

    private record Operator(Long userId, Map<Long, EnumSet<ScopeType>> grants, UserRange range) {
    }
}
