package cn.edu.gpnu.platform.business.video.vo;

import lombok.Data;

import java.util.List;

@Data
public class VideoUploadProgressVO {

    private String uploadId;
    private String status;
    private Integer totalChunks;
    private Integer uploadedChunks;
    private Long uploadedBytes;
    private List<Integer> uploadedChunkIndexes;
    private Long fileId;
    private String validationMessage;
}
