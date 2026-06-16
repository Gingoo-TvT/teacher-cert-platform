package cn.edu.gpnu.platform.business.testresult.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ability_test_result")
public class AbilityTestResult extends BaseEntity {

    private Long studentId;
    private Long collegeId;
    private String assessmentYear;
    private String examOrgMode;
    private String examSubjects;
    private String score;
    private String conclusion;
    private String exemptionRelation;
    private String confirmStatus;
    private Integer locked;
}
