package cn.edu.gpnu.platform.business.material.vo;

import lombok.Data;

import java.util.List;

@Data
public class MaterialDirectUploadVO {

    private Long fileId;
    private String uploadMode;
    private Long partSize;
    private List<MaterialUploadedPartVO> uploadedParts;
    private List<MaterialPresignedPartVO> parts;
}
