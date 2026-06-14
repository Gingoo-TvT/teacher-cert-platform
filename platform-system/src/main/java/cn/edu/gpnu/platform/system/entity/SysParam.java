package cn.edu.gpnu.platform.system.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_param")
public class SysParam extends BaseEntity {

    private String paramKey;
    private String paramValue;
    private String paramType;
    private String paramGroup;
    private String description;
    private Integer editable;
}
