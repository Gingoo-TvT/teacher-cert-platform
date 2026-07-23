package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.file.service.MultipartObjectService;
import cn.edu.gpnu.platform.system.service.ParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class JcodecVideoMediaProbe implements VideoMediaProbe {

    static final String PROBE_VERSION = "JCODEC_PROCESS_V3";
    private static final int FINGERPRINT_CHUNK_SIZE = 8 * 1024 * 1024;
    private static final byte[] FINGERPRINT_MARKER =
            "teacher-cert-file-sha256-tree-v1\0".getBytes(StandardCharsets.UTF_8);
    private static final String DEFAULT_ALLOWED_CODECS = "H264";
    private static final int DEFAULT_TIMELINE_TOLERANCE_SECONDS = 2;

    private final MultipartObjectService multipartObjectService;
    private final ParamService paramService;
    private final VideoProbeProperties properties;
    private final VideoMediaWorker mediaWorker;

    @Override
    public VideoMediaInspection inspect(String objectKey, long expectedSize, String declaredFingerprint) {
        MediaPolicy policy = currentPolicy();
        if (expectedSize < 1) {
            return invalid(policy, "视频对象大小不合法", null, null, null, null);
        }
        Path temporaryFile = null;
        String fingerprint = null;
        long deadlineNanos = deadlineNanos();
        try {
            Path directory = properties.getTempDirectory().toAbsolutePath().normalize();
            Files.createDirectories(directory);
            temporaryFile = Files.createTempFile(directory, "video-probe-", ".mp4");
            fingerprint = downloadAndFingerprint(objectKey, expectedSize, temporaryFile, deadlineNanos).value();
            // 旧 8/32 位摘要仅用于同一会话恢复且不参与秒传；新版 64 位摘要必须与服务端计算值一致。
            if (StringUtils.hasText(declaredFingerprint)
                    && declaredFingerprint.trim().length() == 64
                    && !fingerprint.equalsIgnoreCase(declaredFingerprint.trim())) {
                return invalid(policy, "视频指纹与服务端对象内容不一致",
                        fingerprint, null, null, null);
            }
            VideoMediaWorker.TrackInspection track = mediaWorker.inspect(
                    temporaryFile, policy.allowedCodecs(), policy.timelineToleranceSeconds(),
                    properties.getMaxPackets(), remainingDuration(deadlineNanos));
            return track.valid()
                    ? VideoMediaInspection.valid(fingerprint, track.durationSeconds(), track.codec(),
                            track.frameCount(), policy.hash(), PROBE_VERSION)
                    : invalid(policy, track.message(), fingerprint, track.durationSeconds(),
                            track.codec(), track.frameCount());
        } catch (ProbeLimitException e) {
            return invalid(policy, e.getMessage(), fingerprint, null, null, null);
        } catch (IOException | RuntimeException e) {
            log.warn("视频对象探测失败: {}", e.getClass().getSimpleName());
            return invalid(policy, "读取或解析视频对象失败", fingerprint, null, null, null);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException e) {
                    log.warn("清理视频探测临时文件失败");
                }
            }
        }
    }

    @Override
    public String currentPolicyHash() {
        return currentPolicy().hash();
    }

    @Override
    public String probeVersion() {
        return PROBE_VERSION;
    }

    private FingerprintResult downloadAndFingerprint(String objectKey, long expectedSize, Path target,
                                                     long deadlineNanos) throws IOException {
        MessageDigest leafDigest = sha256();
        List<byte[]> leafDigests = new ArrayList<>();
        long totalBytes = 0L;
        int currentLeafBytes = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = multipartObjectService.openObject(objectKey);
             OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                ensureWithinDeadline(deadlineNanos);
                if (read == 0) {
                    continue;
                }
                totalBytes += read;
                if (totalBytes > expectedSize) {
                    throw new ProbeLimitException("视频对象实际大小与上传会话不一致");
                }
                output.write(buffer, 0, read);
                int offset = 0;
                while (offset < read) {
                    int digestBytes = Math.min(read - offset, FINGERPRINT_CHUNK_SIZE - currentLeafBytes);
                    leafDigest.update(buffer, offset, digestBytes);
                    currentLeafBytes += digestBytes;
                    offset += digestBytes;
                    if (currentLeafBytes == FINGERPRINT_CHUNK_SIZE) {
                        leafDigests.add(leafDigest.digest());
                        currentLeafBytes = 0;
                    }
                }
            }
        }
        if (totalBytes != expectedSize) {
            throw new ProbeLimitException("视频对象实际大小与上传会话不一致");
        }
        if (currentLeafBytes > 0) {
            leafDigests.add(leafDigest.digest());
        }
        ByteBuffer root = ByteBuffer.allocate(FINGERPRINT_MARKER.length + 24 + leafDigests.size() * 32);
        root.put(FINGERPRINT_MARKER);
        root.putLong(totalBytes);
        root.putLong(FINGERPRINT_CHUNK_SIZE);
        root.putLong(leafDigests.size());
        leafDigests.forEach(root::put);
        return new FingerprintResult(hex(sha256().digest(root.array())));
    }

    private MediaPolicy currentPolicy() {
        Set<String> codecs = new TreeSet<>();
        String configured = paramService.getString("video.allowedCodecs", DEFAULT_ALLOWED_CODECS);
        for (String value : configured.split(",")) {
            if (StringUtils.hasText(value)) {
                codecs.add(value.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (codecs.isEmpty()) {
            codecs.add(DEFAULT_ALLOWED_CODECS);
        }
        int timelineTolerance = paramService.getInt(
                "video.timelineToleranceSeconds", DEFAULT_TIMELINE_TOLERANCE_SECONDS);
        if (timelineTolerance < 0) {
            timelineTolerance = DEFAULT_TIMELINE_TOLERANCE_SECONDS;
        }
        String canonical = PROBE_VERSION
                + "|singleVideoTrack=true|allowedCodecs=" + String.join(",", codecs)
                + "|timelineToleranceSeconds=" + timelineTolerance;
        return new MediaPolicy(Set.copyOf(codecs), timelineTolerance,
                hex(sha256().digest(canonical.getBytes(StandardCharsets.UTF_8))));
    }

    private VideoMediaInspection invalid(MediaPolicy policy, String message, String fingerprint,
                                         Integer durationSeconds, String codec, Integer frameCount) {
        return VideoMediaInspection.invalid(message, fingerprint, durationSeconds, codec, frameCount,
                policy.hash(), PROBE_VERSION);
    }

    private long deadlineNanos() {
        long durationNanos = properties.getMaxDuration().toNanos();
        long now = System.nanoTime();
        return durationNanos >= Long.MAX_VALUE - now ? Long.MAX_VALUE : now + durationNanos;
    }

    private void ensureWithinDeadline(long deadlineNanos) {
        if (System.nanoTime() - deadlineNanos >= 0) {
            throw new ProbeLimitException("视频媒体探测超过系统时限");
        }
    }

    private Duration remainingDuration(long deadlineNanos) {
        ensureWithinDeadline(deadlineNanos);
        return Duration.ofNanos(Math.max(1L, deadlineNanos - System.nanoTime()));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM不支持SHA-256", e);
        }
    }

    private String hex(byte[] digest) {
        StringBuilder value = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            value.append(String.format("%02x", item & 0xff));
        }
        return value.toString();
    }

    private record FingerprintResult(String value) {
    }

    private record MediaPolicy(Set<String> allowedCodecs, int timelineToleranceSeconds, String hash) {
    }

    private static final class ProbeLimitException extends RuntimeException {

        private ProbeLimitException(String message) {
            super(message);
        }
    }
}
