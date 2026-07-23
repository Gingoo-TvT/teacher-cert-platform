package cn.edu.gpnu.platform.business.video.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VideoProbeWorkerBoundaryTest {

    @TempDir
    Path tempDirectory;

    @Test
    void wallClockDeadlineForciblyTerminatesBlockedWorkerProcess() {
        VideoProbeProperties properties = properties();
        VideoProbeProcessRunner runner = new VideoProbeProcessRunner(properties);

        long started = System.nanoTime();
        VideoProbeProcessRunner.ProcessResult result = runner.execute(
                BlockingWorkerMain.class.getName(), List.of(), Duration.ofMillis(250));
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

        assertThat(result.timedOut()).isTrue();
        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(elapsedMillis).isLessThan(5_000L);
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

    private record Box(int offset, int size, int headerSize) {
    }

    public static final class BlockingWorkerMain {

        private BlockingWorkerMain() {
        }

        public static void main(String[] args) throws InterruptedException {
            Thread.sleep(Duration.ofMinutes(5).toMillis());
        }
    }
}
