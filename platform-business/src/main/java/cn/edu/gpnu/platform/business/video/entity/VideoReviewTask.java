package cn.edu.gpnu.platform.business.video.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_review_task")
public class VideoReviewTask extends BaseEntity {

    private Long videoReviewId;
    private Long studentId;
    private Long collegeId;
    private Long reviewerId;
    private String reviewerRole;
    private Integer score;
    private String dimensionScoresJson;
    private String comment;
    private String conclusion;
    private Integer submitted;
    private LocalDateTime submitTime;
}
