package cn.edu.gpnu.platform.statistics.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class StatsDetailVO {

    private String studentId;
    private String studentNo;
    private String studentName;
    private String collegeId;
    private String collegeName;
    private String fieldName;
    private String errorReason;
    private Map<String, String> values = new LinkedHashMap<>();
}
