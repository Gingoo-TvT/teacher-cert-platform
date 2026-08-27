package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.TeachingSubject;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.mapper.TeachingSubjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交换模板所需的启用字典与联动下拉值。
 */
@Component
@RequiredArgsConstructor
public class ExchangeDictionaryHelper {

    private static final String DEFAULT_YEAR_VERSION = "GLOBAL";

    private final SysDictItemMapper dictItemMapper;
    private final TeachingSubjectMapper teachingSubjectMapper;

    public Map<String, List<String>> dropdowns() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("gender", codes("gender"));
        values.put("idCardType", codes("id_card_type"));
        values.put("identityType", codes("identity_type"));
        values.put("educationLevel", codes("education_level"));
        values.put("trainingGoal", codes("training_goal"));
        values.put("internshipOrgMode", codes("internship_org_mode"));
        values.put("internshipLocation", codes("internship_location"));
        values.put("teachingSegment", codes("teaching_segment"));
        values.put("teachingSubject", teachingSubjectMapper.selectList(new LambdaQueryWrapper<TeachingSubject>()
                        .eq(TeachingSubject::getStatus, 1)
                        .eq(TeachingSubject::getYearVersion, DEFAULT_YEAR_VERSION)
                        .orderByAsc(TeachingSubject::getSegmentCode)
                        .orderByAsc(TeachingSubject::getSubjectCode))
                .stream()
                .filter(item -> item.getIsCategory() == null || item.getIsCategory() == 0)
                .map(TeachingSubject::getSubjectCode)
                .toList());
        values.put("interviewOrgMode", codes("interview_org_mode"));
        return values;
    }

    public SysDictItem item(String typeCode, String itemCode) {
        if (!StringUtils.hasText(itemCode)) {
            return null;
        }
        return dictItemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getItemCode, itemCode.trim())
                .eq(SysDictItem::getStatus, 1)
                .last("LIMIT 1"));
    }

    private List<String> codes(String typeCode) {
        return dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getTypeCode, typeCode)
                        .eq(SysDictItem::getStatus, 1)
                        .eq(SysDictItem::getYearVersion, DEFAULT_YEAR_VERSION)
                        .orderByAsc(SysDictItem::getSort))
                .stream()
                .map(SysDictItem::getItemCode)
                .toList();
    }
}
