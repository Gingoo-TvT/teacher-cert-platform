package cn.edu.gpnu.platform.business.video.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessVideoMediaWorkerTest {

    @TempDir
    Path tempDirectory;

    @Test
    void nonZeroWorkerExitIsRetryableInfrastructureFailure() throws Exception {
        VideoProbeProperties properties = properties();
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        VideoProbeProcessRunner runner = mock(VideoProbeProcessRunner.class);
        when(runner.execute(anyString(), any(), any()))
                .thenReturn(new VideoProbeProcessRunner.ProcessResult(7, false, 123L));
        ProcessVideoMediaWorker worker = new ProcessVideoMediaWorker(runner, artifacts);
        Path media = Files.write(tempDirectory.resolve("sample.mp4"), new byte[]{1});

        try {
            assertThatThrownBy(() -> worker.inspect(media, Set.of("H264"), 2, 100, Duration.ofSeconds(1)))
                    .isInstanceOf(VideoProbeInfrastructureException.class)
                    .hasMessageContaining("异常退出");
        } finally {
            artifacts.shutdown();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void structuredInvalidResultRemainsContentValidationFailure() throws Exception {
        VideoProbeProperties properties = properties();
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        VideoProbeProcessRunner runner = mock(VideoProbeProcessRunner.class);
        when(runner.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            List<String> arguments = invocation.getArgument(1);
            Files.writeString(Path.of(arguments.get(1)), "valid=false\nmessage=视频编码不在允许范围\n");
            return new VideoProbeProcessRunner.ProcessResult(0, false, 124L);
        });
        ProcessVideoMediaWorker worker = new ProcessVideoMediaWorker(runner, artifacts);
        Path media = Files.write(tempDirectory.resolve("invalid.mp4"), new byte[]{1});

        try {
            VideoMediaWorker.TrackInspection result =
                    worker.inspect(media, Set.of("H264"), 2, 100, Duration.ofSeconds(1));
            assertThat(result.valid()).isFalse();
            assertThat(result.message()).contains("编码");
        } finally {
            artifacts.shutdown();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingGarbageOrTruncatedValidityIsInfrastructureFailure() throws Exception {
        VideoProbeProperties properties = properties();
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        VideoProbeProcessRunner runner = mock(VideoProbeProcessRunner.class);
        AtomicReference<String> payload = new AtomicReference<>();
        when(runner.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            List<String> arguments = invocation.getArgument(1);
            Files.writeString(Path.of(arguments.get(1)), payload.get());
            return new VideoProbeProcessRunner.ProcessResult(0, false, 125L);
        });
        ProcessVideoMediaWorker worker = new ProcessVideoMediaWorker(runner, artifacts);
        Path media = Files.write(tempDirectory.resolve("malformed-result.mp4"), new byte[]{1});

        try {
            for (String malformed : List.of(
                    "message=missing-valid\n",
                    "valid=garbage\nmessage=bad-valid\n",
                    "valid=false\n")) {
                payload.set(malformed);
                assertThatThrownBy(() ->
                        worker.inspect(media, Set.of("H264"), 2, 100, Duration.ofSeconds(1)))
                        .isInstanceOf(VideoProbeInfrastructureException.class)
                        .hasMessageContaining("结果不完整");
            }
        } finally {
            artifacts.shutdown();
        }
    }

    private VideoProbeProperties properties() {
        VideoProbeProperties properties = new VideoProbeProperties();
        properties.setTempDirectory(tempDirectory);
        properties.setMaxDuration(Duration.ofSeconds(5));
        return properties;
    }

    private VideoProbeTempArtifactManager artifacts(VideoProbeProperties properties) {
        VideoProbeTempArtifactManager manager = new VideoProbeTempArtifactManager(properties);
        manager.initialize();
        return manager;
    }
}
