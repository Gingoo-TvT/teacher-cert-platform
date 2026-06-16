package cn.edu.gpnu.platform.business.training.vo;

import cn.edu.gpnu.platform.system.vo.TeachingSubjectVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TrainingOptionsVO {

    private String trainingGoal;
    private String defaultSegment;
    private List<String> allowedSegments = new ArrayList<>();
    private String defaultInternshipLocation;
    private List<String> allowedInternshipLocations = new ArrayList<>();
    private List<TeachingSubjectVO> subjects = new ArrayList<>();
}
