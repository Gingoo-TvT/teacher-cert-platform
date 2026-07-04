package cn.edu.gpnu.platform.business.training.service;

import cn.edu.gpnu.platform.business.training.dto.TrainingProfileSaveRequest;
import cn.edu.gpnu.platform.business.training.dto.TrainingReviewRequest;
import cn.edu.gpnu.platform.business.training.vo.TrainingOptionsVO;
import cn.edu.gpnu.platform.business.training.vo.TrainingProfileVO;
import cn.edu.gpnu.platform.common.api.PageResult;

public interface TrainingProfileService {

    PageResult<TrainingProfileVO> list(String keyword, String status, Long collegeId, String assessmentYear,
                                       Integer page, Integer size);

    TrainingProfileVO get(Long studentId, String assessmentYear);

    Long save(TrainingProfileSaveRequest request, boolean confirm);

    void submit(Long id);

    void firstReview(Long id, TrainingReviewRequest request);

    void secondReview(Long id, TrainingReviewRequest request);

    TrainingOptionsVO options(String trainingGoal, String segment);
}
