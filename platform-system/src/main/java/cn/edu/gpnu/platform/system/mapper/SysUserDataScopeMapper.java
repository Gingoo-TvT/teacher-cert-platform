package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysUserDataScope;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SysUserDataScopeMapper extends BaseMapper<SysUserDataScope> {

    @Select("""
            SELECT DISTINCT college_id
              FROM sys_user_data_scope
             WHERE user_id = #{userId}
               AND deleted = 0
               AND college_id IS NOT NULL
             ORDER BY college_id
            """)
    List<Long> selectCollegeIds(@Param("userId") Long userId);

    @Select("""
            SELECT DISTINCT major_id
              FROM sys_user_data_scope
             WHERE user_id = #{userId}
               AND deleted = 0
               AND major_id IS NOT NULL
             ORDER BY major_id
            """)
    List<Long> selectMajorIds(@Param("userId") Long userId);

    @Update("""
            UPDATE sys_user_data_scope
               SET deleted = 1, updated_by = #{operatorId}, updated_at = NOW()
             WHERE user_id = #{userId}
               AND deleted = 0
            """)
    int disableByUserId(@Param("userId") Long userId, @Param("operatorId") Long operatorId);

    @Insert("""
            INSERT INTO sys_user_data_scope
                (id, user_id, college_id, major_id, created_by, created_at, updated_by, updated_at, deleted)
            VALUES
                (#{id}, #{userId}, #{collegeId}, #{majorId}, #{operatorId}, NOW(), #{operatorId}, NOW(), 0)
            ON DUPLICATE KEY UPDATE
                updated_by = VALUES(updated_by),
                updated_at = NOW(),
                deleted = 0
            """)
    int upsert(@Param("id") Long id, @Param("userId") Long userId, @Param("collegeId") Long collegeId,
               @Param("majorId") Long majorId, @Param("operatorId") Long operatorId);

    // Phase 37a-part2 (P0-14)：物理删除用户的全部数据范围授权，配合 insert 重建。
    @Delete("DELETE FROM sys_user_data_scope WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
