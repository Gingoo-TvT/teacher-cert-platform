package cn.edu.gpnu.platform.file.config;

import okhttp3.OkHttpClient;

import java.time.Duration;

/**
 * 两类 MinIO 调用共享的受管 HTTP transport；完整调用时限与 socket 空闲时限分开配置。
 */
public final class MinioHttpTransport implements AutoCloseable {

    private final OkHttpClient client;

    MinioHttpTransport(MinioProperties properties) {
        client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectionTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .writeTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .callTimeout(Duration.ofSeconds(properties.getCallTimeoutSeconds()))
                .build();
    }

    public OkHttpClient client() {
        return client;
    }

    @Override
    public void close() {
        client.dispatcher().cancelAll();
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
        if (client.cache() != null) {
            try {
                client.cache().close();
            } catch (java.io.IOException ignored) {
                // 无磁盘缓存是默认路径；关闭失败不阻止应用退出。
            }
        }
    }
}
