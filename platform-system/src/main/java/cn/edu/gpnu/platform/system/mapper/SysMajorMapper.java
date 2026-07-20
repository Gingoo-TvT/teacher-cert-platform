package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysMajor;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysMajorMapper extends BaseMapper<SysMajor> {

    @Select("""
            SELECT COUNT(*)
              FROM sys_major
             WHERE internal_major_code = #{code}
               AND year_version = #{yearVersion}
               AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    Long countByCodeYearIncludingDeleted(@Param("code") String code,
                                         @Param("yearVersion") String yearVersion,
                                         @Param("excludeId") Long excludeId);

    @Select("""
            SELECT college_id
              FROM sys_major
             WHERE id = #{id}
               AND deleted = 0
             FOR UPDATE
            """)
    Long selectCollegeIdForUpdate(@Param("id") Long id);
}
