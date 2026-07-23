package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoProbeCapacityGuardTest {

    @TempDir
    Path tempDirectory;

    @Test
    void cumulativeDiskReservationIsEnforcedAndFailedAdmissionIsFullyReleased() {
        VideoProbeProperties properties = properties();
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        // 15 可用 - 5 保底 = 10 探测预算。
        VideoProbeCapacityGuard guard = new VideoProbeCapacityGuard(
                properties, path -> 15L, artifacts, new VideoProbeProcessSupervisor());
        guard.initialize();

        try {
            try (VideoProbeCapacityGuard.Lease first = guard.acquire(6L)) {
                assertThatThrownBy(() -> guard.acquire(5L))
                        .isInstanceOf(BizException.class)
                        .hasMessageContaining("临时空间不足");

                // 前一个失败请求必须同时释放信号量和 5 字节预留；否则 6 + 4 无法获批。
                try (VideoProbeCapacityGuard.Lease second = guard.acquire(4L)) {
                    assertThatThrownBy(() -> guard.acquire(1L))
                            .isInstanceOf(BizException.class)
                            .hasMessageContaining("繁忙");
                }
            }

            // 两个成功租约关闭后，全部预留和并发槽位均可复用。
            try (VideoProbeCapacityGuard.Lease ignored = guard.acquire(10L)) {
                // 取得即为断言。
            }
        } finally {
            artifacts.shutdown();
        }
    }

    @Test
    void alreadyWrittenActiveBytesAreNotCountedTwice() throws Exception {
        VideoProbeProperties properties = properties();
        properties.setMaxConcurrent(3);
        properties.setMaxReservedBytes(4L);
        properties.setMinFreeBytes(1L);
        AtomicLong usable = new AtomicLong(5L);
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        VideoProbeCapacityGuard guard = new VideoProbeCapacityGuard(
                properties, path -> usable.get(), artifacts, new VideoProbeProcessSupervisor());
        guard.initialize();

        try (VideoProbeCapacityGuard.Lease first = guard.acquire(2L);
             VideoProbeTempArtifactManager.Artifact media =
                     artifacts.create(VideoProbeTempArtifactManager.ArtifactKind.MEDIA, ".mp4")) {
            try (OutputStream output = media.openOutputStream()) {
                output.write(new byte[]{1, 2});
                output.flush();
            }
            usable.set(3L);

            // 5 总量 - A 已写 2 = 当前 usable 3；4 总预留 + 1 保底恰好应允许 B。
            try (VideoProbeCapacityGuard.Lease second = guard.acquire(2L)) {
                assertThat(second).isNotNull();
                assertThatThrownBy(() -> guard.acquire(1L))
                        .isInstanceOf(BizException.class)
                        .hasMessageContaining("配额");
            }
        } finally {
            artifacts.shutdown();
        }
    }

    @Test
    void managedWriteCannotInterleaveCapacitySnapshot() throws Exception {
        VideoProbeProperties properties = properties();
        properties.setMaxReservedBytes(4L);
        properties.setMinFreeBytes(1L);
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        CountDownLatch diskReadStarted = new CountDownLatch(1);
        CountDownLatch allowDiskReadToFinish = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (VideoProbeTempArtifactManager.Artifact media =
                     artifacts.create(VideoProbeTempArtifactManager.ArtifactKind.MEDIA, ".mp4");
             OutputStream output = media.openOutputStream()) {
            VideoProbeCapacityGuard guard = new VideoProbeCapacityGuard(
                    properties,
                    path -> {
                        diskReadStarted.countDown();
                        try {
                            if (!allowDiskReadToFinish.await(5, TimeUnit.SECONDS)) {
                                throw new IOException("测试容量快照闸门等待超时");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("测试容量快照闸门被中断", e);
                        }
                        return 5L;
                    },
                    artifacts,
                    new VideoProbeProcessSupervisor());
            guard.initialize();

            Future<VideoProbeCapacityGuard.Lease> acquiring = pool.submit(() -> guard.acquire(4L));
            assertThat(diskReadStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> writing = pool.submit(() -> {
                output.write(new byte[]{1, 2});
                output.flush();
                return null;
            });
            Thread.sleep(100L);
            assertThat(writing.isDone()).isFalse();

            allowDiskReadToFinish.countDown();
            try (VideoProbeCapacityGuard.Lease lease = acquiring.get(5, TimeUnit.SECONDS)) {
                assertThat(lease).isNotNull();
            }
            writing.get(5, TimeUnit.SECONDS);
        } finally {
            allowDiskReadToFinish.countDown();
            pool.shutdownNow();
            artifacts.shutdown();
        }
    }

    @Test
    void liveOrphanBlocksAdmissionUntilItsExitIsObserved() {
        VideoProbeProperties properties = properties();
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        VideoProbeProcessSupervisor supervisor = new VideoProbeProcessSupervisor();
        MutableProcess orphan = new MutableProcess();
        supervisor.registerOrphan(orphan);
        VideoProbeCapacityGuard guard =
                new VideoProbeCapacityGuard(properties, path -> 100L, artifacts, supervisor);
        guard.initialize();
        try {
            assertThatThrownBy(() -> guard.acquire(1L))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("工作进程仍在清理");

            orphan.markExited();
            try (VideoProbeCapacityGuard.Lease lease = guard.acquire(1L)) {
                assertThat(lease).isNotNull();
            }
        } finally {
            artifacts.shutdown();
        }
    }

    @Test
    void orphanRegisteredBetweenCheckAndPermitRejectsLateAdmission() throws Exception {
        VideoProbeProperties properties = properties();
        properties.setMaxConcurrent(1);
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        CoordinatedSupervisor supervisor = new CoordinatedSupervisor();
        VideoProbeCapacityGuard guard =
                new VideoProbeCapacityGuard(properties, path -> 100L, artifacts, supervisor);
        guard.initialize();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        MutableProcess orphan = new MutableProcess();
        VideoProbeCapacityGuard.Lease incumbent = guard.acquire(1L);
        try {
            supervisor.armFirstCheckPause();
            Future<VideoProbeCapacityGuard.Lease> lateAdmission =
                    pool.submit(() -> guard.acquire(1L));
            assertThat(supervisor.awaitFirstCheck()).isTrue();

            supervisor.registerOrphan(orphan);
            incumbent.close();
            supervisor.resumeFirstCheck();

            assertThatThrownBy(() -> lateAdmission.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(BizException.class)
                    .hasRootCauseMessage("视频校验工作进程仍在清理，请稍后重试");

            // 二次检查拒绝时必须归还槽位；孤儿退出后应可立即恢复准入。
            orphan.markExited();
            try (VideoProbeCapacityGuard.Lease recovered = guard.acquire(1L)) {
                assertThat(recovered).isNotNull();
            }
        } finally {
            supervisor.resumeFirstCheck();
            incumbent.close();
            pool.shutdownNow();
            artifacts.shutdown();
        }
    }

    private VideoProbeTempArtifactManager artifacts(VideoProbeProperties properties) {
        VideoProbeTempArtifactManager manager = new VideoProbeTempArtifactManager(properties);
        manager.initialize();
        return manager;
    }

    private VideoProbeProperties properties() {
        VideoProbeProperties properties = new VideoProbeProperties();
        properties.setTempDirectory(tempDirectory);
        properties.setMaxConcurrent(2);
        properties.setMaxReservedBytes(20L);
        properties.setMinFreeBytes(5L);
        properties.setMaxDuration(Duration.ofSeconds(5));
        properties.setMaxPackets(100);
        properties.setLeaseDuration(Duration.ofSeconds(10));
        properties.setLeaseRenewInterval(Duration.ofSeconds(2));
        properties.setWorkerMaxHeapMb(64);
        return properties;
    }

    private static final class MutableProcess extends Process {

        private final AtomicBoolean alive = new AtomicBoolean(true);

        void markExited() {
            alive.set(false);
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            alive.set(false);
            return 0;
        }

        @Override
        public int exitValue() {
            if (alive.get()) {
                throw new IllegalThreadStateException("still alive");
            }
            return 0;
        }

        @Override
        public void destroy() {
            alive.set(false);
        }

        @Override
        public boolean isAlive() {
            return alive.get();
        }

        @Override
        public long pid() {
            return 424_242L;
        }
    }

    private static final class CoordinatedSupervisor extends VideoProbeProcessSupervisor {

        private final AtomicBoolean pauseNextCheck = new AtomicBoolean();
        private final CountDownLatch firstCheckReached = new CountDownLatch(1);
        private final CountDownLatch resumeFirstCheck = new CountDownLatch(1);

        void armFirstCheckPause() {
            pauseNextCheck.set(true);
        }

        boolean awaitFirstCheck() throws InterruptedException {
            return firstCheckReached.await(5, TimeUnit.SECONDS);
        }

        void resumeFirstCheck() {
            resumeFirstCheck.countDown();
        }

        @Override
        public boolean hasLiveOrphans() {
            if (pauseNextCheck.compareAndSet(true, false)) {
                firstCheckReached.countDown();
                try {
                    if (!resumeFirstCheck.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("测试准入闸门等待超时");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("测试准入闸门被中断", e);
                }
                return false;
            }
            return super.hasLiveOrphans();
        }
    }
}
