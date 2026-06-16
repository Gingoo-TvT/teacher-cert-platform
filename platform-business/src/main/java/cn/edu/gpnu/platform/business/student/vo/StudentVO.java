package cn.edu.gpnu.platform.business.student.vo;

import lombok.Data;

@Data
public class StudentVO {

    private Long id;
    private String studentNo;
    private String name;
    private String gender;
    private String idCardType;
    private String idCardNo;
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
    private String statusLabel;
    private Integer locked;
    private String firstReviewComment;
    private String secondReviewComment;
}
