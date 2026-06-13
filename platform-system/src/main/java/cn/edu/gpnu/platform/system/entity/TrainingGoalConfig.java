package cn.edu.gpnu.platform.system.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("training_goal_config")
public class TrainingGoalConfig extends BaseEntity {

    private String trainingGoalCode;
    private String defaultSegment;
    private String allowedSegmentsJson;
    private String defaultInternshipLocation;
    private String allowedInternshipLocationsJson;
    private Integer status;
}
