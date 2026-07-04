package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysDictItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysDictItemMapper extends BaseMapper<SysDictItem> {

    /**
     * 含软删行的编码存在性检查（唯一键 uk_sys_dict_item_type_code_year 不含 deleted，见 Phase43.4）：
     * 若仅按 deleted=0 过滤，软删后同类型+编码+年度版本重建会绕过应用层校验、直接撞库抛裸 DuplicateKeyException。
     */
    @Select("""
            SELECT COUNT(*)
              FROM sys_dict_item
             WHERE type_code = #{typeCode}
               AND item_code = #{itemCode}
               AND year_version = #{yearVersion}
               AND (#{excludeId} IS NULL OR id <> #{excludeId})
            """)
    Long countByItemIncludingDeleted(@Param("typeCode") String typeCode,
                                      @Param("itemCode") String itemCode,
                                      @Param("yearVersion") String yearVersion,
                                      @Param("excludeId") Long excludeId);
}
