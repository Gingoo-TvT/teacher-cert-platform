package cn.edu.gpnu.platform.boot.health;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformReadinessProbeTest {

    @Mock
    private DataSource dataSource;
    @Mock
    private Connection connection;
    @Mock
    private RedisConnectionFactory redisConnectionFactory;
    @Mock
    private RedisConnection redisConnection;
    @Mock
    private ReadinessMinioProbe minioProbe;
    @Mock
    private Flyway flyway;
    @Mock
    private MigrationInfoService migrationInfoService;

    private SimpleMeterRegistry meterRegistry;
    private PlatformReadinessProbe probe;

    @BeforeEach
    void setUp() throws Exception {
        ReadinessProbeProperties properties = new ReadinessProbeProperties();
        properties.setTimeout(java.time.Duration.ofMillis(500));
        meterRegistry = new SimpleMeterRegistry();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");
        when(minioProbe.ready()).thenReturn(true);
        when(flyway.info()).thenReturn(migrationInfoService);
        when(migrationInfoService.current()).thenReturn(mock(MigrationInfo.class));
        when(migrationInfoService.pending()).thenReturn(new MigrationInfo[0]);
        probe = new PlatformReadinessProbe(dataSource, redisConnectionFactory, minioProbe,
                flyway, properties, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        probe.close();
        meterRegistry.close();
    }

    @Test
    void allDependenciesUpMakesApplicationReady() {
        PlatformReadinessProbe.Snapshot snapshot = probe.check();

        assertThat(snapshot.ready()).isTrue();
        assertThat(snapshot.components()).containsOnly(
                org.assertj.core.api.Assertions.entry("mysql", "UP"),
                org.assertj.core.api.Assertions.entry("redis", "UP"),
                org.assertj.core.api.Assertions.entry("minio", "UP"),
                org.assertj.core.api.Assertions.entry("migration", "UP"));
        assertGauge("redis", 1.0);
    }

    @Test
    void redisFailureMakesReadinessDown() {
        when(redisConnection.ping()).thenThrow(new IllegalStateException("redis unavailable"));

        PlatformReadinessProbe.Snapshot snapshot = probe.check();

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.components()).containsEntry("redis", "DOWN");
        assertGauge("redis", 0.0);
    }

    @Test
    void minioFailureMakesReadinessDown() throws Exception {
        reset(minioProbe);
        when(minioProbe.ready())
                .thenThrow(new IllegalStateException("minio unavailable"));

        PlatformReadinessProbe.Snapshot snapshot = probe.check();

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.components()).containsEntry("minio", "DOWN");
        assertGauge("minio", 0.0);
    }

    @Test
    void pendingMigrationMakesReadinessDown() {
        when(migrationInfoService.pending()).thenReturn(new MigrationInfo[]{mock(MigrationInfo.class)});

        PlatformReadinessProbe.Snapshot snapshot = probe.check();

        assertThat(snapshot.ready()).isFalse();
        assertThat(snapshot.components()).containsEntry("migration", "DOWN");
        assertGauge("migration", 0.0);
    }

    @Test
    void totalBudgetRejectsOverlappingChecksAndRecoversWithoutQueueing() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        reset(minioProbe);
        when(minioProbe.ready()).thenAnswer(invocation -> {
            entered.countDown();
            boolean released = false;
            while (!released) {
                try {
                    released = release.await(10, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // 模拟不响应线程中断的依赖调用；释放后仍能由同一个固定 worker 恢复。
                }
            }
            return true;
        });
        try {
            long firstStarted = System.nanoTime();
            PlatformReadinessProbe.Snapshot first = probe.check();
            assertThat(Duration.ofNanos(System.nanoTime() - firstStarted)).isLessThan(Duration.ofSeconds(1));
            assertThat(first.ready()).isFalse();
            assertThat(entered.await(100, TimeUnit.MILLISECONDS)).isTrue();

            long secondStarted = System.nanoTime();
            PlatformReadinessProbe.Snapshot second = probe.check();
            assertThat(Duration.ofNanos(System.nanoTime() - secondStarted)).isLessThan(Duration.ofMillis(300));
            assertThat(second.ready()).isFalse();

            release.countDown();
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            PlatformReadinessProbe.Snapshot recovered = second;
            while (!recovered.ready() && System.nanoTime() < deadline) {
                Thread.sleep(20);
                recovered = probe.check();
            }
            assertThat(recovered.ready()).isTrue();
        } finally {
            release.countDown();
        }
    }

    private void assertGauge(String component, double expected) {
        assertThat(meterRegistry.find("platform.readiness.component")
                .tag("component", component).gauge()).isNotNull();
        assertThat(meterRegistry.find("platform.readiness.component")
                .tag("component", component).gauge().value()).isEqualTo(expected);
    }
}
