package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

@Data
public class RoleVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer sort;
    private Integer status;
}
