package cn.edu.gpnu.platform.business.video.dto;

import lombok.Data;

import java.util.List;

@Data
public class VideoAssignRequest {

    private List<Long> reviewerIds;

    private Long groupId;
}
