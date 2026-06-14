package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PermissionVO {

    private Long id;
    private String code;
    private String name;
    private String type;
    private Long parentId;
    private String path;
    private Integer sort;
    private Integer status;
    private String scopeType;
    private List<PermissionVO> children = new ArrayList<>();
}
