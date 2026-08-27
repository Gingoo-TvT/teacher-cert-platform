package cn.edu.gpnu.platform.boot.health;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 应用就绪探针：只回答当前实例能否使用关键依赖提供业务服务。
 */
@Component
public class PlatformReadinessProbe {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ReadinessMinioProbe minioProbe;
    private final Flyway flyway;
    private final ReadinessProbeProperties properties;
    private final ThreadPoolExecutor executor;
    private final Map<String, AtomicInteger> componentStates = new LinkedHashMap<>();
    private static final AtomicLong THREAD_SEQUENCE = new AtomicLong();

    public PlatformReadinessProbe(DataSource dataSource,
                                  RedisConnectionFactory redisConnectionFactory,
                                  ReadinessMinioProbe minioProbe,
                                  Flyway flyway,
                                  ReadinessProbeProperties properties,
                                  MeterRegistry meterRegistry) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.minioProbe = minioProbe;
        this.flyway = flyway;
        this.properties = properties;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable,
                            "readiness-probe-" + THREAD_SEQUENCE.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        registerGauge(meterRegistry, "mysql");
        registerGauge(meterRegistry, "redis");
        registerGauge(meterRegistry, "minio");
        registerGauge(meterRegistry, "migration");
    }

    public Snapshot check() {
        Future<Snapshot> future;
        try {
            future = executor.submit(this::runChecks);
        } catch (RejectedExecutionException ignored) {
            return publish(unavailableSnapshot());
        }
        try {
            return publish(future.get(properties.getTimeout().toNanos(), TimeUnit.NANOSECONDS));
        } catch (TimeoutException ignored) {
            future.cancel(true);
            minioProbe.cancelActiveCalls();
            return publish(unavailableSnapshot());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            minioProbe.cancelActiveCalls();
            return publish(unavailableSnapshot());
        } catch (ExecutionException ignored) {
            return publish(unavailableSnapshot());
        }
    }

    private Snapshot runChecks() {
        Map<String, String> components = new LinkedHashMap<>();
        boolean ready = checkComponent("mysql", this::databaseReady, components);
        ready &= checkComponent("redis", this::redisReady, components);
        ready &= checkComponent("minio", this::minioReady, components);
        ready &= checkComponent("migration", this::migrationReady, components);
        return new Snapshot(ready, Collections.unmodifiableMap(components));
    }

    private boolean databaseReady() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        }
    }

    private boolean redisReady() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        }
    }

    private boolean minioReady() throws Exception {
        return minioProbe.ready();
    }

    private boolean migrationReady() {
        MigrationInfoService info = flyway.info();
        return info.current() != null && info.pending().length == 0;
    }

    private boolean checkComponent(String component, CheckedBooleanSupplier check,
                                   Map<String, String> components) {
        boolean up;
        try {
            up = check.getAsBoolean();
        } catch (Exception ignored) {
            up = false;
        }
        components.put(component, up ? UP : DOWN);
        return up;
    }

    private Snapshot unavailableSnapshot() {
        Map<String, String> components = new LinkedHashMap<>();
        componentStates.keySet().forEach(component -> components.put(component, DOWN));
        return new Snapshot(false, Collections.unmodifiableMap(components));
    }

    private Snapshot publish(Snapshot snapshot) {
        snapshot.components().forEach((component, status) ->
                componentStates.get(component).set(UP.equals(status) ? 1 : 0));
        return snapshot;
    }

    private void registerGauge(MeterRegistry meterRegistry, String component) {
        AtomicInteger state = new AtomicInteger();
        componentStates.put(component, state);
        Gauge.builder("platform.readiness.component", state, AtomicInteger::get)
                .description("关键依赖就绪状态，1=UP，0=DOWN")
                .tag("component", component)
                .register(meterRegistry);
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    public record Snapshot(boolean ready, Map<String, String> components) {
    }

    @PreDestroy
    void close() {
        minioProbe.cancelActiveCalls();
        executor.shutdownNow();
        try {
            executor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
