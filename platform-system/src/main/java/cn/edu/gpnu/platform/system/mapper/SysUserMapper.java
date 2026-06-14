package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            SELECT *
              FROM sys_user
             WHERE username = #{username}
               AND deleted = 0
             LIMIT 1
            """)
    SysUser selectByUsername(@Param("username") String username);
}
