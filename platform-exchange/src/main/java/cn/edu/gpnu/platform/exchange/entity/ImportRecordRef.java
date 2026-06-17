package cn.edu.gpnu.platform.exchange.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("import_record_ref")
public class ImportRecordRef extends BaseEntity {

    private Long batchId;
    private String batchNo;
    private String tableName;
    private Long recordId;
    private String action;
    private String beforeJson;
    private String afterJson;
    private Integer rowNo;
    private String remark;
}
