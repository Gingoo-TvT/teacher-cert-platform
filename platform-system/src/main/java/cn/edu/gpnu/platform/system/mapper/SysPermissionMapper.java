package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysPermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    @Select("""
            SELECT DISTINCT p.code
              FROM sys_permission p
              JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
              JOIN sys_user_role ur ON ur.role_id = rp.role_id AND ur.deleted = 0
              JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 1
             WHERE ur.user_id = #{userId}
               AND p.deleted = 0
               AND p.status = 1
             ORDER BY p.code
            """)
    List<String> selectCodesByUserId(@Param("userId") Long userId);
}
