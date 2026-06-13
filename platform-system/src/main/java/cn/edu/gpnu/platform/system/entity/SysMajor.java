package cn.edu.gpnu.platform.system.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_major")
public class SysMajor extends BaseEntity {

    private Long collegeId;
    private String internalMajorCode;
    private String internalMajorName;
    private String secondDisciplineCode;
    private String secondDisciplineName;
    private Integer pilotScopeFlag;
    private String yearVersion;
    private Integer sort;
    private Integer status;
}
