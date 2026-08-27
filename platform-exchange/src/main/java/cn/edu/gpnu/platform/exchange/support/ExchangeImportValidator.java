package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.support.BirthDateValidator;
import cn.edu.gpnu.platform.business.student.support.IdCardValidator;
import cn.edu.gpnu.platform.business.student.support.NameValidator;
import cn.edu.gpnu.platform.business.training.dto.TrainingProfileSaveRequest;
import cn.edu.gpnu.platform.business.training.support.MajorCodeValidator;
import cn.edu.gpnu.platform.business.training.support.TrainingLinkValidator;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.exchange.model.ExchangeColumn;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import cn.edu.gpnu.platform.system.entity.SysCollege;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.SysMajor;
import cn.edu.gpnu.platform.system.mapper.SysCollegeMapper;
import cn.edu.gpnu.platform.system.mapper.SysMajorMapper;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 教育部交换导入的 V-01 至 V-13 业务校验。
 */
@Component
@RequiredArgsConstructor
public class ExchangeImportValidator {

    private static final String DEFAULT_YEAR_VERSION = "GLOBAL";
    private static final String SCHOOL_NAME = "广东技术师范大学";
    private static final String DEFAULT_SCHOOL_CODE = "10588";
    private static final String DEFAULT_PROVINCE_CODE = "44";
    private static final Set<String> EDUCATION_GRADUATE_PREFIXES = Set.of("0401", "0451", "0453");

    private final ParamService paramService;
    private final ExchangeDictionaryHelper dictionaryHelper;
    private final NameValidator nameValidator;
    private final IdCardValidator idCardValidator;
    private final BirthDateValidator birthDateValidator;
    private final MajorCodeValidator majorCodeValidator;
    private final TrainingLinkValidator trainingLinkValidator;
    private final SysCollegeMapper collegeMapper;
    private final SysMajorMapper majorMapper;
    private final DataScopeService dataScopeService;
    private final ObjectMapper objectMapper;

    public List<ValidationError> validate(ExchangeStandardRow row,
                                          Map<String, Long> idCardCounts,
                                          Map<String, Long> certNoCounts) {
        List<ValidationError> errors = new ArrayList<>();
        validateCompleteness(row, errors);
        validateTextFormat(row, errors);
        validateSchool(row, errors);
        validateName(row, errors);
        validateId(row, errors);
        validateBirth(row, errors);
        validateIdentityType(row, errors);
        validateMajor(row, errors);
        validateTrainingLink(row, errors);
        validateCertificateNo(row, errors);
        validateValidity(row, errors);
        validateDuplicate(row, errors, idCardCounts, certNoCounts);
        return errors;
    }

    /**
     * 文件内重复检查与单行证件校验共用同一规范化结果。
     */
    public String canonicalIdCardNo(ExchangeStandardRow row) {
        try {
            return idCardValidator.validate(row.getIdCardType(), row.getIdCardNo());
        } catch (BizException e) {
            return null;
        }
    }

    public Long resolveCollegeId(ExchangeStandardRow row) {
        Long direct = parseLong(trim(row.getRemark()));
        if (direct != null && collegeMapper.selectById(direct) != null) {
            return direct;
        }
        SysMajor major = majorMapper.selectOne(new LambdaQueryWrapper<SysMajor>()
                .eq(SysMajor::getInternalMajorCode, trim(row.getInternalMajorCode()))
                .eq(SysMajor::getInternalMajorName, trim(row.getInternalMajorName()))
                .eq(SysMajor::getYearVersion, DEFAULT_YEAR_VERSION)
                .eq(SysMajor::getStatus, 1)
                .last("LIMIT 1"));
        if (major != null) {
            return major.getCollegeId();
        }
        List<Long> allowed = allowedCollegeIds("exchange:import");
        if (allowed.size() == 1) {
            return allowed.get(0);
        }
        throw new BizException("无法识别导入行所属学院，请填写校内专业或在备注填学院ID");
    }

