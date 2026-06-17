package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
