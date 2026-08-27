package cn.edu.gpnu.platform.boot.health;

import cn.edu.gpnu.platform.file.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import jakarta.annotation.PreDestroy;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

/** 只供 readiness 使用的短超时 MinIO 客户端，不改变业务对象读写的长调用预算。 */
@Component
public class ReadinessMinioProbe implements AutoCloseable {

    private final String bucket;
    private final OkHttpClient httpClient;
    private final MinioClient minioClient;

    public ReadinessMinioProbe(MinioProperties minioProperties, ReadinessProbeProperties properties) {
        bucket = minioProperties.getBucket();
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getMinioConnectTimeout())
                .readTimeout(properties.getMinioReadTimeout())
                .writeTimeout(properties.getMinioReadTimeout())
                .callTimeout(properties.getMinioCallTimeout())
                .build();
        minioClient = MinioClient.builder()
                .endpoint(minioProperties.getEndpoint())
                .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                .region(minioProperties.getRegion())
                .httpClient(httpClient)
                .build();
    }

    public boolean ready() throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
    }

    public void cancelActiveCalls() {
        httpClient.dispatcher().cancelAll();
    }

    @Override
    @PreDestroy
    public void close() {
        cancelActiveCalls();
        httpClient.dispatcher().executorService().shutdownNow();
        httpClient.connectionPool().evictAll();
        if (httpClient.cache() != null) {
            try {
                httpClient.cache().close();
            } catch (java.io.IOException ignored) {
                // readiness transport 默认没有磁盘缓存；关闭失败不影响应用退出。
            }
        }
    }
}
