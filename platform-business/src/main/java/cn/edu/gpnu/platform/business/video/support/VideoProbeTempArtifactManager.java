package cn.edu.gpnu.platform.business.video.support;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 持久探测卷的临时工件唯一生命周期管理器。
 *
 * <p>当前 JVM 活跃路径永不清扫；跨 JVM 通过 owner heartbeat 与保守 TTL 判定孤儿。
 * 只处理本组件的严格文件名和三类历史前缀，未知文件、目录、符号链接一律保留。</p>
 */
@Component
@Slf4j
public class VideoProbeTempArtifactManager {

    private static final String OWNER_MARKER_PREFIX = ".video-probe-owner-v1-";
    private static final String OWNER_MARKER_SUFFIX = ".heartbeat";
    private static final String DIRECTORY_LOCK_NAME = ".video-probe-owner-v1.lock";
    private static final String ARTIFACT_PREFIX = "video-probe-v1-";
    private static final Pattern ARTIFACT_PATTERN = Pattern.compile(
            "^video-probe-v1-([a-f0-9]{32})-(\\d{1,19})-(media|result|args)-.+$");
    private static final String[] LEGACY_PREFIXES = {
            "video-probe-", "video-worker-result-", "video-worker-"
    };

    private final VideoProbeProperties properties;
    private final Clock clock;
    private final String ownerId;
    private final Map<Path, ArtifactKind> activePaths = new ConcurrentHashMap<>();
    private final AtomicBoolean cleaning = new AtomicBoolean();
    private final Object mediaAccountingMonitor = new Object();
    private final ScheduledExecutorService scheduler;
    private Path directory;
    private Path ownerMarker;
    private FileChannel directoryLockChannel;
    private FileLock directoryLock;

    @Autowired
    public VideoProbeTempArtifactManager(VideoProbeProperties properties) {
        this(properties, Clock.systemUTC(), UUID.randomUUID().toString().replace("-", ""));
    }

    VideoProbeTempArtifactManager(VideoProbeProperties properties, Clock clock, String ownerId) {
        this.properties = properties;
        this.clock = clock;
        this.ownerId = ownerId;
        // heartbeat 与目录清扫分线程，避免大量目录项拖延 owner 存活信号。
        this.scheduler = Executors.newScheduledThreadPool(2, new ArtifactThreadFactory());
    }

