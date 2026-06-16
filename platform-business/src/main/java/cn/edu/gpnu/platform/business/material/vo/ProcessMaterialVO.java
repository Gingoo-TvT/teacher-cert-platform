package cn.edu.gpnu.platform.business.material.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessMaterialVO {

    private Long id;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private Long collegeId;
    private String assessmentYear;
    private String category;
    private String categoryLabel;
    private Long fileId;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Long uploaderId;
    private LocalDateTime uploadTime;
    private String status;
    private String statusLabel;
    private Integer locked;
    private String firstReviewStatus;
    private String firstReviewComment;
    private String secondReviewStatus;
    private String secondReviewComment;
}
