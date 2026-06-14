package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("""
            SELECT role_id
              FROM sys_user_role
             WHERE user_id = #{userId}
               AND deleted = 0
             ORDER BY role_id
            """)
    List<Long> selectRoleIds(@Param("userId") Long userId);

    @Update("""
            UPDATE sys_user_role
               SET deleted = 1, updated_by = #{operatorId}, updated_at = NOW()
             WHERE user_id = #{userId}
               AND deleted = 0
            """)
    int disableByUserId(@Param("userId") Long userId, @Param("operatorId") Long operatorId);

    @Insert("""
            INSERT INTO sys_user_role (id, user_id, role_id, created_by, created_at, updated_by, updated_at, deleted)
            VALUES (#{id}, #{userId}, #{roleId}, #{operatorId}, NOW(), #{operatorId}, NOW(), 0)
            ON DUPLICATE KEY UPDATE
                updated_by = VALUES(updated_by),
                updated_at = NOW(),
                deleted = 0
            """)
    int upsert(@Param("id") Long id, @Param("userId") Long userId, @Param("roleId") Long roleId,
               @Param("operatorId") Long operatorId);
}
