package cn.edu.gpnu.platform.exchange.service.impl;

import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.student.support.SensitiveMasker;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.exchange.dto.ExchangeQuery;
import cn.edu.gpnu.platform.exchange.entity.ImportErrorDetail;
import cn.edu.gpnu.platform.exchange.entity.ImportExportBatch;
import cn.edu.gpnu.platform.exchange.mapper.ImportErrorDetailMapper;
import cn.edu.gpnu.platform.exchange.mapper.ImportExportBatchMapper;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import cn.edu.gpnu.platform.exchange.support.ExchangeDictionaryHelper;
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.NotificationService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ExchangeExportGuardTest {

    private static final long COLLEGE_A = 4101L;
    private static final long COLLEGE_B = 4102L;
    private static final long OPERATOR_A = 4201L;
    private static final long OPERATOR_B = 4202L;
    private static final long BATCH_A = 4301L;
    private static final long BATCH_B = 4302L;
    private static final String ID_CARD_NO = "11010119900628002X";
    private static final String ID_CARD_CIPHER = "v1:test-id-card-cipher";
    private static final String ID_CARD_HMAC = "test-id-card-hmac";

    static {
        initTableInfo(ImportErrorDetailMapper.class, ImportErrorDetail.class, "exchange-error-export-test");
    }

    @Mock
    private ImportExportBatchMapper batchMapper;
    @Mock
    private ImportErrorDetailMapper errorMapper;
    @Mock
    private CertificateMapper certificateMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private TrainingProfileMapper trainingProfileMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ParamService paramService;
    @Mock
    private ExchangeExcelHelper excelHelper;
    @Mock
    private ExchangeDictionaryHelper dictionaryHelper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private IdCardProtectionService idCardProtectionService;

    @InjectMocks
    private ExchangeServiceImpl service;

    @BeforeEach
    void decryptProtectedFixtureValues() {
        org.mockito.Mockito.lenient()
                .when(idCardProtectionService.decrypt(ID_CARD_CIPHER))
                .thenReturn(ID_CARD_NO);
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void collegeAOperatorCanExportOnlyItsExactBatch() {
        useUser(OPERATOR_A, false);
        when(dataScopeService.resolve("exchange:export:full")).thenReturn(collegeScope(COLLEGE_A));
        when(batchMapper.selectById(BATCH_A)).thenReturn(batch(BATCH_A, OPERATOR_A, COLLEGE_A));
        when(errorMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(error(BATCH_A, "A12345678")));
        when(excelHelper.writeErrorWorkbook(anyList())).thenReturn(new byte[]{1});

        ExchangeQuery query = new ExchangeQuery();
        query.setBatchId(BATCH_A);
        assertThat(service.export("ERROR", query).fileName()).contains("A-异常数据表.xlsx");

        ArgumentCaptor<LambdaQueryWrapper<ImportErrorDetail>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(errorMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("batch_id");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains(BATCH_A);
        assertThat(capturedErrorRow().errorValue()).isEqualTo(SensitiveMasker.idCard("A12345678"));
    }

    @Test
    void collegeBOperatorCanExportItsOwnBatch() {
        useUser(OPERATOR_B, false);
        when(dataScopeService.resolve("exchange:export:full")).thenReturn(collegeScope(COLLEGE_B));
        when(batchMapper.selectById(BATCH_B)).thenReturn(batch(BATCH_B, OPERATOR_B, COLLEGE_B));
        when(errorMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(error(BATCH_B, "B12345678")));
        when(excelHelper.writeErrorWorkbook(anyList())).thenReturn(new byte[]{2});

        ExchangeQuery query = new ExchangeQuery();
        query.setBatchId(BATCH_B);

        assertThat(service.export("ERROR", query).fileName()).contains("B-异常数据表.xlsx");
    }

    @Test
    void collegeAOperatorCannotUseCollegeHintToExportCollegeBOwnedBatch() {
        useUser(OPERATOR_A, false);
        when(dataScopeService.resolve("exchange:export:full")).thenReturn(collegeScope(COLLEGE_A));
        ImportExportBatch other = batch(BATCH_B, OPERATOR_B, COLLEGE_B);
        other.setScopeJson("{\"collegeId\":" + COLLEGE_A + "}");
        when(batchMapper.selectById(BATCH_B)).thenReturn(other);
        ExchangeQuery query = new ExchangeQuery();
        query.setBatchId(BATCH_B);
        query.setCollegeId(COLLEGE_A);

        assertThatThrownBy(() -> service.export("ERROR", query))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(403);

        verify(errorMapper, never()).selectList(any(LambdaQueryWrapper.class));
        verify(excelHelper, never()).writeErrorWorkbook(anyList());
    }

    @Test
    void schoolScopeCanExportAnySpecifiedBatch() {
        useUser(4401L, false);
        when(dataScopeService.resolve("exchange:export:full")).thenReturn(schoolScope());
        when(batchMapper.selectById(BATCH_B)).thenReturn(batch(BATCH_B, OPERATOR_B, COLLEGE_B));
        when(errorMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(error(BATCH_B, "B12345678")));
        when(excelHelper.writeErrorWorkbook(anyList())).thenReturn(new byte[]{3});

        ExchangeQuery query = new ExchangeQuery();
        query.setBatchId(BATCH_B);
        assertThat(service.export("ERROR", query).fileName()).contains("B-异常数据表.xlsx");
    }

    @Test
    void errorExportAlwaysRequiresExactBatch() {
        useUser(4401L, false);

        assertThatThrownBy(() -> service.export("ERROR", new ExchangeQuery()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("必须指定导入批次");

        verify(batchMapper, never()).selectById(any());
        verify(errorMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void standardExportMasksIdentityAndBirthDateWithoutSensitivePermission() {
        useUser(OPERATOR_A, false);
        stubStandardExport();

        service.export("STANDARD", new ExchangeQuery());

        ExchangeStandardRow row = capturedStandardRow();
        assertThat(row.getIdCardNo()).isEqualTo(SensitiveMasker.idCard(ID_CARD_NO));
        assertThat(row.getBirthDate()).isEqualTo("****/*/**");
    }

    @Test
    void standardExportReturnsPlainIdentityOnlyWithSensitivePermission() {
        useUser(4401L, true);
        stubStandardExport();

        service.export("STANDARD", new ExchangeQuery());

        ExchangeStandardRow row = capturedStandardRow();
        assertThat(row.getIdCardNo()).isEqualTo(ID_CARD_NO);
        assertThat(row.getBirthDate()).isEqualTo("1990/6/28");
    }

    @Test
    void errorExportReturnsPlainSensitiveValueOnlyWithSensitivePermission() {
        useUser(OPERATOR_A, true);
        when(dataScopeService.resolve("exchange:export:full")).thenReturn(collegeScope(COLLEGE_A));
        when(batchMapper.selectById(BATCH_A)).thenReturn(batch(BATCH_A, OPERATOR_A, COLLEGE_A));
        when(errorMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(error(BATCH_A, "11010119900628002X")));
        when(excelHelper.writeErrorWorkbook(anyList())).thenReturn(new byte[]{5});
        ExchangeQuery query = new ExchangeQuery();
        query.setBatchId(BATCH_A);

        service.export("ERROR", query);

        assertThat(capturedErrorRow().errorValue()).isEqualTo("11010119900628002X");
    }

    @Test
    void educationGraduateStandardExportAllowsEmptyInternalMajorColumns() {
        useUser(OPERATOR_A, false);
        stubStandardExport("education_master", "", "", COLLEGE_A);

        assertThatCode(() -> service.export("STANDARD", new ExchangeQuery())).doesNotThrowAnyException();

        ExchangeStandardRow row = capturedStandardRow();
        assertThat(row.getInternalMajorCode()).isEmpty();
        assertThat(row.getInternalMajorName()).isEmpty();
    }

    @Test
    void ordinaryNormalStudentStandardExportRejectsEmptyInternalMajorColumns() {
        useUser(OPERATOR_A, false);
        stubStandardExport("student", "", "", COLLEGE_A);

        assertThatThrownBy(() -> service.export("STANDARD", new ExchangeQuery()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("校内专业代码");

        verify(excelHelper, never()).writeStandardWorkbook(anyList(), isNull());
    }

    @Test
    void standardExportAllowsEmptyRemarkColumn() {
        useUser(OPERATOR_A, false);
        stubStandardExport("student", "MATH01", "数学教育", null);

        assertThatCode(() -> service.export("STANDARD", new ExchangeQuery())).doesNotThrowAnyException();

        assertThat(capturedStandardRow().getRemark()).isEmpty();
    }

    private void stubStandardExport() {
        stubStandardExport("student", "MATH01", "数学教育", COLLEGE_A);
    }

    private void stubStandardExport(String identityType, String internalMajorCode,
                                    String internalMajorName, Long certificateCollegeId) {
        Certificate certificate = new Certificate();
        certificate.setId(4501L);
        certificate.setStudentId(4502L);
        certificate.setCollegeId(certificateCollegeId);
        certificate.setAssessmentYear("2026");
        certificate.setStudentNo("P10-PRIVACY");
        certificate.setStudentName("隐私测试");
        certificate.setIdCardType("resident_id_card");
        certificate.setIdCardNo(ID_CARD_CIPHER);
        certificate.setIdCardHmac(ID_CARD_HMAC);
        certificate.setCertNo("202610588344400001");
        certificate.setEducationLevel("bachelor");
        certificate.setTrainingGoal("normal");
        certificate.setTeachingSegment("senior_middle_school");
        certificate.setTeachingSubjectCode("math");
        certificate.setIssuer("签发人");
        certificate.setValidUntil("2029/6/30");
        certificate.setStatus("ISSUED");
        Student student = new Student();
        student.setId(4502L);
        student.setCollegeId(COLLEGE_A);
        student.setGender("male");
        student.setBirthDate("1990/6/28");
        student.setIdentityType(identityType);
        student.setSourceFull("广东省广州市");
        TrainingProfile training = new TrainingProfile();
        training.setStudentId(4502L);
        training.setAssessmentYear("2026");
        training.setSecondDisciplineCode("040102");
        training.setSecondDisciplineName("课程与教学论");
        training.setInternalMajorCode(internalMajorCode);
        training.setInternalMajorName(internalMajorName);
        training.setInternshipOrgMode("school_arranged");
        training.setInternshipLocation("广州市第一中学");
        training.setInterviewOrgMode("school_arranged");
        when(certificateMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(certificate));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(student));
        when(trainingProfileMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(training));
        when(paramService.getString("cert.school.code", "10588")).thenReturn("10588");
        org.mockito.Mockito.lenient()
                .when(excelHelper.writeStandardWorkbook(anyList(), isNull())).thenReturn(new byte[]{4});
    }

    private ExchangeStandardRow capturedStandardRow() {
        ArgumentCaptor<List<ExchangeStandardRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(excelHelper).writeStandardWorkbook(rowsCaptor.capture(), isNull());
        return rowsCaptor.getValue().get(0);
    }

    private ExchangeExcelHelper.ErrorRow capturedErrorRow() {
        ArgumentCaptor<List<ExchangeExcelHelper.ErrorRow>> rowsCaptor = ArgumentCaptor.forClass(List.class);
        verify(excelHelper).writeErrorWorkbook(rowsCaptor.capture());
        return rowsCaptor.getValue().get(0);
    }

    private void useUser(long userId, boolean sensitive) {
        UserContext.CurrentUser user = new UserContext.CurrentUser();
        user.setUserId(userId);
        user.setPermissions(sensitive ? Set.of("exchange:export:sensitive") : Set.of());
        UserContext.set(user);
    }

    private DataScopeContext.Scope collegeScope(long collegeId) {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.COLLEGE);
        scope.setCollegeIds(Set.of(collegeId));
        return scope;
    }

    private DataScopeContext.Scope schoolScope() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.SCHOOL);
        return scope;
    }

    private ImportExportBatch batch(long id, long operatorId, long collegeId) {
        ImportExportBatch batch = new ImportExportBatch();
        batch.setId(id);
        batch.setBatchNo(id == BATCH_A ? "IMP-A" : "IMP-B");
        batch.setType("import");
        batch.setOperatorId(operatorId);
        batch.setScopeJson("{\"collegeId\":" + collegeId + "}");
        return batch;
    }

    private ImportErrorDetail error(long batchId, String value) {
        ImportErrorDetail error = new ImportErrorDetail();
        error.setBatchId(batchId);
        error.setBatchNo(batchId == BATCH_A ? "IMP-A" : "IMP-B");
        error.setRowNo(2);
        error.setStudentNo("S" + batchId);
        error.setStudentName("测试");
        error.setFieldName("身份证件号码");
        error.setErrorValue(value);
        error.setErrorReason("格式错误");
        error.setSuggestion("请更正");
        return error;
    }

    private static void initTableInfo(Class<?> mapperType, Class<?> entityType, String resource) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), resource);
        assistant.setCurrentNamespace(mapperType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
