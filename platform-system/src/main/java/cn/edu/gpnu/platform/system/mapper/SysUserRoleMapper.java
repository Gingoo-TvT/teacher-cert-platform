package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
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

    // Phase 37a-part2 (P0-14)：物理删除用户的全部角色关联。用于"先删后插"重建，避免复用合成 id +
    // 唯一键缺 deleted 导致的授权错乱/唯一冲突（重建后由 insert 自动生成雪花 id）。
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
