package cn.edu.gpnu.platform.exchange.service.impl;

import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeCertificateAggregateGuardTest {

    @Test
    void sameStudentAndAssessmentYearMayUpdateExistingCertificate() {
        Certificate certificate = certificate(42L, "2026");
        Student student = student(42L);

        assertThatCode(() -> ExchangeServiceImpl.assertCertificateAggregate(
                certificate, student, "2026")).doesNotThrowAnyException();
    }

    @Test
    void certificateNumberCannotBeReboundToAnotherStudent() {
        Certificate certificate = certificate(41L, "2026");
        Student student = student(42L);

        assertThatThrownBy(() -> ExchangeServiceImpl.assertCertificateAggregate(
                certificate, student, "2026"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能通过导入换绑");
    }

    @Test
    void certificateNumberCannotBeReboundToAnotherAssessmentYear() {
        Certificate certificate = certificate(42L, "2025");
        Student student = student(42L);

        assertThatThrownBy(() -> ExchangeServiceImpl.assertCertificateAggregate(
                certificate, student, "2026"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能通过导入换绑");
    }

    private Certificate certificate(Long studentId, String assessmentYear) {
        Certificate certificate = new Certificate();
        certificate.setStudentId(studentId);
        certificate.setAssessmentYear(assessmentYear);
        return certificate;
    }

    private Student student(Long id) {
        Student student = new Student();
        student.setId(id);
        return student;
    }
}
