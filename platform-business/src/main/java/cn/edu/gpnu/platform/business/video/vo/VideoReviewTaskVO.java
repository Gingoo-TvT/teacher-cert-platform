package cn.edu.gpnu.platform.business.video.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class VideoReviewTaskVO {

    private Long id;
    private Long videoReviewId;
    private Long reviewerId;
    private String reviewerName;
    private String reviewerRole;
    private Integer score;
    private Map<String, Integer> dimensionScores;
    private String comment;
    private String conclusion;
    private Integer submitted;
    private LocalDateTime submitTime;
}
