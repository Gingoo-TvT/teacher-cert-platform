package cn.edu.gpnu.platform.business.training.support;

import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.training.dto.TrainingProfileSaveRequest;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.SysMajor;
import cn.edu.gpnu.platform.system.mapper.SysMajorMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class MajorCodeValidator {

    private static final String EDUCATION_GRADUATE = "education_master";
    private static final Set<String> EDUCATION_GRADUATE_PREFIXES = Set.of("0401", "0451", "0453");
    private static final int ENABLED = 1;
    private static final int PILOT = 1;
    private static final String DEFAULT_YEAR_VERSION = "GLOBAL";

    private final SysMajorMapper majorMapper;

    public SysMajor validate(Student student, TrainingProfileSaveRequest request) {
        if (student == null) {
            throw new BizException("学生不存在");
        }
        if (EDUCATION_GRADUATE.equals(student.getIdentityType())) {
            String code = requiredTrim(request.getSecondDisciplineCode(), "二级学科代码不能为空");
            if (code.length() < 4 || !EDUCATION_GRADUATE_PREFIXES.contains(code.substring(0, 4))) {
                throw new BizException("专业代码不符合规则");
            }
            return null;
        }
        String majorCode = requiredTrim(request.getInternalMajorCode(), "校内专业代码不能为空");
        String majorName = requiredTrim(request.getInternalMajorName(), "校内专业名称不能为空");
        SysMajor major = majorMapper.selectOne(new LambdaQueryWrapper<SysMajor>()
                .eq(SysMajor::getInternalMajorCode, majorCode)
                .eq(SysMajor::getInternalMajorName, majorName)
                .eq(SysMajor::getCollegeId, student.getCollegeId())
                .eq(SysMajor::getYearVersion, DEFAULT_YEAR_VERSION)
                .eq(SysMajor::getPilotScopeFlag, PILOT)
                .eq(SysMajor::getStatus, ENABLED)
                .last("LIMIT 1"));
        if (major == null) {
            throw new BizException("校内专业不在试点范围或与学院不匹配");
        }
        if (StringUtils.hasText(major.getSecondDisciplineCode())
                && !major.getSecondDisciplineCode().equals(trimToNull(request.getSecondDisciplineCode()))) {
            throw new BizException("二级学科代码与校内专业不匹配");
        }
        if (StringUtils.hasText(major.getSecondDisciplineName())
                && !major.getSecondDisciplineName().equals(trimToNull(request.getSecondDisciplineName()))) {
            throw new BizException("二级学科名称与校内专业不匹配");
        }
        return major;
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
}
