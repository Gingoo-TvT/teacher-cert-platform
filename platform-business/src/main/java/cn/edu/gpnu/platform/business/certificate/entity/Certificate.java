package cn.edu.gpnu.platform.business.certificate.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("certificate")
public class Certificate extends BaseEntity {

    private Long studentId;
    private Long collegeId;
    private String assessmentYear;
    private String certNo;
    private String studentNo;
    private String studentName;
    private String idCardType;
    private String idCardNo;
    private String idCardHmac;
    private String educationLevel;
    private String trainingGoal;
    private String teachingSegment;
    private String teachingSubjectCode;
    private String teachingSubjectName;
    private String issuer;
    private String issueDate;
    private String validUntil;
    private String status;
    private String voidReason;
    private Long voidOperatorId;
    private LocalDateTime voidTime;
    private String reissueOriginCertNo;
    private String correctionReason;
    private Integer locked;
}
