package cn.edu.gpnu.platform.business.video.vo;

import lombok.Data;

@Data
public class VideoPlaybackVO {

    private String url;
    private Integer expirySeconds;
    private String watermarkText;
    private Long issuedAt;
}
