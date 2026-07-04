package cn.edu.gpnu.platform.business.student.service;

import cn.edu.gpnu.platform.business.student.dto.StudentConfirmRequest;
import cn.edu.gpnu.platform.business.student.dto.StudentReviewRequest;
import cn.edu.gpnu.platform.business.student.dto.StudentSaveRequest;
import cn.edu.gpnu.platform.business.student.vo.StudentPlainIdCardVO;
import cn.edu.gpnu.platform.business.student.vo.StudentVO;
import cn.edu.gpnu.platform.common.api.PageResult;

import java.util.List;

public interface StudentService {

    PageResult<StudentVO> list(String keyword, String status, Long collegeId, String grade,
                               boolean plain, Integer page, Integer size);

    List<StudentVO> listAll(String keyword, String status, Long collegeId, boolean plain);

    StudentVO detail(Long id, boolean plain);

    Long create(StudentSaveRequest request);

    List<Long> batchCreate(List<StudentSaveRequest> requests);

    void update(Long id, StudentSaveRequest request);

    void delete(Long id);

    StudentVO confirm(StudentConfirmRequest request);

    void submit(Long id);

    void firstReview(Long id, StudentReviewRequest request);

    void secondReview(Long id, StudentReviewRequest request);

    StudentPlainIdCardVO plainIdCard(Long id);
}
