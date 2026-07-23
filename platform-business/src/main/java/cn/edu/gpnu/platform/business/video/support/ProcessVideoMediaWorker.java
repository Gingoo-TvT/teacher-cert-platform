package cn.edu.gpnu.platform.business.video.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ProcessVideoMediaWorker implements VideoMediaWorker {

    private final VideoProbeProcessRunner processRunner;

    @Override
    public TrackInspection inspect(Path mediaFile, Set<String> allowedCodecs,
                                   int timelineToleranceSeconds, int maxPackets, Duration timeout) {
        Path resultFile = null;
        try {
            resultFile = Files.createTempFile(mediaFile.getParent(), "video-worker-result-", ".properties");
            VideoProbeProcessRunner.ProcessResult processResult = processRunner.execute(
                    VideoProbeWorkerMain.class.getName(),
                    List.of(
                            mediaFile.toAbsolutePath().normalize().toString(),
                            resultFile.toAbsolutePath().normalize().toString(),
                            String.join(",", allowedCodecs),
                            String.valueOf(timelineToleranceSeconds),
                            String.valueOf(maxPackets)),
                    timeout);
            if (processResult.timedOut()) {
                return TrackInspection.invalid("视频媒体探测超过系统时限", null, null, null);
            }
            if (processResult.exitCode() != 0 || Files.size(resultFile) == 0L) {
                // 畸形媒体可能触发工作 JVM 的受限堆 OOM/硬退出；对业务仍按不可解析媒体 fail closed。
                return TrackInspection.invalid("视频文件不是可解析的MP4媒体", null, null, null);
            }
            Properties result = new Properties();
            try (Reader reader = Files.newBufferedReader(resultFile, StandardCharsets.UTF_8)) {
                result.load(reader);
            }
            boolean valid = Boolean.parseBoolean(result.getProperty("valid", "false"));
            Integer duration = integer(result.getProperty("durationSeconds"));
            Integer frameCount = integer(result.getProperty("frameCount"));
            String codec = blankToNull(result.getProperty("codec"));
            String message = blankToNull(result.getProperty("message"));
            return valid
                    ? TrackInspection.valid(duration == null ? 0 : duration, codec, frameCount == null ? 0 : frameCount)
                    : TrackInspection.invalid(
                            message == null ? "视频文件不是可解析的MP4媒体" : message,
                            duration, codec, frameCount);
        } catch (IOException | RuntimeException e) {
            return TrackInspection.invalid("视频媒体探测工作进程异常", null, null, null);
        } finally {
            if (resultFile != null) {
                try {
                    Files.deleteIfExists(resultFile);
                } catch (IOException ignored) {
                    // 独立临时卷由生命周期清理兜底。
                }
            }
        }
    }

    private Integer integer(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
