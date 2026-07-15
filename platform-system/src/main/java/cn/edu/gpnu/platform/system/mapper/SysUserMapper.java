package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            SELECT *
              FROM sys_user
             WHERE username = #{username}
               AND deleted = 0
             LIMIT 1
            """)
    SysUser selectByUsername(@Param("username") String username);

    @Select("""
            SELECT *
              FROM sys_user
             WHERE student_id = #{studentId}
               AND deleted = 0
               AND status = 'ENABLED'
             LIMIT 1
            """)
    SysUser selectEnabledByStudentId(@Param("studentId") Long studentId);

    // MySQL 单表 UPDATE 按 SET 顺序求值；计数递增必须放最后，前两个 CASE 才能基于旧计数判定锁定。
    @Update("""
            UPDATE sys_user
               SET status = CASE
                       WHEN failed_login_count + 1 >= #{lockThreshold} THEN 'LOCKED'
                       ELSE status
                   END,
                   locked_until = CASE
                       WHEN failed_login_count + 1 >= #{lockThreshold} THEN #{lockedUntil}
                       ELSE locked_until
                   END,
                   failed_login_count = failed_login_count + 1,
                   updated_by = 0,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = #{userId}
               AND status = 'ENABLED'
               AND deleted = 0
            """)
    int recordLoginFailure(@Param("userId") Long userId,
                           @Param("lockThreshold") int lockThreshold,
                           @Param("lockedUntil") LocalDateTime lockedUntil);

    @Select("""
            SELECT DISTINCT u.*
              FROM sys_user u
              JOIN sys_user_role ur ON ur.user_id = u.id AND ur.deleted = 0
              JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0
             WHERE r.code = #{roleCode}
               AND u.deleted = 0
               AND u.status = 'ENABLED'
               AND (#{collegeId} IS NULL OR u.college_id = #{collegeId})
             ORDER BY u.id
            """)
    List<SysUser> selectEnabledByRoleAndCollege(@Param("roleCode") String roleCode,
                                                @Param("collegeId") Long collegeId);
}
