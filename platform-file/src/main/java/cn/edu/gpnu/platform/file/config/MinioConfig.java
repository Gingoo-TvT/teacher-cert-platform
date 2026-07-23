package cn.edu.gpnu.platform.file.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;

/**
 * MinIO 客户端 + 启动时确保 bucket 存在。
 */
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties props;

    @Bean
    public MinioHttpTransport minioHttpTransport() {
        return new MinioHttpTransport(props);
    }

    @Bean
    public MinioClient minioClient(MinioHttpTransport transport) {
        return MinioClient.builder()
                .endpoint(props.getEndpoint())
                .credentials(props.getAccessKey(), props.getSecretKey())
                .httpClient(transport.client())
                .build();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .credentialsProvider(credentialsProvider())
                .region(Region.of(props.getRegion()))
                .serviceConfiguration(s3Configuration())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(props.getCallTimeoutSeconds()))
                        .build())
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(props.getConnectionTimeoutSeconds()))
                        .socketTimeout(Duration.ofSeconds(props.getReadTimeoutSeconds())))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.browserEndpoint()))
                .credentialsProvider(credentialsProvider())
                .region(Region.of(props.getRegion()))
                .serviceConfiguration(s3Configuration())
                .build();
    }

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
                props.getAccessKey(), props.getSecretKey()));
    }

    private S3Configuration s3Configuration() {
        return S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
    }

}
