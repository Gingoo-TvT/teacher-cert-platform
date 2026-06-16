package cn.edu.gpnu.platform.business.video.vo;

import lombok.Data;

import java.util.List;

@Data
public class VideoUploadInitVO {

    private String uploadId;
    private boolean instantHit;
    private Long fileId;
    private Long reviewId;
    private List<Integer> uploadedChunks;
    private String status;
    private String validationMessage;
}
