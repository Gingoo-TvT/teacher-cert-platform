package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

@Data
public class SysParamVO {

    private Long id;
    private String paramKey;
    private String paramValue;
    private String paramType;
    private String paramGroup;
    private String description;
    private Integer editable;
    private String updatedAt;
}
