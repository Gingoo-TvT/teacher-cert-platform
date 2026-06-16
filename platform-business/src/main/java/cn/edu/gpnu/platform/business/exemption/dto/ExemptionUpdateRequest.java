package cn.edu.gpnu.platform.business.exemption.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExemptionUpdateRequest {

    @NotBlank(message = "免考依据不能为空")
    private String basis;

    private String remark;
}
