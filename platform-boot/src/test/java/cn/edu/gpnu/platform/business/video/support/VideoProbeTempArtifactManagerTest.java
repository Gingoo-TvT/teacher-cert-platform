package cn.edu.gpnu.platform.business.video.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoProbeTempArtifactManagerTest {

    private static final String LOCAL_OWNER = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String FRESH_REMOTE_OWNER = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String STALE_REMOTE_OWNER = "cccccccccccccccccccccccccccccccc";

    @TempDir
    Path tempDirectory;

    @Test
    void cleanupDeletesOnlyExpiredKnownOrphansAndKeepsActiveOrFreshOwnerFiles() throws Exception {
        Instant started = Instant.parse("2026-07-23T10:00:00Z");
        MutableClock clock = new MutableClock(started);
        VideoProbeProperties properties = properties();
        VideoProbeTempArtifactManager manager =
                new VideoProbeTempArtifactManager(properties, clock, LOCAL_OWNER);
        manager.initialize();

        try (VideoProbeTempArtifactManager.Artifact active =
                     manager.create(VideoProbeTempArtifactManager.ArtifactKind.MEDIA, ".mp4")) {
            Files.write(active.path(), new byte[]{1, 2, 3});
            clock.set(started.plusSeconds(120));
            Files.setLastModifiedTime(active.path(), FileTime.from(started));

            Path expiredOrphan = artifact(STALE_REMOTE_OWNER, started, "media", "expired.mp4");
            Path freshOwnerArtifact = artifact(FRESH_REMOTE_OWNER, started, "result", "fresh.properties");
            Path freshOwnerMarker = tempDirectory.resolve(
                    ".video-probe-owner-v1-" + FRESH_REMOTE_OWNER + ".heartbeat");
            Files.writeString(freshOwnerMarker, "alive");
            Files.setLastModifiedTime(freshOwnerMarker, FileTime.from(clock.instant()));
            Path unknown = tempDirectory.resolve("do-not-touch.bin");
            Files.writeString(unknown, "unknown");
            Files.setLastModifiedTime(unknown, FileTime.from(started));
            Path legacy = tempDirectory.resolve("video-worker-result-legacy.properties");
            Files.writeString(legacy, "legacy");
            Files.setLastModifiedTime(legacy, FileTime.from(started));

            int deleted = manager.cleanupOrphans();

            assertThat(deleted).isEqualTo(2);
            assertThat(expiredOrphan).doesNotExist();
            assertThat(legacy).doesNotExist();
            assertThat(active.path()).exists();
            assertThat(freshOwnerArtifact).exists();
            assertThat(unknown).exists();
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void unknownFilesDoNotExhaustKnownArtifactScanBudget() throws Exception {
        Instant started = Instant.parse("2026-07-23T10:00:00Z");
        MutableClock clock = new MutableClock(started.plusSeconds(120));
        VideoProbeProperties properties = properties();
        properties.setArtifactCleanupScanLimit(1);
        VideoProbeTempArtifactManager manager =
                new VideoProbeTempArtifactManager(properties, clock, LOCAL_OWNER);
        manager.initialize();
        try {
            for (int index = 0; index < 20; index++) {
                Files.writeString(tempDirectory.resolve("unknown-" + index + ".bin"), "keep");
            }
            Path expiredOrphan = artifact(STALE_REMOTE_OWNER, started, "media", "expired.mp4");

            assertThat(manager.cleanupOrphans()).isEqualTo(1);
            assertThat(expiredOrphan).doesNotExist();
            assertThat(tempDirectory.resolve("unknown-0.bin")).exists();
        } finally {
            manager.shutdown();
        }
    }

    @Test
    void sharedProbeDirectoryIsRejectedAcrossLiveManagers() {
        VideoProbeProperties properties = properties();
        VideoProbeTempArtifactManager first = new VideoProbeTempArtifactManager(properties);
        VideoProbeTempArtifactManager second = new VideoProbeTempArtifactManager(properties);
        first.initialize();
        try {
            assertThatThrownBy(second::initialize)
                    .isInstanceOf(IllegalStateException.class)
                    .hasRootCauseMessage("视频探测临时目录已被其他实例占用");
        } finally {
            second.shutdown();
            first.shutdown();
        }
    }

    private Path artifact(String owner, Instant created, String kind, String tail) throws Exception {
        Path path = tempDirectory.resolve(
                "video-probe-v1-" + owner + "-" + created.toEpochMilli() + "-" + kind + "-" + tail);
        Files.writeString(path, "artifact");
        Files.setLastModifiedTime(path, FileTime.from(created));
        return path;
    }

    private VideoProbeProperties properties() {
        VideoProbeProperties properties = new VideoProbeProperties();
        properties.setTempDirectory(tempDirectory);
        properties.setMaxDuration(Duration.ofSeconds(5));
        properties.setArtifactHeartbeatInterval(Duration.ofSeconds(1));
        properties.setArtifactOwnerStaleAfter(Duration.ofSeconds(3));
        properties.setArtifactOrphanTtl(Duration.ofSeconds(20));
        properties.setArtifactLegacyOrphanTtl(Duration.ofSeconds(30));
        properties.setArtifactCleanupInterval(Duration.ofHours(1));
        return properties;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant value) {
            instant = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
