package cn.edu.gpnu.platform.business.video.support;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 把 worker 生命周期绑定到启动它的父 JVM。PID 与 OS 启动令牌同时匹配，避免 PID 复用误判。
 */
public final class VideoProbeParentWatchdog implements AutoCloseable {

    static final String PARENT_PID_PROPERTY = "platform.video.probe.parent.pid";
    static final String PARENT_STARTED_AT_PROPERTY = "platform.video.probe.parent.started-at";
    static final String CHECK_INTERVAL_MILLIS_PROPERTY =
            "platform.video.probe.parent.check-interval-millis";
    private static final int PARENT_LOST_EXIT_CODE = 74;

    private final ExpectedParent expectedParent;
    private final long checkIntervalMillis;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Thread watchdog;

    private VideoProbeParentWatchdog(ExpectedParent expectedParent, long checkIntervalMillis) {
        this.expectedParent = expectedParent;
        this.checkIntervalMillis = checkIntervalMillis;
        if (!matchesExpectedParent()) {
            throw new IllegalStateException("视频探测工作进程父进程身份不匹配");
        }
        watchdog = new Thread(this::watch, "video-probe-parent-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    static VideoProbeParentWatchdog startFromSystemProperties() {
        long parentPid = positiveLong(
                System.getProperty(PARENT_PID_PROPERTY), "父进程PID");
        String startedAt = requiredProperty(PARENT_STARTED_AT_PROPERTY);
        long intervalMillis = positiveLong(
                System.getProperty(CHECK_INTERVAL_MILLIS_PROPERTY), "父进程检查周期");
        return new VideoProbeParentWatchdog(
                new ExpectedParent(parentPid, Instant.parse(startedAt)), intervalMillis);
    }

    static List<String> jvmArgumentsForCurrentParent(Duration checkInterval) {
        if (checkInterval == null || checkInterval.isZero() || checkInterval.isNegative()) {
            throw new VideoProbeInfrastructureException("视频探测父进程检查周期必须为正数");
        }
        ProcessHandle current = ProcessHandle.current();
        Instant startedAt = current.info().startInstant()
                .orElseThrow(() -> new VideoProbeInfrastructureException(
                        "无法取得视频探测父进程启动令牌"));
        return List.of(
                "-D" + PARENT_PID_PROPERTY + "=" + current.pid(),
                "-D" + PARENT_STARTED_AT_PROPERTY + "=" + startedAt,
                "-D" + CHECK_INTERVAL_MILLIS_PROPERTY + "="
                        + Math.max(1L, checkInterval.toMillis()));
    }

    private void watch() {
        while (!closed.get()) {
            try {
                Thread.sleep(checkIntervalMillis);
            } catch (InterruptedException ignored) {
                if (closed.get()) {
                    return;
                }
            }
            if (!closed.get() && !matchesExpectedParent()) {
                Runtime.getRuntime().halt(PARENT_LOST_EXIT_CODE);
            }
        }
    }

    private boolean matchesExpectedParent() {
        return ProcessHandle.current().parent()
                .filter(parent -> parent.pid() == expectedParent.pid())
                .filter(ProcessHandle::isAlive)
                .flatMap(parent -> parent.info().startInstant())
                .map(expectedParent.startedAt()::equals)
                .orElse(false);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            watchdog.interrupt();
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("视频探测工作进程缺少父进程身份");
        }
        return value;
    }

    private static long positiveLong(String value, String label) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) {
                throw new NumberFormatException(label);
            }
            return parsed;
        } catch (RuntimeException e) {
            throw new IllegalStateException("视频探测工作进程" + label + "不合法", e);
        }
    }

    private record ExpectedParent(long pid, Instant startedAt) {
    }
}
