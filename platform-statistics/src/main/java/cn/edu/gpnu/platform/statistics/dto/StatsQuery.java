package cn.edu.gpnu.platform.statistics.dto;

import lombok.Data;

@Data
public class StatsQuery {

    private String assessmentYear;
    private String collegeId;
    private String internalMajorCode;
    private String className;
    private String teachingSegment;
    private String teachingSubjectCode;
    private String status;
    private String keyword;
}
