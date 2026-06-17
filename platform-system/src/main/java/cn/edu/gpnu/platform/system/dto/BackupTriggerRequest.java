package cn.edu.gpnu.platform.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BackupTriggerRequest {

    @NotBlank(message = "备份类型不能为空")
    private String backupType;

    private String scope;
    private String remark;
}
