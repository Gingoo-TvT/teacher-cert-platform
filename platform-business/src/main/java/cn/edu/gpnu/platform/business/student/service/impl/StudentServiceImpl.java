package cn.edu.gpnu.platform.business.student.service.impl;

import cn.edu.gpnu.platform.business.student.dto.StudentConfirmRequest;
import cn.edu.gpnu.platform.business.student.dto.StudentReviewRequest;
import cn.edu.gpnu.platform.business.student.dto.StudentSaveRequest;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.student.service.StudentService;
import cn.edu.gpnu.platform.business.student.support.BirthDateValidator;
import cn.edu.gpnu.platform.business.student.support.IdCardValidator;
import cn.edu.gpnu.platform.business.student.support.NameValidator;
import cn.edu.gpnu.platform.business.student.support.SensitiveMasker;
import cn.edu.gpnu.platform.business.student.support.StudentStatus;
import cn.edu.gpnu.platform.business.support.ReviewNotificationHelper;
import cn.edu.gpnu.platform.business.student.vo.StudentPlainIdCardVO;
import cn.edu.gpnu.platform.business.student.vo.StudentVO;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.SysRole;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long SYS_OPERATOR = 0L;

    private final StudentMapper studentMapper;
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeService dataScopeService;
    private final ParamService paramService;
    private final IdCardValidator idCardValidator;
    private final BirthDateValidator birthDateValidator;
    private final NameValidator nameValidator;
    private final ReviewNotificationHelper notificationHelper;
    private final AuditLogService auditLogService;

    @Override
    public PageResult<StudentVO> list(String keyword, String status, Long collegeId, boolean plain) {
        List<StudentVO> records = listAll(keyword, status, collegeId, plain);
        return new PageResult<>(records.size(), records);
    }

    @Override
    public List<StudentVO> listAll(String keyword, String status, Long collegeId, boolean plain) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<Student>()
                .orderByAsc(Student::getStudentNo);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Student::getStudentNo, kw).or().like(Student::getName, kw));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Student::getStatus, status.trim());
        }
        if (collegeId != null) {
            wrapper.eq(Student::getCollegeId, collegeId);
        }
        return studentMapper.selectList(wrapper).stream().map(item -> toVO(item, plain)).toList();
    }

    @Override
    public StudentVO detail(Long id, boolean plain) {
        return toVO(requireStudent(id), plain);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(StudentSaveRequest request) {
        Student entity = new Student();
        fill(entity, request, false, true);
        entity.setStatus(StudentStatus.DRAFT.name());
        entity.setLocked(0);
        studentMapper.insert(entity);
        ensureStudentAccount(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchCreate(List<StudentSaveRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException("学生列表不能为空");
        }
        return requests.stream().map(this::create).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, StudentSaveRequest request) {
        Student entity = requireStudent(id);
        fill(entity, request, true, true);
        studentMapper.updateById(entity);
        ensureStudentAccount(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Student entity = requireStudent(id);
        ensureEditable(entity, "删除");
        studentMapper.deleteById(id);
        // P0-12：同步停用该学生的登录账号，防删除/退学后仍可登录（JWT filter 每请求校验 status=ENABLED，旧 token 下次请求即失效）
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getStudentId, id)
                .set(SysUser::getStatus, "DISABLED"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StudentVO confirm(StudentConfirmRequest request) {
        Long studentId = currentStudentId();
        Student entity = requireStudent(studentId);
        fill(entity, request, true, false);
        studentMapper.updateById(entity);
        return toVO(entity, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        Student entity = requireStudent(id);
        StudentStatus status = StudentStatus.of(entity.getStatus());
        if (status != StudentStatus.DRAFT && status != StudentStatus.FIRST_REJECTED
                && status != StudentStatus.SECOND_REJECTED) {
            throw new BizException("当前状态不可提交");
        }
        ensureRequired(entity);
        String oldStatus = entity.getStatus();
        String targetStatus = returnTargetFromSecondRejected(status);
        entity.setStatus(targetStatus);
        // 原子条件更新：仅当状态未被并发改变时才写入，防重复提交竞态（P0-10）
        if (studentMapper.update(entity, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, id).eq(Student::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        notificationHelper.notifySubmitted(entity.getCollegeId(), entity.getId(), "学生基本信息",
                targetStatus, "student", entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void firstReview(Long id, StudentReviewRequest request) {
        Student entity = requireStudent(id);
        if (StudentStatus.of(entity.getStatus()) != StudentStatus.FIRST_REVIEW) {
            throw new BizException("当前状态不可初审");
        }
        String oldStatus = entity.getStatus();
        String action = normalizeAction(request.getAction());
        if ("PASS".equals(action)) {
            entity.setStatus(StudentStatus.SECOND_REVIEW.name());
        } else if ("REJECT".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(StudentStatus.FIRST_REJECTED.name());
        } else if ("FAIL".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(StudentStatus.FAILED.name());
        } else {
            throw new BizException("审核结论不支持");
        }
        entity.setFirstReviewerId(UserContext.getUserIdOrSystem());
        entity.setFirstReviewTime(LocalDateTime.now());
        entity.setFirstReviewComment(trimToNull(request.getComment()));
        // 原子条件更新：仅当仍为初审态时才写入，防并发/重复初审竞态（P0-10）
        if (studentMapper.update(entity, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, id).eq(Student::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        auditLogService.record("student", entity.getId(), studentTarget(entity), "firstReview",
                oldStatus, entity.getStatus(), trimToNull(request.getComment()));
        if ("PASS".equals(action)) {
            notificationHelper.notifyFirstReviewPassed(entity.getCollegeId(), entity.getId(), "学生基本信息",
                    "student", entity.getId());
        } else {
            notificationHelper.notifyReturnedToStudent(entity.getId(), "学生基本信息", action, "student", entity.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void secondReview(Long id, StudentReviewRequest request) {
        Student entity = requireStudent(id);
        if (StudentStatus.of(entity.getStatus()) != StudentStatus.SECOND_REVIEW) {
            throw new BizException("当前状态不可复审");
        }
        String oldStatus = entity.getStatus();
        String action = normalizeAction(request.getAction());
        if ("PASS".equals(action)) {
            entity.setStatus(StudentStatus.PASSED.name());
            entity.setLocked(1);
        } else if ("REJECT".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(StudentStatus.SECOND_REJECTED.name());
        } else if ("FAIL".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(StudentStatus.FAILED.name());
        } else {
            throw new BizException("审核结论不支持");
        }
        entity.setSecondReviewerId(UserContext.getUserIdOrSystem());
        entity.setSecondReviewTime(LocalDateTime.now());
        entity.setSecondReviewComment(trimToNull(request.getComment()));
        // 原子条件更新：仅当仍为复审态时才写入，防并发/重复复审竞态（P0-10）
        if (studentMapper.update(entity, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, id).eq(Student::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        auditLogService.record("student", entity.getId(), studentTarget(entity), "secondReview",
                oldStatus, entity.getStatus(), trimToNull(request.getComment()));
        if (!"PASS".equals(action)) {
            notificationHelper.notifySecondReviewReturned(entity.getCollegeId(), entity.getId(), "学生基本信息",
                    action, "student", entity.getId());
        }
    }

    @Override
    public StudentPlainIdCardVO plainIdCard(Long id) {
        Student entity = requireStudent(id);
        return new StudentPlainIdCardVO(entity.getId(), entity.getIdCardNo());
    }

    private void fill(Student entity, StudentSaveRequest request, boolean existing, boolean enforceWriteScope) {
        if (existing) {
            ensureEditable(entity, "修改");
        }
        if (existing && entity.getLocked() != null && entity.getLocked() == 1 && criticalChanged(entity, request)) {
            throw new BizException("关键字段已锁定，不能修改");
        }
        String studentNo = requiredTrim(request.getStudentNo(), "学号不能为空");
        if (existsStudentNo(studentNo, entity.getId())) {
            throw new BizException("学号已存在");
        }
        String idCardType = requiredTrim(request.getIdCardType(), "证件类型不能为空");
        String idCardNo = idCardValidator.validate(idCardType, request.getIdCardNo());
        if (existsIdCardNo(idCardNo, entity.getId())) {
            throw new BizException("证件号码已存在");
        }
        nameValidator.validate(request.getName());
        birthDateValidator.validate(idCardType, idCardNo, request.getBirthDate());
        entity.setStudentNo(studentNo);
        entity.setName(requiredTrim(request.getName(), "姓名不能为空"));
        entity.setGender(requiredTrim(request.getGender(), "性别不能为空"));
        entity.setIdCardType(idCardType);
        entity.setIdCardNo(idCardNo);
        entity.setBirthDate(requiredTrim(request.getBirthDate(), "出生日期不能为空"));
        entity.setIdentityType(requiredTrim(request.getIdentityType(), "身份类型不能为空"));
        entity.setSourceProvince(trimToNull(request.getSourceProvince()));
        entity.setSourceCity(trimToNull(request.getSourceCity()));
        entity.setSourceCounty(trimToNull(request.getSourceCounty()));
        entity.setSourceFull(trimToNull(request.getSourceFull()));
        if (enforceWriteScope) {
            entity.setCollegeId(allowedCollegeId(request.getCollegeId()));
        }
        entity.setGrade(trimToNull(request.getGrade()));
        entity.setClassName(trimToNull(request.getClassName()));
    }

    private Long allowedCollegeId(Long requestedCollegeId) {
        if (requestedCollegeId == null) {
            throw new BizException("学院不能为空");
        }
        DataScopeContext.Scope scope = dataScopeService.resolve("student:edit");
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该学院学生");
        }
        if (scope.allSchool()) {
            return requestedCollegeId;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && scope.getCollegeIds().contains(requestedCollegeId)) {
            return requestedCollegeId;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该学院学生");
    }

    private void ensureEditable(Student entity, String operation) {
        if (entity.getLocked() != null && entity.getLocked() == 1) {
            throw new BizException("关键字段已锁定，不能" + operation);
        }
        if (!StudentStatus.of(entity.getStatus()).editable()) {
            throw new BizException("当前状态不可编辑");
        }
    }

    private boolean criticalChanged(Student entity, StudentSaveRequest request) {
        return changed(entity.getName(), request.getName())
                || changed(entity.getIdCardType(), request.getIdCardType())
                || changed(entity.getIdCardNo(), request.getIdCardNo())
                || changed(entity.getBirthDate(), request.getBirthDate())
                || changed(entity.getIdentityType(), request.getIdentityType());
    }

    private boolean changed(String oldValue, String newValue) {
        return !String.valueOf(trimToNull(oldValue)).equals(String.valueOf(trimToNull(newValue)));
    }

    private void ensureRequired(Student entity) {
        if (!StringUtils.hasText(entity.getStudentNo()) || !StringUtils.hasText(entity.getName())
                || !StringUtils.hasText(entity.getIdCardNo()) || !StringUtils.hasText(entity.getBirthDate())
                || entity.getCollegeId() == null) {
            throw new BizException("学生基本信息未填写完整");
        }
    }

    private String returnTargetFromSecondRejected(StudentStatus currentStatus) {
        if (currentStatus != StudentStatus.SECOND_REJECTED) {
            return StudentStatus.FIRST_REVIEW.name();
        }
        String target = paramService.getString("review.return.target", "FIRST_REVIEW");
        return "SECOND_REVIEW".equalsIgnoreCase(target) ? StudentStatus.SECOND_REVIEW.name() : StudentStatus.FIRST_REVIEW.name();
    }

    private void ensureStudentAccount(Student student) {
        if (!paramService.getBoolean("student.autoCreateAccount", true)) {
            return;
        }
        SysUser user = userMapper.selectByUsername(student.getStudentNo());
        if (user == null) {
            user = new SysUser();
            user.setUsername(student.getStudentNo());
            user.setPasswordHash(passwordEncoder.encode(initialPassword(student.getIdCardNo())));
            user.setFailedLoginCount(0);
            user.setMustChangePwd(1);
            user.setStatus("ENABLED");
            user.setUserType("STUDENT");
            user.setRealName(student.getName());
            user.setCollegeId(student.getCollegeId());
            user.setStudentId(student.getId());
            userMapper.insert(user);
        } else {
            user.setRealName(student.getName());
            user.setStatus("ENABLED");
            user.setUserType("STUDENT");
            user.setCollegeId(student.getCollegeId());
            user.setStudentId(student.getId());
            userMapper.updateById(user);
            userMapper.update(new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, user.getId())
                    .set(SysUser::getStudentId, student.getId())
                    .set(SysUser::getCollegeId, student.getCollegeId()));
        }
        userRoleMapper.upsert(IdWorker.getId(), user.getId(), studentRoleId(), SYS_OPERATOR);
    }

    private String initialPassword(String idCardNo) {
        String rule = paramService.getString("student.defaultPwd", "idcard6");
        if (!"idcard6".equalsIgnoreCase(rule)) {
            return rule;
        }
        String text = idCardNo == null ? "" : idCardNo.trim();
        if (text.length() < 6) {
            throw new BizException("证件号码不足以生成初始密码");
        }
        return text.substring(text.length() - 6);
    }

    private Long studentRoleId() {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getCode, "STUDENT")
                .last("LIMIT 1"));
        return role == null ? STUDENT_ROLE_ID : role.getId();
    }

    private StudentVO toVO(Student entity, boolean plain) {
        StudentVO vo = new StudentVO();
        vo.setId(entity.getId());
        vo.setStudentNo(entity.getStudentNo());
        vo.setName(entity.getName());
        vo.setGender(entity.getGender());
        vo.setIdCardType(entity.getIdCardType());
        vo.setIdCardNo(plain ? entity.getIdCardNo() : SensitiveMasker.idCard(entity.getIdCardNo()));
        vo.setBirthDate(entity.getBirthDate());
        vo.setIdentityType(entity.getIdentityType());
        vo.setSourceProvince(entity.getSourceProvince());
        vo.setSourceCity(entity.getSourceCity());
        vo.setSourceCounty(entity.getSourceCounty());
        vo.setSourceFull(entity.getSourceFull());
        vo.setCollegeId(entity.getCollegeId());
        vo.setGrade(entity.getGrade());
        vo.setClassName(entity.getClassName());
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(StudentStatus.of(entity.getStatus()).label());
        vo.setLocked(entity.getLocked());
        vo.setFirstReviewComment(entity.getFirstReviewComment());
        vo.setSecondReviewComment(entity.getSecondReviewComment());
        return vo;
    }

    private String studentTarget(Student entity) {
        return entity.getId() + "/" + entity.getStudentNo() + "/" + entity.getName();
    }

    private Student requireStudent(Long id) {
        if (id == null) {
            throw new BizException("学生ID不能为空");
        }
        Student entity = studentMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "学生不存在");
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

    private boolean existsStudentNo(String studentNo, Long excludeId) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<Student>().eq(Student::getStudentNo, studentNo);
        if (excludeId != null) {
            wrapper.ne(Student::getId, excludeId);
        }
        return studentMapper.selectCount(wrapper) > 0;
    }

    private boolean existsIdCardNo(String idCardNo, Long excludeId) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<Student>().eq(Student::getIdCardNo, idCardNo);
        if (excludeId != null) {
            wrapper.ne(Student::getId, excludeId);
        }
        return studentMapper.selectCount(wrapper) > 0;
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
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
