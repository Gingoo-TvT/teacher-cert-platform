package cn.edu.gpnu.platform.file.service;

import cn.edu.gpnu.platform.file.model.MultipartObjectInfo;
import cn.edu.gpnu.platform.file.model.MultipartUploadPlan;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MultipartObjectService {

    MultipartUploadPlan createUpload(String objectKey, String contentType, Map<String, String> metadata,
                                      long objectSize, long partSize, int totalParts, int expirySeconds);

    MultipartUploadPlan resumeUpload(String objectKey, String multipartUploadId,
                                      long objectSize, long partSize, int totalParts, int expirySeconds);

    List<MultipartUploadedPart> listUploadedParts(String objectKey, String multipartUploadId);

    MultipartObjectInfo completeUpload(String objectKey, String multipartUploadId,
                                       List<MultipartUploadedPart> clientParts);

    Optional<MultipartObjectInfo> findObject(String objectKey);

    InputStream openObject(String objectKey);

    void abortUpload(String objectKey, String multipartUploadId);
}
