package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 已建立 TCP 但不返回 HTTP 的 MinIO 半开回归，覆盖 Compose 五秒健康检查合同。 */
@SpringBootTest(classes = {PlatformApplication.class, Ws11HealthHalfOpenIT.BusinessMinioConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Ws11HealthHalfOpenIT {

    private static final HalfOpenMinioStub MINIO_STUB = HalfOpenMinioStub.start();

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint", MINIO_STUB::endpoint);
        registry.add("minio.public-endpoint", MINIO_STUB::endpoint);
        registry.add("minio.bucket", () -> "teacher-cert-ws11-half-open");
        registry.add("platform.readiness.timeout", () -> "1s");
        registry.add("platform.readiness.minio-connect-timeout", () -> "200ms");
        registry.add("platform.readiness.minio-read-timeout", () -> "400ms");
        registry.add("platform.readiness.minio-call-timeout", () -> "400ms");
    }

    @AfterAll
    static void closeStub() {
        MINIO_STUB.close();
    }

    @Test
    void halfOpenMinioReturnsDownWithinFiveSecondsWithoutAccumulationAndThenRecovers() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            long started = System.nanoTime();
            ResponseEntity<String> response = rest.getForEntity(url("/api/health/readiness"), String.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            JsonNode data = objectMapper.readTree(response.getBody()).at("/data");
            assertThat(data.at("/components/minio").asText()).isEqualTo("DOWN");
        }

        MINIO_STUB.recover();
        ResponseEntity<String> recovered = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            recovered = rest.getForEntity(url("/api/health/readiness"), String.class);
            if (recovered.getStatusCode() == HttpStatus.OK) {
                break;
            }
            Thread.sleep(50);
        }
        assertThat(recovered).isNotNull();
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(recovered.getBody()).at("/data/components/minio").asText())
                .isEqualTo("UP");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BusinessMinioConfig {

        @Bean
        @Primary
        MinioClient ws11BusinessMinioClient() throws Exception {
            MinioClient client = mock(MinioClient.class);
            when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
            return client;
        }
    }

    private static final class HalfOpenMinioStub implements AutoCloseable {

        private final ServerSocket server;
        private final ExecutorService acceptor;
        private final ExecutorService handlers;
        private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
        private final AtomicBoolean responsive = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();

        private HalfOpenMinioStub() throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            acceptor = Executors.newSingleThreadExecutor(namedThreads("ws11-minio-accept-"));
            handlers = Executors.newFixedThreadPool(12, namedThreads("ws11-minio-connection-"));
            acceptor.execute(this::acceptLoop);
        }

        static HalfOpenMinioStub start() {
            try {
                return new HalfOpenMinioStub();
            } catch (IOException e) {
                throw new IllegalStateException("无法启动 WS-11 MinIO 半开测试桩", e);
            }
        }

        String endpoint() {
            return "http://127.0.0.1:" + server.getLocalPort();
        }

        void recover() {
            responsive.set(true);
        }

        private void acceptLoop() {
            while (!closed.get()) {
                try {
                    Socket socket = server.accept();
                    sockets.add(socket);
                    handlers.execute(() -> handle(socket));
                } catch (IOException e) {
                    if (!closed.get()) {
                        throw new IllegalStateException("WS-11 MinIO 测试桩 accept 失败", e);
                    }
                }
            }
        }

        private void handle(Socket socket) {
            try (socket) {
                socket.setSoTimeout(1000);
                readHeaders(socket.getInputStream());
                while (!responsive.get() && !closed.get()) {
                    Thread.sleep(10);
                }
                if (!closed.get()) {
                    OutputStream output = socket.getOutputStream();
                    output.write(("HTTP/1.1 200 OK\r\n"
                            + "Content-Length: 0\r\n"
                            + "Connection: close\r\n"
                            + "x-amz-bucket-region: us-east-1\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                }
            } catch (IOException ignored) {
                // 超时客户端会先关闭半开连接，属于本测试的预期路径。
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                sockets.remove(socket);
            }
        }

        private void readHeaders(InputStream input) throws IOException {
            int matched = 0;
            byte[] end = {'\r', '\n', '\r', '\n'};
            while (matched < end.length) {
                int value = input.read();
                if (value < 0) {
                    return;
                }
                matched = value == end[matched] ? matched + 1 : (value == end[0] ? 1 : 0);
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                server.close();
            } catch (IOException ignored) {
                // 已关闭等价于清理完成。
            }
            sockets.forEach(socket -> {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // 逐个精确关闭测试桩持有的连接。
                }
            });
            acceptor.shutdownNow();
            handlers.shutdownNow();
            awaitTermination(acceptor);
            awaitTermination(handlers);
        }

        private void awaitTermination(ExecutorService executor) {
            try {
                assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        private static java.util.concurrent.ThreadFactory namedThreads(String prefix) {
            AtomicInteger sequence = new AtomicInteger();
            return runnable -> {
                Thread thread = new Thread(runnable, prefix + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            };
        }
    }
}
