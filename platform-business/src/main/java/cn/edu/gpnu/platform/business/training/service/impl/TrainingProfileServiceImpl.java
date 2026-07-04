package cn.edu.gpnu.platform.business.training.service.impl;

import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.support.ReviewNotificationHelper;
import cn.edu.gpnu.platform.business.training.dto.TrainingProfileSaveRequest;
import cn.edu.gpnu.platform.business.training.dto.TrainingReviewRequest;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.business.training.service.TrainingProfileService;
import cn.edu.gpnu.platform.business.training.support.MajorCodeValidator;
import cn.edu.gpnu.platform.business.training.support.TrainingLinkValidator;
import cn.edu.gpnu.platform.business.training.support.TrainingStatus;
import cn.edu.gpnu.platform.business.training.vo.TrainingOptionsVO;
import cn.edu.gpnu.platform.business.training.vo.TrainingProfileVO;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.SysMajor;
import cn.edu.gpnu.platform.system.entity.TeachingSubject;
import cn.edu.gpnu.platform.system.entity.TrainingGoalConfig;
import cn.edu.gpnu.platform.system.mapper.TeachingSubjectMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import cn.edu.gpnu.platform.system.vo.TeachingSubjectVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainingProfileServiceImpl implements TrainingProfileService {

    private static final String DEFAULT_YEAR_VERSION = "GLOBAL";
    private static final int ENABLED = 1;

    private final TrainingProfileMapper trainingProfileMapper;
    private final StudentMapper studentMapper;
    private final TeachingSubjectMapper teachingSubjectMapper;
    private final DataScopeService dataScopeService;
    private final ParamService paramService;
    private final MajorCodeValidator majorCodeValidator;
    private final TrainingLinkValidator trainingLinkValidator;
    private final ReviewNotificationHelper notificationHelper;
    private final AuditLogService auditLogService;

    @Override
    public PageResult<TrainingProfileVO> list(String keyword, String status, Long collegeId, String assessmentYear) {
        List<TrainingProfile> records = listProfiles(keyword, status, collegeId, assessmentYear);
        return new PageResult<>(records.size(), toVOs(records));
    }

    @Override
    public TrainingProfileVO get(Long studentId, String assessmentYear) {
        String year = requiredTrim(assessmentYear, "考核年度不能为空");
        TrainingProfile entity = trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, studentId)
                .eq(TrainingProfile::getAssessmentYear, year)
                .last("LIMIT 1"));
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "培养信息不存在");
        }
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(TrainingProfileSaveRequest request, boolean confirm) {
        Student student = requireStudent(request.getStudentId());
        if (confirm && !request.getStudentId().equals(currentStudentId())) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权维护其他学生培养信息");
        }
        Long allowedCollegeId = allowedCollegeId(student.getCollegeId(), confirm ? "training:confirm" : "training:edit");
        if (request.getCollegeId() != null && !request.getCollegeId().equals(allowedCollegeId)) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该学院培养信息");
        }
        SysMajor matchedMajor = majorCodeValidator.validate(student, request);
        TeachingSubject subject = trainingLinkValidator.validate(request, matchedMajor);
        String year = requiredTrim(request.getAssessmentYear(), "考核年度不能为空");
        TrainingProfile entity = trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, student.getId())
                .eq(TrainingProfile::getAssessmentYear, year)
                .last("LIMIT 1"));
        boolean existing = entity != null;
        if (!existing) {
            entity = new TrainingProfile();
            entity.setStudentId(student.getId());
            entity.setAssessmentYear(year);
            entity.setStatus(TrainingStatus.DRAFT.name());
            entity.setLocked(0);
        }
        if (existing) {
            ensureEditable(entity);
        }
        if (existing && entity.getLocked() != null && entity.getLocked() == 1 && criticalChanged(entity, request)) {
            throw new BizException("关键字段已锁定，不能修改");
        }
        fill(entity, student, request, subject, matchedMajor);
        if (existing) {
            trainingProfileMapper.updateById(entity);
        } else {
            trainingProfileMapper.insert(entity);
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        TrainingProfile entity = requireProfile(id);
        TrainingStatus status = TrainingStatus.of(entity.getStatus());
        if (status != TrainingStatus.DRAFT && status != TrainingStatus.FIRST_REJECTED
                && status != TrainingStatus.SECOND_REJECTED) {
            throw new BizException("当前状态不可提交");
        }
        String oldStatus = entity.getStatus();
        String targetStatus = returnTargetFromSecondRejected(status);
        entity.setStatus(targetStatus);
        // 原子条件更新：仅当状态未被并发改变时才写入，防重复提交竞态（P0-10）
        if (trainingProfileMapper.update(entity, new LambdaUpdateWrapper<TrainingProfile>()
                .eq(TrainingProfile::getId, id).eq(TrainingProfile::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        notificationHelper.notifySubmitted(entity.getCollegeId(), entity.getStudentId(), "专业培养信息",
                targetStatus, "training_profile", entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void firstReview(Long id, TrainingReviewRequest request) {
        TrainingProfile entity = requireProfile(id);
        if (TrainingStatus.of(entity.getStatus()) != TrainingStatus.FIRST_REVIEW) {
            throw new BizException("当前状态不可初审");
        }
        String oldStatus = entity.getStatus();
        String action = normalizeAction(request.getAction());
        if ("PASS".equals(action)) {
            entity.setStatus(TrainingStatus.SECOND_REVIEW.name());
        } else if ("REJECT".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(TrainingStatus.FIRST_REJECTED.name());
        } else if ("FAIL".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(TrainingStatus.FAILED.name());
        } else {
            throw new BizException("审核结论不支持");
        }
        entity.setFirstReviewerId(UserContext.getUserIdOrSystem());
        entity.setFirstReviewTime(LocalDateTime.now());
        entity.setFirstReviewComment(trimToNull(request.getComment()));
        // 原子条件更新：仅当仍为初审态时才写入，防并发/重复初审竞态（P0-10）
        if (trainingProfileMapper.update(entity, new LambdaUpdateWrapper<TrainingProfile>()
                .eq(TrainingProfile::getId, id).eq(TrainingProfile::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        auditLogService.record("training", entity.getId(), trainingTarget(entity), "firstReview",
                oldStatus, entity.getStatus(), trimToNull(request.getComment()));
        if ("PASS".equals(action)) {
            notificationHelper.notifyFirstReviewPassed(entity.getCollegeId(), entity.getStudentId(), "专业培养信息",
                    "training_profile", entity.getId());
        } else {
            notificationHelper.notifyReturnedToStudent(entity.getStudentId(), "专业培养信息", action,
                    "training_profile", entity.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void secondReview(Long id, TrainingReviewRequest request) {
        TrainingProfile entity = requireProfile(id);
        if (TrainingStatus.of(entity.getStatus()) != TrainingStatus.SECOND_REVIEW) {
            throw new BizException("当前状态不可复审");
        }
        String oldStatus = entity.getStatus();
        String action = normalizeAction(request.getAction());
        if ("PASS".equals(action)) {
            entity.setStatus(TrainingStatus.PASSED.name());
            entity.setLocked(1);
        } else if ("REJECT".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(TrainingStatus.SECOND_REJECTED.name());
        } else if ("FAIL".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(TrainingStatus.FAILED.name());
        } else {
            throw new BizException("审核结论不支持");
        }
        entity.setSecondReviewerId(UserContext.getUserIdOrSystem());
        entity.setSecondReviewTime(LocalDateTime.now());
        entity.setSecondReviewComment(trimToNull(request.getComment()));
        // 原子条件更新：仅当仍为复审态时才写入，防并发/重复复审竞态（P0-10）
        if (trainingProfileMapper.update(entity, new LambdaUpdateWrapper<TrainingProfile>()
                .eq(TrainingProfile::getId, id).eq(TrainingProfile::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        auditLogService.record("training", entity.getId(), trainingTarget(entity), "secondReview",
                oldStatus, entity.getStatus(), trimToNull(request.getComment()));
        if (!"PASS".equals(action)) {
            notificationHelper.notifySecondReviewReturned(entity.getCollegeId(), entity.getStudentId(), "专业培养信息",
                    action, "training_profile", entity.getId());
        }
    }

    @Override
    public TrainingOptionsVO options(String trainingGoal, String segment) {
        TrainingGoalConfig config = trainingLinkValidator.requireConfig(trainingGoal);
        TrainingOptionsVO vo = new TrainingOptionsVO();
        vo.setTrainingGoal(config.getTrainingGoalCode());
        vo.setDefaultSegment(config.getDefaultSegment());
        vo.setAllowedSegments(trainingLinkValidator.allowedSegments(trainingGoal));
        vo.setDefaultInternshipLocation(config.getDefaultInternshipLocation());
        vo.setAllowedInternshipLocations(trainingLinkValidator.allowedLocations(trainingGoal));
        String subjectSegment = StringUtils.hasText(segment) ? segment.trim() : config.getDefaultSegment();
        if (StringUtils.hasText(subjectSegment)) {
            vo.setSubjects(teachingSubjectMapper.selectList(new LambdaQueryWrapper<TeachingSubject>()
                    .eq(TeachingSubject::getSegmentCode, subjectSegment)
                    .eq(TeachingSubject::getYearVersion, DEFAULT_YEAR_VERSION)
                    .eq(TeachingSubject::getStatus, ENABLED)
                    .orderByAsc(TeachingSubject::getCategoryNode)
                    .orderByAsc(TeachingSubject::getIsCategory)
                    .orderByAsc(TeachingSubject::getSubjectCode))
                    .stream()
                    .map(this::toSubjectVO)
                    .toList());
        }
        return vo;
    }

    private List<TrainingProfile> listProfiles(String keyword, String status, Long collegeId, String assessmentYear) {
        LambdaQueryWrapper<TrainingProfile> wrapper = new LambdaQueryWrapper<TrainingProfile>()
                .orderByAsc(TrainingProfile::getAssessmentYear)
                .orderByAsc(TrainingProfile::getStudentId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(TrainingProfile::getInternalMajorCode, kw)
                    .or()
                    .like(TrainingProfile::getInternalMajorName, kw)
                    .or()
                    .like(TrainingProfile::getTeachingSubjectName, kw));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(TrainingProfile::getStatus, status.trim());
        }
        if (collegeId != null) {
            wrapper.eq(TrainingProfile::getCollegeId, collegeId);
        }
        if (StringUtils.hasText(assessmentYear)) {
            wrapper.eq(TrainingProfile::getAssessmentYear, assessmentYear.trim());
        }
        return trainingProfileMapper.selectList(wrapper);
    }

    private void fill(TrainingProfile entity, Student student, TrainingProfileSaveRequest request,
                      TeachingSubject subject, SysMajor matchedMajor) {
        entity.setStudentId(student.getId());
        entity.setCollegeId(student.getCollegeId());
        entity.setAssessmentYear(requiredTrim(request.getAssessmentYear(), "考核年度不能为空"));
        entity.setSecondDisciplineCode(requiredTrim(request.getSecondDisciplineCode(), "二级学科代码不能为空"));
        entity.setSecondDisciplineName(requiredTrim(request.getSecondDisciplineName(), "二级学科名称不能为空"));
        entity.setInternalMajorCode(trimToNull(request.getInternalMajorCode()));
        entity.setInternalMajorName(trimToNull(request.getInternalMajorName()));
        if (matchedMajor != null) {
            entity.setInternalMajorCode(matchedMajor.getInternalMajorCode());
            entity.setInternalMajorName(matchedMajor.getInternalMajorName());
        }
        entity.setEducationLevel(requiredTrim(request.getEducationLevel(), "学历层次不能为空"));
        entity.setTrainingGoal(requiredTrim(request.getTrainingGoal(), "培养目标不能为空"));
        entity.setInternshipOrgMode(requiredTrim(request.getInternshipOrgMode(), "实习组织方式不能为空"));
        entity.setInternshipLocation(requiredTrim(request.getInternshipLocation(), "实习地点不能为空"));
        entity.setTeachingSegment(requiredTrim(request.getTeachingSegment(), "任教学段不能为空"));
        entity.setTeachingSubjectId(subject.getId());
        entity.setTeachingSubjectCode(subject.getSubjectCode());
        entity.setTeachingSubjectName(subject.getSubjectName());
        entity.setInterviewOrgMode(requiredTrim(request.getInterviewOrgMode(), "面试组织方式不能为空"));
        entity.setAbilityTestConclusion(trimToNull(request.getAbilityTestConclusion()));
    }

    private Long allowedCollegeId(Long requestedCollegeId, String permissionCode) {
        if (requestedCollegeId == null) {
            throw new BizException("学院不能为空");
        }
        DataScopeContext.Scope scope = dataScopeService.resolve(permissionCode);
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该学院培养信息");
        }
        if (scope.allSchool()) {
            return requestedCollegeId;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && scope.getCollegeIds().contains(requestedCollegeId)) {
            return requestedCollegeId;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.SELF
                && scope.getStudentId() != null
                && scope.getStudentId().equals(currentStudentId())) {
            return requestedCollegeId;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该学院培养信息");
    }

    private void ensureEditable(TrainingProfile entity) {
        if (entity.getLocked() != null && entity.getLocked() == 1) {
            throw new BizException("关键字段已锁定，不能修改");
        }
        if (!TrainingStatus.of(entity.getStatus()).editable()) {
            throw new BizException("当前状态不可编辑");
        }
    }

    private boolean criticalChanged(TrainingProfile entity, TrainingProfileSaveRequest request) {
        return changed(entity.getSecondDisciplineCode(), request.getSecondDisciplineCode())
                || changed(entity.getSecondDisciplineName(), request.getSecondDisciplineName())
                || changed(entity.getInternalMajorCode(), request.getInternalMajorCode())
                || changed(entity.getInternalMajorName(), request.getInternalMajorName())
                || changed(entity.getEducationLevel(), request.getEducationLevel())
                || changed(entity.getTrainingGoal(), request.getTrainingGoal())
                || changed(entity.getTeachingSegment(), request.getTeachingSegment())
                || changed(entity.getTeachingSubjectCode(), request.getTeachingSubjectCode());
    }

    private boolean changed(String oldValue, String newValue) {
        return !String.valueOf(trimToNull(oldValue)).equals(String.valueOf(trimToNull(newValue)));
    }

    private String returnTargetFromSecondRejected(TrainingStatus currentStatus) {
        if (currentStatus != TrainingStatus.SECOND_REJECTED) {
            return TrainingStatus.FIRST_REVIEW.name();
        }
        String target = paramService.getString("review.return.target", "FIRST_REVIEW");
        return "SECOND_REVIEW".equalsIgnoreCase(target) ? TrainingStatus.SECOND_REVIEW.name() : TrainingStatus.FIRST_REVIEW.name();
    }

    private List<TrainingProfileVO> toVOs(List<TrainingProfile> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> studentIds = records.stream().map(TrainingProfile::getStudentId).collect(Collectors.toSet());
        Map<Long, Student> students = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, item -> item));
        return records.stream().map(entity -> toVO(entity, students.get(entity.getStudentId()))).toList();
    }

    private TrainingProfileVO toVO(TrainingProfile entity) {
        Student student = studentMapper.selectById(entity.getStudentId());
        return toVO(entity, student);
    }

    private TrainingProfileVO toVO(TrainingProfile entity, Student student) {
        TrainingProfileVO vo = new TrainingProfileVO();
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setStudentNo(student == null ? null : student.getStudentNo());
        vo.setStudentName(student == null ? null : student.getName());
        vo.setIdentityType(student == null ? null : student.getIdentityType());
        vo.setCollegeId(entity.getCollegeId());
        vo.setAssessmentYear(entity.getAssessmentYear());
        vo.setSecondDisciplineCode(entity.getSecondDisciplineCode());
        vo.setSecondDisciplineName(entity.getSecondDisciplineName());
        vo.setInternalMajorCode(entity.getInternalMajorCode());
        vo.setInternalMajorName(entity.getInternalMajorName());
        vo.setEducationLevel(entity.getEducationLevel());
        vo.setTrainingGoal(entity.getTrainingGoal());
        vo.setInternshipOrgMode(entity.getInternshipOrgMode());
        vo.setInternshipLocation(entity.getInternshipLocation());
        vo.setTeachingSegment(entity.getTeachingSegment());
        vo.setTeachingSubjectId(entity.getTeachingSubjectId());
        vo.setTeachingSubjectCode(entity.getTeachingSubjectCode());
        vo.setTeachingSubjectName(entity.getTeachingSubjectName());
        vo.setInterviewOrgMode(entity.getInterviewOrgMode());
        vo.setAbilityTestConclusion(entity.getAbilityTestConclusion());
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(TrainingStatus.of(entity.getStatus()).label());
        vo.setLocked(entity.getLocked());
        vo.setFirstReviewComment(entity.getFirstReviewComment());
        vo.setSecondReviewComment(entity.getSecondReviewComment());
        return vo;
    }

    private String trainingTarget(TrainingProfile entity) {
        return entity.getId() + "/" + entity.getAssessmentYear() + "/" + entity.getStudentId() + "/"
                + entity.getTrainingGoal() + "/" + entity.getTeachingSegment() + "/" + entity.getTeachingSubjectName();
    }

    private TeachingSubjectVO toSubjectVO(TeachingSubject entity) {
        TeachingSubjectVO vo = new TeachingSubjectVO();
        vo.setId(entity.getId());
        vo.setSegmentCode(entity.getSegmentCode());
        vo.setCategoryNode(entity.getCategoryNode());
        vo.setSubjectCode(entity.getSubjectCode());
        vo.setSubjectName(entity.getSubjectName());
        vo.setIsCategory(entity.getIsCategory());
        vo.setSelectable(entity.getStatus() != null && entity.getStatus() == ENABLED
                && (entity.getIsCategory() == null || entity.getIsCategory() != 1));
        vo.setKeyword(entity.getKeyword());
        vo.setYearVersion(entity.getYearVersion());
        vo.setStatus(entity.getStatus());
        return vo;
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

    private TrainingProfile requireProfile(Long id) {
        if (id == null) {
            throw new BizException("培养信息ID不能为空");
        }
        TrainingProfile entity = trainingProfileMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "培养信息不存在");
        }
        return entity;
    }

    private Long currentStudentId() {
        UserContext.CurrentUser user = UserContext.get();
        if (user == null || user.getStudentId() == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该学生");
        }
        return user.getStudentId();
    }

    private String normalizeAction(String action) {
        return requiredTrim(action, "审核结论不能为空").toUpperCase();
    }

    private void requireComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            throw new BizException("退回或不通过必须填写原因");
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
