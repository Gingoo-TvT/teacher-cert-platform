package cn.edu.gpnu.platform.business.video.vo;

import lombok.Data;

@Data
public class VideoUploadedPartVO {

    private Integer partNumber;
    private String etag;
    private Long size;
}
