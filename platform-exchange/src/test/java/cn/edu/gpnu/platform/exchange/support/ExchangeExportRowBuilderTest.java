package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.support.SensitiveMasker;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.exchange.model.ExchangeColumn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeExportRowBuilderTest {

    private static final String ID_CARD_NO = "11010119900628002X";

    @Test
    void standardRowKeepsExactAZFieldSnapshot() {
        Certificate certificate = certificate();
        Student student = student();
        TrainingProfile training = training();

        var row = ExchangeExportRowBuilder.standardRow(
                certificate, student, training, 7, true, "10588", "广东技术师范大学", ID_CARD_NO);

        assertThat(ExchangeColumn.ALL.stream().map(column -> column.value(row)).toList())
                .containsExactly(
                        "7", "10588", "广东技术师范大学", "20260001", "张三", "male",
                        "resident_id_card", ID_CARD_NO, "1990/6/28", "normal_student", "广东广州",
                        "0401", "教育学", "M01", "师范专业", "bachelor", "normal", "centralized",
                        "广州一中", "senior_middle_school", "math", "school", "202610588344400001",
                        "2029/6/30", "签发人", "42");
    }

    @Test
    void standardRowMasksSensitiveFieldsWithoutPermission() {
        var row = ExchangeExportRowBuilder.standardRow(
                certificate(), student(), training(), 1, false, "10588", "广东技术师范大学", ID_CARD_NO);

        assertThat(row.getIdCardNo()).isEqualTo(SensitiveMasker.idCard(ID_CARD_NO));
        assertThat(row.getBirthDate()).isEqualTo(SensitiveMasker.birthDate("1990/6/28"));
    }

    @Test
    void standardRowKeepsMissingAssociationsAsEmptyStrings() {
        var row = ExchangeExportRowBuilder.standardRow(
                certificate(), null, null, 1, true, "10588", "广东技术师范大学", ID_CARD_NO);

        assertThat(List.of(row.getGender(), row.getBirthDate(), row.getIdentityType(), row.getSourcePlace(),
                row.getSecondDisciplineCode(), row.getSecondDisciplineName(), row.getInternalMajorCode(),
                row.getInternalMajorName(), row.getInternshipOrgMode(), row.getInternshipLocation(),
                row.getInterviewOrgMode())).allMatch(String::isEmpty);
    }

    @Test
    void auditRowCanProjectStudentAndTrainingWithoutCertificate() {
        Student student = student();
        student.setStudentNo("20260002");
        student.setName("李四");
        student.setIdCardType("resident_id_card");
        student.setCollegeId(43L);
        TrainingProfile training = training();
        training.setEducationLevel("bachelor");
        training.setTrainingGoal("normal");
        training.setTeachingSegment("senior_middle_school");
        training.setTeachingSubjectCode("math");

        var row = ExchangeExportRowBuilder.standardRow(
                null, student, training, 1, true, "10588", "广东技术师范大学", ID_CARD_NO);

        assertThat(row.getStudentNo()).isEqualTo("20260002");
        assertThat(row.getName()).isEqualTo("李四");
        assertThat(row.getEducationLevel()).isEqualTo("bachelor");
        assertThat(row.getTeachingSegment()).isEqualTo("senior_middle_school");
        assertThat(row.getTeachingSubject()).isEqualTo("math");
        assertThat(row.getCertNo()).isEmpty();
        assertThat(row.getRemark()).isEqualTo("43");
    }

    private Certificate certificate() {
        Certificate certificate = new Certificate();
        certificate.setCollegeId(42L);
        certificate.setStudentNo("20260001");
        certificate.setStudentName("张三");
        certificate.setIdCardType("resident_id_card");
        certificate.setEducationLevel("bachelor");
        certificate.setTrainingGoal("normal");
        certificate.setTeachingSegment("senior_middle_school");
        certificate.setTeachingSubjectCode("math");
        certificate.setCertNo("202610588344400001");
        certificate.setValidUntil("2029/6/30");
        certificate.setIssuer("签发人");
        return certificate;
    }

    private Student student() {
        Student student = new Student();
        student.setGender("male");
        student.setBirthDate("1990/6/28");
        student.setIdentityType("normal_student");
        student.setSourceFull("广东广州");
        return student;
    }

    private TrainingProfile training() {
        TrainingProfile training = new TrainingProfile();
        training.setSecondDisciplineCode("0401");
        training.setSecondDisciplineName("教育学");
        training.setInternalMajorCode("M01");
        training.setInternalMajorName("师范专业");
        training.setInternshipOrgMode("centralized");
        training.setInternshipLocation("广州一中");
        training.setInterviewOrgMode("school");
        return training;
    }
}
