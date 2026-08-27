package cn.edu.gpnu.platform.business.certificate.service.impl;

import cn.edu.gpnu.platform.business.certificate.dto.CertificateCorrectRequest;
import cn.edu.gpnu.platform.business.certificate.entity.CertSequence;
import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertSequenceMapper;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.material.service.ProcessMaterialService;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.testresult.mapper.AbilityTestResultMapper;
import cn.edu.gpnu.platform.business.testresult.service.AbilityTestResultService;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.business.training.support.TrainingLinkValidator;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.annotations.Select;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceCorrectionTest {

    private static final long CERTIFICATE_ID = 101L;
    private static final String OLD_CERT_NO = "203510588344400007";
    private static final String NEW_CERT_NO = "203510588344400042";

    static {
        MapperBuilderAssistant certificateAssistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "certificate-correction-test");
        certificateAssistant.setCurrentNamespace(CertificateMapper.class.getName());
        TableInfoHelper.initTableInfo(certificateAssistant, Certificate.class);
        MapperBuilderAssistant sequenceAssistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "certificate-sequence-test");
        sequenceAssistant.setCurrentNamespace(CertSequenceMapper.class.getName());
        TableInfoHelper.initTableInfo(sequenceAssistant, CertSequence.class);
    }

    @Mock
    private CertificateMapper certificateMapper;
    @Mock
    private CertSequenceMapper sequenceMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private TrainingProfileMapper trainingProfileMapper;
    @Mock
    private VideoReviewMapper videoReviewMapper;
    @Mock
    private AbilityTestResultMapper abilityTestResultMapper;
    @Mock
    private SysDictItemMapper dictItemMapper;
    @Mock
    private ProcessMaterialService processMaterialService;
    @Mock
    private AbilityTestResultService abilityTestResultService;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private ParamService paramService;
    @Mock
    private AuditLogService auditLogService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private IdCardProtectionService idCardProtectionService;
    @Mock
    private TrainingLinkValidator trainingLinkValidator;

    @InjectMocks
    private CertificateServiceImpl certificateService;

    @BeforeEach
    void allowSchoolCorrection() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.SCHOOL);
        when(dataScopeService.resolve("cert:correct")).thenReturn(scope);
    }

    @Test
    void correctedNumberReservesItsFinalSequence() {
        Certificate certificate = certificate();
        certificate.setUpdatedAt(LocalDateTime.of(2035, 1, 1, 10, 0, 0, 987_654_321));
        Certificate persisted = certificate();
        persisted.setCertNo(NEW_CERT_NO);
        persisted.setCorrectionReason("更正证书编号");
        persisted.setUpdatedAt(LocalDateTime.of(2035, 1, 1, 10, 0));
        when(certificateMapper.selectByIdForUpdate(CERTIFICATE_ID)).thenReturn(certificate);
        when(certificateMapper.selectById(CERTIFICATE_ID)).thenReturn(persisted);
        when(certificateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(certificateMapper.updateById(any(Certificate.class))).thenReturn(1);
        stubStandardNumberValidation();
        when(idCardProtectionService.decrypt("encrypted-id-card")).thenReturn("440101199001010010");

        CertificateCorrectRequest request = correctionRequest(certificate, "更正证书编号");
        request.setCertNo(NEW_CERT_NO);

        String originalRevision = request.getCorrectionRevision();
        assertThat(certificateService.correct(CERTIFICATE_ID, request))
                .satisfies(result -> {
                    assertThat(result.getCertNo()).isEqualTo(NEW_CERT_NO);
                    assertThat(result.getCorrectionRevision()).isNotEqualTo(originalRevision);
                    assertThat(result.getCorrectionRevision())
                            .isEqualTo(CertificateServiceImpl.correctionRevision(persisted));
                });

        verify(certificateMapper).selectByIdForUpdate(CERTIFICATE_ID);
        verify(certificateMapper).selectById(CERTIFICATE_ID);
        ArgumentCaptor<Certificate> updatedCertificate = ArgumentCaptor.forClass(Certificate.class);
        verify(certificateMapper).updateById(updatedCertificate.capture());
        assertThat(updatedCertificate.getValue().getCertNo()).isEqualTo(NEW_CERT_NO);
        assertThat(updatedCertificate.getValue().getCorrectionReason()).isEqualTo("更正证书编号");
        verify(sequenceMapper).ensureScopeRow(any(Long.class), eq("10588:2035:4"));
        ArgumentCaptor<LambdaUpdateWrapper<CertSequence>> sequenceUpdate = sequenceUpdateCaptor();
        verify(sequenceMapper).update(any(), sequenceUpdate.capture());
        assertThat(sequenceUpdate.getValue().getSqlSegment()).contains("id");
        assertThat(sequenceUpdate.getValue().getParamNameValuePairs().values()).contains(202L, 42);
    }

    @Test
    void correctionUsesLockingReadAndSequenceReservationSharesRollbackBoundary() throws Exception {
        Certificate certificate = certificate();
        when(certificateMapper.selectByIdForUpdate(CERTIFICATE_ID)).thenReturn(certificate);
        when(certificateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(certificateMapper.updateById(any(Certificate.class))).thenReturn(0);
        stubStandardNumberValidation();

        CertificateCorrectRequest request = correctionRequest(certificate, "更正编号后模拟写冲突");
        request.setCertNo(NEW_CERT_NO);

        assertThatThrownBy(() -> certificateService.correct(CERTIFICATE_ID, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("操作冲突");

        verify(certificateMapper).selectByIdForUpdate(CERTIFICATE_ID);
        verify(sequenceMapper).update(any(), any(LambdaUpdateWrapper.class));

        Method lockingRead = CertificateMapper.class.getMethod("selectByIdForUpdate", Long.class);
        Select select = lockingRead.getAnnotation(Select.class);
        assertThat(select).isNotNull();
        assertThat(String.join(" ", select.value())).containsIgnoringCase("FOR UPDATE");

        Method correct = CertificateServiceImpl.class.getMethod(
                "correct", Long.class, CertificateCorrectRequest.class);
        Transactional transaction = correct.getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.rollbackFor()).contains(Exception.class);
    }

    @Test
    void staleNumberCorrectionIsRevalidatedAgainstTheLatestLockedAggregate() {
        Certificate latest = certificate();
        latest.setCertNo("203510588344500041");
        latest.setTeachingSegment("secondary_vocational_school");
        latest.setTeachingSubjectCode("svs_ecommerce");
        latest.setTeachingSubjectName("电子商务");
        when(certificateMapper.selectByIdForUpdate(CERTIFICATE_ID)).thenReturn(latest);
        when(certificateMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(paramService.getString("cert.school.code", "10588")).thenReturn("10588");
        when(paramService.getString("cert.province.code", "44")).thenReturn("44");
        when(dictItemMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(dictItem("{\"certLevelCode\":\"3\"}"))
                .thenReturn(dictItem("{\"certSegmentCode\":\"5\"}"));

        CertificateCorrectRequest request = correctionRequest(latest, "基于旧学段提交的迟到编号更正");
        request.setCertNo(NEW_CERT_NO);

        assertThatThrownBy(() -> certificateService.correct(CERTIFICATE_ID, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("内嵌学段码与任教学段不一致");

        verify(certificateMapper).selectByIdForUpdate(CERTIFICATE_ID);
        verify(certificateMapper, never()).updateById(any(Certificate.class));
        verify(sequenceMapper, never()).ensureScopeRow(any(Long.class), any(String.class));
    }

    @Test
    void staleFullFormRevisionIsRejectedBeforeItCanRestoreEarlierCorrection() {
        Certificate openedSnapshot = certificate();
        Certificate latest = certificate();
        latest.setTeachingSubjectCode("sms_math");
        latest.setTeachingSubjectName("数学");
        latest.setCorrectionReason("管理员A更正学科");
        when(certificateMapper.selectByIdForUpdate(CERTIFICATE_ID)).thenReturn(latest);

        CertificateCorrectRequest staleRequest = correctionRequest(openedSnapshot, "管理员B更正有效期");
        staleRequest.setCertNo(openedSnapshot.getCertNo());
        staleRequest.setValidUntil("2038/12/31");
        staleRequest.setTrainingGoal(openedSnapshot.getTrainingGoal());
        staleRequest.setTeachingSegment(openedSnapshot.getTeachingSegment());
        staleRequest.setTeachingSubjectCode(openedSnapshot.getTeachingSubjectCode());
        staleRequest.setTeachingSubjectName(openedSnapshot.getTeachingSubjectName());

        assertThatThrownBy(() -> certificateService.correct(CERTIFICATE_ID, staleRequest))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("记录已更新，请刷新后重试");

        assertThat(latest.getTeachingSubjectCode()).isEqualTo("sms_math");
        assertThat(latest.getTeachingSubjectName()).isEqualTo("数学");
        verify(certificateMapper, never()).updateById(any(Certificate.class));
        verify(sequenceMapper, never()).ensureScopeRow(any(Long.class), any(String.class));
        verify(auditLogService, never()).record(any());
    }

    @Test
    void correctionWithoutRevisionIsRejectedWithRefreshInstruction() {
        Certificate latest = certificate();
        when(certificateMapper.selectByIdForUpdate(CERTIFICATE_ID)).thenReturn(latest);
        CertificateCorrectRequest request = new CertificateCorrectRequest();
        request.setValidUntil("2038/12/31");
        request.setReason("旧客户端请求");

        assertThatThrownBy(() -> certificateService.correct(CERTIFICATE_ID, request))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("版本不能为空，请刷新后重试");

        verify(certificateMapper, never()).updateById(any(Certificate.class));
        verify(auditLogService, never()).record(any());
    }

    private void stubStandardNumberValidation() {
        when(paramService.getString("cert.school.code", "10588")).thenReturn("10588");
        when(paramService.getString("cert.province.code", "44")).thenReturn("44");
        when(paramService.getString("cert.seq.scope", "SCHOOL_YEAR_SEGMENT"))
                .thenReturn("SCHOOL_YEAR_SEGMENT");
        when(dictItemMapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(dictItem("{\"certLevelCode\":\"3\"}"))
                .thenReturn(dictItem("{\"certSegmentCode\":\"4\"}"));
        CertSequence sequence = new CertSequence();
        sequence.setId(202L);
        sequence.setScopeKey("10588:2035:4");
        sequence.setCurrentSeq(7);
        when(sequenceMapper.selectByScopeKeyForUpdate("10588:2035:4")).thenReturn(sequence);
    }

    private Certificate certificate() {
        Certificate certificate = new Certificate();
        certificate.setId(CERTIFICATE_ID);
        certificate.setStudentId(301L);
        certificate.setCollegeId(401L);
        certificate.setAssessmentYear("2035");
        certificate.setCertNo(OLD_CERT_NO);
        certificate.setStudentNo("20350001");
        certificate.setStudentName("证书测试学生");
        certificate.setIdCardType("resident_id");
        certificate.setIdCardNo("encrypted-id-card");
        certificate.setEducationLevel("bachelor");
        certificate.setTrainingGoal("normal_teacher");
        certificate.setTeachingSegment("SENIOR_MIDDLE");
        certificate.setTeachingSubjectCode("sms_chinese");
        certificate.setTeachingSubjectName("语文");
        certificate.setValidUntil("2038/6/30");
        certificate.setStatus("GENERATED");
        certificate.setCorrectionReason("首次生成");
        certificate.setLocked(1);
        return certificate;
    }

    private CertificateCorrectRequest correctionRequest(Certificate snapshot, String reason) {
        CertificateCorrectRequest request = new CertificateCorrectRequest();
        request.setCorrectionRevision(CertificateServiceImpl.correctionRevision(snapshot));
        request.setReason(reason);
        return request;
    }

    private SysDictItem dictItem(String extJson) {
        SysDictItem item = new SysDictItem();
        item.setExtJson(extJson);
        return item;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ArgumentCaptor<LambdaUpdateWrapper<CertSequence>> sequenceUpdateCaptor() {
        return ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }
}
