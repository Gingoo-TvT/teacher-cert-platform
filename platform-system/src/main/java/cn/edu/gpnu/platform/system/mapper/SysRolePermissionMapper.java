package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysRolePermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    @Select("""
            SELECT DISTINCT rp.scope_type
              FROM sys_role_permission rp
              JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 AND p.status = 1
              JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.deleted = 0
              JOIN sys_role r ON r.id = rp.role_id AND r.deleted = 0 AND r.status = 1
             WHERE ur.user_id = #{userId}
               AND rp.deleted = 0
               AND (#{permissionCode} IS NULL OR #{permissionCode} = '' OR p.code = #{permissionCode})
             ORDER BY FIELD(rp.scope_type, 'SYSTEM', 'SCHOOL', 'LOGIN_ALL', 'COLLEGE', 'SELF', 'ASSIGNED', 'NONE')
            """)
    List<String> selectScopeTypes(@Param("userId") Long userId, @Param("permissionCode") String permissionCode);

    @Select("""
            SELECT *
              FROM sys_role_permission
             WHERE role_id = #{roleId}
               AND deleted = 0
             ORDER BY permission_id
            """)
    List<SysRolePermission> selectByRoleId(@Param("roleId") Long roleId);

    @Update("""
            UPDATE sys_role_permission
               SET deleted = 1, updated_by = #{operatorId}, updated_at = NOW()
             WHERE role_id = #{roleId}
               AND deleted = 0
            """)
    int disableByRoleId(@Param("roleId") Long roleId, @Param("operatorId") Long operatorId);

    @Insert("""
            INSERT INTO sys_role_permission
                (id, role_id, permission_id, scope_type, created_by, created_at, updated_by, updated_at, deleted)
            VALUES
                (#{id}, #{roleId}, #{permissionId}, #{scopeType}, #{operatorId}, NOW(), #{operatorId}, NOW(), 0)
            ON DUPLICATE KEY UPDATE
                scope_type = VALUES(scope_type),
                updated_by = VALUES(updated_by),
                updated_at = NOW(),
                deleted = 0
            """)
    int upsert(@Param("id") Long id, @Param("roleId") Long roleId, @Param("permissionId") Long permissionId,
               @Param("scopeType") String scopeType, @Param("operatorId") Long operatorId);
}
