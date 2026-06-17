package cn.edu.gpnu.platform.exchange.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("import_export_batch")
public class ImportExportBatch extends BaseEntity {

    private String batchNo;
    private String type;
    private String fileName;
    private Long operatorId;
    private LocalDateTime operateTime;
    private Integer total;
    private Integer successCount;
    private Integer failCount;
    private Long errorReportFileId;
    private String scopeJson;
    private String previewJson;
    private String strategy;
    private String status;
    private String remark;
}
