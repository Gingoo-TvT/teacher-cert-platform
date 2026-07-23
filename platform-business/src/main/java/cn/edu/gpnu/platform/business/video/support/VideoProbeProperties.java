package cn.edu.gpnu.platform.business.video.support;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

/**
 * 媒体探测的进程级资源边界。业务格式、编码和时长策略仍由 sys_param 管理。
 */
@Component
@ConfigurationProperties(prefix = "platform.video.probe")
@Data
public class VideoProbeProperties {

    private Path tempDirectory =
            Path.of(System.getProperty("java.io.tmpdir"), "teacher-cert-video-probe");
    private int maxConcurrent = 2;
    private long maxReservedBytes = 4_294_967_296L;
    private long minFreeBytes = 1_073_741_824L;
    private Duration maxDuration = Duration.ofMinutes(10);
    private int maxPackets = 2_000_000;
    private Duration leaseDuration = Duration.ofMinutes(2);
    private Duration leaseRenewInterval = Duration.ofSeconds(30);
    private int workerMaxHeapMb = 256;
    private Duration artifactHeartbeatInterval = Duration.ofSeconds(30);
    private Duration artifactOwnerStaleAfter = Duration.ofMinutes(2);
    private Duration artifactOrphanTtl = Duration.ofMinutes(30);
    private Duration artifactLegacyOrphanTtl = Duration.ofHours(24);
    private Duration artifactCleanupInterval = Duration.ofMinutes(5);
    private int artifactCleanupScanLimit = 10_000;
}
