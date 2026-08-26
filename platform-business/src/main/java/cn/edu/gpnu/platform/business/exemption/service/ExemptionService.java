package cn.edu.gpnu.platform.business.exemption.service;

import cn.edu.gpnu.platform.business.exemption.dto.ExemptionApplyRequest;
import cn.edu.gpnu.platform.business.exemption.dto.ExemptionQuery;
import cn.edu.gpnu.platform.business.exemption.dto.ExemptionReviewRequest;
import cn.edu.gpnu.platform.business.exemption.dto.ExemptionUpdateRequest;
import cn.edu.gpnu.platform.business.exemption.vo.ExamSubjectVO;
import cn.edu.gpnu.platform.business.exemption.vo.ExemptionRequestVO;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.system.entity.SysDictItem;

import java.io.InputStream;
import java.util.List;

public interface ExemptionService {

    List<SysDictItem> subjects(String segment);

    PageResult<ExemptionRequestVO> list(ExemptionQuery query);

    List<ExemptionRequestVO> studentRequests(Long studentId, String assessmentYear);

    List<Long> apply(ExemptionApplyRequest request);

    void update(Long id, ExemptionUpdateRequest request);

    void uploadMaterial(Long id, InputStream input, String originalFilename, String contentType, long size);

    void replaceMaterial(Long materialId, InputStream input, String originalFilename, String contentType, long size);

    void deleteMaterial(Long materialId);

    Long previewMaterialFileId(Long materialId);

    void submit(Long id);

    void firstReview(Long id, ExemptionReviewRequest request);

    void secondReview(Long id, ExemptionReviewRequest request);

    List<ExamSubjectVO> examSubjects(Long studentId, String assessmentYear, String teachingSegment);
}
