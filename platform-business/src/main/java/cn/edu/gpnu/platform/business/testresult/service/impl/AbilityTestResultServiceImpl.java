package cn.edu.gpnu.platform.business.testresult.service.impl;

import cn.edu.gpnu.platform.business.exemption.service.ExemptionService;
import cn.edu.gpnu.platform.business.exemption.vo.ExamSubjectVO;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.testresult.dto.AbilityTestImportRequest;
import cn.edu.gpnu.platform.business.testresult.dto.AbilityTestQuery;
import cn.edu.gpnu.platform.business.testresult.dto.AbilityTestSaveRequest;
import cn.edu.gpnu.platform.business.testresult.entity.AbilityTestResult;
import cn.edu.gpnu.platform.business.testresult.mapper.AbilityTestResultMapper;
import cn.edu.gpnu.platform.business.testresult.service.AbilityTestResultService;
import cn.edu.gpnu.platform.business.testresult.support.AbilityTestConclusion;
import cn.edu.gpnu.platform.business.testresult.support.TestConfirmStatus;
import cn.edu.gpnu.platform.business.testresult.vo.AbilityTestResultVO;
import cn.edu.gpnu.platform.business.testresult.vo.AbilityTestValidityVO;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.DictService;
import cn.idev.excel.FastExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AbilityTestResultServiceImpl implements AbilityTestResultService {

    private static final TypeReference<List<ExamSubjectVO>> SUBJECT_LIST_TYPE = new TypeReference<>() {
    };

    private final AbilityTestResultMapper resultMapper;
    private final StudentMapper studentMapper;
    private final TrainingProfileMapper trainingProfileMapper;
    private final DictService dictService;
    private final ExemptionService exemptionService;
    private final DataScopeService dataScopeService;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<AbilityTestResultVO> list(AbilityTestQuery query) {
        List<AbilityTestResult> records = selectResults(query);
        return new PageResult<>(records.size(), toVOs(records));
    }

    @Override
    public AbilityTestResultVO get(Long studentId, String assessmentYear, String teachingSegment) {
        Student student = requireStudent(studentId);
        ensureCanReadStudent(student);
        String year = requiredTrim(assessmentYear, "考核年度不能为空");
        AbilityTestResult entity = existing(student.getId(), year);
        if (entity == null) {
            AbilityTestResultVO vo = emptyVO(student, year, resolveTeachingSegment(student.getId(), year, teachingSegment));
            vo.setExamSubjects(examSubjects(student.getId(), year, vo.getTeachingSegment()));
            return vo;
        }
        AbilityTestResultVO vo = toVO(entity);
        vo.setTeachingSegment(resolveTeachingSegment(student.getId(), year, teachingSegment));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> importRows(AbilityTestImportRequest request) {
        if (request == null || request.getRows() == null || request.getRows().isEmpty()) {
            throw new BizException("导入记录不能为空");
        }
        List<Long> ids = new ArrayList<>();
        for (AbilityTestSaveRequest row : request.getRows()) {
            ids.add(save(row, "test:import"));
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> importFile(InputStream input, String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename.trim().toLowerCase(Locale.ROOT) : "";
        List<AbilityTestSaveRequest> rows;
        if (filename.endsWith(".csv")) {
            rows = readCsv(input);
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            rows = readExcel(input);
        } else {
            throw new BizException("仅支持 xls/xlsx/csv 导入文件");
        }
        AbilityTestImportRequest request = new AbilityTestImportRequest();
        request.setRows(rows);
        return importRows(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        AbilityTestResult entity = requireResult(id);
        ensureCanWriteResult(entity, "test:confirm");
        if (entity.getLocked() != null && entity.getLocked() == 1) {
            throw new BizException("测试结果已锁定");
        }
        if (TestConfirmStatus.of(entity.getConfirmStatus()) != TestConfirmStatus.PENDING) {
            throw new BizException("当前状态不可确认");
        }
        entity.setConfirmStatus(TestConfirmStatus.CONFIRMED.name());
        entity.setLocked(1);
        resultMapper.updateById(entity);
    }

    @Override
    public AbilityTestValidityVO validity(Long studentId, String assessmentYear) {
        Student student = requireStudent(studentId);
        ensureCanReadStudent(student);
        String year = requiredTrim(assessmentYear, "考核年度不能为空");
        AbilityTestResult entity = existing(studentId, year);
        AbilityTestValidityVO vo = new AbilityTestValidityVO();
        vo.setStudentId(studentId);
        vo.setAssessmentYear(year);
        if (entity == null) {
            vo.setValidForCertificate(false);
            vo.setMessage("测试结果不存在");
            return vo;
        }
        AbilityTestConclusion conclusion = AbilityTestConclusion.of(entity.getConclusion());
        vo.setConclusion(conclusion.code());
        vo.setValidForCertificate(conclusion.validForCertificate());
        vo.setMessage(conclusion.validForCertificate() ? "测试结论有效" : "测试结论未满足证书前置");
        return vo;
    }

    private Long save(AbilityTestSaveRequest request, String permissionCode) {
        Student student = requireStudent(request.getStudentId());
        ensureCanWriteStudent(student, permissionCode);
        String year = requiredTrim(request.getAssessmentYear(), "考核年度不能为空");
        String segment = resolveTeachingSegment(student.getId(), year, request.getTeachingSegment());
        validateDict("exam_org_mode", requiredTrim(request.getExamOrgMode(), "考试组织方式不能为空"), "考试组织方式不在字典范围");
        AbilityTestConclusion conclusion = AbilityTestConclusion.of(requiredTrim(request.getConclusion(), "测试结论不能为空"));
        validateDict("ability_test_conclusion", conclusion.code(), "测试结论不在字典范围");
        List<ExamSubjectVO> subjects = examSubjects(student.getId(), year, segment);

        AbilityTestResult entity = existing(student.getId(), year);
        boolean existing = entity != null;
        if (!existing) {
            entity = new AbilityTestResult();
            entity.setStudentId(student.getId());
            entity.setAssessmentYear(year);
            entity.setConfirmStatus(TestConfirmStatus.PENDING.name());
            entity.setLocked(0);
        } else {
            ensureEditable(entity);
        }
        entity.setCollegeId(student.getCollegeId());
        entity.setExamOrgMode(request.getExamOrgMode().trim());
        entity.setExamSubjects(writeJson(subjects.stream().filter(ExamSubjectVO::isIncludedInExam).toList()));
        entity.setExemptionRelation(writeJson(subjects.stream().filter(ExamSubjectVO::isExempted).toList()));
        entity.setScore(trimToNull(request.getScore()));
        entity.setConclusion(conclusion.code());
        entity.setConfirmStatus(TestConfirmStatus.PENDING.name());
        entity.setLocked(0);
        if (existing) {
            resultMapper.updateById(entity);
        } else {
            resultMapper.insert(entity);
        }
        return entity.getId();
    }

    private List<AbilityTestResult> selectResults(AbilityTestQuery query) {
        AbilityTestQuery q = query == null ? new AbilityTestQuery() : query;
        LambdaQueryWrapper<AbilityTestResult> wrapper = new LambdaQueryWrapper<AbilityTestResult>()
                .orderByAsc(AbilityTestResult::getAssessmentYear)
                .orderByAsc(AbilityTestResult::getStudentId);
        if (q.getStudentId() != null) {
            wrapper.eq(AbilityTestResult::getStudentId, q.getStudentId());
        }
        if (q.getCollegeId() != null) {
            wrapper.eq(AbilityTestResult::getCollegeId, q.getCollegeId());
        }
        if (StringUtils.hasText(q.getAssessmentYear())) {
            wrapper.eq(AbilityTestResult::getAssessmentYear, q.getAssessmentYear().trim());
        }
        if (StringUtils.hasText(q.getConclusion())) {
            wrapper.eq(AbilityTestResult::getConclusion, q.getConclusion().trim());
        }
        if (StringUtils.hasText(q.getConfirmStatus())) {
            wrapper.eq(AbilityTestResult::getConfirmStatus, q.getConfirmStatus().trim());
        }
        List<AbilityTestResult> records = resultMapper.selectList(wrapper);
        if (!StringUtils.hasText(q.getKeyword())) {
            return records;
        }
        String keyword = q.getKeyword().trim();
        Set<Long> matchedStudentIds = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                        .like(Student::getStudentNo, keyword)
                        .or()
                        .like(Student::getName, keyword))
                .stream()
                .map(Student::getId)
                .collect(Collectors.toSet());
        return records.stream()
                .filter(item -> matchedStudentIds.contains(item.getStudentId())
                        || (item.getScore() != null && item.getScore().contains(keyword)))
                .toList();
    }

    private List<AbilityTestResultVO> toVOs(List<AbilityTestResult> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> studentIds = records.stream().map(AbilityTestResult::getStudentId).collect(Collectors.toSet());
        Map<Long, Student> students = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, item -> item));
        Map<String, String> examOrgModeLabels = dictLabels("exam_org_mode");
        return records.stream().map(entity -> toVO(entity, students.get(entity.getStudentId()), examOrgModeLabels)).toList();
    }

    private AbilityTestResultVO toVO(AbilityTestResult entity) {
        Student student = studentMapper.selectById(entity.getStudentId());
        return toVO(entity, student, dictLabels("exam_org_mode"));
    }

    private AbilityTestResultVO toVO(AbilityTestResult entity, Student student, Map<String, String> examOrgModeLabels) {
        AbilityTestResultVO vo = emptyVO(student, entity.getAssessmentYear(), null);
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setCollegeId(entity.getCollegeId());
        vo.setExamOrgMode(entity.getExamOrgMode());
        vo.setExamOrgModeLabel(examOrgModeLabels.getOrDefault(entity.getExamOrgMode(), entity.getExamOrgMode()));
        vo.setExamSubjects(readSubjects(entity.getExamSubjects()));
        vo.setScore(entity.getScore());
        AbilityTestConclusion conclusion = AbilityTestConclusion.of(entity.getConclusion());
        vo.setConclusion(conclusion.code());
        vo.setConclusionLabel(conclusion.label());
        TestConfirmStatus confirmStatus = TestConfirmStatus.of(entity.getConfirmStatus());
        vo.setConfirmStatus(entity.getConfirmStatus());
        vo.setConfirmStatusLabel(confirmStatus.label());
        vo.setLocked(entity.getLocked());
        vo.setValidForCertificate(conclusion.validForCertificate());
        vo.setExemptionRelation(entity.getExemptionRelation());
        return vo;
    }

    private AbilityTestResultVO emptyVO(Student student, String assessmentYear, String teachingSegment) {
        AbilityTestResultVO vo = new AbilityTestResultVO();
        if (student != null) {
            vo.setStudentId(student.getId());
            vo.setStudentNo(student.getStudentNo());
            vo.setStudentName(student.getName());
            vo.setCollegeId(student.getCollegeId());
        }
        vo.setAssessmentYear(assessmentYear);
        vo.setTeachingSegment(teachingSegment);
        vo.setConclusion(AbilityTestConclusion.PENDING_CONFIRM.code());
        vo.setConclusionLabel(AbilityTestConclusion.PENDING_CONFIRM.label());
        vo.setConfirmStatus(TestConfirmStatus.PENDING.name());
        vo.setConfirmStatusLabel(TestConfirmStatus.PENDING.label());
        vo.setLocked(0);
        vo.setValidForCertificate(false);
        return vo;
    }

    private String resolveTeachingSegment(Long studentId, String assessmentYear, String requestedSegment) {
        String segment = trimToNull(requestedSegment);
        if (segment != null) {
            validateDict("teaching_segment", segment, "任教学段不在字典范围");
            return segment;
        }
        TrainingProfile profile = trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, studentId)
                .eq(TrainingProfile::getAssessmentYear, assessmentYear)
                .last("LIMIT 1"));
        if (profile == null || !StringUtils.hasText(profile.getTeachingSegment())) {
            throw new BizException("任教学段不能为空");
        }
        return profile.getTeachingSegment();
    }

    private List<ExamSubjectVO> examSubjects(Long studentId, String year, String segment) {
        return exemptionService.examSubjects(studentId, year, segment);
    }

    private List<AbilityTestSaveRequest> readExcel(InputStream input) {
        List<?> rawRows = FastExcel.read(input)
                .headRowNumber(1)
                .useScientificFormat(false)
                .sheet()
                .doReadSync();
        List<AbilityTestSaveRequest> rows = new ArrayList<>();
        for (Object rawRow : rawRows) {
            if (rawRow instanceof Map<?, ?> map) {
                rows.add(rowFromMap(map));
            }
        }
        return rows;
    }

    private List<AbilityTestSaveRequest> readCsv(InputStream input) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .skip(1)
                    .map(this::parseCsvLine)
                    .filter(values -> values.length > 0 && StringUtils.hasText(values[0]))
                    .map(this::rowFromValues)
                    .toList();
        } catch (Exception e) {
            throw new BizException("导入文件读取失败: " + e.getMessage());
        }
    }

    private AbilityTestSaveRequest rowFromMap(Map<?, ?> map) {
        return rowFromValues(new String[]{
                valueAt(map, 0),
                valueAt(map, 1),
                valueAt(map, 2),
                valueAt(map, 3),
                valueAt(map, 4),
                valueAt(map, 5)
        });
    }

    private AbilityTestSaveRequest rowFromValues(String[] values) {
        if (values.length < 6) {
            throw new BizException("导入列不足，需包含学号/年度/学段/组织方式/成绩/结论");
        }
        Student student = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, requiredTrim(values[0], "导入学号不能为空"))
                .last("LIMIT 1"));
        if (student == null) {
            throw new BizException("导入学生不存在: " + values[0]);
        }
        AbilityTestSaveRequest request = new AbilityTestSaveRequest();
        request.setStudentId(student.getId());
        request.setAssessmentYear(values[1]);
        request.setTeachingSegment(values[2]);
        request.setExamOrgMode(values[3]);
        request.setScore(values[4]);
        request.setConclusion(values[5]);
        return request;
    }

    private String valueAt(Map<?, ?> map, int index) {
        Object value = map.get(index);
        return value == null ? null : String.valueOf(value);
    }

    private String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values.toArray(String[]::new);
    }

    private void ensureEditable(AbilityTestResult entity) {
        if (entity.getLocked() != null && entity.getLocked() == 1) {
            throw new BizException("测试结果已锁定，不能修改");
        }
        if (!TestConfirmStatus.of(entity.getConfirmStatus()).editable()) {
            throw new BizException("当前状态不可编辑");
        }
    }

    private void ensureCanWriteResult(AbilityTestResult result, String permissionCode) {
        ensureCanWriteStudent(requireStudent(result.getStudentId()), permissionCode);
    }

    private void ensureCanWriteStudent(Student student, String permissionCode) {
        DataScopeContext.Scope scope = dataScopeService.resolve(permissionCode);
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该测试结果");
        }
        if (scope.allSchool()) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && scope.getCollegeIds().contains(student.getCollegeId())) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该测试结果");
    }

    private void ensureCanReadStudent(Student student) {
        DataScopeContext.Scope scope = dataScopeService.resolve("student:view");
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该测试结果");
        }
        if (scope.allSchool()) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && scope.getCollegeIds().contains(student.getCollegeId())) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.SELF
                && scope.getStudentId() != null
                && scope.getStudentId().equals(student.getId())) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该测试结果");
    }

    private void validateDict(String typeCode, String itemCode, String message) {
        if (!dictLabels(typeCode).containsKey(itemCode)) {
            throw new BizException(message);
        }
    }

    // Phase 44c（§7.3）：改走 DictService 缓存标签表（写时逐出）；返回可变副本，保持原「每次新 map」语义。
    private Map<String, String> dictLabels(String typeCode) {
        return new LinkedHashMap<>(dictService.dictLabels(typeCode));
    }

    private AbilityTestResult existing(Long studentId, String year) {
        return resultMapper.selectOne(new LambdaQueryWrapper<AbilityTestResult>()
                .eq(AbilityTestResult::getStudentId, studentId)
                .eq(AbilityTestResult::getAssessmentYear, year)
                .last("LIMIT 1"));
    }

    private AbilityTestResult requireResult(Long id) {
        if (id == null) {
            throw new BizException("测试结果ID不能为空");
        }
        AbilityTestResult entity = resultMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "测试结果不存在");
        }
        return entity;
    }

    private Student requireStudent(Long id) {
        if (id == null) {
            throw new BizException("学生ID不能为空");
        }
        Student student = studentMapper.selectById(id);
        if (student == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "学生不存在");
        }
        return student;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            throw new BizException("测试结果序列化失败");
        }
    }

    private List<ExamSubjectVO> readSubjects(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, SUBJECT_LIST_TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String requiredTrim(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
