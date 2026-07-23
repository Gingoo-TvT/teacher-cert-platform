package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.service.MultipartObjectService;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MinioVideoFinalizationObjectStore implements VideoFinalizationObjectStore {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final MultipartObjectService multipartObjectService;

    @Override
    public void remove(String bucket, String objectKey) {
        requireManagedBucket(bucket);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new VideoProbeInfrastructureException("删除未登记视频定稿对象失败", e);
        }
    }

    @Override
    public boolean exists(String bucket, String objectKey) {
        requireManagedBucket(bucket);
        return multipartObjectService.findObject(objectKey).isPresent();
    }

    private void requireManagedBucket(String bucket) {
        if (!minioProperties.getBucket().equals(bucket)) {
            throw new VideoProbeInfrastructureException("视频定稿候选对象不属于当前受管存储桶");
        }
    }
}