    @PostConstruct
    void initialize() {
        validateConfiguration();
        directory = properties.getTempDirectory().toAbsolutePath().normalize();
        ownerMarker = directory.resolve(OWNER_MARKER_PREFIX + ownerId + OWNER_MARKER_SUFFIX);
        try {
            Files.createDirectories(directory);
            acquireDirectoryLock();
            heartbeat();
            cleanupOrphans();
        } catch (IOException | RuntimeException e) {
            releaseDirectoryLock();
            throw new IllegalStateException("视频探测临时目录不可用", e);
        }
        scheduler.scheduleWithFixedDelay(this::heartbeatQuietly,
                properties.getArtifactHeartbeatInterval().toMillis(),
                properties.getArtifactHeartbeatInterval().toMillis(), TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::cleanupQuietly,
                properties.getArtifactCleanupInterval().toMillis(),
                properties.getArtifactCleanupInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    public Artifact create(ArtifactKind kind, String suffix) throws IOException {
        synchronized (mediaAccountingMonitor) {
            Path file = Files.createTempFile(directory,
                    ARTIFACT_PREFIX + ownerId + "-" + clock.millis() + "-"
                            + kind.name().toLowerCase(Locale.ROOT) + "-",
                    suffix).toAbsolutePath().normalize();
            activePaths.put(file, kind);
            return new Artifact(this, file, kind);
        }
    }

    public long activeMediaBytes() throws IOException {
        synchronized (mediaAccountingMonitor) {
            return activeMediaBytesUnlocked();
        }
    }

    DiskSnapshot diskSnapshot(Path path, VideoProbeDiskSpace diskSpace) throws IOException {
        synchronized (mediaAccountingMonitor) {
            long usable = diskSpace.usableSpace(path);
            return new DiskSnapshot(usable, activeMediaBytesUnlocked());
        }
    }

    private long activeMediaBytesUnlocked() throws IOException {
        long total = 0L;
        for (Map.Entry<Path, ArtifactKind> entry : activePaths.entrySet()) {
            if (entry.getValue() == ArtifactKind.MEDIA && Files.isRegularFile(
                    entry.getKey(), LinkOption.NOFOLLOW_LINKS)) {
                total = Math.addExact(total, Files.size(entry.getKey()));
            }
        }
        return total;
    }

    public int cleanupOrphans() throws IOException {
        if (!cleaning.compareAndSet(false, true)) {
            return 0;
        }
        int deleted = 0;
        int scanned = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path candidate : stream) {
                Path normalized = candidate.toAbsolutePath().normalize();
                if (activePaths.containsKey(normalized)
                        || Files.isSymbolicLink(normalized)
                        || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (normalized.equals(ownerMarker)) {
                    continue;
                }
                String name = normalized.getFileName().toString();
                Matcher matcher = ARTIFACT_PATTERN.matcher(name);
                boolean versionedArtifact = matcher.matches();
                boolean legacyArtifact = isLegacyArtifact(name);
                boolean ownerHeartbeat =
                        name.startsWith(OWNER_MARKER_PREFIX) && name.endsWith(OWNER_MARKER_SUFFIX);
                // 未知/用户文件不消耗扫描预算，避免大量保留文件永久饿死后续可清理孤儿。
                if (!versionedArtifact && !legacyArtifact && !ownerHeartbeat) {
                    continue;
                }
                if (++scanned > properties.getArtifactCleanupScanLimit()) {
                    break;
                }
                if (versionedArtifact) {
                    String artifactOwner = matcher.group(1);
                    long createdMillis;
                    try {
                        createdMillis = Long.parseLong(matcher.group(2));
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    if (isExpired(normalized, Instant.ofEpochMilli(createdMillis),
                            properties.getArtifactOrphanTtl())
                            && ownerCanBeReaped(artifactOwner)) {
                        deleted += deleteKnownArtifact(normalized);
                    }
                    continue;
                }
                if (legacyArtifact
                        && isExpired(normalized, Files.getLastModifiedTime(normalized).toInstant(),
                        properties.getArtifactLegacyOrphanTtl())) {
                    deleted += deleteKnownArtifact(normalized);
                    continue;
                }
                if (ownerHeartbeat
                        && olderThan(normalized, properties.getArtifactOrphanTtl())) {
                    deleted += deleteKnownArtifact(normalized);
                }
            }
        } finally {
            cleaning.set(false);
        }
        if (deleted > 0) {
            log.info("视频探测临时卷孤儿清扫完成: deleted={}", deleted);
        }
        return deleted;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        if (ownerMarker != null) {
            try {
                Files.deleteIfExists(ownerMarker);
            } catch (IOException e) {
                log.warn("删除视频探测 owner heartbeat 失败: {}", e.getMessage());
            }
        }
        releaseDirectoryLock();
    }

    private boolean ownerCanBeReaped(String artifactOwner) throws IOException {
        if (ownerId.equals(artifactOwner)) {
            return true;
        }
        Path marker = directory.resolve(OWNER_MARKER_PREFIX + artifactOwner + OWNER_MARKER_SUFFIX);
        return !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
                || olderThan(marker, properties.getArtifactOwnerStaleAfter());
    }

    private boolean isExpired(Path file, Instant createdAt, Duration ttl) throws IOException {
        Instant cutoff = clock.instant().minus(ttl);
        FileTime modified = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS);
        return createdAt.isBefore(cutoff) && modified.toInstant().isBefore(cutoff);
    }

    private boolean olderThan(Path file, Duration age) throws IOException {
        return Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant()
                .isBefore(clock.instant().minus(age));
    }

    private boolean isLegacyArtifact(String name) {
        if (name.startsWith(ARTIFACT_PREFIX)) {
            return false;
        }
        for (String prefix : LEGACY_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private int deleteKnownArtifact(Path file) {
        try {
            return Files.deleteIfExists(file) ? 1 : 0;
        } catch (IOException e) {
            log.warn("清理视频探测孤儿文件失败: file={}, message={}", file.getFileName(), e.getMessage());
            return 0;
        }
    }

    private void release(Path file, boolean delete) {
        synchronized (mediaAccountingMonitor) {
            try {
                if (delete) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException e) {
                log.warn("清理视频探测临时文件失败: file={}, message={}", file.getFileName(), e.getMessage());
            } finally {
                activePaths.remove(file);
            }
        }
    }

    private OutputStream mediaOutput(Path file) throws IOException {
        if (activePaths.get(file) != ArtifactKind.MEDIA) {
            throw new IllegalStateException("仅媒体临时工件可打开受管写入流");
        }
        OutputStream delegate;
        synchronized (mediaAccountingMonitor) {
            delegate = Files.newOutputStream(file);
        }
        return new FilterOutputStream(delegate) {
            @Override
            public void write(int value) throws IOException {
                synchronized (mediaAccountingMonitor) {
                    out.write(value);
                }
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                synchronized (mediaAccountingMonitor) {
                    out.write(bytes, offset, length);
                }
            }

            @Override
            public void flush() throws IOException {
                synchronized (mediaAccountingMonitor) {
                    out.flush();
                }
            }

            @Override
            public void close() throws IOException {
                synchronized (mediaAccountingMonitor) {
                    out.close();
                }
            }
        };
    }

    private void heartbeat() throws IOException {
        Files.writeString(ownerMarker, String.valueOf(clock.millis()),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        Files.setLastModifiedTime(ownerMarker, FileTime.from(clock.instant()));
    }

    private void acquireDirectoryLock() throws IOException {
        directoryLockChannel = FileChannel.open(directory.resolve(DIRECTORY_LOCK_NAME),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            directoryLock = directoryLockChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            directoryLock = null;
        }
        if (directoryLock == null) {
            throw new IllegalStateException("视频探测临时目录已被其他实例占用");
        }
    }

    private void releaseDirectoryLock() {
        if (directoryLock != null) {
            try {
                directoryLock.release();
            } catch (IOException e) {
                log.warn("释放视频探测临时目录独占锁失败: {}", e.getMessage());
            } finally {
                directoryLock = null;
            }
        }
        if (directoryLockChannel != null) {
            try {
                directoryLockChannel.close();
            } catch (IOException e) {
                log.warn("关闭视频探测临时目录锁通道失败: {}", e.getMessage());
            } finally {
                directoryLockChannel = null;
            }
        }
    }

    private void heartbeatQuietly() {
        try {
            heartbeat();
        } catch (IOException e) {
            log.error("刷新视频探测 owner heartbeat 失败", e);
        }
    }

    private void cleanupQuietly() {
        try {
            cleanupOrphans();
        } catch (IOException e) {
            log.error("视频探测临时卷孤儿清扫失败", e);
        }
    }

    private void validateConfiguration() {
        Duration safetyFloor = properties.getMaxDuration().plusSeconds(10);
        if (properties.getArtifactHeartbeatInterval() == null
                || properties.getArtifactHeartbeatInterval().isZero()
                || properties.getArtifactHeartbeatInterval().isNegative()
                || properties.getArtifactOwnerStaleAfter() == null
                || properties.getArtifactOwnerStaleAfter()
                        .compareTo(properties.getArtifactHeartbeatInterval().multipliedBy(2)) <= 0
                || properties.getArtifactOrphanTtl() == null
                || properties.getArtifactOrphanTtl().compareTo(safetyFloor) <= 0
                || properties.getArtifactLegacyOrphanTtl() == null
                || properties.getArtifactLegacyOrphanTtl()
                        .compareTo(properties.getArtifactOrphanTtl()) < 0
                || properties.getArtifactCleanupInterval() == null
                || properties.getArtifactCleanupInterval().isZero()
                || properties.getArtifactCleanupInterval().isNegative()
                || properties.getArtifactCleanupScanLimit() < 1) {
            throw new IllegalStateException("视频探测临时工件清理配置不合法");
        }
    }

    public enum ArtifactKind {
        MEDIA,
        RESULT,
        ARGS
    }

    record DiskSnapshot(long usableBytes, long activeMediaBytes) {
    }

    public static final class Artifact implements AutoCloseable {

        private final VideoProbeTempArtifactManager owner;
        private final Path path;
        private final ArtifactKind kind;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean outputOpened = new AtomicBoolean();
        private volatile boolean deleteOnClose = true;

        private Artifact(VideoProbeTempArtifactManager owner, Path path, ArtifactKind kind) {
            this.owner = owner;
            this.path = path;
            this.kind = kind;
        }

        public Path path() {
            return path;
        }

        public OutputStream openOutputStream() throws IOException {
            if (kind != ArtifactKind.MEDIA || !outputOpened.compareAndSet(false, true)) {
                throw new IllegalStateException("媒体临时工件写入流不可重复打开");
            }
            return owner.mediaOutput(path);
        }

        /**
         * 未确认退出的进程仍可能读取该文件；本次不删除，交由 TTL/heartbeat 清扫。
         */
        public void preserveForReaper() {
            deleteOnClose = false;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owner.release(path, deleteOnClose);
            }
        }
    }

    private static final class ArtifactThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "video-probe-artifact-reaper");
            thread.setDaemon(true);
            return thread;
        }
    }
}
