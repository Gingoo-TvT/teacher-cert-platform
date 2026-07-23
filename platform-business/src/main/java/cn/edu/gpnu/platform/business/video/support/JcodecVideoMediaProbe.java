package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.file.service.MultipartObjectService;
import cn.edu.gpnu.platform.system.service.ParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.DemuxerTrack;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Packet;
import org.jcodec.common.model.Picture;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class JcodecVideoMediaProbe implements VideoMediaProbe {

    private static final int FINGERPRINT_CHUNK_SIZE = 8 * 1024 * 1024;
    private static final byte[] FINGERPRINT_MARKER =
            "teacher-cert-file-sha256-tree-v1\0".getBytes(StandardCharsets.UTF_8);
    private static final String DEFAULT_ALLOWED_CODECS = "H264";

    private final MultipartObjectService multipartObjectService;
    private final ParamService paramService;

    @Override
    public VideoMediaInspection inspect(String objectKey, long expectedSize, String declaredFingerprint) {
        if (expectedSize < 1) {
            throw new BizException("视频对象大小不合法");
        }
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("teacher-cert-video-probe-", ".mp4");
            FingerprintResult fingerprint = downloadAndFingerprint(objectKey, expectedSize, temporaryFile);
            // 旧 8/32 位摘要仅用于同一会话恢复且不参与秒传；新版 64 位摘要必须与服务端计算值一致。
            if (StringUtils.hasText(declaredFingerprint)
                    && declaredFingerprint.trim().length() == 64
                    && !fingerprint.value().equalsIgnoreCase(declaredFingerprint.trim())) {
                return VideoMediaInspection.invalid(
                        "视频指纹与服务端对象内容不一致",
                        fingerprint.value(), null, null, null);
            }
            return inspectMp4(temporaryFile, fingerprint.value());
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw new BizException("读取视频对象失败");
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

    private FingerprintResult downloadAndFingerprint(String objectKey, long expectedSize, Path target)
            throws IOException {
        MessageDigest leafDigest = sha256();
        List<byte[]> leafDigests = new ArrayList<>();
        long totalBytes = 0L;
        int currentLeafBytes = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = multipartObjectService.openObject(objectKey);
             OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                totalBytes += read;
                if (totalBytes > expectedSize) {
                    throw new BizException("视频对象实际大小与上传会话不一致");
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
            throw new BizException("视频对象实际大小与上传会话不一致");
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

    private VideoMediaInspection inspectMp4(Path file, String fingerprint) {
        try (SeekableByteChannel channel = org.jcodec.common.io.NIOUtils.readableChannel(file.toFile())) {
            MP4Demuxer demuxer = MP4Demuxer.createMP4Demuxer(channel);
            List<DemuxerTrack> videoTracks = demuxer.getVideoTracks();
            if (videoTracks.isEmpty()) {
                return VideoMediaInspection.invalid(
                        "MP4中不存在视频轨道", fingerprint, null, null, null);
            }
            DemuxerTrack videoTrack = videoTracks.get(0);
            DemuxerTrackMeta metadata = videoTrack.getMeta();
            String codec = metadata.getCodec() == null
                    ? null : metadata.getCodec().name().toUpperCase(Locale.ROOT);
            int frameCount = metadata.getTotalFrames();
            double duration = metadata.getTotalDuration();
            if (!allowedCodecs().contains(codec)) {
                return VideoMediaInspection.invalid(
                        "视频编码不在允许范围", fingerprint, safeDuration(duration), codec, frameCount);
            }
            if (!Double.isFinite(duration) || duration <= 0 || duration > Integer.MAX_VALUE) {
                return VideoMediaInspection.invalid(
                        "视频时长不可解析", fingerprint, null, codec, frameCount);
            }
            Packet firstPacket = videoTrack.nextFrame();
            if (frameCount < 1 || firstPacket == null || firstPacket.getData() == null
                    || !firstPacket.getData().hasRemaining()) {
                return VideoMediaInspection.invalid(
                        "视频轨道不包含可读取帧", fingerprint, safeDuration(duration), codec, frameCount);
            }
            Picture decodedFrame = FrameGrab.getFrameFromFile(file.toFile(), 0);
            if (decodedFrame == null || decodedFrame.getWidth() < 1 || decodedFrame.getHeight() < 1) {
                return VideoMediaInspection.invalid(
                        "视频首帧无法解码", fingerprint, safeDuration(duration), codec, frameCount);
            }
            return VideoMediaInspection.valid(
                    fingerprint, Math.max(1, (int) Math.round(duration)), codec, frameCount);
        } catch (IOException | JCodecException | RuntimeException e) {
            log.warn("MP4媒体探测失败: {}", e.getClass().getSimpleName());
            return VideoMediaInspection.invalid(
                    "视频文件不是可解析的MP4媒体", fingerprint, null, null, null);
        }
    }

    private Set<String> allowedCodecs() {
        String configured = paramService.getString("video.allowedCodecs", DEFAULT_ALLOWED_CODECS);
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .forEach(values::add);
        if (values.isEmpty()) {
            values.add(DEFAULT_ALLOWED_CODECS);
        }
        return values;
    }

    private Integer safeDuration(double duration) {
        if (!Double.isFinite(duration) || duration <= 0 || duration > Integer.MAX_VALUE) {
            return null;
        }
        return Math.max(1, (int) Math.round(duration));
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
}
