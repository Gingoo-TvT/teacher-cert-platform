package cn.edu.gpnu.platform.exchange.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("import_error_detail")
public class ImportErrorDetail extends BaseEntity {

    private Long batchId;
    private String batchNo;
    private Integer rowNo;
    private String studentNo;
    private String studentName;
    private String fieldName;
    private String errorValue;
    private String errorReason;
    private String suggestion;
}
