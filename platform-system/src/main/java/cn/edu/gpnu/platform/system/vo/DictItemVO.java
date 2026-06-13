package cn.edu.gpnu.platform.system.vo;

import lombok.Data;

/**
 * 字典项出参。
 */
@Data
public class DictItemVO {

    private Long id;
    private String typeCode;
    private String itemCode;
    private String itemValue;
    private String parentCode;
    private Integer sort;
    private Integer status;
    private String yearVersion;
    private String extJson;
}
