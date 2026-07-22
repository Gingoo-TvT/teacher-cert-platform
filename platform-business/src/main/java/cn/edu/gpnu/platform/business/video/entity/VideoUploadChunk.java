package cn.edu.gpnu.platform.business.video.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_upload_chunk")
public class VideoUploadChunk extends BaseEntity {

    private String uploadId;
    private Integer chunkIndex;
    private Integer partNumber;
    private String etag;
    private String chunkMd5;
    private Long chunkSize;
    private String objectKey;
    private LocalDateTime uploadedAt;
}
