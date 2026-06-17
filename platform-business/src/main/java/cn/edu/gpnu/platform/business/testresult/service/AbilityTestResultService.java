package cn.edu.gpnu.platform.business.testresult.service;

import cn.edu.gpnu.platform.business.testresult.dto.AbilityTestImportRequest;
import cn.edu.gpnu.platform.business.testresult.dto.AbilityTestQuery;
import cn.edu.gpnu.platform.business.testresult.vo.AbilityTestResultVO;
import cn.edu.gpnu.platform.business.testresult.vo.AbilityTestValidityVO;
import cn.edu.gpnu.platform.common.api.PageResult;

import java.io.InputStream;
import java.util.List;

public interface AbilityTestResultService {

    PageResult<AbilityTestResultVO> list(AbilityTestQuery query);

    AbilityTestResultVO get(Long studentId, String assessmentYear, String teachingSegment);

    List<Long> importRows(AbilityTestImportRequest request);

    List<Long> importFile(InputStream input, String originalFilename);

    void confirm(Long id);

    AbilityTestValidityVO validity(Long studentId, String assessmentYear);
}
