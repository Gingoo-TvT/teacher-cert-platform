package cn.edu.gpnu.platform.business.material.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class MaterialPresignedPartVO {

    private Integer partNumber;
    private String url;
    private Instant expiresAt;
}
