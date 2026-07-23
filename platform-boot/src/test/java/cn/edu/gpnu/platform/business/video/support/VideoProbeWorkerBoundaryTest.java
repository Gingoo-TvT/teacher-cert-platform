package cn.edu.gpnu.platform.business.video.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoProbeWorkerBoundaryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void wallClockDeadlineForciblyTerminatesBlockedWorkerProcess() {
        VideoProbeProperties properties = properties();
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        VideoProbeProcessRunner runner = new VideoProbeProcessRunner(
                properties, artifacts, new DefaultVideoProbeProcessFactory(),
                new VideoProbeProcessSupervisor());

        try {
            long started = System.nanoTime();
            VideoProbeProcessRunner.ProcessResult result = runner.execute(
                    BlockingWorkerMain.class.getName(), List.of(), Duration.ofMillis(250));
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

            assertThat(result.timedOut()).isTrue();
            assertThat(result.exitCode()).isEqualTo(-1);
            assertThat(ProcessHandle.of(result.processId()).map(ProcessHandle::isAlive).orElse(false)).isFalse();
            assertThat(elapsedMillis).isLessThan(5_000L);
        } finally {
            artifacts.shutdown();
        }
    }

    @Test
    void unconfirmedTerminationIsRegisteredAndReportedAsInfrastructureFailure() {
        VideoProbeProperties properties = properties();
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        VideoProbeProcessSupervisor supervisor = new VideoProbeProcessSupervisor();
        Process refusingProcess = new RefusingTerminationProcess();
        VideoProbeProcessRunner runner = new VideoProbeProcessRunner(
                properties, artifacts, command -> refusingProcess, supervisor);

        try {
            assertThatThrownBy(() -> runner.execute(
                    BlockingWorkerMain.class.getName(), List.of(), Duration.ofMillis(1)))
                    .isInstanceOf(VideoProbeInfrastructureException.class)
                    .hasMessageContaining("无法终止");
            assertThat(supervisor.hasLiveOrphans()).isTrue();
        } finally {
            artifacts.shutdown();
        }
    }

    @Test
    void packetLimitRejectsOtherwiseParseableVideoDeterministically()
            throws IOException, URISyntaxException {
        VideoMediaWorker.TrackInspection result = VideoProbeWorkerMain.inspectTrack(
                sampleVideo(), Set.of("H264"), 2, 1);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("安全上限");
    }

    @Test
    void multipleVideoTracksAreRejectedBeforeFrameDecode()
            throws IOException, URISyntaxException {
        byte[] original = Files.readAllBytes(sampleVideo());
        Path duplicatedTrack = tempDirectory.resolve("two-video-tracks.mp4");
        Files.write(duplicatedTrack, duplicateFirstTrack(original));

        VideoMediaWorker.TrackInspection result = VideoProbeWorkerMain.inspectTrack(
                duplicatedTrack, Set.of("H264"), 2, 100_000);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("只能包含一个视频轨道");
    }

    @Test
    void malformedMp4IsReportedAsStructuredContentFailure() throws IOException {
        Path malformed = Files.write(tempDirectory.resolve("malformed.mp4"),
                "....ftypmp42-not-a-real-video-mdat"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        VideoMediaWorker.TrackInspection result = VideoProbeWorkerMain.inspectTrack(
                malformed, Set.of("H264"), 2, 100_000);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("可解析的MP4");
    }

    private Path sampleVideo() throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource("media/short-video.mp4").toURI());
    }

    private byte[] duplicateFirstTrack(byte[] source) {
        Box moov = findBox(source, 0, source.length, "moov");
        Box track = findBox(source, moov.offset() + moov.headerSize(),
                moov.offset() + moov.size(), "trak");
        int insertion = moov.offset() + moov.size();
        byte[] duplicated = new byte[source.length + track.size()];
        System.arraycopy(source, 0, duplicated, 0, insertion);
        System.arraycopy(source, track.offset(), duplicated, insertion, track.size());
        System.arraycopy(source, insertion, duplicated, insertion + track.size(), source.length - insertion);
        ByteBuffer.wrap(duplicated, moov.offset(), 4)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(moov.size() + track.size());
        return duplicated;
    }

    private Box findBox(byte[] source, int from, int limit, String type) {
        int offset = from;
        while (offset + 8 <= limit) {
            long declaredSize = Integer.toUnsignedLong(
                    ByteBuffer.wrap(source, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt());
            int headerSize = 8;
            long resolvedSize = declaredSize;
            if (declaredSize == 1L) {
                if (offset + 16 > limit) {
                    throw new IllegalArgumentException("非法 MP4 扩展 box");
                }
                resolvedSize = ByteBuffer.wrap(source, offset + 8, 8)
                        .order(ByteOrder.BIG_ENDIAN).getLong();
                headerSize = 16;
            } else if (declaredSize == 0L) {
                resolvedSize = limit - offset;
            }
            if (resolvedSize < headerSize || resolvedSize > Integer.MAX_VALUE
                    || offset + resolvedSize > limit) {
                throw new IllegalArgumentException("非法 MP4 box");
            }
            int size = (int) resolvedSize;
            String actual = new String(source, offset + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
            if (type.equals(actual)) {
                return new Box(offset, size, headerSize);
            }
            offset += size;
        }
        throw new IllegalArgumentException("未找到 MP4 box: " + type);
    }

    private VideoProbeProperties properties() {
        VideoProbeProperties properties = new VideoProbeProperties();
        properties.setTempDirectory(tempDirectory);
        properties.setWorkerMaxHeapMb(64);
        return properties;
    }

    private VideoProbeTempArtifactManager artifacts(VideoProbeProperties properties) {
        VideoProbeTempArtifactManager manager = new VideoProbeTempArtifactManager(properties);
        manager.initialize();
        return manager;
    }

    private record Box(int offset, int size, int headerSize) {
    }

    public static final class BlockingWorkerMain {

        private BlockingWorkerMain() {
        }

        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(Duration.ofMinutes(5).toMillis());
        }
    }

    private static final class RefusingTerminationProcess extends Process {

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public java.io.InputStream getInputStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public java.io.InputStream getErrorStream() {
            return java.io.InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public int exitValue() {
            throw new IllegalThreadStateException("still running");
        }

        @Override
        public void destroy() {
            // 模拟运行时拒绝终止。
        }

        @Override
        public Process destroyForcibly() {
            return this;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public long pid() {
            return Long.MAX_VALUE;
        }
    }
}
