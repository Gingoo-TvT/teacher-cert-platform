package cn.edu.gpnu.platform.business.video.vo;

import lombok.Data;

import java.time.Instant;

@Data
public class VideoPresignedPartVO {

    private Integer partNumber;
    private String url;
    private Instant expiresAt;
}
