package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.api.PageQuery;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.dto.RolePermissionAssignRequest;
import cn.edu.gpnu.platform.system.dto.RoleSaveRequest;
import cn.edu.gpnu.platform.system.dto.UserDataScopeRequest;
import cn.edu.gpnu.platform.system.dto.UserRoleAssignRequest;
import cn.edu.gpnu.platform.system.dto.UserSaveRequest;
import cn.edu.gpnu.platform.system.entity.SysCollege;
import cn.edu.gpnu.platform.system.entity.SysPermission;
import cn.edu.gpnu.platform.system.entity.SysRole;
import cn.edu.gpnu.platform.system.entity.SysRolePermission;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.entity.SysUserDataScope;
import cn.edu.gpnu.platform.system.entity.SysUserRole;
import cn.edu.gpnu.platform.system.mapper.SysCollegeMapper;
import cn.edu.gpnu.platform.system.mapper.SysMajorMapper;
import cn.edu.gpnu.platform.system.mapper.SysPermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysRolePermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserDataScopeMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.CollegeParentGuard;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.vo.PermissionVO;
import cn.edu.gpnu.platform.system.vo.RoleVO;
import cn.edu.gpnu.platform.system.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SecurityAdminServiceImpl implements SecurityAdminService {

    private static final String DEV_PUBLIC_INITIAL_PASSWORD = "ChangeMe123!";
    private static final String EXAMPLE_INITIAL_PASSWORD = "change-me-strong-staff-initial-password";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String TEMP_PASSWORD_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String TEMP_PASSWORD_LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String TEMP_PASSWORD_DIGITS = "23456789";
    private static final String TEMP_PASSWORD_SPECIAL = "@#$%!";
    private static final String TEMP_PASSWORD_ALL =
            TEMP_PASSWORD_UPPER + TEMP_PASSWORD_LOWER + TEMP_PASSWORD_DIGITS + TEMP_PASSWORD_SPECIAL;

    private static final Set<String> USER_STATUSES = Set.of("ENABLED", "LOCKED", "DISABLED");
    private static final Set<String> USER_TYPES = Set.of("STAFF", "STUDENT");
    private static final Set<String> SCOPE_TYPES = Set.of("SELF", "COLLEGE", "SCHOOL", "SYSTEM", "LOGIN_ALL", "ASSIGNED", "NONE");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserDataScopeMapper userDataScopeMapper;
    private final SysCollegeMapper collegeMapper;
    private final SysMajorMapper majorMapper;
    private final CollegeParentGuard collegeParentGuard;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationService tokenRevocationService;
    private final Environment environment;
    private final DataScopeService dataScopeService;
    private final RbacAuthorizationGuard authorizationGuard;
    private final AuditLogService auditLogService;

    @Value("${platform.security.initial-password:}")
    private String initialPassword;

    // dev/test 保留既有便利口令；prod 必须显式提供强值，并拒绝公开 dev 值和旧 .env.example 占位值。
    @jakarta.annotation.PostConstruct
    void validateInitialPassword() {
        if (!StringUtils.hasText(initialPassword)) {
            throw new BizException("STAFF 初始口令未配置：生产请通过环境变量 STAFF_INITIAL_PASSWORD 显式注入（勿用公开默认口令）");
        }
        if (environment.acceptsProfiles(Profiles.of("prod"))
                && (DEV_PUBLIC_INITIAL_PASSWORD.equals(initialPassword)
                || EXAMPLE_INITIAL_PASSWORD.equals(initialPassword)
                || !initialPassword.equals(initialPassword.trim())
                || !isStrongInitialPassword(initialPassword))) {
            throw new BizException("STAFF_INITIAL_PASSWORD 不安全：须为 12-64 位并包含大小写字母、数字和特殊字符，"
                    + "且不得使用公开 dev/示例口令");
        }
    }

    private boolean isStrongInitialPassword(String value) {
        return value.length() >= 12 && value.length() <= 64
                && value.chars().anyMatch(Character::isUpperCase)
                && value.chars().anyMatch(Character::isLowerCase)
                && value.chars().anyMatch(Character::isDigit)
                && value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
    }

    // Phase 44e-contract（P1-1 真分页样例）：由「全表 selectList 后 new PageResult<>(size, records)」改为
    // MyBatis-Plus Page + selectPage 真分页。@DataScope（SystemSecurityController.listUsers，alias=sys_user）
    // 设置的线程范围经数据权限拦截器在 selectPage 的 count 与数据两条 SQL 上均生效 → 该页与 total 同为
    // 「已按数据范围过滤」的结果。
    @Override
    public PageResult<UserVO> listUsers(String keyword, String status, Long collegeId, Integer page, Integer size) {
        Page<SysUser> result = userMapper.selectPage(PageQuery.of(page, size), buildUserListWrapper(keyword, status, collegeId));
        List<UserVO> records = result.getRecords().stream().map(this::toUserVO).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    private LambdaQueryWrapper<SysUser> buildUserListWrapper(String keyword, String status, Long collegeId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .orderByAsc(SysUser::getUsername);
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword != null) {
            wrapper.and(w -> w.like(SysUser::getUsername, normalizedKeyword)
                    .or()
                    .like(SysUser::getRealName, normalizedKeyword)
                    .or()
                    .like(SysUser::getWorkNo, normalizedKeyword));
        }
        String normalizedStatus = trimToNull(status);
        if (normalizedStatus != null) {
            wrapper.eq(SysUser::getStatus, normalizedStatus);
        }
        if (collegeId != null) {
            wrapper.eq(SysUser::getCollegeId, collegeId);
        }
        return wrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserSaveRequest request) {
        ensureSchoolUserManagement();
        normalizeUserRequest(request);
        if (!"STAFF".equals(request.getUserType())) {
            throw new BizException("通用用户管理仅支持创建 STAFF 账号");
        }
        ensureStaffHasNoStudentBinding(request);
        lockTargetCollege(request.getCollegeId(), CollegeParentGuard.Operation.CREATE_USER);
        if (userMapper.selectByUsername(request.getUsername().trim()) != null) {
            throw new BizException("用户名已存在");
        }
        authorizationGuard.assertCanSetUserAuthorization(
                null, request.getRoleIds(), request.getCollegeId(), List.of(), List.of());
        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setPasswordHash(passwordEncoder.encode(initialPassword));
        fillUser(user, request);
        user.setMustChangePwd(1);
        user.setFailedLoginCount(0);
        userMapper.insert(user);
        replaceUserRoles(user.getId(), request.getRoleIds());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long id, UserSaveRequest request) {
        ensureSchoolUserManagement();
        SysUser user = requireUser(id);
        normalizeUserRequest(request);
        if (!Objects.equals(user.getUserType(), request.getUserType())) {
            throw new BizException("用户类型不可通过通用用户管理修改");
        }
        boolean student = "STUDENT".equals(user.getUserType());
        if (student) {
            ensureStudentBindingUnchanged(user, request);
            ensureStudentRoleOnly(request.getRoleIds());
        } else {
            ensureStaffHasNoStudentBinding(request);
        }
        lockTargetCollege(request.getCollegeId(), CollegeParentGuard.Operation.UPDATE_USER);
        authorizationGuard.assertCanSetUserAuthorization(
                id,
                request.getRoleIds(),
                request.getCollegeId(),
                userDataScopeMapper.selectCollegeIds(id),
                userDataScopeMapper.selectMajorIds(id));
        String username = request.getUsername().trim();
        SysUser exists = userMapper.selectByUsername(username);
        if (exists != null && !exists.getId().equals(id)) {
            throw new BizException("用户名已存在");
        }
        fillEditableUserFields(user, request);
        LambdaUpdateWrapper<SysUser> updateWrapper = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .eq(SysUser::getPasswordHash, user.getPasswordHash())
                .set(SysUser::getRealName, user.getRealName())
                .set(SysUser::getWorkNo, user.getWorkNo())
                .set(SysUser::getEmail, user.getEmail())
                .set(SysUser::getPhone, user.getPhone())
                .set(SysUser::getStatus, user.getStatus())
                .set(SysUser::getUpdatedBy, UserContext.getUserIdOrSystem())
                .set(SysUser::getUpdatedAt, LocalDateTime.now());
        if (!student) {
            updateWrapper
                    .set(SysUser::getUsername, username)
                    .set(SysUser::getCollegeId, request.getCollegeId())
                    .set(SysUser::getStudentId, null);
        }
        int updated = userMapper.update(updateWrapper);
        if (updated != 1) {
            throw new BizException("用户信息更新发生并发冲突，请刷新后重试");
        }
        replaceUserRoles(id, request.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        ensureSchoolUserManagement();
        requireUser(id);
        authorizationGuard.assertCanManageUser(id);
        userMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetPassword(Long id) {
        ensureSchoolUserManagement();
        SysUser user = requireUser(id);
        authorizationGuard.assertCanManageUser(id);
        String studentTemporaryPassword = null;
        String newPassword;
        if ("STUDENT".equals(user.getUserType())) {
            studentTemporaryPassword = randomTemporaryPassword();
            newPassword = studentTemporaryPassword;
        } else if ("STAFF".equals(user.getUserType())) {
            newPassword = initialPassword;
        } else {
            throw new BizException("用户类型不支持重置密码");
        }
        int updated = userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .eq(SysUser::getUserType, user.getUserType())
                .eq(SysUser::getPasswordHash, user.getPasswordHash())
                .set(SysUser::getPasswordHash, passwordEncoder.encode(newPassword))
                .set(SysUser::getMustChangePwd, 1)
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getStatus, "ENABLED")
                .set(SysUser::getUpdatedBy, UserContext.getUserIdOrSystem())
                .set(SysUser::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BizException("密码重置发生并发冲突，请刷新后重试");
        }
        tokenRevocationService.revoke(id); // 重置密码后目标用户旧 token 立即失效
        auditLogService.record(
                "systemUser", id, "user:" + id, "resetPassword",
                null, "SUCCESS", "管理员重置用户密码");
        return studentTemporaryPassword;
    }

    private String randomTemporaryPassword() {
        char[] value = new char[20];
        value[0] = randomChar(TEMP_PASSWORD_UPPER);
        value[1] = randomChar(TEMP_PASSWORD_LOWER);
        value[2] = randomChar(TEMP_PASSWORD_DIGITS);
        value[3] = randomChar(TEMP_PASSWORD_SPECIAL);
        for (int i = 4; i < value.length; i++) {
            value[i] = randomChar(TEMP_PASSWORD_ALL);
        }
        for (int i = value.length - 1; i > 0; i--) {
            int swapIndex = SECURE_RANDOM.nextInt(i + 1);
            char current = value[i];
            value[i] = value[swapIndex];
            value[swapIndex] = current;
        }
        return new String(value);
    }

    private char randomChar(String alphabet) {
        return alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length()));
    }

    private void ensureSchoolUserManagement() {
        // This must be the first database operation in every user-authorization write. Under
        // MySQL REPEATABLE READ, taking it later would retain a pre-lock snapshot and allow write skew.
        authorizationGuard.lockAuthorizationState();
        DataScopeContext.Scope scope = dataScopeService.resolve("system:user:manage");
        if (scope != null && scope.allSchool()) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "用户管理写操作仅限校级权限");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserRoles(Long id, UserRoleAssignRequest request) {
        ensureSchoolUserManagement();
        SysUser user = requireUser(id);
        if ("STUDENT".equals(user.getUserType())) {
            ensureStudentRoleOnly(request.getRoleIds());
        }
        authorizationGuard.assertCanSetUserAuthorization(
                id,
                request.getRoleIds(),
                user.getCollegeId(),
                userDataScopeMapper.selectCollegeIds(id),
                userDataScopeMapper.selectMajorIds(id));
        replaceUserRoles(id, request.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserDataScope(Long id, UserDataScopeRequest request) {
        ensureSchoolUserManagement();
        SysUser user = requireUser(id);
        LinkedHashSet<Long> collegeIds = new LinkedHashSet<>(request.getCollegeIds() == null ? List.of() : request.getCollegeIds());
        LinkedHashSet<Long> majorIds = new LinkedHashSet<>(request.getMajorIds() == null ? List.of() : request.getMajorIds());
        collegeIds.forEach(this::requireCollege);
        majorIds.forEach(this::requireMajor);
        authorizationGuard.assertCanSetUserAuthorization(
                id, userRoleMapper.selectRoleIds(id), user.getCollegeId(), collegeIds, majorIds);
        // 先物理删除旧授权再重建：主键由 ASSIGN_ID 生成、审计字段由 AuditMetaObjectHandler 自动填充，
        // 规避复用合成 id 且唯一键不含 deleted 导致的唯一冲突/授权错乱（P0-14）。
        userDataScopeMapper.deleteByUserId(id);
        for (Long collegeId : collegeIds) {
            SysUserDataScope scope = new SysUserDataScope();
            scope.setUserId(id);
            scope.setCollegeId(collegeId);
            userDataScopeMapper.insert(scope);
        }
        for (Long majorId : majorIds) {
            SysUserDataScope scope = new SysUserDataScope();
            scope.setUserId(id);
            scope.setMajorId(majorId);
            userDataScopeMapper.insert(scope);
        }
    }

    @Override
    public List<RoleVO> listRoles(String keyword) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .orderByAsc(SysRole::getSort)
                .orderByAsc(SysRole::getCode);
        String normalized = trimToNull(keyword);
        if (normalized != null) {
            wrapper.and(w -> w.like(SysRole::getCode, normalized).or().like(SysRole::getName, normalized));
        }
        return roleMapper.selectList(wrapper).stream().map(this::toRoleVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRole(RoleSaveRequest request) {
        authorizationGuard.requireSystemRoleManagement();
        String code = normalizeRequired(request.getCode(), "角色编码不能为空");
        if (roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code).last("LIMIT 1")) != null) {
            throw new BizException("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setCode(code);
        fillRole(role, request);
        roleMapper.insert(role);
        return role.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, RoleSaveRequest request) {
        authorizationGuard.requireSystemRoleManagement();
        SysRole role = requireRole(id);
        authorizationGuard.assertCanManageRole(id);
        String code = normalizeRequired(request.getCode(), "角色编码不能为空");
        SysRole exists = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code).last("LIMIT 1"));
        if (exists != null && !exists.getId().equals(id)) {
            throw new BizException("角色编码已存在");
        }
        role.setCode(code);
        fillRole(role, request);
        roleMapper.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        authorizationGuard.requireSystemRoleManagement();
        requireRole(id);
        authorizationGuard.assertCanManageRole(id);
        roleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRolePermissions(Long id, RolePermissionAssignRequest request) {
        authorizationGuard.requireSystemRoleManagement();
        requireRole(id);
        // 先校验并按权限去重（同一权限多次出现时以最后一次范围为准，规避唯一键冲突）。
        LinkedHashMap<Long, String> scopeByPermission = new LinkedHashMap<>();
        for (RolePermissionAssignRequest.Item item : request.getPermissions()) {
            requirePermission(item.getPermissionId());
            String scopeType = normalizeRequired(item.getScopeType(), "范围类型不能为空");
            if (!SCOPE_TYPES.contains(scopeType)) {
                throw new BizException("范围类型不合法：" + scopeType);
            }
            scopeByPermission.put(item.getPermissionId(), scopeType);
        }
        authorizationGuard.assertCanSetRolePermissions(id, scopeByPermission);
        // 物理删除旧授权后重建：主键 ASSIGN_ID、审计字段自动填充，规避 id 复用 + 唯一键缺 deleted 冲突（P0-14）。
        rolePermissionMapper.deleteByRoleId(id);
        scopeByPermission.forEach((permissionId, scopeType) -> {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(id);
            relation.setPermissionId(permissionId);
            relation.setScopeType(scopeType);
            rolePermissionMapper.insert(relation);
        });
    }

    @Override
    public List<PermissionVO> permissionTree() {
        List<PermissionVO> permissions = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSort)
                .orderByAsc(SysPermission::getCode)).stream().map(this::toPermissionVO).toList();
        return buildTree(permissions);
    }

    @Override
    public List<PermissionVO> rolePermissions(Long roleId) {
        requireRole(roleId);
        Map<Long, String> scopes = new LinkedHashMap<>();
        for (SysRolePermission relation : rolePermissionMapper.selectByRoleId(roleId)) {
            scopes.put(relation.getPermissionId(), relation.getScopeType());
        }
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                        .orderByAsc(SysPermission::getSort)
                        .orderByAsc(SysPermission::getCode))
                .stream()
                .filter(permission -> scopes.containsKey(permission.getId()))
                .map(permission -> {
                    PermissionVO vo = toPermissionVO(permission);
                    vo.setScopeType(scopes.get(permission.getId()));
                    return vo;
                })
                .toList();
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new BizException("用户至少需要一个角色");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(roleIds);
        unique.forEach(this::requireRole);
        // 物理删除旧关联后重建：主键 ASSIGN_ID、审计字段自动填充，规避 id 复用 + 唯一键缺 deleted 冲突（P0-14）。
        userRoleMapper.deleteByUserId(userId);
        for (Long roleId : unique) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    private void normalizeUserRequest(UserSaveRequest request) {
        if (!USER_STATUSES.contains(request.getStatus())) {
            throw new BizException("用户状态不合法");
        }
        if (!USER_TYPES.contains(request.getUserType())) {
            throw new BizException("用户类型不合法");
        }
    }

    private void ensureStaffHasNoStudentBinding(UserSaveRequest request) {
        if (request.getStudentId() != null) {
            throw new BizException("STAFF 账号不能关联学生档案");
        }
    }

    private void lockTargetCollege(Long collegeId, CollegeParentGuard.Operation operation) {
        if (collegeId != null) {
            // 用户历史语义允许归属停用学院；这里只锁定并校验父行仍存在。
            collegeParentGuard.lockExisting(collegeId, operation);
        }
    }

    private void ensureStudentBindingUnchanged(SysUser user, UserSaveRequest request) {
        if (!Objects.equals(user.getUsername(), request.getUsername().trim())
                || !Objects.equals(user.getCollegeId(), request.getCollegeId())
                || !Objects.equals(user.getStudentId(), request.getStudentId())) {
            throw new BizException("学生账号的用户名、学院和学生绑定只能通过学生管理维护");
        }
    }

    private void ensureStudentRoleOnly(List<Long> roleIds) {
        LinkedHashSet<Long> unique = roleIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(roleIds);
        if (unique.size() != 1) {
            throw new BizException("学生账号只能绑定系统 STUDENT 角色");
        }
        Long roleId = unique.iterator().next();
        SysRole role = roleId == null ? null : roleMapper.selectById(roleId);
        if (role == null || !"STUDENT".equals(role.getCode())) {
            throw new BizException("学生账号只能绑定系统 STUDENT 角色");
        }
    }

    private void fillUser(SysUser user, UserSaveRequest request) {
        fillEditableUserFields(user, request);
        user.setUserType(request.getUserType());
        user.setCollegeId(request.getCollegeId());
        user.setStudentId(request.getStudentId());
    }

    private void fillEditableUserFields(SysUser user, UserSaveRequest request) {
        user.setRealName(normalizeRequired(request.getRealName(), "真实姓名不能为空"));
        user.setWorkNo(trimToNull(request.getWorkNo()));
        user.setEmail(trimToNull(request.getEmail()));
        user.setPhone(trimToNull(request.getPhone()));
        user.setStatus(request.getStatus());
    }

    private void fillRole(SysRole role, RoleSaveRequest request) {
        role.setName(normalizeRequired(request.getName(), "角色名称不能为空"));
        role.setDescription(trimToNull(request.getDescription()));
        role.setSort(request.getSort() == null ? 0 : request.getSort());
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private SysUser requireUser(Long id) {
        if (id == null) {
            throw new BizException("用户ID不能为空");
        }
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private SysRole requireRole(Long id) {
        if (id == null) {
            throw new BizException("角色ID不能为空");
        }
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException("角色不存在");
        }
        return role;
    }

    private SysPermission requirePermission(Long id) {
        if (id == null) {
            throw new BizException("权限ID不能为空");
        }
        SysPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BizException("权限不存在");
        }
        return permission;
    }

    private SysCollege requireCollege(Long id) {
        SysCollege college = collegeMapper.selectById(id);
        if (college == null) {
            throw new BizException("学院不存在");
        }
        return college;
    }

    private void requireMajor(Long id) {
        if (majorMapper.selectById(id) == null) {
            throw new BizException("专业不存在");
        }
    }

    private UserVO toUserVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setWorkNo(user.getWorkNo());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setUserType(user.getUserType());
        vo.setCollegeId(user.getCollegeId());
        if (user.getCollegeId() != null) {
            SysCollege college = collegeMapper.selectById(user.getCollegeId());
            vo.setCollegeName(college == null ? null : college.getName());
        }
        vo.setStudentId(user.getStudentId());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setMustChangePwd(user.getMustChangePwd());
        vo.setRoles(userRoleMapper.selectRoleIds(user.getId()).stream()
                .map(roleMapper::selectById)
                .filter(role -> role != null)
                .map(this::toRoleVO)
                .toList());
        vo.setDataScopeCollegeIds(new ArrayList<>(userDataScopeMapper.selectCollegeIds(user.getId())));
        vo.setDataScopeMajorIds(new ArrayList<>(userDataScopeMapper.selectMajorIds(user.getId())));
        return vo;
    }

    private RoleVO toRoleVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setDescription(role.getDescription());
        vo.setSort(role.getSort());
        vo.setStatus(role.getStatus());
        return vo;
    }

    private PermissionVO toPermissionVO(SysPermission permission) {
        PermissionVO vo = new PermissionVO();
        vo.setId(permission.getId());
        vo.setCode(permission.getCode());
        vo.setName(permission.getName());
        vo.setType(permission.getType());
        vo.setParentId(permission.getParentId());
        vo.setPath(permission.getPath());
        vo.setSort(permission.getSort());
        vo.setStatus(permission.getStatus());
        return vo;
    }

    private List<PermissionVO> buildTree(List<PermissionVO> permissions) {
        Map<Long, PermissionVO> byId = new LinkedHashMap<>();
        List<PermissionVO> roots = new ArrayList<>();
        for (PermissionVO permission : permissions) {
            byId.put(permission.getId(), permission);
        }
        for (PermissionVO permission : permissions) {
            if (permission.getParentId() == null || !byId.containsKey(permission.getParentId())) {
                roots.add(permission);
            } else {
                byId.get(permission.getParentId()).getChildren().add(permission);
            }
        }
        return roots;
    }

    private String normalizeRequired(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BizException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
