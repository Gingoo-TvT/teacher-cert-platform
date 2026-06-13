package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TrainingGoalConfigVO {

    private Long id;
    private String trainingGoalCode;
    private String trainingGoalName;
    private String defaultSegment;
    private String defaultSegmentName;
    private List<String> allowedSegments = new ArrayList<>();
    private String defaultInternshipLocation;
    private String defaultInternshipLocationName;
    private List<String> allowedInternshipLocations = new ArrayList<>();
    private Integer status;
}
