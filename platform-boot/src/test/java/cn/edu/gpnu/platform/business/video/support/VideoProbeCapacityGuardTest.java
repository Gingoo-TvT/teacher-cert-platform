package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoProbeCapacityGuardTest {

    @TempDir
    Path tempDirectory;

    @Test
    void cumulativeDiskReservationIsEnforcedAndFailedAdmissionIsFullyReleased() {
        VideoProbeProperties properties = properties();
        // 15 可用 - 5 保底 = 10 探测预算。
        VideoProbeCapacityGuard guard = new VideoProbeCapacityGuard(properties, path -> 15L);
        guard.initialize();

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
}
