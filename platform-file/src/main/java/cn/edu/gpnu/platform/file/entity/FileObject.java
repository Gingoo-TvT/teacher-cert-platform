package cn.edu.gpnu.platform.file.entity;

import cn.edu.gpnu.platform.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 文件登记（对应 MinIO 对象）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("file_object")
public class FileObject extends BaseEntity {

    private String originalName;
    private String storedName;
    private String bucket;
    private String objectKey;
    private Long size;
    private String contentType;
    private String md5;
    private String bizType;
    private String status;
    private String multipartUploadId;
    private Long uploadPartSize;
    private Integer uploadTotalParts;
    private LocalDateTime uploadExpiresAt;
    private String uploadContextHash;
    private String uploadMetadataHash;
    private String checksumAlgorithm;
    private Integer contentHashVerified;
    private String mediaCodec;
    private String mediaValidationPolicyHash;
    private String mediaProbeVersion;
    private Long uploaderId;
    private LocalDateTime uploadTime;
}
