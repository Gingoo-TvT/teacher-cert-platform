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
    private final VideoProbeTempArtifactManager artifactManager;

    @Override
    public TrackInspection inspect(Path mediaFile, Set<String> allowedCodecs,
                                   int timelineToleranceSeconds, int maxPackets, Duration timeout) {
        try (VideoProbeTempArtifactManager.Artifact artifact =
                     artifactManager.create(VideoProbeTempArtifactManager.ArtifactKind.RESULT, ".properties")) {
            Path resultFile = artifact.path();
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
                throw new VideoProbeTimeoutException("视频媒体探测超过系统时限，请稍后重试");
            }
            if (processResult.exitCode() != 0 || Files.size(resultFile) == 0L) {
                throw new VideoProbeInfrastructureException("视频媒体探测工作进程异常退出，请稍后重试");
            }
            Properties result = new Properties();
            try (Reader reader = Files.newBufferedReader(resultFile, StandardCharsets.UTF_8)) {
                result.load(reader);
            }
            String validValue = blankToNull(result.getProperty("valid"));
            if (!"true".equals(validValue) && !"false".equals(validValue)) {
                throw malformedResult();
            }
            boolean valid = "true".equals(validValue);
            Integer duration = integer(result.getProperty("durationSeconds"));
            Integer frameCount = integer(result.getProperty("frameCount"));
            String codec = blankToNull(result.getProperty("codec"));
            String message = blankToNull(result.getProperty("message"));
            if (valid) {
                if (duration == null || duration < 1 || codec == null
                        || frameCount == null || frameCount < 1 || message != null) {
                    throw malformedResult();
                }
                return TrackInspection.valid(duration, codec, frameCount);
            }
            if (message == null) {
                throw malformedResult();
            }
            return TrackInspection.invalid(message, duration, codec, frameCount);
        } catch (VideoProbeTimeoutException | VideoProbeInfrastructureException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new VideoProbeInfrastructureException("视频媒体探测工作进程异常，请稍后重试", e);
        }
    }

    private Integer integer(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private VideoProbeInfrastructureException malformedResult() {
        return new VideoProbeInfrastructureException("视频媒体探测工作进程结果不完整，请稍后重试");
    }
}
