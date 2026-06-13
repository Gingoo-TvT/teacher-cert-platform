package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MajorVO {

    private Long id;
    private Long collegeId;
    private String collegeName;
    private String internalMajorCode;
    private String internalMajorName;
    private String secondDisciplineCode;
    private String secondDisciplineName;
    private Integer pilotScopeFlag;
    private String yearVersion;
    private Integer sort;
    private Integer status;
    private List<TrainingGoalVO> trainingGoals = new ArrayList<>();
}
