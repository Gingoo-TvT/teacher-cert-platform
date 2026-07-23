package cn.edu.gpnu.platform.file.config;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinioConfigTest {

    @Test
    void managedMinioTransportAppliesConnectReadWriteAndIndependentCallTimeouts() {
        MinioProperties properties = new MinioProperties();
        properties.setConnectionTimeoutSeconds(7);
        properties.setReadTimeoutSeconds(31);
        properties.setCallTimeoutSeconds(901);
        MinioHttpTransport transport = new MinioHttpTransport(properties);

        try {
            OkHttpClient client = transport.client();
            assertThat(client.connectTimeoutMillis()).isEqualTo(7_000);
            assertThat(client.readTimeoutMillis()).isEqualTo(31_000);
            assertThat(client.writeTimeoutMillis()).isEqualTo(31_000);
            assertThat(client.callTimeoutMillis()).isEqualTo(901_000);
        } finally {
            transport.close();
        }
    }

    @Test
    void awsS3ClientAppliesIndependentTotalCallTimeout() {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("test-access");
        properties.setSecretKey("test-secret");
        properties.setCallTimeoutSeconds(777);
        MinioConfig config = new MinioConfig(properties);

        try (S3Client client = config.s3Client()) {
            assertThat(client.serviceClientConfiguration()
                    .overrideConfiguration()
                    .apiCallTimeout())
                    .contains(Duration.ofSeconds(777));
        }
    }

    @Test
    void zeroTimeoutCannotDisableBoundedMinioCalls() {
        MinioProperties connect = new MinioProperties();
        connect.setConnectionTimeoutSeconds(0);
        MinioProperties read = new MinioProperties();
        read.setReadTimeoutSeconds(0);
        MinioProperties call = new MinioProperties();
        call.setCallTimeoutSeconds(0);

        for (MinioProperties properties : java.util.List.of(connect, read, call)) {
            assertThatThrownBy(properties::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("必须为正数");
        }
    }
}
