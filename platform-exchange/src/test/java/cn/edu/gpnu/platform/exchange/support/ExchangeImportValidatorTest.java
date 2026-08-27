package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.business.student.support.BirthDateValidator;
import cn.edu.gpnu.platform.business.student.support.IdCardValidator;
import cn.edu.gpnu.platform.business.student.support.NameValidator;
import cn.edu.gpnu.platform.business.training.support.MajorCodeValidator;
import cn.edu.gpnu.platform.business.training.support.TrainingLinkValidator;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import cn.edu.gpnu.platform.system.entity.SysCollege;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.mapper.SysCollegeMapper;
import cn.edu.gpnu.platform.system.mapper.SysMajorMapper;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeImportValidatorTest {

    private ExchangeImportValidator validator;

    @BeforeEach
    void setUp() {
        ParamService paramService = mock(ParamService.class);
        when(paramService.getBoolean("validate.idcard.checksum", false)).thenReturn(false);
        when(paramService.getString(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        ExchangeDictionaryHelper dictionaryHelper = mock(ExchangeDictionaryHelper.class);
        when(dictionaryHelper.item(anyString(), anyString())).thenAnswer(invocation ->
                dictionaryItem(invocation.getArgument(0)));
        IdCardValidator idCardValidator = new IdCardValidator(paramService);
        SysCollegeMapper collegeMapper = mock(SysCollegeMapper.class);
        SysCollege college = new SysCollege();
        college.setId(9L);
        when(collegeMapper.selectById(9L)).thenReturn(college);
        validator = new ExchangeImportValidator(paramService, dictionaryHelper,
                new NameValidator(paramService), idCardValidator, new BirthDateValidator(idCardValidator),
                mock(MajorCodeValidator.class), mock(TrainingLinkValidator.class), collegeMapper,
                mock(SysMajorMapper.class), mock(DataScopeService.class), new ObjectMapper());
    }

    @Test
    void canonicalIdUsesTheSameCaseNormalizationAsSingleRowValidation() {
        ExchangeStandardRow resident = new ExchangeStandardRow();
        resident.setIdCardType(IdCardValidator.RESIDENT_ID_CARD);
        resident.setIdCardNo("44010120000101001x");
        ExchangeStandardRow travelPermit = new ExchangeStandardRow();
        travelPermit.setIdCardType(IdCardValidator.HM_TRAVEL_PERMIT);
        travelPermit.setIdCardNo("a12345678");

        assertThat(validator.canonicalIdCardNo(resident)).isEqualTo("44010120000101001X");
        assertThat(validator.canonicalIdCardNo(travelPermit)).isEqualTo("a12345678");
    }

    @Test
    void validRowIsNormalizedInPlaceWithoutCreatingErrors() {
        ExchangeStandardRow row = validRow();

        List<ExchangeImportValidator.ValidationError> errors = validator.validate(row,
                Map.of("44010120000101001X", 1L), Map.of(row.getCertNo(), 1L));

        assertThat(errors).isEmpty();
        assertThat(row.getIdCardNo()).isEqualTo("44010120000101001X");
        assertThat(row.getValidUntil()).isEqualTo("2029/6/30");
    }

    @Test
    void duplicateCountsUseCanonicalIdAndReportBothV13Fields() {
        ExchangeStandardRow row = validRow();

        List<ExchangeImportValidator.ValidationError> errors = validator.validate(row,
                Map.of("44010120000101001X", 2L), Map.of(row.getCertNo(), 2L));

        assertThat(errors).extracting(ExchangeImportValidator.ValidationError::errorReason)
                .containsExactly("V-13证件号码已存在", "V-13证书编号已存在");
    }

    private ExchangeStandardRow validRow() {
        ExchangeStandardRow row = new ExchangeStandardRow();
        row.setSequenceNo("1");
        row.setSchoolCode("10588");
        row.setSchoolName("广东技术师范大学");
        row.setStudentNo("20260001");
        row.setName("学生甲");
        row.setGender("male");
        row.setIdCardType(IdCardValidator.RESIDENT_ID_CARD);
        row.setIdCardNo("44010120000101001x");
        row.setBirthDate("2000/1/1");
        row.setIdentityType("education_master");
        row.setSourcePlace("广东");
        row.setSecondDisciplineCode("040101");
        row.setSecondDisciplineName("教育学");
        row.setEducationLevel("undergraduate");
        row.setTrainingGoal("teacher");
        row.setInternshipOrgMode("centralized");
        row.setInternshipLocation("school");
        row.setTeachingSegment("secondary");
        row.setTeachingSubject("math");
        row.setInterviewOrgMode("school");
        row.setCertNo("202610588144200001");
        row.setValidUntil("2029-06-30");
        row.setIssuer("签发人");
        row.setRemark("9");
        return row;
    }

    private SysDictItem dictionaryItem(String typeCode) {
        SysDictItem item = new SysDictItem();
        item.setItemCode("configured");
        if ("school".equals(typeCode)) {
            item.setItemValue("广东技术师范大学");
        } else if ("education_level".equals(typeCode)) {
            item.setExtJson("{\"certLevelCode\":\"1\"}");
        } else if ("teaching_segment".equals(typeCode)) {
            item.setExtJson("{\"certSegmentCode\":\"2\"}");
        }
        return item;
    }
}
