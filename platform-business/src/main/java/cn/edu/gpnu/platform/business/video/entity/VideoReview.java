package cn.edu.gpnu.platform.business.video.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_review")
public class VideoReview extends BaseEntity {

    private Long studentId;
    private Long collegeId;
    private String assessmentYear;
    private Long videoFileId;
    private String videoFileName;
    private String fileMd5;
    private Integer durationSeconds;
    private String formatCheck;
    private String validationMessage;
    private String status;
    private Integer finalScore;
    private String finalConclusion;
    private Long arbitrateReviewer;
    private String arbitrateMode;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Integer locked;
}
