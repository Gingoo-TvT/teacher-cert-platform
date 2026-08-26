package cn.edu.gpnu.platform.business.student.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("student")
public class Student extends BaseEntity {

    private String studentNo;
    private String name;
    private String gender;
    private String idCardType;
    private String idCardNo;
    private String idCardHmac;
    private String birthDate;
    private String identityType;
    private String sourceProvince;
    private String sourceCity;
    private String sourceCounty;
    private String sourceFull;
    private Long collegeId;
    private String grade;
    private String className;
    private String status;
    private Integer locked;
    private Long firstReviewerId;
    private LocalDateTime firstReviewTime;
    private String firstReviewComment;
    private Long secondReviewerId;
    private LocalDateTime secondReviewTime;
    private String secondReviewComment;
}