    public void ensureCanImportCollege(Long collegeId) {
        DataScopeContext.Scope scope = dataScopeService.resolve("exchange:import");
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权导入该学院数据");
        }
        if (scope.allSchool()) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE && scope.getCollegeIds().contains(collegeId)) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权导入该学院数据");
    }

    public TrainingProfileSaveRequest trainingRequest(ExchangeStandardRow row, Long studentId) {
        TrainingProfileSaveRequest request = new TrainingProfileSaveRequest();
        request.setStudentId(studentId == null ? 0L : studentId);
        request.setAssessmentYear(assessmentYear(row));
        request.setSecondDisciplineCode(trim(row.getSecondDisciplineCode()));
        request.setSecondDisciplineName(trim(row.getSecondDisciplineName()));
        request.setInternalMajorCode(trim(row.getInternalMajorCode()));
        request.setInternalMajorName(trim(row.getInternalMajorName()));
        request.setEducationLevel(trim(row.getEducationLevel()));
        request.setTrainingGoal(trim(row.getTrainingGoal()));
        request.setInternshipOrgMode(trim(row.getInternshipOrgMode()));
        request.setInternshipLocation(trim(row.getInternshipLocation()));
        request.setTeachingSegment(trim(row.getTeachingSegment()));
        request.setTeachingSubjectCode(trim(row.getTeachingSubject()));
        request.setInterviewOrgMode(trim(row.getInterviewOrgMode()));
        request.setAbilityTestConclusion("qualified");
        return request;
    }

    public SysMajor majorByCodeName(Long collegeId, String code, String name) {
        if (collegeId == null || !StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            return null;
        }
        return majorMapper.selectOne(new LambdaQueryWrapper<SysMajor>()
                .eq(SysMajor::getCollegeId, collegeId)
                .eq(SysMajor::getInternalMajorCode, code.trim())
                .eq(SysMajor::getInternalMajorName, name.trim())
                .eq(SysMajor::getYearVersion, DEFAULT_YEAR_VERSION)
                .eq(SysMajor::getStatus, 1)
                .last("LIMIT 1"));
    }

    public String assessmentYear(ExchangeStandardRow row) {
        String certNo = required(row.getCertNo(), "证书编号不能为空");
        if (certNo.length() >= 4) {
            return certNo.substring(0, 4);
        }
        return required(row.getSequenceNo(), "考核年度不能为空");
    }

    private void validateCompleteness(ExchangeStandardRow row, List<ValidationError> errors) {
        for (ExchangeColumn column : ExchangeColumn.ALL) {
            if (column.required() && !StringUtils.hasText(column.value(row))) {
                errors.add(new ValidationError(column.header(), column.value(row),
                        "V-01字段不能为空", "请补齐" + column.header()));
            }
        }
        if (!"education_master".equals(trim(row.getIdentityType()))) {
            if (!StringUtils.hasText(row.getInternalMajorCode())) {
                errors.add(new ValidationError("校内专业代码", row.getInternalMajorCode(),
                        "V-01普通师范生校内专业代码不能为空", "请填写试点专业代码"));
            }
            if (!StringUtils.hasText(row.getInternalMajorName())) {
                errors.add(new ValidationError("校内专业名称", row.getInternalMajorName(),
                        "V-01普通师范生校内专业名称不能为空", "请填写试点专业名称"));
            }
        }
    }

    private void validateTextFormat(ExchangeStandardRow row, List<ValidationError> errors) {
        Map<String, String> values = Map.of(
                "学校代码", nvl(row.getSchoolCode()),
                "学号", nvl(row.getStudentNo()),
                "身份证件号码", nvl(row.getIdCardNo()),
                "出生日期", nvl(row.getBirthDate()),
                "证书编号", nvl(row.getCertNo()),
                "有效期限", nvl(row.getValidUntil()));
        values.forEach((field, value) -> {
            if (!StringUtils.hasText(value)) {
                return;
            }
            if (value.matches("(?i).*[0-9]+E\\+?[0-9]+.*") || value.matches("^\\d+\\.0+$")) {
                errors.add(new ValidationError(field, value,
                        "V-02文本字段疑似被Excel转换", "请将单元格设为文本后重填"));
            }
        });
        if (StringUtils.hasText(row.getStudentNo()) && row.getStudentNo().length() > 1
                && row.getStudentNo().startsWith("0") && !row.getStudentNo().matches("^0+\\S+")) {
            errors.add(new ValidationError("学号", row.getStudentNo(),
                    "V-02学号文本格式异常", "请以文本格式填写学号"));
        }
    }

    private void validateSchool(ExchangeStandardRow row, List<ValidationError> errors) {
        String code = paramService.getString("cert.school.code", DEFAULT_SCHOOL_CODE);
        SysDictItem school = dictionaryHelper.item("school", code);
        String name = school == null ? SCHOOL_NAME : school.getItemValue();
        if (!code.equals(trim(row.getSchoolCode())) || !name.equals(trim(row.getSchoolName()))) {
            errors.add(new ValidationError("学校代码/学校名称",
                    row.getSchoolCode() + "/" + row.getSchoolName(),
                    "V-03学校代码与名称不匹配", "应为" + code + "/" + name));
        }
    }

    private void validateName(ExchangeStandardRow row, List<ValidationError> errors) {
        try {
            nameValidator.validate(row.getName());
        } catch (BizException e) {
            errors.add(new ValidationError("姓名", row.getName(),
                    "V-04" + e.getMessage(), "请按姓名规则填写"));
        }
    }

    private void validateId(ExchangeStandardRow row, List<ValidationError> errors) {
        if (dictionaryHelper.item("id_card_type", trim(row.getIdCardType())) == null) {
            errors.add(new ValidationError("身份证件类型", row.getIdCardType(),
                    "V-05证件类型不在字典范围", "请使用模板下拉值"));
            return;
        }
        try {
            row.setIdCardNo(idCardValidator.validate(row.getIdCardType(), row.getIdCardNo()));
        } catch (BizException e) {
            errors.add(new ValidationError("身份证件号码", row.getIdCardNo(),
                    "V-05" + e.getMessage(), "请核对证件类型与号码"));
        }
    }

    private void validateBirth(ExchangeStandardRow row, List<ValidationError> errors) {
        try {
            birthDateValidator.validate(row.getIdCardType(), row.getIdCardNo(), row.getBirthDate());
        } catch (BizException e) {
            errors.add(new ValidationError("出生日期", row.getBirthDate(),
                    "V-06" + e.getMessage(), "请核对出生日期与证件号"));
        }
    }

    private void validateIdentityType(ExchangeStandardRow row, List<ValidationError> errors) {
        if (dictionaryHelper.item("identity_type", trim(row.getIdentityType())) == null) {
            errors.add(new ValidationError("身份类型", row.getIdentityType(),
                    "V-07身份类型不在字典范围", "请使用模板下拉值"));
        }
    }

    private void validateMajor(ExchangeStandardRow row, List<ValidationError> errors) {
        if ("education_master".equals(trim(row.getIdentityType()))) {
            String code = trim(row.getSecondDisciplineCode());
            if (code == null || code.length() < 4 || !EDUCATION_GRADUATE_PREFIXES.contains(code.substring(0, 4))) {
                errors.add(new ValidationError("二级学科（专业）代码", row.getSecondDisciplineCode(),
                        "V-08教育类研究生专业代码须为0401/0451/0453系列", "请填写教育类研究生标准专业代码"));
            }
            return;
        }
        try {
            Student student = minimalStudent(row);
            student.setCollegeId(resolveCollegeId(row));
            majorCodeValidator.validate(student, trainingRequest(row, student.getId()));
        } catch (BizException e) {
            errors.add(new ValidationError("二级学科（专业）代码", row.getSecondDisciplineCode(),
                    "V-08" + e.getMessage(), "请核对专业代码与试点专业范围"));
        }
    }

    private void validateTrainingLink(ExchangeStandardRow row, List<ValidationError> errors) {
        try {
            Student student = minimalStudent(row);
            student.setCollegeId(resolveCollegeId(row));
            SysMajor major = null;
            if (!"education_master".equals(trim(row.getIdentityType()))) {
                major = majorByCodeName(resolveCollegeId(row),
                        row.getInternalMajorCode(), row.getInternalMajorName());
            }
            trainingLinkValidator.validate(trainingRequest(row, student.getId()), major);
        } catch (BizException e) {
            String field = e.getMessage().contains("任教学科") ? "任教学科" : "教育实习实践地点";
            String code = field.equals("任教学科") ? "V-10" : "V-09";
            String value = field.equals("任教学科") ? row.getTeachingSubject() : row.getInternshipLocation();
            errors.add(new ValidationError(field, value, code + e.getMessage(), "请使用模板联动下拉项"));
        }
    }

    private void validateCertificateNo(ExchangeStandardRow row, List<ValidationError> errors) {
        String certNo = trim(row.getCertNo());
        if (certNo == null || !certNo.matches("^\\d{18}$")) {
            errors.add(new ValidationError("证书编号", row.getCertNo(),
                    "V-11证书编号必须为18位数字", "请使用系统生成的证书编号"));
            return;
        }
        try {
            String year = certNo.substring(0, 4);
            String school = fixedDigits(paramService.getString("cert.school.code", DEFAULT_SCHOOL_CODE), 5);
            String province = fixedDigits(paramService.getString("cert.province.code", DEFAULT_PROVINCE_CODE), 2);
            String levelCode = certCode("education_level", row.getEducationLevel(), "certLevelCode");
            String segmentCode = certCode("teaching_segment", row.getTeachingSegment(), "certSegmentCode");
            if (!certNo.substring(4, 9).equals(school)
                    || !certNo.substring(9, 10).equals(levelCode)
                    || !certNo.substring(10, 12).equals(province)
                    || !certNo.substring(12, 13).equals(segmentCode)
                    || !certNo.substring(13).matches("^\\d{5}$")
                    || !year.matches("^\\d{4}$")) {
                errors.add(new ValidationError("证书编号", row.getCertNo(),
                        "V-11证书编号段码不合法", "请核对年份/学校/层次/省码/学段/序号"));
            }
        } catch (BizException e) {
            errors.add(new ValidationError("证书编号", row.getCertNo(),
                    "V-11" + e.getMessage(), "请补齐字典段码配置"));
        }
    }

    private void validateValidity(ExchangeStandardRow row, List<ValidationError> errors) {
        String certNo = trim(row.getCertNo());
        if (certNo == null || certNo.length() < 4) {
            return;
        }
        try {
            String normalized = normalizeDate(row.getValidUntil());
            int certYear = Integer.parseInt(certNo.substring(0, 4));
            Set<String> allowed = Set.of((certYear + 3) + "/6/30", (certYear + 3) + "/12/31");
            if (!allowed.contains(normalized)) {
                errors.add(new ValidationError("有效期限", row.getValidUntil(),
                        "V-12有效期限不符合证书年份+3年的上/下半年规则", "应为" + String.join(" 或 ", allowed)));
            } else {
                row.setValidUntil(normalized);
            }
        } catch (Exception e) {
            errors.add(new ValidationError("有效期限", row.getValidUntil(),
                    "V-12有效期限格式异常", "请填写YYYY/M/D文本"));
        }
    }

    private void validateDuplicate(ExchangeStandardRow row, List<ValidationError> errors,
                                   Map<String, Long> idCardCounts, Map<String, Long> certNoCounts) {
        String canonicalIdCardNo = canonicalIdCardNo(row);
        if (StringUtils.hasText(canonicalIdCardNo) && idCardCounts.getOrDefault(canonicalIdCardNo, 0L) > 1) {
            errors.add(new ValidationError("身份证件号码", row.getIdCardNo(),
                    "V-13证件号码已存在", "请选择覆盖/跳过策略或核对数据"));
        }
        if (StringUtils.hasText(row.getCertNo()) && certNoCounts.getOrDefault(row.getCertNo().trim(), 0L) > 1) {
            errors.add(new ValidationError("证书编号", row.getCertNo(),
                    "V-13证书编号已存在", "请选择覆盖/跳过策略或核对数据"));
        }
    }

    private List<Long> allowedCollegeIds(String permission) {
        DataScopeContext.Scope scope = dataScopeService.resolve(permission);
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权导入该学院数据");
        }
        if (scope.allSchool()) {
            return collegeMapper.selectList(new LambdaQueryWrapper<SysCollege>().eq(SysCollege::getStatus, 1))
                    .stream().map(SysCollege::getId).toList();
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE) {
            return new ArrayList<>(scope.getCollegeIds());
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权导入该学院数据");
    }

    private Student minimalStudent(ExchangeStandardRow row) {
        Student student = new Student();
        student.setStudentNo(trim(row.getStudentNo()));
        student.setName(trim(row.getName()));
        student.setGender(trim(row.getGender()));
        student.setIdCardType(trim(row.getIdCardType()));
        student.setIdCardNo(trim(row.getIdCardNo()));
        student.setBirthDate(trim(row.getBirthDate()));
        student.setIdentityType(trim(row.getIdentityType()));
        return student;
    }

    private String certCode(String typeCode, String itemCode, String fieldName) {
        SysDictItem item = dictionaryHelper.item(typeCode, itemCode);
        if (item == null || !StringUtils.hasText(item.getExtJson())) {
            throw new BizException(typeCode + "证书段码未配置: " + itemCode);
        }
        try {
            JsonNode node = objectMapper.readTree(item.getExtJson());
            String value = node.path(fieldName).asText(null);
            if (!StringUtils.hasText(value)) {
                throw new BizException(typeCode + "证书段码未配置: " + itemCode);
            }
            return value;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(typeCode + "证书段码解析失败: " + itemCode);
        }
    }

    private String fixedDigits(String value, int length) {
        String text = required(value, "参数不能为空");
        if (!text.matches("^\\d{" + length + "}$")) {
            throw new BizException("参数必须为" + length + "位数字");
        }
        return text;
    }

    private String normalizeDate(String value) {
        String text = required(value, "日期不能为空");
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy/M/d"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ISO_LOCAL_DATE)) {
            try {
                LocalDate date = LocalDate.parse(text, formatter);
                return date.getYear() + "/" + date.getMonthValue() + "/" + date.getDayOfMonth();
            } catch (Exception ignored) {
            }
        }
        throw new BizException("日期格式不正确");
    }

    private String required(String value, String message) {
        String text = trim(value);
        if (text == null) {
            throw new BizException(message);
        }
        return text;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value) || !value.matches("^\\d+$")) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record ValidationError(String fieldName, String errorValue, String errorReason, String suggestion) {
    }
}
