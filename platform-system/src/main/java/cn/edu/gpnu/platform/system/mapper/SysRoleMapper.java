package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            SELECT DISTINCT r.code
              FROM sys_role r
              JOIN sys_user_role ur ON ur.role_id = r.id AND ur.deleted = 0
             WHERE ur.user_id = #{userId}
               AND r.deleted = 0
               AND r.status = 1
             ORDER BY r.code
            """)
    List<String> selectCodesByUserId(@Param("userId") Long userId);
}
