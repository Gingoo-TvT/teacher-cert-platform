package cn.edu.gpnu.platform.system.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("backup_record")
public class BackupRecord extends BaseEntity {

    private String backupType;
    private String status;
    private String scope;
    private String storageUri;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long operatorId;
    private String remark;
    private String errorMessage;

    /** Phase 41.2（P0-6）：真实产物元数据。 */
    private Long byteSize;
    private String checksum;
    private Integer tableCount;
    private Long rowCount;
}
