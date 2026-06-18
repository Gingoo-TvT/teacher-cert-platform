package cn.edu.gpnu.platform.business.video.vo;

import lombok.Data;

import java.util.List;

@Data
public class ReviewerGroupVO {

    private Long id;
    private Long collegeId;
    private String name;
    private String status;
    private Integer memberCount;
    private List<ReviewerGroupMemberVO> members;
}
