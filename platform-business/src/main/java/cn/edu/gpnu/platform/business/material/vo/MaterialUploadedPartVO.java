package cn.edu.gpnu.platform.business.material.vo;

import lombok.Data;

@Data
public class MaterialUploadedPartVO {

    private Integer partNumber;
    private String etag;
    private Long size;
}
