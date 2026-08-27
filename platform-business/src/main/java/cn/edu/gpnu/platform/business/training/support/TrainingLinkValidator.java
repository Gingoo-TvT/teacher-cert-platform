package cn.edu.gpnu.platform.business.training.support;

import cn.edu.gpnu.platform.business.training.dto.TrainingProfileSaveRequest;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.MajorTrainingGoal;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.SysMajor;
import cn.edu.gpnu.platform.system.entity.TeachingSubject;
import cn.edu.gpnu.platform.system.entity.TrainingGoalConfig;
import cn.edu.gpnu.platform.system.mapper.MajorTrainingGoalMapper;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.mapper.TeachingSubjectMapper;
import cn.edu.gpnu.platform.system.mapper.TrainingGoalConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TrainingLinkValidator {

    private static final int ENABLED = 1;
    private static final int CATEGORY = 1;
    private static final String DEFAULT_YEAR_VERSION = "GLOBAL";
    private static final String VOCATIONAL_LOCATION = "enterprise_vocational_education";
    private static final String OVERSEAS_LOCATION = "overseas_chinese_international_education";
    private static final String VOCATIONAL_GOAL = "secondary_vocational_school_teacher";
    private static final String CHINESE_INTERNATIONAL_MAJOR = "汉语国际教育专业";

    private final TrainingGoalConfigMapper trainingGoalConfigMapper;
    private final TeachingSubjectMapper teachingSubjectMapper;
    private final MajorTrainingGoalMapper majorTrainingGoalMapper;
    private final SysDictItemMapper dictItemMapper;
    private final ObjectMapper objectMapper;

    public TeachingSubject validate(TrainingProfileSaveRequest request, SysMajor matchedMajor) {
        String goal = requiredTrim(request.getTrainingGoal(), "培养目标不能为空");
        TrainingGoalConfig config = requireConfig(goal);
        if (matchedMajor != null) {
            ensureMajorGoal(matchedMajor.getId(), goal);
        }
        String segment = requiredTrim(request.getTeachingSegment(), "任教学段不能为空");
        String location = requiredTrim(request.getInternshipLocation(), "实习地点不能为空");
        if (!readJsonArray(config.getAllowedSegmentsJson()).contains(segment)) {
            throw new BizException("任教学段不在培养目标允许范围");
        }
        if (!readJsonArray(config.getAllowedInternshipLocationsJson()).contains(location)) {
            throw new BizException("实习地点不在培养目标允许范围");
        }
        validateLocationRestriction(location, goal, request, matchedMajor);
        validateDict("education_level", request.getEducationLevel(), "学历层次编码不存在或已停用");
        validateDict("internship_org_mode", request.getInternshipOrgMode(), "实习组织方式编码不存在或已停用");
        validateDict("interview_org_mode", request.getInterviewOrgMode(), "面试组织方式编码不存在或已停用");
        if (StringUtils.hasText(request.getAbilityTestConclusion())) {
            validateDict("ability_test_conclusion", request.getAbilityTestConclusion(), "测试结论编码不存在或已停用");
        }
        return requireSubject(segment, request.getTeachingSubjectCode());
    }

    public TrainingGoalConfig requireConfig(String trainingGoal) {
        TrainingGoalConfig config = trainingGoalConfigMapper.selectOne(new LambdaQueryWrapper<TrainingGoalConfig>()
                .eq(TrainingGoalConfig::getTrainingGoalCode, requiredTrim(trainingGoal, "培养目标不能为空"))
                .eq(TrainingGoalConfig::getStatus, ENABLED)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BizException("培养目标联动配置不存在或已停用");
        }
        return config;
    }

    public List<String> allowedSegments(String trainingGoal) {
        return readJsonArray(requireConfig(trainingGoal).getAllowedSegmentsJson());
    }

    public List<String> allowedLocations(String trainingGoal) {
        return readJsonArray(requireConfig(trainingGoal).getAllowedInternshipLocationsJson());
    }

    /**
     * 证书更正复用培养信息的培养目标、学段、学科联动规则。
     */
    public TeachingSubject validateCertificateLink(String trainingGoal, String segment, String subjectCode) {
        TrainingGoalConfig config = requireConfig(trainingGoal);
        String normalizedSegment = requiredTrim(segment, "任教学段不能为空");
        if (!readJsonArray(config.getAllowedSegmentsJson()).contains(normalizedSegment)) {
            throw new BizException("任教学段不在培养目标允许范围");
        }
        return requireSubject(normalizedSegment, subjectCode);
    }

    private void ensureMajorGoal(Long majorId, String goal) {
        Long count = majorTrainingGoalMapper.selectCount(new LambdaQueryWrapper<MajorTrainingGoal>()
                .eq(MajorTrainingGoal::getMajorId, majorId)
                .eq(MajorTrainingGoal::getTrainingGoalCode, goal)
                .eq(MajorTrainingGoal::getStatus, ENABLED));
        if (count == null || count == 0) {
            throw new BizException("培养目标不属于该专业可选范围");
        }
    }

    private void validateLocationRestriction(String location, String goal, TrainingProfileSaveRequest request, SysMajor matchedMajor) {
        if (VOCATIONAL_LOCATION.equals(location) && !VOCATIONAL_GOAL.equals(goal)) {
            throw new BizException("企业实习地点仅职业技术教育专业可选");
        }
        if (OVERSEAS_LOCATION.equals(location)) {
            String majorName = matchedMajor == null ? request.getInternalMajorName() : matchedMajor.getInternalMajorName();
            if (!CHINESE_INTERNATIONAL_MAJOR.equals(trimToNull(majorName))) {
                throw new BizException("海外实习地点仅汉语国际教育专业可选");
            }
        }
    }

    private TeachingSubject requireSubject(String segment, String subjectCode) {
        TeachingSubject subject = teachingSubjectMapper.selectOne(new LambdaQueryWrapper<TeachingSubject>()
                .eq(TeachingSubject::getSegmentCode, requiredTrim(segment, "任教学段不能为空"))
                .eq(TeachingSubject::getSubjectCode, requiredTrim(subjectCode, "任教学科编码不能为空"))
                .eq(TeachingSubject::getYearVersion, DEFAULT_YEAR_VERSION)
                .eq(TeachingSubject::getStatus, ENABLED)
                .last("LIMIT 1"));
        if (subject == null) {
            throw new BizException("任教学科不属于当前学段或未启用");
        }
        if (subject.getIsCategory() != null && subject.getIsCategory() == CATEGORY) {
            throw new BizException("任教学科类别节点不可选择");
        }
        return subject;
    }

    private void validateDict(String typeCode, String itemCode, String message) {
        String code = requiredTrim(itemCode, message);
        SysDictItem item = dictItemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getItemCode, code)
                .eq(SysDictItem::getYearVersion, DEFAULT_YEAR_VERSION)
                .eq(SysDictItem::getStatus, ENABLED)
                .last("LIMIT 1"));
        if (item == null) {
            throw new BizException(message + "：" + code);
        }
    }

    private List<String> readJsonArray(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new BizException("培养目标联动配置解析失败");
        }
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
