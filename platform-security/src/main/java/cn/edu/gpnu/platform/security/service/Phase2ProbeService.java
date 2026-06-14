package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.vo.DataScopeProbeVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class Phase2ProbeService {

    private static final long PHASE2_SELF_STUDENT_ID = 9001L;
    private static final long PHASE2_OTHER_STUDENT_ID = 9002L;
    private static final long PHASE2_OTHER_COLLEGE_OFFSET = 900000L;

    public List<DataScopeProbeVO> listStudents() {
        DataScopeContext.Scope scope = DataScopeContext.get();
        List<DataScopeProbeVO> students = students(scope);
        if (scope == null || scope.allSchool()) {
            return students;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE) {
            return students.stream().filter(item -> scope.getCollegeIds().contains(item.getCollegeId())).toList();
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.SELF) {
            return students.stream().filter(item -> item.getStudentId().equals(scope.getStudentId())).toList();
        }
        return List.of();
    }

    public DataScopeProbeVO getStudent(Long studentId) {
        return listStudents().stream()
                .filter(item -> item.getStudentId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该学生"));
    }

    private List<DataScopeProbeVO> students(DataScopeContext.Scope scope) {
        Long collegeId = scope == null ? null : scope.getCollegeId();
        Long selfStudentId = scope == null ? null : scope.getStudentId();
        UserContext.CurrentUser current = UserContext.get();
        if (collegeId == null && scope != null && !scope.getCollegeIds().isEmpty()) {
            collegeId = scope.getCollegeIds().iterator().next();
        }
        if (collegeId == null && current != null) {
            collegeId = current.getCollegeId();
        }
        if (selfStudentId == null && current != null) {
            selfStudentId = current.getStudentId();
        }
        long visibleCollegeId = collegeId == null ? 201L : collegeId;
        long otherCollegeId = visibleCollegeId + PHASE2_OTHER_COLLEGE_OFFSET;
        long selfId = selfStudentId == null ? PHASE2_SELF_STUDENT_ID : selfStudentId;
        List<DataScopeProbeVO> result = new ArrayList<>();
        result.add(new DataScopeProbeVO(selfId, "AT13本范围学生", visibleCollegeId));
        result.add(new DataScopeProbeVO(PHASE2_OTHER_STUDENT_ID, "AT13外院学生", otherCollegeId));
        return result;
    }
}
