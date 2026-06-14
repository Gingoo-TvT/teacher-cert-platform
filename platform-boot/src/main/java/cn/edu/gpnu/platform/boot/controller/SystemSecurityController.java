package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.security.service.SecurityAdminService;
import cn.edu.gpnu.platform.system.dto.RolePermissionAssignRequest;
import cn.edu.gpnu.platform.system.dto.RoleSaveRequest;
import cn.edu.gpnu.platform.system.dto.UserDataScopeRequest;
import cn.edu.gpnu.platform.system.dto.UserRoleAssignRequest;
import cn.edu.gpnu.platform.system.dto.UserSaveRequest;
import cn.edu.gpnu.platform.system.vo.PermissionVO;
import cn.edu.gpnu.platform.system.vo.RoleVO;
import cn.edu.gpnu.platform.system.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "系统账号角色权限")
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemSecurityController {

    private final SecurityAdminService securityAdminService;

    @Operation(summary = "用户列表")
    @PreAuthorize("@pms.has('system:user:manage')")
    @DataScope(permission = "system:user:manage")
    @GetMapping("/user")
    public Result<PageResult<UserVO>> listUsers(@RequestParam(value = "keyword", required = false) String keyword,
                                                @RequestParam(value = "status", required = false) String status,
                                                @RequestParam(value = "collegeId", required = false) Long collegeId) {
        return Result.ok(securityAdminService.listUsers(keyword, status, collegeId));
    }

    @Operation(summary = "新增用户")
    @PreAuthorize("@pms.has('system:user:manage')")
    @AuditLog(bizType = "systemUser", operation = "create")
    @PostMapping("/user")
    public Result<Long> createUser(@Valid @RequestBody UserSaveRequest request) {
        return Result.ok(securityAdminService.createUser(request));
    }

    @Operation(summary = "修改用户")
    @PreAuthorize("@pms.has('system:user:manage')")
    @AuditLog(bizType = "systemUser", operation = "update")
    @PutMapping("/user/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        securityAdminService.updateUser(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除用户")
    @PreAuthorize("@pms.has('system:user:manage')")
    @AuditLog(bizType = "systemUser", operation = "delete")
    @DeleteMapping("/user/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        securityAdminService.deleteUser(id);
        return Result.ok();
    }

    @Operation(summary = "重置密码")
    @PreAuthorize("@pms.has('system:user:manage')")
    @AuditLog(bizType = "systemUser", operation = "resetPassword")
    @PutMapping("/user/{id}/reset-pwd")
    public Result<Void> resetPassword(@PathVariable Long id) {
        securityAdminService.resetPassword(id);
        return Result.ok();
    }

    @Operation(summary = "分配用户角色")
    @PreAuthorize("@pms.has('system:user:manage')")
    @AuditLog(bizType = "systemUser", operation = "assignRoles")
    @PutMapping("/user/{id}/roles")
    public Result<Void> assignUserRoles(@PathVariable Long id, @Valid @RequestBody UserRoleAssignRequest request) {
        securityAdminService.assignUserRoles(id, request);
        return Result.ok();
    }

    @Operation(summary = "分配用户数据范围")
    @PreAuthorize("@pms.has('system:user:manage')")
    @AuditLog(bizType = "systemUser", operation = "assignDataScope")
    @PutMapping("/user/{id}/data-scope")
    public Result<Void> assignUserDataScope(@PathVariable Long id, @RequestBody UserDataScopeRequest request) {
        securityAdminService.assignUserDataScope(id, request);
        return Result.ok();
    }

    @Operation(summary = "角色列表")
    @PreAuthorize("@pms.has('system:role:manage')")
    @DataScope(permission = "system:role:manage")
    @GetMapping("/role")
    public Result<List<RoleVO>> listRoles(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.ok(securityAdminService.listRoles(keyword));
    }

    @Operation(summary = "新增角色")
    @PreAuthorize("@pms.has('system:role:manage')")
    @AuditLog(bizType = "systemRole", operation = "create")
    @PostMapping("/role")
    public Result<Long> createRole(@Valid @RequestBody RoleSaveRequest request) {
        return Result.ok(securityAdminService.createRole(request));
    }

    @Operation(summary = "修改角色")
    @PreAuthorize("@pms.has('system:role:manage')")
    @AuditLog(bizType = "systemRole", operation = "update")
    @PutMapping("/role/{id}")
    public Result<Void> updateRole(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest request) {
        securityAdminService.updateRole(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除角色")
    @PreAuthorize("@pms.has('system:role:manage')")
    @AuditLog(bizType = "systemRole", operation = "delete")
    @DeleteMapping("/role/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        securityAdminService.deleteRole(id);
        return Result.ok();
    }

    @Operation(summary = "角色权限列表")
    @PreAuthorize("@pms.has('system:role:manage')")
    @DataScope(permission = "system:role:manage")
    @GetMapping("/role/{id}/permissions")
    public Result<List<PermissionVO>> rolePermissions(@PathVariable Long id) {
        return Result.ok(securityAdminService.rolePermissions(id));
    }

    @Operation(summary = "分配角色权限")
    @PreAuthorize("@pms.has('system:role:manage')")
    @AuditLog(bizType = "systemRole", operation = "assignPermissions")
    @PutMapping("/role/{id}/permissions")
    public Result<Void> assignRolePermissions(@PathVariable Long id,
                                              @Valid @RequestBody RolePermissionAssignRequest request) {
        securityAdminService.assignRolePermissions(id, request);
        return Result.ok();
    }

    @Operation(summary = "权限树")
    @PreAuthorize("@pms.has('system:perm:manage')")
    @DataScope(permission = "system:perm:manage")
    @GetMapping("/permission/tree")
    public Result<List<PermissionVO>> permissionTree() {
        return Result.ok(securityAdminService.permissionTree());
    }
}
