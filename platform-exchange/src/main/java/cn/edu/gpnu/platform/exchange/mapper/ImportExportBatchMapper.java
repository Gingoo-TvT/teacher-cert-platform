package cn.edu.gpnu.platform.exchange.mapper;

import cn.edu.gpnu.platform.exchange.entity.ImportExportBatch;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ImportExportBatchMapper extends BaseMapper<ImportExportBatch> {

    @Select("SELECT * FROM import_export_batch WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ImportExportBatch selectByIdForUpdate(@Param("id") Long id);
}
