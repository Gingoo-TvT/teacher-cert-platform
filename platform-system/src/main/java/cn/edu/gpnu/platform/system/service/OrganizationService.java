package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.system.dto.CollegeSaveRequest;
import cn.edu.gpnu.platform.system.dto.MajorSaveRequest;
import cn.edu.gpnu.platform.system.dto.TrainingGoalConfigSaveRequest;
import cn.edu.gpnu.platform.system.vo.CollegeVO;
import cn.edu.gpnu.platform.system.vo.MajorVO;
import cn.edu.gpnu.platform.system.vo.TrainingGoalConfigVO;
import cn.edu.gpnu.platform.system.vo.TrainingGoalVO;

import java.util.List;

public interface OrganizationService {

    List<CollegeVO> listColleges(String keyword, Integer status);

    Long createCollege(CollegeSaveRequest request);

    void updateCollege(Long id, CollegeSaveRequest request);

    void deleteCollege(Long id);

    List<MajorVO> listMajors(Long collegeId, String yearVersion, Integer pilotScopeFlag, Integer status, String keyword);

    MajorVO getMajor(Long id);

    Long createMajor(MajorSaveRequest request);

    void updateMajor(Long id, MajorSaveRequest request);

    void deleteMajor(Long id);

    List<TrainingGoalVO> getMajorTrainingGoals(Long majorId);

    void replaceMajorTrainingGoals(Long majorId, List<String> trainingGoalCodes);

    List<TrainingGoalConfigVO> listTrainingGoalConfigs(String trainingGoalCode);

    void saveTrainingGoalConfig(TrainingGoalConfigSaveRequest request);
}
