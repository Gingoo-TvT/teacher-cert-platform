package cn.edu.gpnu.platform.business.student.service.impl;

import cn.edu.gpnu.platform.boot.controller.StudentController;
import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.certificate.service.impl.CertificateServiceImpl;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.student.support.SensitiveMasker;
import cn.edu.gpnu.platform.business.student.vo.StudentPlainIdCardVO;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensitiveProjectionTest {

    private static final long STUDENT_ID = 5101L;
    private static final long CERTIFICATE_ID = 5102L;
    private static final long COLLEGE_ID = 5103L;
    private static final String ID_CARD_NO = "11010119900628002X";
    private static final String ID_CARD_CIPHER = "v1:test-id-card-cipher";
    private static final String ID_CARD_HMAC = "test-id-card-hmac";
    private static final String BIRTH_DATE = "1990/6/28";

    @Mock
    private StudentMapper studentMapper;
    @Mock
    private CertificateMapper certificateMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private IdCardProtectionService idCardProtectionService;

    @InjectMocks
    private StudentServiceImpl studentService;
    @InjectMocks
    private CertificateServiceImpl certificateService;

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
    void ordinaryStudentProjectionMasksIdentityAndBirthDate() {
        UserContext.CurrentUser user = new UserContext.CurrentUser();
        user.setUserId(5201L);
        user.setPermissions(Set.of("exchange:export:sensitive"));
        UserContext.set(user);
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());
        when(dataScopeService.resolve("student:view")).thenReturn(collegeScope());

        var ordinary = studentService.detail(STUDENT_ID);
        assertThat(ordinary.getIdCardNo()).isEqualTo(SensitiveMasker.idCard(ID_CARD_NO));
        assertThat(ordinary.getBirthDate()).isEqualTo("****/*/**");

        StudentPlainIdCardVO sensitive = studentService.plainIdCard(STUDENT_ID);
        assertThat(sensitive.getIdCardNo()).isEqualTo(ID_CARD_NO);
        assertThat(sensitive.getBirthDate()).isEqualTo(BIRTH_DATE);
    }

    @Test
    void plainIdentityEndpointRemainsGuardedBySensitivePermission() throws NoSuchMethodException {
        PreAuthorize guard = StudentController.class
                .getMethod("plainIdCard", Long.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(guard).isNotNull();
        assertThat(guard.value()).contains("exchange:export:sensitive");
    }

    @Test
    void serviceRejectsPlainIdentityWithoutSensitivePermission() {
        assertThatThrownBy(() -> studentService.plainIdCard(STUDENT_ID))
                .hasMessage("无权查看明文证件信息");
    }

    @Test
    void ordinaryCertificateProjectionNeverReturnsPlainIdentity() {
        UserContext.CurrentUser user = new UserContext.CurrentUser();
        user.setUserId(5201L);
        user.setPermissions(Set.of("exchange:export:sensitive"));
        UserContext.set(user);
        when(certificateMapper.selectById(CERTIFICATE_ID)).thenReturn(certificate());
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student());
        when(dataScopeService.resolve("cert:view")).thenReturn(collegeScope());

        var ordinary = certificateService.detail(CERTIFICATE_ID);

        assertThat(ordinary.getIdCardNo()).isEqualTo(SensitiveMasker.idCard(ID_CARD_NO));
    }

    private Student student() {
        Student student = new Student();
        student.setId(STUDENT_ID);
        student.setCollegeId(COLLEGE_ID);
        student.setIdCardNo(ID_CARD_CIPHER);
        student.setIdCardHmac(ID_CARD_HMAC);
        student.setBirthDate(BIRTH_DATE);
        student.setStatus("DRAFT");
        return student;
    }

    private Certificate certificate() {
        Certificate certificate = new Certificate();
        certificate.setId(CERTIFICATE_ID);
        certificate.setStudentId(STUDENT_ID);
        certificate.setCollegeId(COLLEGE_ID);
        certificate.setIdCardNo(ID_CARD_CIPHER);
        certificate.setIdCardHmac(ID_CARD_HMAC);
        certificate.setStatus("GENERATED");
        return certificate;
    }

    private DataScopeContext.Scope collegeScope() {
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.COLLEGE);
        scope.setCollegeIds(Set.of(COLLEGE_ID));
        return scope;
    }
}
