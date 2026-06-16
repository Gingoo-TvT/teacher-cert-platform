package cn.edu.gpnu.platform.business.video.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class VideoAssignRequest {

    @NotEmpty(message = "评审教师不能为空")
    private List<Long> reviewerIds;
}
