package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

/**
 * 行政区划节点。
 */
@Data
public class RegionVO {

    private Long id;
    private String code;
    private String name;
    private String parentCode;
    private Integer level;
    private Integer sort;
    private Integer status;
    private Boolean leaf;
}
