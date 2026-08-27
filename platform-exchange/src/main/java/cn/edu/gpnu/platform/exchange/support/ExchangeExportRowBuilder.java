package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.support.SensitiveMasker;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;

/**
 * 导出行的无状态字段映射。
 *
 * <p>查询、权限、字典和敏感字段解密由 service 完成；这里只保持标准 A-Z 字段顺序及投影语义。
 */
public final class ExchangeExportRowBuilder {

    private ExchangeExportRowBuilder() {
    }

    public static ExchangeStandardRow standardRow(
            Certificate certificate,
            Student student,
            TrainingProfile training,
            int sequence,
            boolean sensitive,
            String schoolCode,
            String schoolName,
            String plainIdCardNo) {
        ExchangeStandardRow row = new ExchangeStandardRow();
        row.setSequenceNo(String.valueOf(sequence));
        row.setSchoolCode(schoolCode);
        row.setSchoolName(schoolName);
        row.setStudentNo(firstText(student == null ? null : student.getStudentNo(),
                value(certificate, Certificate::getStudentNo)));
        row.setName(firstText(student == null ? null : student.getName(),
                value(certificate, Certificate::getStudentName)));
        row.setGender(student == null ? "" : student.getGender());
        row.setIdCardType(firstText(student == null ? null : student.getIdCardType(),
                value(certificate, Certificate::getIdCardType)));
        row.setIdCardNo(sensitive ? plainIdCardNo : SensitiveMasker.idCard(plainIdCardNo));
        row.setBirthDate(student == null ? ""
                : sensitive ? student.getBirthDate() : SensitiveMasker.birthDate(student.getBirthDate()));
        row.setIdentityType(student == null ? "" : student.getIdentityType());
        row.setSourcePlace(student == null ? "" : student.getSourceFull());
        row.setSecondDisciplineCode(training == null ? "" : training.getSecondDisciplineCode());
        row.setSecondDisciplineName(training == null ? "" : training.getSecondDisciplineName());
        row.setInternalMajorCode(training == null ? "" : training.getInternalMajorCode());
        row.setInternalMajorName(training == null ? "" : training.getInternalMajorName());
        row.setEducationLevel(certificate == null
                ? value(training, TrainingProfile::getEducationLevel) : certificate.getEducationLevel());
        row.setTrainingGoal(certificate == null
                ? value(training, TrainingProfile::getTrainingGoal) : certificate.getTrainingGoal());
        row.setInternshipOrgMode(training == null ? "" : training.getInternshipOrgMode());
        row.setInternshipLocation(training == null ? "" : training.getInternshipLocation());
        row.setTeachingSegment(certificate == null
                ? value(training, TrainingProfile::getTeachingSegment) : certificate.getTeachingSegment());
        row.setTeachingSubject(certificate == null
                ? value(training, TrainingProfile::getTeachingSubjectCode) : certificate.getTeachingSubjectCode());
        row.setInterviewOrgMode(training == null ? "" : training.getInterviewOrgMode());
        row.setCertNo(value(certificate, Certificate::getCertNo));
        row.setValidUntil(value(certificate, Certificate::getValidUntil));
        row.setIssuer(value(certificate, Certificate::getIssuer));
        Long collegeId = certificate == null
                ? student == null ? null : student.getCollegeId()
                : certificate.getCollegeId();
        row.setRemark(collegeId == null ? "" : String.valueOf(collegeId));
        return row;
    }

    private static <T> String value(T source, java.util.function.Function<T, String> getter) {
        return source == null ? "" : getter.apply(source);
    }

    private static String firstText(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
