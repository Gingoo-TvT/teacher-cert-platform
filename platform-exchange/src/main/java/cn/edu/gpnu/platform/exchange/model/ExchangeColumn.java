package cn.edu.gpnu.platform.exchange.model;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

public enum ExchangeColumn {
    SEQUENCE_NO("A", "序号", true, ExchangeStandardRow::getSequenceNo, ExchangeStandardRow::setSequenceNo),
    SCHOOL_CODE("B", "学校代码", true, ExchangeStandardRow::getSchoolCode, ExchangeStandardRow::setSchoolCode),
    SCHOOL_NAME("C", "学校名称", true, ExchangeStandardRow::getSchoolName, ExchangeStandardRow::setSchoolName),
    STUDENT_NO("D", "学号", true, ExchangeStandardRow::getStudentNo, ExchangeStandardRow::setStudentNo),
    NAME("E", "姓名", true, ExchangeStandardRow::getName, ExchangeStandardRow::setName),
    GENDER("F", "性别", true, ExchangeStandardRow::getGender, ExchangeStandardRow::setGender),
    ID_CARD_TYPE("G", "身份证件类型", true, ExchangeStandardRow::getIdCardType, ExchangeStandardRow::setIdCardType),
    ID_CARD_NO("H", "身份证件号码", true, ExchangeStandardRow::getIdCardNo, ExchangeStandardRow::setIdCardNo),
    BIRTH_DATE("I", "出生日期", true, ExchangeStandardRow::getBirthDate, ExchangeStandardRow::setBirthDate),
    IDENTITY_TYPE("J", "身份类型", true, ExchangeStandardRow::getIdentityType, ExchangeStandardRow::setIdentityType),
    SOURCE_PLACE("K", "生源地", true, ExchangeStandardRow::getSourcePlace, ExchangeStandardRow::setSourcePlace),
    SECOND_DISCIPLINE_CODE("L", "二级学科（专业）代码", true, ExchangeStandardRow::getSecondDisciplineCode, ExchangeStandardRow::setSecondDisciplineCode),
    SECOND_DISCIPLINE_NAME("M", "二级学科（专业）名称", true, ExchangeStandardRow::getSecondDisciplineName, ExchangeStandardRow::setSecondDisciplineName),
    INTERNAL_MAJOR_CODE("N", "校内专业代码", false, ExchangeStandardRow::getInternalMajorCode, ExchangeStandardRow::setInternalMajorCode),
    INTERNAL_MAJOR_NAME("O", "校内专业名称", false, ExchangeStandardRow::getInternalMajorName, ExchangeStandardRow::setInternalMajorName),
    EDUCATION_LEVEL("P", "学历层次", true, ExchangeStandardRow::getEducationLevel, ExchangeStandardRow::setEducationLevel),
    TRAINING_GOAL("Q", "专业培养目标", true, ExchangeStandardRow::getTrainingGoal, ExchangeStandardRow::setTrainingGoal),
    INTERNSHIP_ORG_MODE("R", "教育实习实践组织方式", true, ExchangeStandardRow::getInternshipOrgMode, ExchangeStandardRow::setInternshipOrgMode),
    INTERNSHIP_LOCATION("S", "教育实习实践地点", true, ExchangeStandardRow::getInternshipLocation, ExchangeStandardRow::setInternshipLocation),
    TEACHING_SEGMENT("T", "任教学段", true, ExchangeStandardRow::getTeachingSegment, ExchangeStandardRow::setTeachingSegment),
    TEACHING_SUBJECT("U", "任教学科", true, ExchangeStandardRow::getTeachingSubject, ExchangeStandardRow::setTeachingSubject),
    INTERVIEW_ORG_MODE("V", "面试考试组织方式", true, ExchangeStandardRow::getInterviewOrgMode, ExchangeStandardRow::setInterviewOrgMode),
    CERT_NO("W", "证书编号", true, ExchangeStandardRow::getCertNo, ExchangeStandardRow::setCertNo),
    VALID_UNTIL("X", "有效期限", true, ExchangeStandardRow::getValidUntil, ExchangeStandardRow::setValidUntil),
    ISSUER("Y", "证书签发人", true, ExchangeStandardRow::getIssuer, ExchangeStandardRow::setIssuer),
    REMARK("Z", "备注", false, ExchangeStandardRow::getRemark, ExchangeStandardRow::setRemark);

    public static final List<ExchangeColumn> ALL = Arrays.asList(values());

    private final String letter;
    private final String header;
    private final boolean required;
    private final Function<ExchangeStandardRow, String> getter;
    private final BiConsumer<ExchangeStandardRow, String> setter;

    ExchangeColumn(String letter, String header, boolean required,
                   Function<ExchangeStandardRow, String> getter,
                   BiConsumer<ExchangeStandardRow, String> setter) {
        this.letter = letter;
        this.header = header;
        this.required = required;
        this.getter = getter;
        this.setter = setter;
    }

    public String letter() {
        return letter;
    }

    public String header() {
        return header;
    }

    public boolean required() {
        return required;
    }

    public String value(ExchangeStandardRow row) {
        return getter.apply(row);
    }

    public void set(ExchangeStandardRow row, String value) {
        setter.accept(row, value);
    }
}
