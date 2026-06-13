package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.system.dto.SubjectImportResult;
import cn.edu.gpnu.platform.system.vo.TeachingSubjectVO;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface TeachingSubjectService {

    List<TeachingSubjectVO> list(String segmentCode, String keyword, String category, String yearVersion);

    SubjectImportResult importSubjects(InputStream inputStream, String defaultYearVersion) throws IOException;

    void validateSelectable(String segmentCode, String subjectCode, String yearVersion);

    void recordRecent(String segmentCode, String subjectCode, String yearVersion);

    List<TeachingSubjectVO> recent(String segmentCode, String yearVersion);
}
