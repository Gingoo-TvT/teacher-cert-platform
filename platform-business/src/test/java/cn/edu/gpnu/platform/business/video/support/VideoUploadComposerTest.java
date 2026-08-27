package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.business.video.dto.MultipartCompletedPartRequest;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadChunk;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadSession;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadInitVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadProgressVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadedPartVO;
import cn.edu.gpnu.platform.file.model.MultipartUploadPlan;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;
import cn.edu.gpnu.platform.file.model.PresignedUploadPart;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoUploadComposerTest {

    @Test
    void serverAndDirectInitKeepTheirExistingOrderingRules() {
        VideoUploadSession session = session(null, 2, 10L, 20L);

        VideoUploadInitVO server = VideoUploadComposer.serverInit(session, List.of(1, 0));
        assertThat(server.getUploadMode()).isEqualTo("SERVER_CHUNK");
        assertThat(server.getUploadedChunks()).containsExactly(1, 0);
        assertThat(server.getUploadedParts()).isEmpty();
        assertThat(server.getParts()).isEmpty();

        MultipartUploadedPart second = new MultipartUploadedPart(2, "etag-2", 10L);
        MultipartUploadedPart first = new MultipartUploadedPart(1, "etag-1", 10L);
        Instant expiresAt = Instant.parse("2026-08-20T10:00:00Z");
        MultipartUploadPlan plan = new MultipartUploadPlan("s3", "video/key", expiresAt,
                List.of(second, first), List.of(
                new PresignedUploadPart(2, "url-2", expiresAt),
                new PresignedUploadPart(1, "url-1", expiresAt)));

        VideoUploadInitVO direct = VideoUploadComposer.directInit(session, plan);
        assertThat(direct.getUploadedChunks()).containsExactly(0, 1);
        assertThat(direct.getUploadedParts()).extracting(VideoUploadedPartVO::getPartNumber)
                .containsExactly(2, 1);
        assertThat(direct.getParts()).extracting(item -> item.getPartNumber()).containsExactly(2, 1);
    }

    @Test
    void mergingAndInstantHitMapTheCompleteResponse() {
        VideoUploadSession session = session("PRESIGNED_MULTIPART", 2, 10L, 20L);
        session.setStatus("MERGING");
        session.setValidationMessage("处理中");
        List<MultipartUploadedPart> parts = List.of(
                new MultipartUploadedPart(1, "one", 10L),
                new MultipartUploadedPart(2, "two", 10L));

        VideoUploadInitVO merging = VideoUploadComposer.mergingInit(session, parts);
        assertThat(merging.getUploadMode()).isEqualTo("PRESIGNED_MULTIPART");
        assertThat(merging.getUploadedChunks()).containsExactly(0, 1);
        assertThat(merging.getParts()).isEmpty();
        assertThat(merging.getStatus()).isEqualTo("MERGING");
        assertThat(merging.getValidationMessage()).isEqualTo("处理中");

        VideoUploadInitVO instant = VideoUploadComposer.instantHit(10L, 7L, 8L, "READY", "通过");
        assertThat(instant.isInstantHit()).isTrue();
        assertThat(instant.getUploadMode()).isEqualTo("FAST_HIT");
        assertThat(instant.getFileId()).isEqualTo(7L);
        assertThat(instant.getReviewId()).isEqualTo(8L);
        assertThat(instant.getUploadedChunks()).isEmpty();
    }

    @Test
    void progressUsesDatabaseIndexesForServerAndPartIndexesForDirectUpload() {
        VideoUploadSession serverSession = session(null, 2, 10L, 20L);
        VideoUploadProgressVO server = VideoUploadComposer.progress(serverSession, List.of(), List.of(1, 0));
        assertThat(server.getUploadMode()).isEqualTo("SERVER_CHUNK");
        assertThat(server.getUploadedChunkIndexes()).containsExactly(1, 0);

        VideoUploadSession directSession = session("PRESIGNED_MULTIPART", 2, 10L, 20L);
        VideoUploadProgressVO direct = VideoUploadComposer.progress(directSession, List.of(
                new MultipartUploadedPart(2, "two", 10L),
                new MultipartUploadedPart(1, "one", 10L)), List.of());
        assertThat(direct.getUploadedChunkIndexes()).containsExactly(0, 1);
        assertThat(direct.getUploadedParts()).extracting(VideoUploadedPartVO::getPartNumber)
                .containsExactly(2, 1);
    }

    @Test
    void clientPartVerificationAcceptsQuotedCaseVariantsAndReturnsServerPartsSorted() {
        MultipartUploadedPart first = new MultipartUploadedPart(1, "ABCD", 10L);
        MultipartUploadedPart second = new MultipartUploadedPart(2, "EFGH", 10L);

        List<MultipartUploadedPart> actual = VideoUploadComposer.verifyClientParts(
                List.of(request(2, "\"efgh\""), request(1, "abcd")), List.of(second, first));

        assertThat(actual).containsExactly(first, second);
    }

    @Test
    void clientPartVerificationKeepsTheExistingFailureMessages() {
        MultipartUploadedPart stored = new MultipartUploadedPart(1, "etag", 10L);

        assertThatThrownBy(() -> VideoUploadComposer.verifyClientParts(List.of(), List.of(stored)))
                .hasMessage("上传分片数量不一致");
        assertThatThrownBy(() -> VideoUploadComposer.verifyClientParts(
                List.of(request(1, "etag"), request(1, "etag")),
                List.of(stored, new MultipartUploadedPart(2, "etag-2", 10L))))
                .hasMessage("上传分片序号重复或为空");
        assertThatThrownBy(() -> VideoUploadComposer.verifyClientParts(
                List.of(request(1, "other")), List.of(stored)))
                .hasMessage("上传分片ETag校验失败");
    }

    @Test
    void directPartValidationChecksCountSequenceAndExactSizes() {
        VideoUploadSession session = session("PRESIGNED_MULTIPART", 2, 10L, 15L);
        List<MultipartUploadedPart> valid = List.of(
                new MultipartUploadedPart(1, "one", 10L),
                new MultipartUploadedPart(2, "two", 5L));
        VideoUploadComposer.validateDirectParts(session, valid);

        assertThatThrownBy(() -> VideoUploadComposer.validateDirectParts(session, valid.subList(0, 1)))
                .hasMessage("分片尚未全部上传");
        assertThatThrownBy(() -> VideoUploadComposer.validateDirectParts(session, List.of(
                new MultipartUploadedPart(2, "two", 10L), new MultipartUploadedPart(1, "one", 5L))))
                .hasMessage("上传分片序号必须连续");
        assertThatThrownBy(() -> VideoUploadComposer.validateDirectParts(session, List.of(
                new MultipartUploadedPart(1, "one", 9L), new MultipartUploadedPart(2, "two", 6L))))
                .hasMessage("上传分片大小不符合会话约束");
    }

    @Test
    void serverSideComposeRequiresTwoPartsAndOnlyChecksNonFinalPartSize() {
        long minimum = 5L * 1024L * 1024L;
        assertThat(VideoUploadComposer.canServerSideCompose(List.of(chunk(minimum)), minimum)).isFalse();
        assertThat(VideoUploadComposer.canServerSideCompose(
                List.of(chunk(minimum - 1), chunk(1L)), minimum)).isFalse();
        assertThat(VideoUploadComposer.canServerSideCompose(
                List.of(chunk(minimum), chunk(1L)), minimum)).isTrue();
    }

    private VideoUploadSession session(String mode, int totalChunks, long chunkSize, long fileSize) {
        VideoUploadSession session = new VideoUploadSession();
        session.setUploadId("upload-1");
        session.setUploadMode(mode);
        session.setChunkSize(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setFileSize(fileSize);
        session.setUploadedChunks(0);
        session.setUploadedBytes(0L);
        session.setStatus("UPLOADING");
        session.setFileId(99L);
        return session;
    }

    private MultipartCompletedPartRequest request(int partNumber, String etag) {
        MultipartCompletedPartRequest request = new MultipartCompletedPartRequest();
        request.setPartNumber(partNumber);
        request.setEtag(etag);
        return request;
    }

    private VideoUploadChunk chunk(long size) {
        VideoUploadChunk chunk = new VideoUploadChunk();
        chunk.setChunkSize(size);
        return chunk;
    }
}
