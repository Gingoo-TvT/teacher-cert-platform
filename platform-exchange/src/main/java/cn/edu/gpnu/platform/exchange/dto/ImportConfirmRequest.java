package cn.edu.gpnu.platform.exchange.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImportConfirmRequest {

    @NotBlank(message = "导入策略不能为空")
    private String strategy;
}
