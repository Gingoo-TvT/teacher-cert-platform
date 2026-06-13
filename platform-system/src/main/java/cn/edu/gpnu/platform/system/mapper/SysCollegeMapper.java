package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysCollege;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysCollegeMapper extends BaseMapper<SysCollege> {

    @Select("""
            SELECT COUNT(*)
              FROM sys_college
             WHERE code = #{code}
               AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    Long countByCodeIncludingDeleted(@Param("code") String code, @Param("excludeId") Long excludeId);
}
