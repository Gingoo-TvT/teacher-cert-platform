package cn.edu.gpnu.platform.file.exception;

import cn.edu.gpnu.platform.common.exception.BizException;

/**
 * The backing S3 multipart upload no longer exists, usually because it expired or was aborted.
 */
public class MultipartUploadNotFoundException extends BizException {

    public MultipartUploadNotFoundException(String operation) {
        super(operation + "：S3分片上传会话不存在或已失效");
    }
}
