package cn.edu.gpnu.platform.system.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 行政区划。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_region")
public class SysRegion extends BaseEntity {

    private String code;
    private String name;
    private String parentCode;
    private Integer level;
    private Integer sort;
    private Integer status;
}
