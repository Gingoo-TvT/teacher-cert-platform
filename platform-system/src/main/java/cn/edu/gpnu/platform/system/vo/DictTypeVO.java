package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

/**
 * 字典类型出参。
 */
@Data
public class DictTypeVO {

    private Long id;
    private String typeCode;
    private String typeName;
    private String description;
    private Integer sort;
    private Integer status;
}
