package cn.edu.gpnu.platform.business.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewerGroupRequest {

    @NotBlank(message = "评审组名称不能为空")
    @Size(max = 100, message = "评审组名称过长")
    private String name;

    private String status;
}
