package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

@Data
public class BackupRecordVO {

    private Long id;
    private String backupType;
    private String status;
    private String scope;
    private String storageUri;
    private String startedAt;
    private String finishedAt;
    private Long operatorId;
    private String remark;
    private String errorMessage;
}
