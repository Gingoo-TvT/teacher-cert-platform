package cn.edu.gpnu.platform.system.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("major_training_goal")
public class MajorTrainingGoal extends BaseEntity {

    private Long majorId;
    private String trainingGoalCode;
    private Integer sort;
    private Integer status;
}
