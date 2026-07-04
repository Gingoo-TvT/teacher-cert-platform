package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysDictType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysDictTypeMapper extends BaseMapper<SysDictType> {

    /**
     * 含软删行的编码存在性检查（唯一键 uk_sys_dict_type_code 不含 deleted，见 Phase43.4）：
     * 若仅按 deleted=0 过滤，软删后同码重建会绕过应用层校验、直接撞库抛裸 DuplicateKeyException。
     */
    @Select("""
            SELECT COUNT(*)
              FROM sys_dict_type
             WHERE type_code = #{typeCode}
               AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    Long countByTypeCodeIncludingDeleted(@Param("typeCode") String typeCode, @Param("excludeId") Long excludeId);
}
