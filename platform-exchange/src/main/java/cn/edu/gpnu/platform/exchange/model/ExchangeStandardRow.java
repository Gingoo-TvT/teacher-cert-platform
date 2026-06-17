package cn.edu.gpnu.platform.exchange.model;

import lombok.Data;

@Data
public class ExchangeStandardRow {

    private String sequenceNo;
    private String schoolCode;
    private String schoolName;
    private String studentNo;
    private String name;
    private String gender;
    private String idCardType;
    private String idCardNo;
    private String birthDate;
    private String identityType;
    private String sourcePlace;
    private String secondDisciplineCode;
    private String secondDisciplineName;
    private String internalMajorCode;
    private String internalMajorName;
    private String educationLevel;
    private String trainingGoal;
    private String internshipOrgMode;
    private String internshipLocation;
    private String teachingSegment;
    private String teachingSubject;
    private String interviewOrgMode;
    private String certNo;
    private String validUntil;
    private String issuer;
    private String remark;
}
