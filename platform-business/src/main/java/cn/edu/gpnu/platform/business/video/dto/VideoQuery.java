package cn.edu.gpnu.platform.business.video.dto;

import lombok.Data;

@Data
public class VideoQuery {

    private String keyword;
    private String status;
    private Long collegeId;
    private Long studentId;
    private String assessmentYear;
    private Integer page;
    private Integer size;
}
