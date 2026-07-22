package cn.edu.gpnu.platform.business.video.vo;

import lombok.Data;

import java.util.List;

@Data
public class VideoUploadInitVO {

    private String uploadId;
    private String uploadMode;
    private Long partSize;
    private boolean instantHit;
    private Long fileId;
    private Long reviewId;
    private List<Integer> uploadedChunks;
    private List<VideoUploadedPartVO> uploadedParts;
    private List<VideoPresignedPartVO> parts;
    private String status;
    private String validationMessage;
}
