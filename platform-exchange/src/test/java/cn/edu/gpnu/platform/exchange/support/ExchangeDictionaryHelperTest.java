package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.TeachingSubject;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.mapper.TeachingSubjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExchangeDictionaryHelperTest {

    @Test
    void buildsTemplateDropdownsInTheExistingOrderAndExcludesSubjectCategories() {
        SysDictItemMapper dictMapper = mock(SysDictItemMapper.class);
        TeachingSubjectMapper subjectMapper = mock(TeachingSubjectMapper.class);
        when(dictMapper.selectList(any())).thenReturn(List.of(dictItem("A"), dictItem("B")));
        when(subjectMapper.selectList(any())).thenReturn(List.of(subject("category", 1),
                subject("subject-one", 0), subject("subject-two", null)));
        ExchangeDictionaryHelper helper = new ExchangeDictionaryHelper(dictMapper, subjectMapper);

        Map<String, List<String>> actual = helper.dropdowns();

        assertThat(actual.keySet()).containsExactly("gender", "idCardType", "identityType", "educationLevel",
                "trainingGoal", "internshipOrgMode", "internshipLocation", "teachingSegment",
                "teachingSubject", "interviewOrgMode");
        assertThat(actual.get("gender")).containsExactly("A", "B");
        assertThat(actual.get("teachingSubject")).containsExactly("subject-one", "subject-two");
    }

    @Test
    void blankItemCodeDoesNotQueryAndConfiguredItemIsReturned() {
        SysDictItemMapper dictMapper = mock(SysDictItemMapper.class);
        TeachingSubjectMapper subjectMapper = mock(TeachingSubjectMapper.class);
        ExchangeDictionaryHelper helper = new ExchangeDictionaryHelper(dictMapper, subjectMapper);

        assertThat(helper.item("school", "  ")).isNull();
        verify(dictMapper, never()).selectOne(any());

        SysDictItem expected = dictItem("10588");
        when(dictMapper.selectOne(any())).thenReturn(expected);
        assertThat(helper.item("school", " 10588 ")).isSameAs(expected);
    }

    private SysDictItem dictItem(String code) {
        SysDictItem item = new SysDictItem();
        item.setItemCode(code);
        return item;
    }

    private TeachingSubject subject(String code, Integer category) {
        TeachingSubject subject = new TeachingSubject();
        subject.setSubjectCode(code);
        subject.setIsCategory(category);
        return subject;
    }
}
