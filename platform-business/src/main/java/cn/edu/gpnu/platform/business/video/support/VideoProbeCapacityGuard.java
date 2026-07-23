package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 在上传会话进入 MERGING 前预留媒体探测的并发槽位与临时盘预算。
 */
@Component
@RequiredArgsConstructor
public class VideoProbeCapacityGuard {

    private final VideoProbeProperties properties;
    private final VideoProbeDiskSpace diskSpace;
    private final VideoProbeTempArtifactManager artifactManager;
    private final VideoProbeProcessSupervisor processSupervisor;
    private final AtomicLong reservedBytes = new AtomicLong();
    private Semaphore permits;

    @PostConstruct
    void initialize() {
        if (properties.getMaxConcurrent() < 1
                || properties.getMaxReservedBytes() < 1
                || properties.getMinFreeBytes() < 0
                || properties.getMaxDuration() == null
                || properties.getMaxDuration().isNegative()
                || properties.getMaxDuration().isZero()
                || properties.getMaxPackets() < 1
                || properties.getLeaseDuration() == null
                || properties.getLeaseDuration().isNegative()
                || properties.getLeaseDuration().isZero()
                || properties.getLeaseRenewInterval() == null
                || properties.getLeaseRenewInterval().isNegative()
                || properties.getLeaseRenewInterval().isZero()
                || properties.getLeaseRenewInterval().multipliedBy(2)
                        .compareTo(properties.getLeaseDuration()) >= 0
                || properties.getWorkerMaxHeapMb() < 64) {
            throw new IllegalStateException("视频媒体探测资源配置不合法");
        }
        permits = new Semaphore(properties.getMaxConcurrent(), true);
    }

    public Lease acquire(long expectedBytes) {
        if (processSupervisor.hasLiveOrphans()) {
            throw new BizException("视频校验工作进程仍在清理，请稍后重试");
        }
        if (expectedBytes < 1 || expectedBytes > properties.getMaxReservedBytes()) {
            throw new BizException("视频校验所需临时空间超过系统配额");
        }
        if (!permits.tryAcquire()) {
            throw new BizException("视频校验任务繁忙，请稍后重试");
        }
        // 首次检查与信号量获取之间，旧 worker 可能刚登记为孤儿并释放其租约。
        // 取得槽位后必须再次确认，避免迟到请求复用该槽位启动第二个实际进程。
        if (processSupervisor.hasLiveOrphans()) {
            permits.release();
            throw new BizException("视频校验工作进程仍在清理，请稍后重试");
        }
        boolean reserved = false;
        try {
            long totalReserved = reserveBytes(expectedBytes);
            reserved = true;
            Path directory = properties.getTempDirectory().toAbsolutePath().normalize();
            Files.createDirectories(directory);
            VideoProbeTempArtifactManager.DiskSnapshot diskSnapshot =
                    artifactManager.diskSnapshot(directory, diskSpace);
            long writtenByActiveProbes = diskSnapshot.activeMediaBytes();
            long usable = diskSnapshot.usableBytes();
            long usableForProbes = usable <= properties.getMinFreeBytes()
                    ? 0L : usable - properties.getMinFreeBytes();
            // usable 已扣除活跃媒体文件的已写字节；只比较尚未落盘的未来增长，避免同一字节重复计数。
            long futureGrowth = totalReserved <= writtenByActiveProbes
                    ? 0L : totalReserved - writtenByActiveProbes;
            if (futureGrowth > usableForProbes) {
                throw new BizException("视频校验临时空间不足，请稍后重试");
            }
            return new Lease(this, expectedBytes);
        } catch (IOException e) {
            if (reserved) {
                reservedBytes.addAndGet(-expectedBytes);
            }
            permits.release();
            throw new BizException("视频校验临时目录不可用");
        } catch (RuntimeException e) {
            if (reserved) {
                reservedBytes.addAndGet(-expectedBytes);
            }
            permits.release();
            throw e;
        }
    }

    private long reserveBytes(long expectedBytes) {
        while (true) {
            long current = reservedBytes.get();
            if (expectedBytes > properties.getMaxReservedBytes() - current) {
                throw new BizException("视频校验临时空间配额已满，请稍后重试");
            }
            long updated = current + expectedBytes;
            if (reservedBytes.compareAndSet(current, updated)) {
                return updated;
            }
        }
    }

    private void release(long expectedBytes) {
        reservedBytes.addAndGet(-expectedBytes);
        permits.release();
    }

    public static final class Lease implements AutoCloseable {

        private final VideoProbeCapacityGuard owner;
        private final long reservedBytes;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(VideoProbeCapacityGuard owner, long reservedBytes) {
            this.owner = owner;
            this.reservedBytes = reservedBytes;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owner.release(reservedBytes);
            }
        }
    }
}
