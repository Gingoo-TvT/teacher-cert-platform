package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysRolePermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * Serializes all application RBAC mutations on one stable row. Callers must hold a transaction
     * and acquire this lock before any consistent read of authorization state.
     */
    @Select("""
            SELECT id
             FROM sys_permission
             WHERE code = 'system:role:manage'
             FOR UPDATE
            """)
    Long lockAuthorizationState();

    /**
     * Loads the caller's currently effective grants. Authorization ceiling checks must use this
     * database view instead of the permission codes carried in the request's UserContext.
     */
    @Select("""
            SELECT rp.*
              FROM sys_role_permission rp
              JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 AND p.status = 1
              JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.deleted = 0
              JOIN sys_role r ON r.id = rp.role_id AND r.deleted = 0 AND r.status = 1
              JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0 AND u.status = 'ENABLED'
             WHERE ur.user_id = #{userId}
               AND rp.deleted = 0
             ORDER BY rp.permission_id,
                      FIELD(rp.scope_type, 'SYSTEM', 'SCHOOL', 'LOGIN_ALL', 'COLLEGE', 'SELF', 'ASSIGNED', 'NONE')
            """)
    List<SysRolePermission> selectEffectiveByUserId(@Param("userId") Long userId);

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

    @Select({
            "<script>",
            "SELECT * FROM sys_role_permission",
            "WHERE deleted = 0",
            "AND role_id IN",
            "<foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>",
            "#{roleId}",
            "</foreach>",
            "ORDER BY role_id, permission_id, id",
            "</script>"
    })
    List<SysRolePermission> selectByRoleIds(@Param("roleIds") Collection<Long> roleIds);

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
                updated_by = VALUES(updated_by),
                updated_at = NOW(),
                deleted = 0
            """)
    int upsert(@Param("id") Long id, @Param("roleId") Long roleId, @Param("permissionId") Long permissionId,
               @Param("scopeType") String scopeType, @Param("operatorId") Long operatorId);

    // Phase 37a-part2 (P0-14)：物理删除角色的全部权限关联，配合 insert 重建（避免复用合成 id +
    // 唯一键缺 deleted 导致的 scope_type/permission 错乱与唯一冲突）。
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
