package cn.edu.gpnu.platform.business.video.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("video_upload_session")
public class VideoUploadSession extends BaseEntity {

    private String uploadId;
    private String uploadMode;
    private String s3UploadId;
    private String objectKey;
    private java.time.LocalDateTime presignExpiresAt;
    private Integer slotClaimed;
    private Long studentId;
    private Long collegeId;
    private String assessmentYear;
    private String fileMd5;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private Long chunkSize;
    private Integer totalChunks;
    private Integer uploadedChunks;
    private Long uploadedBytes;
    private Integer durationSeconds;
    private String status;
    private Long fileId;
    private String validationMessage;
}
