package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.business.video.dto.MultipartCompletedPartRequest;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadChunk;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadSession;
import cn.edu.gpnu.platform.business.video.vo.VideoPresignedPartVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadInitVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadProgressVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadedPartVO;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.file.model.MultipartUploadPlan;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 视频上传响应组装与分片纯校验，不执行数据库或对象存储操作。
 */
public final class VideoUploadComposer {

    private static final String SERVER_CHUNK_MODE = "SERVER_CHUNK";
    private static final String PRESIGNED_MULTIPART_MODE = "PRESIGNED_MULTIPART";

    private VideoUploadComposer() {
    }

    public static VideoUploadInitVO serverInit(VideoUploadSession session, List<Integer> uploadedIndexes) {
        VideoUploadInitVO vo = baseInit(session);
        vo.setUploadedChunks(uploadedIndexes);
        vo.setUploadedParts(List.of());
        vo.setParts(List.of());
        return vo;
    }

    public static VideoUploadInitVO directInit(VideoUploadSession session, MultipartUploadPlan uploadPlan) {
        VideoUploadInitVO vo = baseInit(session);
        vo.setUploadedChunks(uploadPlan.uploadedParts().stream()
                .map(part -> part.partNumber() - 1)
                .sorted()
                .toList());
        vo.setUploadedParts(uploadPlan.uploadedParts().stream().map(VideoUploadComposer::uploadedPart).toList());
        vo.setParts(uploadPlan.parts().stream().map(part -> {
            VideoPresignedPartVO item = new VideoPresignedPartVO();
            item.setPartNumber(part.partNumber());
            item.setUrl(part.url());
            item.setExpiresAt(part.expiresAt());
            return item;
        }).toList());
        return vo;
    }

    public static VideoUploadInitVO mergingInit(VideoUploadSession session,
                                                List<MultipartUploadedPart> verifiedParts) {
        VideoUploadInitVO vo = baseInit(session);
        vo.setUploadMode(PRESIGNED_MULTIPART_MODE);
        vo.setUploadedChunks(verifiedParts.stream().map(part -> part.partNumber() - 1).toList());
        vo.setUploadedParts(verifiedParts.stream().map(VideoUploadComposer::uploadedPart).toList());
        vo.setParts(List.of());
        return vo;
    }

    public static VideoUploadInitVO instantHit(Long partSize, Long fileId, Long reviewId,
                                               String status, String validationMessage) {
        VideoUploadInitVO vo = new VideoUploadInitVO();
        vo.setUploadId(null);
        vo.setUploadMode("FAST_HIT");
        vo.setPartSize(partSize);
        vo.setInstantHit(true);
        vo.setFileId(fileId);
        vo.setReviewId(reviewId);
        vo.setUploadedChunks(List.of());
        vo.setUploadedParts(List.of());
        vo.setParts(List.of());
        vo.setStatus(status);
        vo.setValidationMessage(validationMessage);
        return vo;
    }

    public static VideoUploadProgressVO progress(VideoUploadSession session,
                                                 List<MultipartUploadedPart> directParts,
                                                 List<Integer> serverUploadedIndexes) {
        boolean direct = PRESIGNED_MULTIPART_MODE.equals(session.getUploadMode());
        VideoUploadProgressVO vo = new VideoUploadProgressVO();
        vo.setUploadId(session.getUploadId());
        vo.setUploadMode(StringUtils.hasText(session.getUploadMode()) ? session.getUploadMode() : SERVER_CHUNK_MODE);
        vo.setStatus(session.getStatus());
        vo.setTotalChunks(session.getTotalChunks());
        vo.setUploadedChunks(session.getUploadedChunks());
        vo.setUploadedBytes(session.getUploadedBytes());
        vo.setUploadedChunkIndexes(direct
                ? directParts.stream().map(part -> part.partNumber() - 1).sorted().toList()
                : serverUploadedIndexes);
        vo.setUploadedParts(directParts.stream().map(VideoUploadComposer::uploadedPart).toList());
        vo.setFileId(session.getFileId());
        vo.setValidationMessage(session.getValidationMessage());
        return vo;
    }

    public static List<MultipartUploadedPart> verifyClientParts(
            List<MultipartCompletedPartRequest> requestedParts, List<MultipartUploadedPart> storedParts) {
        Map<Integer, MultipartUploadedPart> stored = storedParts.stream().collect(java.util.stream.Collectors.toMap(
                MultipartUploadedPart::partNumber, part -> part, (left, right) -> left, LinkedHashMap::new));
        if (requestedParts == null || requestedParts.size() != stored.size()) {
            throw new BizException("上传分片数量不一致");
        }
        Map<Integer, MultipartUploadedPart> verified = new LinkedHashMap<>();
        for (MultipartCompletedPartRequest requested : requestedParts) {
            if (requested == null || requested.getPartNumber() == null
                    || verified.containsKey(requested.getPartNumber())) {
                throw new BizException("上传分片序号重复或为空");
            }
            MultipartUploadedPart storedPart = stored.get(requested.getPartNumber());
            if (storedPart == null || !normalizeETag(storedPart.eTag()).equals(normalizeETag(requested.getEtag()))) {
                throw new BizException("上传分片ETag校验失败");
            }
            verified.put(requested.getPartNumber(), storedPart);
        }
        return verified.values().stream()
                .sorted(Comparator.comparingInt(MultipartUploadedPart::partNumber))
                .toList();
    }

    public static void validateDirectParts(VideoUploadSession session, List<MultipartUploadedPart> parts) {
        if (parts.size() != session.getTotalChunks()) {
            throw new BizException("分片尚未全部上传");
        }
        long totalSize = 0L;
        for (int index = 0; index < parts.size(); index++) {
            MultipartUploadedPart part = parts.get(index);
            if (part.partNumber() != index + 1) {
                throw new BizException("上传分片序号必须连续");
            }
            long expectedSize = index == parts.size() - 1
                    ? session.getFileSize() - session.getChunkSize() * index
                    : session.getChunkSize();
            if (part.size() != expectedSize) {
                throw new BizException("上传分片大小不符合会话约束");
            }
            totalSize += part.size();
        }
        if (totalSize != session.getFileSize()) {
            throw new BizException("上传对象总大小不一致");
        }
    }

    public static boolean canServerSideCompose(List<VideoUploadChunk> sortedChunks, long minPartSize) {
        if (sortedChunks.size() < 2) {
            return false;
        }
        for (int index = 0; index < sortedChunks.size() - 1; index++) {
            Long size = sortedChunks.get(index).getChunkSize();
            if (size == null || size < minPartSize) {
                return false;
            }
        }
        return true;
    }

    private static VideoUploadInitVO baseInit(VideoUploadSession session) {
        VideoUploadInitVO vo = new VideoUploadInitVO();
        vo.setUploadId(session.getUploadId());
        vo.setUploadMode(StringUtils.hasText(session.getUploadMode()) ? session.getUploadMode() : SERVER_CHUNK_MODE);
        vo.setPartSize(session.getChunkSize());
        vo.setInstantHit(false);
        vo.setStatus(session.getStatus());
        vo.setValidationMessage(session.getValidationMessage());
        vo.setFileId(session.getFileId());
        return vo;
    }

    private static VideoUploadedPartVO uploadedPart(MultipartUploadedPart part) {
        VideoUploadedPartVO vo = new VideoUploadedPartVO();
        vo.setPartNumber(part.partNumber());
        vo.setEtag(part.eTag());
        vo.setSize(part.size());
        return vo;
    }

    private static String normalizeETag(String eTag) {
        if (!StringUtils.hasText(eTag)) {
            return "";
        }
        String value = eTag.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
