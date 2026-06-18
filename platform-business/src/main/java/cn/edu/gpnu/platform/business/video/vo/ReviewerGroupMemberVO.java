package cn.edu.gpnu.platform.business.video.vo;

import lombok.Data;

@Data
public class ReviewerGroupMemberVO {

    private Long id;
    private Long reviewerUserId;
    private String reviewerName;
    private String workNo;
}
