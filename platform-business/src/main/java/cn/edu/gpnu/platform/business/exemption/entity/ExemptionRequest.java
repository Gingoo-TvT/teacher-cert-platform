package cn.edu.gpnu.platform.business.exemption.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exemption_request")
public class ExemptionRequest extends BaseEntity {

    private Long studentId;
    private Long collegeId;
    private String assessmentYear;
    private String teachingSegment;
    private String subject;
    private String subjectLabel;
    private String basis;
    private String basisLabel;
    private String remark;
    private String firstReviewStatus;
    private Long firstReviewerId;
    private LocalDateTime firstReviewTime;
    private String firstReviewComment;
    private String secondReviewStatus;
    private Long secondReviewerId;
    private LocalDateTime secondReviewTime;
    private String secondReviewComment;
    private String finalStatus;
    private Integer includedInExam;
    private Integer locked;

    @TableField(exist = false)
    private Long materialCount;
}
