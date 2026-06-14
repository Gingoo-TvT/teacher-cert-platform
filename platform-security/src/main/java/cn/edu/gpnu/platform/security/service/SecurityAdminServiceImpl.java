package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.api.PageResult;
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
import cn.edu.gpnu.platform.system.mapper.SysCollegeMapper;
import cn.edu.gpnu.platform.system.mapper.SysMajorMapper;
import cn.edu.gpnu.platform.system.mapper.SysPermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysRolePermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserDataScopeMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import cn.edu.gpnu.platform.system.vo.PermissionVO;
import cn.edu.gpnu.platform.system.vo.RoleVO;
import cn.edu.gpnu.platform.system.vo.UserVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SecurityAdminServiceImpl implements SecurityAdminService {

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
    private final PasswordEncoder passwordEncoder;

    @Value("${platform.security.initial-password:ChangeMe123!}")
    private String initialPassword;

    @Override
    public PageResult<UserVO> listUsers(String keyword, String status, Long collegeId) {
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
        List<UserVO> records = userMapper.selectList(wrapper).stream().map(this::toUserVO).toList();
        return new PageResult<>(records.size(), records);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createUser(UserSaveRequest request) {
        normalizeUserRequest(request);
        if (userMapper.selectByUsername(request.getUsername().trim()) != null) {
            throw new BizException("用户名已存在");
        }
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
        SysUser user = requireUser(id);
        normalizeUserRequest(request);
        SysUser exists = userMapper.selectByUsername(request.getUsername().trim());
        if (exists != null && !exists.getId().equals(id)) {
            throw new BizException("用户名已存在");
        }
        user.setUsername(request.getUsername().trim());
        fillUser(user, request);
        userMapper.updateById(user);
        replaceUserRoles(id, request.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long id) {
        requireUser(id);
        userMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id) {
        requireUser(id);
        userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, id)
                .set(SysUser::getPasswordHash, passwordEncoder.encode(initialPassword))
                .set(SysUser::getMustChangePwd, 1)
                .set(SysUser::getFailedLoginCount, 0)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getStatus, "ENABLED"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserRoles(Long id, UserRoleAssignRequest request) {
        requireUser(id);
        replaceUserRoles(id, request.getRoleIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUserDataScope(Long id, UserDataScopeRequest request) {
        requireUser(id);
        Long operatorId = UserContext.getUserIdOrSystem();
        userDataScopeMapper.disableByUserId(id, operatorId);
        LinkedHashSet<Long> collegeIds = new LinkedHashSet<>(request.getCollegeIds() == null ? List.of() : request.getCollegeIds());
        LinkedHashSet<Long> majorIds = new LinkedHashSet<>(request.getMajorIds() == null ? List.of() : request.getMajorIds());
        long seed = id * 1000;
        int index = 1;
        for (Long collegeId : collegeIds) {
            requireCollege(collegeId);
            userDataScopeMapper.upsert(seed + index++, id, collegeId, null, operatorId);
        }
        for (Long majorId : majorIds) {
            requireMajor(majorId);
            userDataScopeMapper.upsert(seed + index++, id, null, majorId, operatorId);
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
        SysRole role = requireRole(id);
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
        requireRole(id);
        roleMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRolePermissions(Long id, RolePermissionAssignRequest request) {
        requireRole(id);
        Long operatorId = UserContext.getUserIdOrSystem();
        rolePermissionMapper.disableByRoleId(id, operatorId);
        long seed = id * 1000;
        int index = 1;
        for (RolePermissionAssignRequest.Item item : request.getPermissions()) {
            requirePermission(item.getPermissionId());
            String scopeType = normalizeRequired(item.getScopeType(), "范围类型不能为空");
            if (!SCOPE_TYPES.contains(scopeType)) {
                throw new BizException("范围类型不合法：" + scopeType);
            }
            rolePermissionMapper.upsert(seed + index++, id, item.getPermissionId(), scopeType, operatorId);
        }
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
        Long operatorId = UserContext.getUserIdOrSystem();
        userRoleMapper.disableByUserId(userId, operatorId);
        LinkedHashSet<Long> unique = new LinkedHashSet<>(roleIds);
        int index = 1;
        long seed = userId * 1000;
        for (Long roleId : unique) {
            requireRole(roleId);
            userRoleMapper.upsert(seed + index++, userId, roleId, operatorId);
        }
    }

    private void normalizeUserRequest(UserSaveRequest request) {
        if (!USER_STATUSES.contains(request.getStatus())) {
            throw new BizException("用户状态不合法");
        }
        if (!USER_TYPES.contains(request.getUserType())) {
            throw new BizException("用户类型不合法");
        }
        if (request.getCollegeId() != null) {
            requireCollege(request.getCollegeId());
        }
    }

    private void fillUser(SysUser user, UserSaveRequest request) {
        user.setRealName(normalizeRequired(request.getRealName(), "真实姓名不能为空"));
        user.setWorkNo(trimToNull(request.getWorkNo()));
        user.setEmail(trimToNull(request.getEmail()));
        user.setPhone(trimToNull(request.getPhone()));
        user.setStatus(request.getStatus());
        user.setUserType(request.getUserType());
        user.setCollegeId(request.getCollegeId());
        user.setStudentId(request.getStudentId());
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
