package cn.edu.gpnu.platform.business.exemption.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ExemptionMaterialVO {

    private Long id;
    private Long exemptionRequestId;
    private Long fileId;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Long uploaderId;
    private LocalDateTime uploadTime;
}
