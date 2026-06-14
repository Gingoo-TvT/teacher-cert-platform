package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysParam;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysParamMapper extends BaseMapper<SysParam> {

    @Select("""
            SELECT param_value
              FROM sys_param
             WHERE param_key = #{key}
               AND deleted = 0
             LIMIT 1
            """)
    String selectValue(@Param("key") String key);
}
