package cn.edu.gpnu.platform.business.video.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
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

    @Test
    void jcodecInternalIOExceptionIsReportedAsStructuredContentFailure() throws IOException {
        Path malformed = Files.write(
                tempDirectory.resolve("jcodec-io-malformed.mp4"), jcodecIOExceptionFixture());

        assertThat(VideoProbeWorkerMain.hasSaneTopLevelBoxes(malformed)).isTrue();
        VideoMediaWorker.TrackInspection result = VideoProbeWorkerMain.inspectTrack(
                malformed, Set.of("H264"), 2, 100_000);

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("可解析的MP4");
    }

    @Test
    void realWorkerReturnsExitZeroAndStructuredInvalidForJcodecIOException() throws Exception {
        VideoProbeProperties properties = properties();
        VideoProbeTempArtifactManager artifacts = artifacts(properties);
        VideoProbeProcessRunner runner = new VideoProbeProcessRunner(
                properties, artifacts, new DefaultVideoProbeProcessFactory(),
                new VideoProbeProcessSupervisor());
        Path malformed = Files.write(
                tempDirectory.resolve("worker-jcodec-io.mp4"), jcodecIOExceptionFixture());

        try (VideoProbeTempArtifactManager.Artifact resultArtifact =
                     artifacts.create(VideoProbeTempArtifactManager.ArtifactKind.RESULT, ".properties")) {
            VideoProbeProcessRunner.ProcessResult process = runner.execute(
                    VideoProbeWorkerMain.class.getName(),
                    List.of(
                            malformed.toString(),
                            resultArtifact.path().toString(),
                            "H264",
                            "2",
                            "100000"),
                    Duration.ofSeconds(10));

            assertThat(process.timedOut()).isFalse();
            assertThat(process.exitCode()).isZero();
            Properties result = new Properties();
            try (Reader reader = Files.newBufferedReader(
                    resultArtifact.path(), StandardCharsets.UTF_8)) {
                result.load(reader);
            }
            assertThat(result.getProperty("valid")).isEqualTo("false");
            assertThat(result.getProperty("message")).contains("可解析的MP4");
        } finally {
            artifacts.shutdown();
        }
    }

    @Test
    void missingMediaAndSourceChannelIoRemainInfrastructureIo() {
        Path missing = tempDirectory.resolve("missing.mp4");
        assertThatThrownBy(() -> VideoProbeWorkerMain.inspectTrack(
                missing, Set.of("H264"), 2, 100_000))
                .isInstanceOf(IOException.class);

        VideoProbeWorkerMain.TrackingSeekableByteChannel failing =
                new VideoProbeWorkerMain.TrackingSeekableByteChannel(
                        new FailingReadSeekableByteChannel());
        assertThatThrownBy(() -> VideoProbeWorkerMain.inspectParsedMedia(
                failing, Set.of("H264"), 2, 100_000))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("simulated source I/O");
        assertThat(failing.sourceIoFailed()).isTrue();
    }

    @Test
    void watchedWorkerExitsAfterItsParentJvmHalts() throws Exception {
        Path state = tempDirectory.resolve("watched-child.properties");
        Path ready = tempDirectory.resolve("watched-child.ready");
        Path stdout = tempDirectory.resolve("parent-harness.stdout.log");
        Path stderr = tempDirectory.resolve("parent-harness.stderr.log");
        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        command.add("-cp");
        command.add(testClassPath());
        command.add(ParentCrashHarnessMain.class.getName());
        command.add(state.toString());
        command.add(ready.toString());
        Process parent = new ProcessBuilder(command)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();

        Long childPid = null;
        Instant childStartedAt = null;
        try {
            assertThat(parent.waitFor(15, TimeUnit.SECONDS))
                    .as("父进程故障 harness 应自然结束: %s", readQuietly(stderr))
                    .isTrue();
            assertThat(parent.exitValue()).isZero();
            assertThat(state).isNotEmptyFile();
            Properties values = new Properties();
            try (Reader reader = Files.newBufferedReader(state, StandardCharsets.UTF_8)) {
                values.load(reader);
            }
            childPid = Long.valueOf(values.getProperty("pid"));
            childStartedAt = Instant.parse(values.getProperty("startedAt"));
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (sameLiveProcess(childPid, childStartedAt) && System.nanoTime() < deadline) {
                Thread.sleep(50L);
            }
            assertThat(sameLiveProcess(childPid, childStartedAt))
                    .as("父 JVM halt 后受监控 worker 必须自行退出")
                    .isFalse();
        } finally {
            if (childPid != null && childStartedAt != null
                    && sameLiveProcess(childPid, childStartedAt)) {
                ProcessHandle.of(childPid).ifPresent(ProcessHandle::destroyForcibly);
            }
            if (parent.isAlive()) {
                parent.destroyForcibly();
            }
        }
    }

    private Path sampleVideo() throws URISyntaxException {
        return Path.of(getClass().getClassLoader().getResource("media/short-video.mp4").toURI());
    }

    private Path javaExecutable() {
        String name = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                .contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", name);
    }

    private String testClassPath() {
        return System.getProperty(
                "surefire.test.class.path", System.getProperty("java.class.path"));
    }

    private boolean sameLiveProcess(long pid, Instant startedAt) {
        return ProcessHandle.of(pid)
                .filter(ProcessHandle::isAlive)
                .flatMap(handle -> handle.info().startInstant())
                .map(startedAt::equals)
                .orElse(false);
    }

    private String readQuietly(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (IOException ignored) {
            return "";
        }
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

    private byte[] jcodecIOExceptionFixture() {
        ByteBuffer fixture = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN);
        fixture.putInt(24).put("ftyp".getBytes(StandardCharsets.US_ASCII));
        fixture.put("isom".getBytes(StandardCharsets.US_ASCII));
        fixture.putInt(0x200);
        fixture.put("isom".getBytes(StandardCharsets.US_ASCII));
        fixture.put("iso2".getBytes(StandardCharsets.US_ASCII));
        fixture.putInt(8).put("mdat".getBytes(StandardCharsets.US_ASCII));
        fixture.putInt(0).put("moov".getBytes(StandardCharsets.US_ASCII));
        fixture.put("junk".getBytes(StandardCharsets.US_ASCII));
        return fixture.array();
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

    public static final class ParentCrashHarnessMain {

        private ParentCrashHarnessMain() {
        }

        public static void main(String[] args) throws Exception {
            Path state = Path.of(args[0]);
            Path ready = Path.of(args[1]);
            List<String> command = new ArrayList<>();
            command.add(Path.of(System.getProperty("java.home"), "bin",
                    System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                            .contains("win") ? "java.exe" : "java").toString());
            command.addAll(VideoProbeParentWatchdog.jvmArgumentsForCurrentParent(
                    Duration.ofMillis(100)));
            command.add("-cp");
            command.add(System.getProperty(
                    "surefire.test.class.path", System.getProperty("java.class.path")));
            command.add(WatchedBlockingWorkerMain.class.getName());
            command.add(ready.toString());
            Process child = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (!Files.exists(ready) && child.isAlive() && System.nanoTime() < deadline) {
                Thread.sleep(20L);
            }
            if (!Files.exists(ready) || !child.isAlive()) {
                child.destroyForcibly();
                throw new IllegalStateException("受监控 worker 未就绪");
            }
            Instant childStartedAt = child.info().startInstant()
                    .orElseThrow(() -> new IllegalStateException("无法取得 child 启动令牌"));
            Properties values = new Properties();
            values.setProperty("pid", String.valueOf(child.pid()));
            values.setProperty("startedAt", childStartedAt.toString());
            try (java.io.Writer writer = Files.newBufferedWriter(state, StandardCharsets.UTF_8)) {
                values.store(writer, null);
            }
            Runtime.getRuntime().halt(0);
        }
    }

    public static final class WatchedBlockingWorkerMain {

        private WatchedBlockingWorkerMain() {
        }

        public static void main(String[] args) throws Exception {
            try (VideoProbeParentWatchdog ignored =
                         VideoProbeParentWatchdog.startFromSystemProperties()) {
                Files.writeString(Path.of(args[0]), "ready", StandardCharsets.UTF_8);
                Thread.sleep(Duration.ofMinutes(5).toMillis());
            }
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

    private static final class FailingReadSeekableByteChannel
            implements org.jcodec.common.io.SeekableByteChannel {

        private boolean open = true;

        @Override
        public int read(ByteBuffer destination) throws IOException {
            throw new IOException("simulated source I/O");
        }

        @Override
        public int write(ByteBuffer source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long position() {
            return 0;
        }

        @Override
        public org.jcodec.common.io.SeekableByteChannel setPosition(long newPosition) {
            return this;
        }

        @Override
        public long size() {
            return 44;
        }

        @Override
        public org.jcodec.common.io.SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
