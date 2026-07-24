package cn.edu.gpnu.platform.exchange.mapper;

import cn.edu.gpnu.platform.exchange.entity.ImportExportBatch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ImportExportBatchMapper extends BaseMapper<ImportExportBatch> {

    /**
     * rollback 单次读取批次元数据时使用；逐行导入不得调用，避免反复装载 preview_json。
     */
    @Select("SELECT * FROM import_export_batch WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ImportExportBatch selectByIdForUpdate(@Param("id") Long id);

    @Select("SELECT status FROM import_export_batch WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    String selectStatusByIdForUpdate(@Param("id") Long id);
}
