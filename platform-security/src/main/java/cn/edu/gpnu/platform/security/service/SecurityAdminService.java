package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.system.dto.RolePermissionAssignRequest;
import cn.edu.gpnu.platform.system.dto.RoleSaveRequest;
import cn.edu.gpnu.platform.system.dto.UserDataScopeRequest;
import cn.edu.gpnu.platform.system.dto.UserRoleAssignRequest;
import cn.edu.gpnu.platform.system.dto.UserSaveRequest;
import cn.edu.gpnu.platform.system.vo.PermissionVO;
import cn.edu.gpnu.platform.system.vo.RoleVO;
import cn.edu.gpnu.platform.system.vo.UserVO;

import java.util.List;

public interface SecurityAdminService {

    PageResult<UserVO> listUsers(String keyword, String status, Long collegeId, Integer page, Integer size);

    Long createUser(UserSaveRequest request);

    void updateUser(Long id, UserSaveRequest request);

    void deleteUser(Long id);

    /**
     * 重置账号口令。学生账号返回只展示一次的随机临时口令；STAFF 使用受控部署口令并返回 null。
     */
    String resetPassword(Long id);

    void assignUserRoles(Long id, UserRoleAssignRequest request);

    void assignUserDataScope(Long id, UserDataScopeRequest request);

    List<RoleVO> listRoles(String keyword);

    Long createRole(RoleSaveRequest request);

    void updateRole(Long id, RoleSaveRequest request);

    void deleteRole(Long id);

    void assignRolePermissions(Long id, RolePermissionAssignRequest request);

    List<PermissionVO> permissionTree();

    List<PermissionVO> rolePermissions(Long roleId);
}
