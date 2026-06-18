package cn.edu.gpnu.platform.business.video.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewerGroupMemberRequest {

    @NotNull(message = "评审教师不能为空")
    private Long reviewerUserId;
}
