package cn.edu.gpnu.platform.business.certificate.service.impl;

import cn.edu.gpnu.platform.business.certificate.dto.CertificateCorrectRequest;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateGenerateRequest;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateIssueRequest;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateQuery;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateVoidRequest;
import cn.edu.gpnu.platform.business.certificate.entity.CertSequence;
import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertSequenceMapper;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.certificate.service.CertificateService;
import cn.edu.gpnu.platform.business.certificate.support.CertificateStatus;
import cn.edu.gpnu.platform.business.certificate.vo.CertificatePrecheckVO;
import cn.edu.gpnu.platform.business.certificate.vo.CertificateVO;
import cn.edu.gpnu.platform.business.material.service.ProcessMaterialService;
import cn.edu.gpnu.platform.business.material.vo.ProcessStatusVO;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.student.support.SensitiveMasker;
import cn.edu.gpnu.platform.business.student.support.StudentStatus;
import cn.edu.gpnu.platform.business.testresult.entity.AbilityTestResult;
import cn.edu.gpnu.platform.business.testresult.mapper.AbilityTestResultMapper;
import cn.edu.gpnu.platform.business.testresult.service.AbilityTestResultService;
import cn.edu.gpnu.platform.business.testresult.vo.AbilityTestValidityVO;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.business.training.support.TrainingStatus;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.support.VideoReviewStatus;
import cn.edu.gpnu.platform.common.api.PageQuery;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CertificateServiceImpl implements CertificateService {

    private static final int MAX_SEQUENCE = 99_999;
    private static final String DEFAULT_SEQ_SCOPE = "SCHOOL_YEAR_SEGMENT";
    private static final String DEFAULT_SCHOOL_CODE = "10588";
    private static final String DEFAULT_PROVINCE_CODE = "44";

    private final CertificateMapper certificateMapper;
    private final CertSequenceMapper sequenceMapper;
    private final StudentMapper studentMapper;
    private final TrainingProfileMapper trainingProfileMapper;
    private final VideoReviewMapper videoReviewMapper;
    private final AbilityTestResultMapper abilityTestResultMapper;
    private final SysDictItemMapper dictItemMapper;
    private final ProcessMaterialService processMaterialService;
    private final AbilityTestResultService abilityTestResultService;
    private final DataScopeService dataScopeService;
    private final ParamService paramService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final IdCardProtectionService idCardProtectionService;

    // Phase 44e（P1-1 真分页铺开）：由「全表 selectList 后 new PageResult<>(size, records)」改为
    // MyBatis-Plus Page + selectPage 真分页。@DataScope（CertificateController.list，alias=certificate）设置的
    // 线程范围经数据权限拦截器在 selectPage 的 count 与数据两条 SQL 上均生效 → 该页与 total 同为「已按范围过滤」的结果。
    @Override
    public PageResult<CertificateVO> list(CertificateQuery query) {
        CertificateQuery q = query == null ? new CertificateQuery() : query;
        Page<Certificate> result = certificateMapper.selectPage(
                PageQuery.of(q.getPage(), q.getSize()), buildListWrapper(q));
        List<CertificateVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public CertificateVO detail(Long id) {
        Certificate certificate = requireCertificate(id);
        ensureCanRead(certificate);
        return toVO(certificate);
    }

    @Override
    public CertificatePrecheckVO precheck(Long studentId, String assessmentYear) {
        Student student = requireStudent(studentId);
        ensureCanReadStudent(student);
        return doPrecheck(student, requiredTrim(assessmentYear, "考核年度不能为空"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateVO generate(CertificateGenerateRequest request) {
        ensureSchoolWrite("cert:generate");
        Student student = requireStudent(request.getStudentId());
        String year = requiredTrim(request.getAssessmentYear(), "考核年度不能为空");
        CertificatePrecheckVO precheck = doPrecheck(student, year);
        if (!precheck.isPassed()) {
            throw new BizException("证书前置条件不满足: " + String.join("、", precheck.getMissingItems()));
        }
        TrainingProfile training = requirePassedTraining(student.getId(), year);
        Certificate existing = activeCertificate(student.getId(), year);
        if (existing != null) {
            throw new BizException("该学生本年度已有未作废证书");
        }
        Certificate entity = new Certificate();
        entity.setStudentId(student.getId());
        entity.setCollegeId(student.getCollegeId());
        entity.setAssessmentYear(year);
        entity.setStatus(CertificateStatus.WAIT_GENERATE.name());
        entity.setLocked(0);
        entity.setReissueOriginCertNo(trimToNull(request.getReissueOriginCertNo()));
        snapshot(entity, student, training);
        entity.setCertNo(nextCertNo(year, training.getEducationLevel(), training.getTeachingSegment()));
        entity.setStatus(CertificateStatus.GENERATED.name());
        entity.setLocked(1);
        try {
            certificateMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // 生成列唯一键 uk_cert_active（Phase42.1）：两并发 generate 各自过 activeCertificate 快照预检、
            // 都插入活跃证书 → 后到者撞该唯一键，转友好提示（否则裸 DuplicateKeyException 被全局兜底为 500）。
            if (violatesIndex(e, "uk_cert_active")) {
                throw new BizException("本年度已有有效证书");
            }
            // 既有：证书编号唯一键 uk_certificate_cert_no 撞号
            throw new BizException("证书编号已存在，请重试");
        }
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateVO issue(Long id, CertificateIssueRequest request) {
        ensureSchoolWrite("cert:issue");
        Certificate entity = requireCertificate(id);
        if (CertificateStatus.of(entity.getStatus()) != CertificateStatus.GENERATED) {
            throw new BizException("当前状态不可签发");
        }
        String oldStatus = entity.getStatus();
        entity.setIssuer(requiredTrim(request.getIssuer(), "签发人不能为空"));
        String issueDate = normalizeDate(requiredTrim(request.getIssueDate(), "签发日期不能为空"));
        entity.setIssueDate(issueDate);
        entity.setValidUntil(validUntil(issueDate));
        entity.setStatus(CertificateStatus.ISSUED.name());
        entity.setLocked(1);
        Certificate patch = new Certificate();
        patch.setIssuer(entity.getIssuer());
        patch.setIssueDate(entity.getIssueDate());
        patch.setValidUntil(entity.getValidUntil());
        patch.setStatus(entity.getStatus());
        patch.setLocked(entity.getLocked());
        // 状态流转仅写本次状态字段，避免把并发更正后的证书内容覆盖回旧快照。
        if (certificateMapper.update(patch, new LambdaUpdateWrapper<Certificate>()
                .eq(Certificate::getId, id).eq(Certificate::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateVO markExported(Long id) {
        ensureSchoolWrite("cert:view");
        Certificate entity = requireCertificate(id);
        if (CertificateStatus.of(entity.getStatus()) != CertificateStatus.ISSUED) {
            throw new BizException("当前状态不可标记导出");
        }
        String oldStatus = entity.getStatus();
        entity.setStatus(CertificateStatus.EXPORTED.name());
        entity.setLocked(1);
        Certificate patch = new Certificate();
        patch.setStatus(entity.getStatus());
        patch.setLocked(entity.getLocked());
        if (certificateMapper.update(patch, new LambdaUpdateWrapper<Certificate>()
                .eq(Certificate::getId, id).eq(Certificate::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateVO archive(Long id) {
        ensureSchoolWrite("cert:view");
        Certificate entity = requireCertificate(id);
        if (CertificateStatus.of(entity.getStatus()) != CertificateStatus.EXPORTED) {
            throw new BizException("当前状态不可归档");
        }
        String oldStatus = entity.getStatus();
        entity.setStatus(CertificateStatus.ARCHIVED.name());
        entity.setLocked(1);
        Certificate patch = new Certificate();
        patch.setStatus(entity.getStatus());
        patch.setLocked(entity.getLocked());
        if (certificateMapper.update(patch, new LambdaUpdateWrapper<Certificate>()
                .eq(Certificate::getId, id).eq(Certificate::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateVO voidCertificate(Long id, CertificateVoidRequest request) {
        ensureSchoolWrite("cert:void");
        Certificate entity = requireCertificate(id);
        CertificateStatus status = CertificateStatus.of(entity.getStatus());
        if (status != CertificateStatus.GENERATED && status != CertificateStatus.ISSUED) {
            throw new BizException("当前状态不可作废");
        }
        String oldStatus = entity.getStatus();
        entity.setVoidReason(requiredTrim(request.getReason(), "作废原因不能为空"));
        entity.setVoidOperatorId(UserContext.getUserIdOrSystem());
        entity.setVoidTime(LocalDateTime.now());
        entity.setStatus(CertificateStatus.VOIDED.name());
        entity.setLocked(1);
        Certificate patch = new Certificate();
        patch.setVoidReason(entity.getVoidReason());
        patch.setVoidOperatorId(entity.getVoidOperatorId());
        patch.setVoidTime(entity.getVoidTime());
        patch.setStatus(entity.getStatus());
        patch.setLocked(entity.getLocked());
        if (certificateMapper.update(patch, new LambdaUpdateWrapper<Certificate>()
                .eq(Certificate::getId, id).eq(Certificate::getStatus, oldStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        recordAudit(entity, "void", oldStatus, entity.getStatus(), entity.getVoidReason());
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateVO reissue(Long id) {
        ensureSchoolWrite("cert:reissue");
        Certificate original = requireCertificate(id);
        if (CertificateStatus.of(original.getStatus()) != CertificateStatus.VOIDED) {
            throw new BizException("仅已作废证书可重开");
        }
        Certificate active = activeCertificate(original.getStudentId(), original.getAssessmentYear());
        if (active != null) {
            throw new BizException("该学生本年度已有未作废证书");
        }
        String oldStatus = original.getStatus();
        original.setStatus(CertificateStatus.REISSUED.name());
        original.setLocked(1);
        // 原子条件更新：仅当原证仍为已作废态时才置为已重开，既把 REISSUED 落库（§7.4 死枚举）
        // 又防并发重复重开——两并发只有一个能命中 VOIDED、另一个受影响行数=0 被拒（P0-10）。
        Certificate patch = new Certificate();
        patch.setStatus(original.getStatus());
        patch.setLocked(original.getLocked());
        if (certificateMapper.update(patch, new LambdaUpdateWrapper<Certificate>()
                .eq(Certificate::getId, id).eq(Certificate::getStatus, oldStatus)) == 0) {
            throw new BizException("证书状态已变更或已重开，请刷新后重试");
        }
        recordAudit(original, "reissue", oldStatus, original.getStatus(), "重开证书");
        CertificateGenerateRequest request = new CertificateGenerateRequest();
        request.setStudentId(original.getStudentId());
        request.setAssessmentYear(original.getAssessmentYear());
        request.setReissueOriginCertNo(original.getCertNo());
        return generate(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CertificateVO correct(Long id, CertificateCorrectRequest request) {
        ensureSchoolWrite("cert:correct");
        Certificate entity = requireCertificate(id);
        CertificateStatus status = CertificateStatus.of(entity.getStatus());
        if (status == CertificateStatus.VOIDED || status == CertificateStatus.REISSUED || status == CertificateStatus.ARCHIVED) {
            throw new BizException("当前状态不可更正");
        }
        String oldStatus = entity.getStatus();
        if (StringUtils.hasText(request.getCertNo())) {
            String certNo = request.getCertNo().trim();
            if (!certNo.matches("^\\d{18}$")) {
                throw new BizException("证书编号必须为18位数字");
            }
            if (certificateMapper.selectCount(new LambdaQueryWrapper<Certificate>()
                    .eq(Certificate::getCertNo, certNo)
                    .ne(Certificate::getId, entity.getId())) > 0) {
                throw new BizException("证书编号已存在");
            }
            entity.setCertNo(certNo);
        }
        if (StringUtils.hasText(request.getValidUntil())) {
            entity.setValidUntil(normalizeDate(request.getValidUntil().trim()));
        }
        if (StringUtils.hasText(request.getTeachingSubjectCode())) {
            entity.setTeachingSubjectCode(request.getTeachingSubjectCode().trim());
        }
        if (StringUtils.hasText(request.getTeachingSubjectName())) {
            entity.setTeachingSubjectName(request.getTeachingSubjectName().trim());
        }
        if (StringUtils.hasText(request.getTeachingSegment())) {
            entity.setTeachingSegment(request.getTeachingSegment().trim());
        }
        if (StringUtils.hasText(request.getTrainingGoal())) {
            entity.setTrainingGoal(request.getTrainingGoal().trim());
        }
        // Phase 48 §7.4（P2）：更正 任教学段 或 证书编号 时，校验 18 位标准证书号内嵌的
        // 学历码（第10位/idx9）/学段码（第13位/idx12）与更正后的 学历层次/任教学段 一致，否则拒绝——
        // 与导入端 ExchangeServiceImpl.validateCertificateNo 的段码校验、nextCertNo 的编排同一规则，
        // 防止更正把「证书编号」与「学段/层次字段」改成互相矛盾。
        if (StringUtils.hasText(request.getCertNo()) || StringUtils.hasText(request.getTeachingSegment())) {
            ensureCertNoMatchesSegmentAndLevel(entity);
        }
        entity.setCorrectionReason(requiredTrim(request.getReason(), "更正原因不能为空"));
        entity.setLocked(1);
        Certificate patch = new Certificate();
        patch.setCertNo(StringUtils.hasText(request.getCertNo()) ? entity.getCertNo() : null);
        patch.setValidUntil(StringUtils.hasText(request.getValidUntil()) ? entity.getValidUntil() : null);
        patch.setTeachingSubjectCode(StringUtils.hasText(request.getTeachingSubjectCode())
                ? entity.getTeachingSubjectCode() : null);
        patch.setTeachingSubjectName(StringUtils.hasText(request.getTeachingSubjectName())
                ? entity.getTeachingSubjectName() : null);
        patch.setTeachingSegment(StringUtils.hasText(request.getTeachingSegment())
                ? entity.getTeachingSegment() : null);
        patch.setTrainingGoal(StringUtils.hasText(request.getTrainingGoal()) ? entity.getTrainingGoal() : null);
        patch.setCorrectionReason(entity.getCorrectionReason());
        patch.setLocked(entity.getLocked());
        LambdaUpdateWrapper<Certificate> update = new LambdaUpdateWrapper<Certificate>()
                .eq(Certificate::getId, id)
                .eq(Certificate::getStatus, oldStatus);
        // 更正以读取时状态作 CAS，且不写 status；流转先提交时旧更正失败，反向顺序则保留更正内容。
        if (certificateMapper.update(patch, update) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        recordAudit(entity, "correct", oldStatus, entity.getStatus(), entity.getCorrectionReason());
        return toVO(entity);
    }

    /**
     * 校验 18 位标准证书号内嵌段码与证书 学历层次/任教学段 一致：学历码在第 10 位（idx 9）、学段码在第 13 位（idx 12），
     * 与 nextCertNo 的编排、导入端 ExchangeServiceImpl.validateCertificateNo 的校验同一规则。非 18 位历史/外部编号
     * 无法映射，跳过校验（与 reserveImportedSequence 对历史/外部编号的取舍一致）。
     */
    private void ensureCertNoMatchesSegmentAndLevel(Certificate entity) {
        String certNo = entity.getCertNo();
        if (certNo == null || !certNo.matches("^\\d{18}$")) {
            return;
        }
        if (StringUtils.hasText(entity.getEducationLevel())) {
            String levelCode = certCode("education_level", entity.getEducationLevel(), "certLevelCode", "学历层次证书码未配置");
            if (!certNo.substring(9, 10).equals(levelCode)) {
                throw new BizException("证书编号内嵌学历码与学历层次不一致");
            }
        }
        if (StringUtils.hasText(entity.getTeachingSegment())) {
            String segmentCode = certCode("teaching_segment", entity.getTeachingSegment(), "certSegmentCode", "任教学段证书码未配置");
            if (!certNo.substring(12, 13).equals(segmentCode)) {
                throw new BizException("证书编号内嵌学段码与任教学段不一致");
            }
        }
    }

    private LambdaQueryWrapper<Certificate> buildListWrapper(CertificateQuery q) {
        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<Certificate>()
                .orderByDesc(Certificate::getCreatedAt)
                .orderByAsc(Certificate::getStudentId);
        if (q.getStudentId() != null) {
            wrapper.eq(Certificate::getStudentId, q.getStudentId());
        }
        if (q.getCollegeId() != null) {
            wrapper.eq(Certificate::getCollegeId, q.getCollegeId());
        }
        if (StringUtils.hasText(q.getAssessmentYear())) {
            wrapper.eq(Certificate::getAssessmentYear, q.getAssessmentYear().trim());
        }
        if (StringUtils.hasText(q.getStatus())) {
            wrapper.eq(Certificate::getStatus, q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String keyword = q.getKeyword().trim();
            wrapper.and(w -> w.like(Certificate::getStudentNo, keyword)
                    .or()
                    .like(Certificate::getStudentName, keyword)
                    .or()
                    .like(Certificate::getCertNo, keyword)
                    .or()
                    .like(Certificate::getTeachingSubjectName, keyword));
        }
        return wrapper;
    }

    private CertificatePrecheckVO doPrecheck(Student student, String year) {
        CertificatePrecheckVO vo = new CertificatePrecheckVO();
        vo.setStudentId(student.getId());
        vo.setAssessmentYear(year);
        List<String> missing = new ArrayList<>();
        if (StudentStatus.of(student.getStatus()) != StudentStatus.PASSED) {
            missing.add("基本信息复审通过");
        }
        TrainingProfile training = passedTraining(student.getId(), year);
        if (training == null) {
            missing.add("培养信息复审通过");
        }
        try {
            ProcessStatusVO processStatus = processMaterialService.processStatus(student.getId(), year);
            if (!processStatus.isQualified()) {
                missing.add("过程性考核");
            }
        } catch (BizException e) {
            missing.add("过程性考核");
        }
        try {
            AbilityTestValidityVO validity = abilityTestResultService.validity(student.getId(), year);
            if (!validity.isValidForCertificate()) {
                missing.add("测试/免考结论有效");
            }
            AbilityTestResult testResult = abilityTestResultMapper.selectOne(new LambdaQueryWrapper<AbilityTestResult>()
                    .eq(AbilityTestResult::getStudentId, student.getId())
                    .eq(AbilityTestResult::getAssessmentYear, year)
                    .last("LIMIT 1"));
            if (testResult == null || !"CONFIRMED".equals(testResult.getConfirmStatus())) {
                missing.add("教务处确认");
            }
        } catch (BizException e) {
            missing.add("测试/免考结论有效");
        }
        if (paramService.getBoolean("video.required", true) && !videoPassed(student.getId(), year)) {
            missing.add("视频评审通过");
        }
        vo.setMissingItems(missing);
        vo.setPassed(missing.isEmpty());
        return vo;
    }

    private boolean videoPassed(Long studentId, String year) {
        VideoReview review = videoReviewMapper.selectOne(new LambdaQueryWrapper<VideoReview>()
                .eq(VideoReview::getStudentId, studentId)
                .eq(VideoReview::getAssessmentYear, year)
                .last("LIMIT 1"));
        return review != null
                && VideoReviewStatus.of(review.getStatus()) == VideoReviewStatus.CONFIRMED
                && "PASS".equals(review.getFinalConclusion());
    }

    private TrainingProfile passedTraining(Long studentId, String year) {
        TrainingProfile profile = trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, studentId)
                .eq(TrainingProfile::getAssessmentYear, year)
                .last("LIMIT 1"));
        if (profile == null || TrainingStatus.of(profile.getStatus()) != TrainingStatus.PASSED) {
            return null;
        }
        return profile;
    }

    private TrainingProfile requirePassedTraining(Long studentId, String year) {
        TrainingProfile training = passedTraining(studentId, year);
        if (training == null) {
            throw new BizException("培养信息未复审通过");
        }
        return training;
    }

    private Certificate activeCertificate(Long studentId, String year) {
        return certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getStudentId, studentId)
                .eq(Certificate::getAssessmentYear, year)
                .notIn(Certificate::getStatus, CertificateStatus.VOIDED.name(), CertificateStatus.REISSUED.name())
                .orderByDesc(Certificate::getCreatedAt)
                .last("LIMIT 1"));
    }

    private void snapshot(Certificate entity, Student student, TrainingProfile training) {
        entity.setStudentNo(student.getStudentNo());
        entity.setStudentName(student.getName());
        entity.setIdCardType(student.getIdCardType());
        String plainIdCardNo = idCardProtectionService.decrypt(student.getIdCardNo());
        entity.setIdCardNo(idCardProtectionService.encrypt(plainIdCardNo));
        entity.setIdCardHmac(idCardProtectionService.hmac(plainIdCardNo));
        entity.setEducationLevel(training.getEducationLevel());
        entity.setTrainingGoal(training.getTrainingGoal());
        entity.setTeachingSegment(training.getTeachingSegment());
        entity.setTeachingSubjectCode(training.getTeachingSubjectCode());
        entity.setTeachingSubjectName(training.getTeachingSubjectName());
    }

    private String nextCertNo(String year, String educationLevel, String teachingSegment) {
        String schoolCode = fixedDigits(paramService.getString("cert.school.code", DEFAULT_SCHOOL_CODE), 5, "学校代码");
        String provinceCode = fixedDigits(paramService.getString("cert.province.code", DEFAULT_PROVINCE_CODE), 2, "省码");
        String levelCode = certCode("education_level", educationLevel, "certLevelCode", "学历层次证书码未配置");
        String segmentCode = certCode("teaching_segment", teachingSegment, "certSegmentCode", "任教学段证书码未配置");
        String scopeKey = scopeKey(year, schoolCode, segmentCode);
        int seq = nextSequence(scopeKey);
        return year + schoolCode + levelCode + provinceCode + segmentCode + String.format("%05d", seq);
    }

    private int nextSequence(String scopeKey) {
        CertSequence sequence = lockScopeRow(scopeKey);
        int next = (sequence.getCurrentSeq() == null ? 0 : sequence.getCurrentSeq()) + 1;
        if (next > MAX_SEQUENCE) {
            throw new BizException("证书序列已超过99999");
        }
        setSequence(sequence, next);
        return next;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reserveImportedSequence(String certNo) {
        String cert = certNo == null ? null : certNo.trim();
        // 仅本系统 18 位标准编号可映射到序列作用域；历史/外部编号无法映射，跳过占用（不阻断导入）。
        if (cert == null || !cert.matches("^\\d{18}$")) {
            return;
        }
        int seq;
        try {
            seq = Integer.parseInt(cert.substring(13));
        } catch (NumberFormatException e) {
            return;
        }
        // 由编号自身还原 年度/学校码/学段码，与 nextCertNo 采用同一 scopeKey 规则 →
        // 后续自动生成落在同一序列行，从而跳过已占号（导入校验已保证编号段码与配置一致）。
        String scopeKey = scopeKey(cert.substring(0, 4), cert.substring(4, 9), cert.substring(12, 13));
        CertSequence sequence = lockScopeRow(scopeKey);
        int current = sequence.getCurrentSeq() == null ? 0 : sequence.getCurrentSeq();
        if (seq > current) {
            setSequence(sequence, seq);
        }
    }

    private CertSequence lockScopeRow(String scopeKey) {
        sequenceMapper.ensureScopeRow(IdWorker.getId(), scopeKey);
        CertSequence sequence = sequenceMapper.selectByScopeKeyForUpdate(scopeKey);
        if (sequence == null) {
            throw new BizException("证书序列初始化失败");
        }
        return sequence;
    }

    private void setSequence(CertSequence sequence, int value) {
        sequenceMapper.update(null, new LambdaUpdateWrapper<CertSequence>()
                .eq(CertSequence::getId, sequence.getId())
                .set(CertSequence::getCurrentSeq, value));
    }

    private String scopeKey(String year, String schoolCode, String segmentCode) {
        String scope = paramService.getString("cert.seq.scope", DEFAULT_SEQ_SCOPE).trim().toUpperCase(Locale.ROOT);
        if ("SCHOOL_YEAR".equals(scope)) {
            return schoolCode + ":" + year;
        }
        if ("SCHOOL_YEAR_SEGMENT".equals(scope)) {
            return schoolCode + ":" + year + ":" + segmentCode;
        }
        throw new BizException("未知证书序列作用域: " + scope);
    }

    private String certCode(String typeCode, String itemCode, String fieldName, String message) {
        SysDictItem item = dictItemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getItemCode, requiredTrim(itemCode, message))
                .eq(SysDictItem::getStatus, 1)
                .last("LIMIT 1"));
        if (item == null || !StringUtils.hasText(item.getExtJson())) {
            throw new BizException(message + ": " + itemCode);
        }
        try {
            JsonNode node = objectMapper.readTree(item.getExtJson());
            String value = node.path(fieldName).asText(null);
            if (!StringUtils.hasText(value)) {
                throw new BizException(message + ": " + itemCode);
            }
            return value.trim();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(message + ": " + itemCode);
        }
    }

    private String fixedDigits(String value, int length, String label) {
        String text = requiredTrim(value, label + "不能为空");
        if (!text.matches("^\\d{" + length + "}$")) {
            throw new BizException(label + "必须为" + length + "位数字");
        }
        return text;
    }

    private String validUntil(String issueDate) {
        LocalDate date = parseDate(issueDate);
        int validYear = date.getYear() + 3;
        if (date.getMonthValue() <= 6) {
            return validYear + "/6/30";
        }
        return validYear + "/12/31";
    }

    private String normalizeDate(String value) {
        LocalDate date = parseDate(value);
        return date.getYear() + "/" + date.getMonthValue() + "/" + date.getDayOfMonth();
    }

    private LocalDate parseDate(String value) {
        String text = requiredTrim(value, "日期不能为空");
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy/M/d"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ISO_LOCAL_DATE
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (Exception ignored) {
            }
        }
        throw new BizException("日期格式不正确，应为YYYY/M/D");
    }

    private CertificateVO toVO(Certificate entity) {
        CertificateVO vo = new CertificateVO();
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setCollegeId(entity.getCollegeId());
        vo.setAssessmentYear(entity.getAssessmentYear());
        vo.setCertNo(entity.getCertNo());
        vo.setStudentNo(entity.getStudentNo());
        vo.setStudentName(entity.getStudentName());
        vo.setIdCardType(entity.getIdCardType());
        vo.setIdCardNo(SensitiveMasker.idCard(idCardProtectionService.decrypt(entity.getIdCardNo())));
        vo.setEducationLevel(entity.getEducationLevel());
        vo.setTrainingGoal(entity.getTrainingGoal());
        vo.setTeachingSegment(entity.getTeachingSegment());
        vo.setTeachingSubjectCode(entity.getTeachingSubjectCode());
        vo.setTeachingSubjectName(entity.getTeachingSubjectName());
        vo.setIssuer(entity.getIssuer());
        vo.setIssueDate(entity.getIssueDate());
        vo.setValidUntil(entity.getValidUntil());
        CertificateStatus status = CertificateStatus.of(entity.getStatus());
        vo.setStatus(status.name());
        vo.setStatusLabel(status.label());
        vo.setVoidReason(entity.getVoidReason());
        vo.setReissueOriginCertNo(entity.getReissueOriginCertNo());
        vo.setCorrectionReason(entity.getCorrectionReason());
        vo.setLocked(entity.getLocked());
        return vo;
    }

    private void ensureSchoolWrite(String permissionCode) {
        DataScopeContext.Scope scope = dataScopeService.resolve(permissionCode);
        if (scope == null || !scope.allSchool()) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "证书操作仅限校级权限");
        }
    }

    private void ensureCanRead(Certificate certificate) {
        Student student = requireStudent(certificate.getStudentId());
        ensureCanReadStudent(student);
    }

    private void ensureCanReadStudent(Student student) {
        DataScopeContext.Scope scope = dataScopeService.resolve("cert:view");
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该证书");
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
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该证书");
    }

    private Certificate requireCertificate(Long id) {
        if (id == null) {
            throw new BizException("证书ID不能为空");
        }
        Certificate entity = certificateMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "证书不存在");
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

    private void recordAudit(Certificate entity, String operation, String oldStatus, String newStatus, String comment) {
        SysAuditLog log = new SysAuditLog();
        log.setBizType("cert");
        log.setBizId(entity.getId());
        log.setTarget(entity.getId() + "/" + entity.getAssessmentYear() + "/" + entity.getStudentId()
                + "/" + entity.getCertNo());
        log.setOperatorId(UserContext.getUserIdOrSystem());
        log.setOperateTime(LocalDateTime.now());
        log.setOperation(operation);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setComment(comment);
        auditLogService.record(log);
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

    /**
     * 判断 DuplicateKeyException 是否由指定唯一索引触发（沿异常 cause 链匹配索引名）。
     * generate() 现可能撞两种唯一键（uk_certificate_cert_no 撞号 / uk_cert_active 本年度已有有效证书），
     * 需按索引名区分，避免统一措辞误导。
     */
    private static boolean violatesIndex(Throwable e, String indexName) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.contains(indexName)) {
                return true;
            }
        }
        return false;
    }
}
