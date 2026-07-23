package cn.edu.gpnu.platform.file.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 使用受管、已配置超时的唯一 MinioClient 确保业务桶存在。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MinioBucketInitializer {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @PostConstruct
    void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                log.info("MinIO bucket 已创建: {}", properties.getBucket());
            } else {
                log.info("MinIO bucket 已存在: {}", properties.getBucket());
            }
        } catch (Exception e) {
            log.warn("MinIO bucket 初始化失败(稍后可重试): {}", e.getMessage());
        }
    }
}
