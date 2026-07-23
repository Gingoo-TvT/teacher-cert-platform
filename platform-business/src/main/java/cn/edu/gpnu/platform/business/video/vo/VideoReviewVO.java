package cn.edu.gpnu.platform.business.video.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class VideoReviewVO {

    private Long id;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private Long collegeId;
    private String assessmentYear;
    private Long videoFileId;
    private String videoFileName;
    private Integer durationSeconds;
    private String formatCheck;
    private String validationMessage;
    private String status;
    private String statusLabel;
    private Integer finalScore;
    private String finalConclusion;
    private Long arbitrateReviewer;
    private String arbitrateMode;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Integer locked;
    private List<VideoReviewTaskVO> tasks;
}
