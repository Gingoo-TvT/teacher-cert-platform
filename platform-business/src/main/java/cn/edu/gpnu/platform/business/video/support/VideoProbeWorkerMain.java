package cn.edu.gpnu.platform.business.video.support;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.DemuxerTrack;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Picture;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;

import java.io.IOException;
import java.io.Writer;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 独立媒体探测 JVM 入口。主进程只等待结构化结果，墙钟超时后可直接强杀本进程。
 */
public final class VideoProbeWorkerMain {

    private VideoProbeWorkerMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("视频探测工作进程参数不完整");
        }
        Path mediaFile = Path.of(args[0]).toAbsolutePath().normalize();
        Path resultFile = Path.of(args[1]).toAbsolutePath().normalize();
        Set<String> allowedCodecs = Arrays.stream(args[2].split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        int timelineToleranceSeconds = Integer.parseInt(args[3]);
        int maxPackets = Integer.parseInt(args[4]);
        VideoMediaWorker.TrackInspection inspection =
                inspectTrack(mediaFile, allowedCodecs, timelineToleranceSeconds, maxPackets);
        writeResult(resultFile, inspection);
    }

    static VideoMediaWorker.TrackInspection inspectTrack(Path mediaFile, Set<String> allowedCodecs,
                                                         int timelineToleranceSeconds, int maxPackets)
            throws IOException {
        if (!hasSaneTopLevelBoxes(mediaFile)) {
            return invalid("视频文件不是可解析的MP4媒体", null, null, null);
        }
        try (SeekableByteChannel channel =
                     org.jcodec.common.io.NIOUtils.readableChannel(mediaFile.toFile())) {
            MP4Demuxer demuxer = MP4Demuxer.createMP4Demuxer(channel);
            java.util.List<DemuxerTrack> videoTracks = demuxer.getVideoTracks();
            if (videoTracks.isEmpty()) {
                return invalid("MP4中不存在视频轨道", null, null, null);
            }
            if (videoTracks.size() != 1) {
                return invalid("MP4必须且只能包含一个视频轨道", null, null, null);
            }
            VideoMediaWorker.TrackInspection timeline =
                    inspectTimeline(videoTracks.get(0), allowedCodecs, timelineToleranceSeconds, maxPackets);
            if (!timeline.valid()) {
                return timeline;
            }
            Picture decodedFrame = FrameGrab.getFrameFromFile(mediaFile.toFile(), 0);
            if (decodedFrame == null || decodedFrame.getWidth() < 1 || decodedFrame.getHeight() < 1) {
                return invalid("视频首帧无法解码", timeline.durationSeconds(),
                        timeline.codec(), timeline.frameCount());
            }
            return timeline;
        } catch (JCodecException | BufferUnderflowException
                 | IndexOutOfBoundsException | IllegalArgumentException e) {
            return invalid("视频文件不是可解析的MP4媒体", null, null, null);
        }
    }

    /**
     * 在进入第三方解析器前只读取固定长度 box 头，拒绝越界尺寸，避免畸形输入诱发巨额分配。
     * I/O 故障直接向上抛出，由主进程按可重试基础设施故障处理。
     */
    private static boolean hasSaneTopLevelBoxes(Path mediaFile) throws IOException {
        long fileSize = Files.size(mediaFile);
        if (fileSize < 8L) {
            return false;
        }
        boolean ftyp = false;
        boolean moov = false;
        boolean mdat = false;
        long offset = 0L;
        int boxCount = 0;
        try (FileChannel channel = FileChannel.open(mediaFile, StandardOpenOption.READ)) {
            while (offset + 8L <= fileSize && boxCount++ < 100_000) {
                ByteBuffer header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
                if (!readFully(channel, header, offset, 8)) {
                    return false;
                }
                header.flip();
                long declaredSize = Integer.toUnsignedLong(header.getInt());
                byte[] typeBytes = new byte[4];
                header.get(typeBytes);
                String type = new String(typeBytes, StandardCharsets.US_ASCII);
                int headerSize = 8;
                long boxSize = declaredSize;
                if (declaredSize == 1L) {
                    header.clear();
                    if (!readFully(channel, header, offset + 8L, 8)) {
                        return false;
                    }
                    header.flip();
                    boxSize = header.getLong();
                    headerSize = 16;
                } else if (declaredSize == 0L) {
                    boxSize = fileSize - offset;
                }
                if (boxSize < headerSize || boxSize > fileSize - offset) {
                    return false;
                }
                ftyp |= "ftyp".equals(type);
                moov |= "moov".equals(type);
                mdat |= "mdat".equals(type);
                offset += boxSize;
            }
        }
        return offset == fileSize && boxCount <= 100_000 && ftyp && moov && mdat;
    }

    private static boolean readFully(
            FileChannel channel, ByteBuffer target, long offset, int length) throws IOException {
        target.limit(length);
        int read = 0;
        while (target.hasRemaining()) {
            int current = channel.read(target, offset + read);
            if (current < 0) {
                return false;
            }
            read += current;
        }
        return true;
    }

    private static VideoMediaWorker.TrackInspection inspectTimeline(
            DemuxerTrack videoTrack, Set<String> allowedCodecs,
            int timelineToleranceSeconds, int maxPackets) throws IOException {
        DemuxerTrackMeta metadata = videoTrack.getMeta();
        String codec = metadata.getCodec() == null
                ? null : metadata.getCodec().name().toUpperCase(Locale.ROOT);
        int declaredFrames = metadata.getTotalFrames();
        double headerDuration = metadata.getTotalDuration();
        if (!allowedCodecs.contains(codec)) {
            return invalid("视频编码不在允许范围", safeDuration(headerDuration), codec, declaredFrames);
        }
        if (!validPositiveDuration(headerDuration)) {
            return invalid("视频头时长不可解析", null, codec, declaredFrames);
        }

        int packetCount = 0;
        double earliestStart = Double.POSITIVE_INFINITY;
        double latestEnd = Double.NEGATIVE_INFINITY;
        double sampleDuration = 0D;
        Packet packet;
        while ((packet = videoTrack.nextFrame()) != null) {
            packetCount++;
            if (packetCount > maxPackets) {
                return invalid("视频帧数超过媒体探测安全上限", null, codec, packetCount);
            }
            if (packet.getData() == null || !packet.getData().hasRemaining()
                    || packet.getTimescale() <= 0 || packet.getDuration() <= 0) {
                return invalid("视频轨道包含无效样本", null, codec, packetCount);
            }
            double start = packet.getPtsD();
            double duration = packet.getDurationD();
            double end = start + duration;
            if (!Double.isFinite(start) || !validPositiveDuration(duration) || !Double.isFinite(end)) {
                return invalid("视频样本时间线不可解析", null, codec, packetCount);
            }
            earliestStart = Math.min(earliestStart, start);
            latestEnd = Math.max(latestEnd, end);
            sampleDuration += duration;
        }
        if (packetCount < 1) {
            return invalid("视频轨道不包含可读取帧",
                    safeDuration(headerDuration), codec, packetCount);
        }
        if (declaredFrames > 0 && declaredFrames != packetCount) {
            return invalid("视频头帧数与实际样本数不一致",
                    safeDuration(headerDuration), codec, packetCount);
        }
        double timelineDuration = latestEnd - earliestStart;
        if (!validPositiveDuration(timelineDuration) || !validPositiveDuration(sampleDuration)) {
            return invalid("视频样本时间线不可解析", null, codec, packetCount);
        }
        if (Math.abs(headerDuration - timelineDuration) > timelineToleranceSeconds
                || Math.abs(headerDuration - sampleDuration) > timelineToleranceSeconds
                || Math.abs(timelineDuration - sampleDuration) > timelineToleranceSeconds) {
            return invalid("视频头时长与样本时间线不一致",
                    safeDuration(timelineDuration), codec, packetCount);
        }
        return VideoMediaWorker.TrackInspection.valid(
                Math.max(1, (int) Math.round(timelineDuration)), codec, packetCount);
    }

    private static VideoMediaWorker.TrackInspection invalid(
            String message, Integer durationSeconds, String codec, Integer frameCount) {
        return VideoMediaWorker.TrackInspection.invalid(message, durationSeconds, codec, frameCount);
    }

    private static boolean validPositiveDuration(double duration) {
        return Double.isFinite(duration) && duration > 0 && duration <= Integer.MAX_VALUE;
    }

    private static Integer safeDuration(double duration) {
        return validPositiveDuration(duration) ? Math.max(1, (int) Math.round(duration)) : null;
    }

    private static void writeResult(Path resultFile, VideoMediaWorker.TrackInspection inspection)
            throws IOException {
        Properties properties = new Properties();
        properties.setProperty("valid", String.valueOf(inspection.valid()));
        put(properties, "message", inspection.message());
        put(properties, "durationSeconds", inspection.durationSeconds());
        put(properties, "codec", inspection.codec());
        put(properties, "frameCount", inspection.frameCount());
        try (Writer writer = Files.newBufferedWriter(resultFile, StandardCharsets.UTF_8)) {
            properties.store(writer, null);
        }
    }

    private static void put(Properties properties, String key, Object value) {
        if (value != null) {
            properties.setProperty(key, String.valueOf(value));
        }
    }
}
