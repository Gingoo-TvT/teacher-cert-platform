package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadChunk;
import cn.edu.gpnu.platform.business.video.entity.VideoUploadSession;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoUploadChunkMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoUploadSessionMapper;
import cn.edu.gpnu.platform.business.video.support.JcodecVideoMediaProbe;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizationHook;
import cn.edu.gpnu.platform.business.video.support.VideoFinalizeSingleFlight;
import cn.edu.gpnu.platform.business.video.support.VideoMediaInspection;
import cn.edu.gpnu.platform.business.video.support.VideoMediaProbe;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;
import cn.edu.gpnu.platform.file.service.MultipartObjectService;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserRoleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(Phase7VideoReviewIT.ProbeTestConfiguration.class)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30",
        "platform.video.probe.lease-duration=PT1S",
        "platform.video.probe.lease-renew-interval=PT0.2S"
})
class Phase7VideoReviewIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long REVIEW_TEACHER_ROLE_ID = 800000000000000004L;
    private static final long COLLEGE_AUDITOR_USER_ID = 800000000000003004L;
    private static final long STUDENT_B_USER_ID = 800000000000003009L;
    private static final long STUDENT_B_ROLE_ID = 800000000000004009L;
    private static final long REVIEWER_B_USER_ID = 800000000000003010L;
    private static final long REVIEWER_C_USER_ID = 800000000000003011L;
    private static final long REVIEWER_D_USER_ID = 800000000000003012L;
    private static final long REVIEWER_B_ROLE_ID = 800000000000004010L;
    private static final long REVIEWER_C_ROLE_ID = 800000000000004011L;
    private static final long REVIEWER_D_ROLE_ID = 800000000000004012L;
    private static final String YEAR = "P7-2026";
    private static final int VIDEO_PART_SIZE = 8 * 1024 * 1024;
    private static final byte[] VIDEO_FINGERPRINT_MARKER =
            "teacher-cert-file-sha256-tree-v1\0".getBytes(StandardCharsets.UTF_8);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Autowired
    private StudentMapper studentMapper;

    @Autowired
    private VideoReviewMapper reviewMapper;

    @Autowired
    private VideoReviewTaskMapper taskMapper;

    @Autowired
    private VideoUploadSessionMapper sessionMapper;

    @Autowired
    private VideoUploadChunkMapper chunkMapper;

    @Autowired
    private SysParamMapper paramMapper;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

    @Autowired
    private SysAuditLogMapper auditLogMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioProperties minioProperties;

    @Autowired
    private FileObjectMapper fileObjectMapper;

    @Autowired
    private MultipartObjectService multipartObjectService;

    @Autowired
    private CountingVideoMediaProbe countingVideoMediaProbe;

    @Autowired
    private ControlledVideoFinalizationHook controlledVideoFinalizationHook;

    @Autowired
    private VideoFinalizeSingleFlight videoFinalizeSingleFlight;

    @BeforeEach
    @AfterEach
    void resetSeedUsers() {
        cleanupGeneratedData();
        resetSeedStudents();
        ensureSecondCollegeStudent();
        ensureReviewer("test_review_teacher_b", REVIEWER_B_USER_ID, REVIEWER_B_ROLE_ID, "评审教师测试账号B");
        ensureReviewer("test_review_teacher_c", REVIEWER_C_USER_ID, REVIEWER_C_ROLE_ID, "评审教师测试账号C");
        ensureReviewer("test_review_teacher_d", REVIEWER_D_USER_ID, REVIEWER_D_ROLE_ID, "评审教师测试账号D", COLLEGE_B);
        resetParam("file.maxSize.video", "2147483648");
        resetParam("video.durationTarget", "900");
        resetParam("video.durationTolerance", "60");
        resetParam("video.allowedCodecs", "H264");
        resetParam("video.timelineToleranceSeconds", "2");
        resetParam("video.passLine", "60");
        resetParam("video.diffThreshold", "12");
        resetParam("video.reviewerCount", "2");
        resetParam("video.arbitrate.mode", "thirdExpert");
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
        resetUser("test_review_teacher", true);
        resetUser("test_review_teacher_b", true);
        resetUser("test_review_teacher_c", true);
        resetUser("test_review_teacher_d", true);
        countingVideoMediaProbe.reset();
        controlledVideoFinalizationHook.reset();
    }

    @Test
    void presignedUploadCompleteResumeAndInstantHitWorkWithValidation() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] content = mp4("phase7-ok");
        String fingerprint = videoFingerprint(content);
        String partMd5 = md5(content);

        JsonNode init = initUpload(student.accessToken(), 9001L, YEAR, "lesson.mp4", "video/mp4",
                content.length, VIDEO_PART_SIZE, fingerprint, 900);
        assertThat(init.at("/uploadMode").asText()).isEqualTo("PRESIGNED_MULTIPART");
        assertThat(init.at("/partSize").asInt()).isEqualTo(VIDEO_PART_SIZE);
        assertThat(init.at("/parts")).hasSize(1);
        assertThat(init.at("/parts/0/expiresAt").asText()).isNotBlank();
        URI publicUrl = URI.create(init.at("/parts/0/url").asText());
        assertThat(publicUrl.getHost()).isEqualTo("localhost");
        assertThat(publicUrl.getPort()).isEqualTo(9000);
        for (String method : List.of("PUT", "GET", "HEAD")) {
            PresignedMultipartUploadTestClient.CorsPreflight preflight =
                    PresignedMultipartUploadTestClient.preflight(init.at("/parts/0"), method);
            assertThat(preflight.status()).isIn(200, 204);
            assertThat(preflight.allowOrigin()).isEqualTo(PresignedMultipartUploadTestClient.BROWSER_ORIGIN);
            assertThat(preflight.allowMethods()).containsIgnoringCase(method);
        }
        PresignedMultipartUploadTestClient.CorsPreflight evilOrigin =
                PresignedMultipartUploadTestClient.preflight(
                        init.at("/parts/0"), "PUT", "https://evil.example");
        assertThat(evilOrigin.status()).isEqualTo(204);
        assertThat(evilOrigin.allowOrigin()).isEmpty();
        String uploadId = init.at("/uploadId").asText();
        PresignedMultipartUploadTestClient.CompletedPart uploaded =
                PresignedMultipartUploadTestClient.put(init.at("/parts/0"), content);
        assertThat(uploaded.allowOrigin()).isIn(PresignedMultipartUploadTestClient.BROWSER_ORIGIN, "*");
        assertThat(uploaded.exposedHeaders()).containsIgnoringCase("ETag");
        JsonNode resume = initUpload(student.accessToken(), 9001L, YEAR, "lesson.mp4", "video/mp4",
                content.length, VIDEO_PART_SIZE, fingerprint, 900);
        assertThat(resume.at("/uploadId").asText()).isEqualTo(uploadId);
        assertThat(resume.at("/uploadedChunks").toString()).contains("0");
        assertThat(resume.at("/uploadedParts")).hasSize(1);
        assertThat(resume.at("/uploadedParts/0/partNumber").asInt()).isEqualTo(1);
        assertThat(resume.at("/uploadedParts/0/etag").asText()).contains(partMd5);
        assertThat(resume.at("/uploadedParts/0/size").asLong()).isEqualTo(content.length);
        assertThat(resume.at("/parts")).isEmpty();

        JsonNode completed = complete(student.accessToken(), uploadId, 900, List.of(uploaded));
        assertThat(completed.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        assertThat(completed.at("/formatCheck").asText()).isEqualTo("PASS");
        assertThat(completed.has("fileMd5")).isFalse();

        LoginResult otherStudent = readyLogin("test_student_b");
        JsonNode crossUserReplay = initUpload(otherStudent.accessToken(), 9002L, "P7-CROSS-FP",
                "lesson.mp4", "video/mp4", content.length, VIDEO_PART_SIZE, fingerprint, 900);
        assertThat(crossUserReplay.at("/instantHit").asBoolean()).isFalse();
        assertThat(crossUserReplay.at("/uploadMode").asText()).isEqualTo("PRESIGNED_MULTIPART");
        assertThat(json(exchange("/api/video/upload/" + crossUserReplay.at("/uploadId").asText(),
                HttpMethod.DELETE, otherStudent.accessToken(), null)).at("/code").asInt()).isEqualTo(0);

        JsonNode instant = initUpload(student.accessToken(), 9001L, "P7-INSTANT", "lesson.mp4", "video/mp4",
                content.length, VIDEO_PART_SIZE, fingerprint, 900);
        assertThat(instant.at("/instantHit").asBoolean()).isTrue();
        assertThat(instant.at("/uploadMode").asText()).isEqualTo("FAST_HIT");
        assertThat(instant.at("/status").asText()).isEqualTo("WAIT_REVIEW");
    }

    @Test
    void nonMp4InvalidPartPlanAndExtremeDurationsAreRejected() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] avi = "RIFF-AVI".getBytes();
        ResponseEntity<String> badType = exchange("/api/video/upload/init", HttpMethod.POST, student.accessToken(),
                initBody(9001L, YEAR, "bad.avi", "video/x-msvideo", avi.length, VIDEO_PART_SIZE,
                        videoFingerprint(avi), 900));
        assertThat(json(badType).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(badType).at("/msg").asText()).contains("视频格式必须为MP4");

        int tooSmallPart = 4 * 1024 * 1024;
        ResponseEntity<String> badPartPlan = exchange("/api/video/upload/init", HttpMethod.POST,
                student.accessToken(), initBody(9001L, "P7-SMALL-PART", "small-part.mp4", "video/mp4",
                        tooSmallPart + 1L, tooSmallPart, videoFingerprint(mp4("small-part")), 900));
        assertThat(json(badPartPlan).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(badPartPlan).at("/msg").asText()).contains("不得小于5MiB");

        byte[] extreme = mp4("extreme-duration");
        ResponseEntity<String> negativeInit = exchange("/api/video/upload/init", HttpMethod.POST,
                student.accessToken(), initBody(9001L, "P7-DUR-MIN", "duration-min.mp4", "video/mp4",
                        extreme.length, VIDEO_PART_SIZE, videoFingerprint(extreme), Integer.MIN_VALUE));
        assertThat(json(negativeInit).at("/code").asInt()).isEqualTo(400);
        assertThat(json(negativeInit).at("/msg").asText()).contains("视频时长必须大于0");

        ResponseEntity<String> excessiveInit = exchange("/api/video/upload/init", HttpMethod.POST,
                student.accessToken(), initBody(9001L, "P7-DUR-MAX", "duration-max.mp4", "video/mp4",
                        extreme.length, VIDEO_PART_SIZE, videoFingerprint(extreme), 86_401));
        assertThat(json(excessiveInit).at("/code").asInt()).isEqualTo(400);
        assertThat(json(excessiveInit).at("/msg").asText()).contains("视频时长不能超过86400秒");

        ResponseEntity<String> negativeMerge = exchange("/api/video/upload/merge", HttpMethod.POST,
                student.accessToken(), Map.of("uploadId", "duration-guard", "durationSeconds", Integer.MIN_VALUE));
        assertThat(json(negativeMerge).at("/code").asInt()).isEqualTo(400);
        assertThat(json(negativeMerge).at("/msg").asText()).contains("视频时长必须大于0");
        ResponseEntity<String> excessiveMerge = exchange("/api/video/upload/merge", HttpMethod.POST,
                student.accessToken(), Map.of("uploadId", "duration-guard", "durationSeconds", 86_401));
        assertThat(json(excessiveMerge).at("/code").asInt()).isEqualTo(400);
        assertThat(json(excessiveMerge).at("/msg").asText()).contains("视频时长不能超过86400秒");

        byte[] content = shortVideo();
        JsonNode init = initUpload(student.accessToken(), 9001L, "P7-DURATION", "duration.mp4", "video/mp4",
                content.length, VIDEO_PART_SIZE, videoFingerprint(content), 900);
        String uploadId = init.at("/uploadId").asText();
        List<PresignedMultipartUploadTestClient.CompletedPart> parts =
                PresignedMultipartUploadTestClient.putAll(init, content);
        ResponseEntity<String> negativeComplete = exchange("/api/video/upload/complete", HttpMethod.POST,
                student.accessToken(), completeBody(uploadId, Integer.MIN_VALUE, parts));
        assertThat(json(negativeComplete).at("/code").asInt()).isEqualTo(400);
        assertThat(json(negativeComplete).at("/msg").asText()).contains("视频时长必须大于0");
        ResponseEntity<String> excessiveComplete = exchange("/api/video/upload/complete", HttpMethod.POST,
                student.accessToken(), completeBody(uploadId, 86_401, parts));
        assertThat(json(excessiveComplete).at("/code").asInt()).isEqualTo(400);
        assertThat(json(excessiveComplete).at("/msg").asText()).contains("视频时长不能超过86400秒");
        assertThat(sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, uploadId)
                .last("LIMIT 1")).getStatus()).isEqualTo("UPLOADING");

        JsonNode completed = complete(student.accessToken(), uploadId, 900, parts);
        assertThat(completed.at("/status").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(completed.at("/validationMessage").asText()).contains("视频时长超出容差");
        assertThat(completed.at("/durationSeconds").asInt()).isEqualTo(3);
    }

    @Test
    void fakeMediaAndFingerprintMismatchFailClosedWithServerHash() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] fakeMedia = "....ftypmp42-not-a-real-video-mdat".getBytes(StandardCharsets.UTF_8);
        String fakeHash = videoFingerprint(fakeMedia);
        JsonNode fakeInit = initUpload(student.accessToken(), 9001L, "P7-FAKE-MEDIA",
                "fake.mp4", "video/mp4", fakeMedia.length, VIDEO_PART_SIZE, fakeHash, 900);
        List<PresignedMultipartUploadTestClient.CompletedPart> fakeParts =
                PresignedMultipartUploadTestClient.putAll(fakeInit, fakeMedia);
        JsonNode fakeCompleted = complete(student.accessToken(), fakeInit.at("/uploadId").asText(), 900, fakeParts);
        assertThat(fakeCompleted.at("/status").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(fakeCompleted.at("/formatCheck").asText()).isEqualTo("FAIL");
        assertThat(fakeCompleted.at("/validationMessage").asText()).contains("可解析的MP4");

        byte[] actualMedia = mp4("fingerprint-mismatch");
        byte[] declaredOther = mp4("declared-other-content");
        String actualHash = videoFingerprint(actualMedia);
        JsonNode mismatchInit = initUpload(student.accessToken(), 9001L, "P7-HASH-MISMATCH",
                "mismatch.mp4", "video/mp4", actualMedia.length, VIDEO_PART_SIZE,
                videoFingerprint(declaredOther), 900);
        List<PresignedMultipartUploadTestClient.CompletedPart> mismatchParts =
                PresignedMultipartUploadTestClient.putAll(mismatchInit, actualMedia);
        JsonNode mismatchCompleted = complete(student.accessToken(),
                mismatchInit.at("/uploadId").asText(), 900, mismatchParts);
        assertThat(mismatchCompleted.at("/status").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(mismatchCompleted.at("/validationMessage").asText()).contains("指纹");
        VideoReview mismatchReview = reviewMapper.selectById(mismatchCompleted.at("/id").asLong());
        FileObject stored = fileObjectMapper.selectById(mismatchReview.getVideoFileId());
        assertThat(stored.getMd5()).isEqualTo(actualHash);
        assertThat(stored.getChecksumAlgorithm()).isEqualTo("SHA256_TREE_V1");
        assertThat(stored.getContentHashVerified()).isEqualTo(1);
    }

    @Test
    void mediaHeaderDurationMustMatchDecodedSampleTimeline() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] inconsistent = withTrackHeaderDuration(shortVideo(), 900);
        JsonNode init = initUpload(student.accessToken(), 9001L, "P7-TIMELINE",
                "timeline.mp4", "video/mp4", inconsistent.length, VIDEO_PART_SIZE,
                videoFingerprint(inconsistent), 900);
        List<PresignedMultipartUploadTestClient.CompletedPart> parts =
                PresignedMultipartUploadTestClient.putAll(init, inconsistent);

        JsonNode completed = complete(student.accessToken(), init.at("/uploadId").asText(), 900, parts);

        assertThat(completed.at("/status").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(completed.at("/validationMessage").asText()).contains("样本时间线");
    }

    @Test
    void duplicateCompleteRunsExactlyOneProbe() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] content = mp4("single-flight");
        JsonNode init = initUpload(student.accessToken(), 9001L, "P7-SINGLE-FLIGHT",
                "single-flight.mp4", "video/mp4", content.length, VIDEO_PART_SIZE,
                videoFingerprint(content), 900);
        List<PresignedMultipartUploadTestClient.CompletedPart> parts =
                PresignedMultipartUploadTestClient.putAll(init, content);
        String uploadId = init.at("/uploadId").asText();
        countingVideoMediaProbe.arm();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<String>> first = pool.submit(() ->
                    exchange("/api/video/upload/complete", HttpMethod.POST, student.accessToken(),
                            completeBody(uploadId, 900, parts)));
            assertThat(countingVideoMediaProbe.awaitEntered()).isTrue();

            ResponseEntity<String> duplicate = exchange("/api/video/upload/complete", HttpMethod.POST,
                    student.accessToken(), completeBody(uploadId, 900, parts));

            assertThat(json(duplicate).at("/code").asInt()).isEqualTo(1000);
            assertThat(json(duplicate).at("/msg").asText()).contains("正在合并");
            assertThat(countingVideoMediaProbe.invocations()).isEqualTo(1);
            countingVideoMediaProbe.release();
            assertThat(json(first.get(15, TimeUnit.SECONDS)).at("/data/status").asText())
                    .isEqualTo("WAIT_REVIEW");
            assertThat(countingVideoMediaProbe.invocations()).isEqualTo(1);
        } finally {
            countingVideoMediaProbe.release();
            pool.shutdownNow();
        }
    }

    @Test
    void currentCodecPolicyAndObjectExistenceInvalidateFastHit() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P7-POLICY-SOURCE";
        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, year);
        VideoReview source = reviewMapper.selectById(reviewId);
        FileObject sourceFile = fileObjectMapper.selectById(source.getVideoFileId());
        byte[] content = mp4(year);
        String fingerprint = videoFingerprint(content);
        assertThat(sourceFile.getMediaCodec()).isEqualTo("H264");
        assertThat(sourceFile.getMediaValidationPolicyHash()).hasSize(64);
        assertThat(sourceFile.getMediaProbeVersion()).isEqualTo("JCODEC_PROCESS_V3");

        resetParam("video.allowedCodecs", "VP8");
        JsonNode policyMiss = initUpload(student.accessToken(), 9001L, "P7-POLICY-MISS",
                "policy.mp4", "video/mp4", content.length, VIDEO_PART_SIZE, fingerprint, 900);
        assertThat(policyMiss.at("/instantHit").asBoolean()).isFalse();
        assertThat(json(exchange("/api/video/upload/" + policyMiss.at("/uploadId").asText(),
                HttpMethod.DELETE, student.accessToken(), null)).at("/code").asInt()).isEqualTo(0);

        resetParam("video.allowedCodecs", "H264");
        minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                .bucket(sourceFile.getBucket()).object(sourceFile.getObjectKey()).build());
        JsonNode missingObject = initUpload(student.accessToken(), 9001L, "P7-OBJECT-MISS",
                "missing.mp4", "video/mp4", content.length, VIDEO_PART_SIZE, fingerprint, 900);
        assertThat(missingObject.at("/instantHit").asBoolean()).isFalse();
        assertThat(json(exchange("/api/video/upload/" + missingObject.at("/uploadId").asText(),
                HttpMethod.DELETE, student.accessToken(), null)).at("/code").asInt()).isEqualTo(0);
    }

    @Test
    void assigningWhileProbeRunsCannotBeOverwrittenByFinalize() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        String year = "P7-FIN-ASGN";
        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, year);
        VideoReview original = reviewMapper.selectById(reviewId);

        byte[] replacement = mp4("replacement-" + year);
        JsonNode init = initUpload(student.accessToken(), 9001L, year,
                "replacement.mp4", "video/mp4", replacement.length, VIDEO_PART_SIZE,
                videoFingerprint(replacement), 900);
        List<PresignedMultipartUploadTestClient.CompletedPart> parts =
                PresignedMultipartUploadTestClient.putAll(init, replacement);
        countingVideoMediaProbe.arm();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ResponseEntity<String>> completing = pool.submit(() ->
                    exchange("/api/video/upload/complete", HttpMethod.POST, student.accessToken(),
                            completeBody(init.at("/uploadId").asText(), 900, parts)));
            assertThat(countingVideoMediaProbe.awaitEntered()).isTrue();

            assign(auditor.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
            countingVideoMediaProbe.release();
            ResponseEntity<String> completion = completing.get(15, TimeUnit.SECONDS);

            assertThat(json(completion).at("/code").asInt()).isEqualTo(1000);
            assertThat(json(completion).at("/msg").asText()).contains("评审进行中");
            VideoReview after = reviewMapper.selectById(reviewId);
            assertThat(after.getStatus()).isEqualTo("REVIEWING");
            assertThat(after.getVideoFileId()).isEqualTo(original.getVideoFileId());
            assertThat(taskCount(reviewId)).isEqualTo(2);
        } finally {
            countingVideoMediaProbe.release();
            pool.shutdownNow();
        }
    }

    @Test
    void legacyFingerprintLengthsRemainExplicitCompatibilityPaths() throws Exception {
        LoginResult student = readyLogin("test_student_b");
        byte[] content = mp4("legacy-fingerprint");
        String legacy32Hash = md5(content);
        String legacy8Hash = "1234abcd";
        Long otherUploaderId = userMapper.selectByUsername("test_student").getId();
        FileObject legacy32Seed = seedReadyVideoFile(legacy32Hash, content, "legacy32", otherUploaderId);
        FileObject legacy8Seed = seedReadyVideoFile(legacy8Hash, content, "legacy8", otherUploaderId);

        try {
            JsonNode legacy32 = initUpload(student.accessToken(), 9002L, "P7-LEGACY32", "legacy32.mp4",
                    "video/mp4", content.length, VIDEO_PART_SIZE, legacy32Hash, 900);
            assertThat(legacy32.at("/instantHit").asBoolean()).isFalse();
            assertThat(legacy32.at("/uploadMode").asText()).isEqualTo("PRESIGNED_MULTIPART");
            assertThat(legacy32.hasNonNull("fileId")).isFalse();
            assertThat(json(exchange("/api/video/upload/" + legacy32.at("/uploadId").asText(), HttpMethod.DELETE,
                    student.accessToken(), null)).at("/code").asInt()).isEqualTo(0);

            JsonNode legacy8 = initUpload(student.accessToken(), 9002L, "P7-LEGACY8", "legacy8.mp4", "video/mp4",
                    content.length, VIDEO_PART_SIZE, legacy8Hash, 900);
            assertThat(legacy8.at("/instantHit").asBoolean()).isFalse();
            assertThat(legacy8.at("/uploadMode").asText()).isEqualTo("PRESIGNED_MULTIPART");
            assertThat(legacy8.hasNonNull("fileId")).isFalse();
            assertThat(json(exchange("/api/video/upload/" + legacy8.at("/uploadId").asText(), HttpMethod.DELETE,
                    student.accessToken(), null)).at("/code").asInt()).isEqualTo(0);

            ResponseEntity<String> invalid = exchange("/api/video/upload/init", HttpMethod.POST,
                    student.accessToken(), initBody(9002L, "P7-BAD-HASH", "bad-hash.mp4", "video/mp4",
                            content.length, VIDEO_PART_SIZE, "0123456789abcdef", 900));
            assertThat(json(invalid).at("/code").asInt()).isEqualTo(400);
            assertThat(json(invalid).at("/msg").asText()).contains("文件指纹格式不合法");
        } finally {
            jdbcTemplate.update("DELETE FROM file_object WHERE id IN (?, ?)",
                    legacy32Seed.getId(), legacy8Seed.getId());
        }
    }

    @Test
    void independentReviewHidesOtherScoresBeforeSettlementAndSettlesNormalCase() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");
        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, YEAR);
        assign(auditor.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        long taskA = taskId(reviewerA.accessToken());
        long taskB = taskId(reviewerB.accessToken());

        score(reviewerA.accessToken(), taskA, 85, "PASS");
        JsonNode bVisibleReview = json(exchange("/api/video/reviews/" + reviewId, HttpMethod.GET,
                reviewerB.accessToken(), null)).at("/data/tasks");
        assertThat(bVisibleReview.size()).isEqualTo(1);
        assertThat(bVisibleReview.at("/0/id").asLong()).isEqualTo(taskB);
        assertThat(bVisibleReview.toString()).doesNotContain("评分意见-85");
        assertThat(bVisibleReview.toString()).doesNotContain("\"score\":85");

        score(reviewerB.accessToken(), taskB, 80, "PASS");
        VideoReview review = reviewMapper.selectById(reviewId);
        assertThat(review.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(review.getFinalScore()).isEqualTo(83);
        assertThat(review.getFinalConclusion()).isEqualTo("PASS");
    }

    @Test
    void diffOverThresholdOrConclusionConflictRequiresReviewAndThirdExpertPairMinSettles() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");

        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-DIFF");
        assign(auditor.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskId(reviewerA.accessToken()), 85, "PASS");
        score(reviewerB.accessToken(), taskId(reviewerB.accessToken()), 60, "PASS");
        assertThat(reviewMapper.selectById(reviewId).getStatus()).isEqualTo("NEED_REVIEW");
        thirdReview(auditor.accessToken(), reviewId, REVIEWER_C_USER_ID, 81, "PASS");
        VideoReview settled = reviewMapper.selectById(reviewId);
        assertThat(settled.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(settled.getFinalScore()).isEqualTo(83);

        long conflictId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-CONFLICT");
        assign(auditor.accessToken(), conflictId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), conflictId), 85, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), conflictId), 58, "FAIL");
        assertThat(reviewMapper.selectById(conflictId).getStatus()).isEqualTo("NEED_REVIEW");
    }

    @Test
    void reuploadIsRejectedAfterReviewTasksExistOrNeedReview() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");

        String reviewingYear = "P7-RU-REV";
        long reviewingId = uploadValidatedVideo(student.accessToken(), 9001L, reviewingYear);
        byte[] replacement = mp4("replacement-before-reviewing");
        String replacementFingerprint = videoFingerprint(replacement);
        JsonNode replacementInit = initUpload(student.accessToken(), 9001L, reviewingYear,
                "replacement.mp4", "video/mp4", replacement.length, VIDEO_PART_SIZE, replacementFingerprint, 900);
        String replacementUploadId = replacementInit.at("/uploadId").asText();
        List<PresignedMultipartUploadTestClient.CompletedPart> replacementParts =
                PresignedMultipartUploadTestClient.putAll(replacementInit, replacement);

        assign(auditor.accessToken(), reviewingId, 800000000000003005L, REVIEWER_B_USER_ID);
        VideoReview beforeReviewing = reviewMapper.selectById(reviewingId);
        Long beforeTaskCount = taskCount(reviewingId);

        byte[] original = mp4(reviewingYear);
        ResponseEntity<String> instantHit = exchange("/api/video/upload/init", HttpMethod.POST, student.accessToken(),
                initBody(9001L, reviewingYear, "lesson-" + reviewingYear + ".mp4",
                        "video/mp4", original.length, VIDEO_PART_SIZE, videoFingerprint(original), 900));
        assertThat(json(instantHit).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(instantHit).at("/msg").asText()).contains("评审进行中不可重新上传");

        ResponseEntity<String> completeAgain = exchange("/api/video/upload/complete", HttpMethod.POST,
                student.accessToken(), completeBody(replacementUploadId, 900, replacementParts));
        assertThat(json(completeAgain).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(completeAgain).at("/msg").asText()).contains("评审进行中不可重新上传");
        VideoReview afterReviewing = reviewMapper.selectById(reviewingId);
        assertThat(afterReviewing.getStatus()).isEqualTo("REVIEWING");
        assertThat(afterReviewing.getVideoFileId()).isEqualTo(beforeReviewing.getVideoFileId());
        assertThat(taskCount(reviewingId)).isEqualTo(beforeTaskCount);

        String needReviewYear = "P7-RU-NEED";
        long needReviewId = uploadValidatedVideo(student.accessToken(), 9001L, needReviewYear);
        assign(auditor.accessToken(), needReviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), needReviewId), 85, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), needReviewId), 60, "PASS");
        assertThat(reviewMapper.selectById(needReviewId).getStatus()).isEqualTo("NEED_REVIEW");
        Long needReviewTaskCount = taskCount(needReviewId);
        byte[] needReviewFile = mp4(needReviewYear);
        ResponseEntity<String> needReviewReupload = exchange("/api/video/upload/init", HttpMethod.POST, student.accessToken(),
                initBody(9001L, needReviewYear, "lesson-" + needReviewYear + ".mp4",
                        "video/mp4", needReviewFile.length, VIDEO_PART_SIZE, videoFingerprint(needReviewFile), 900));
        assertThat(json(needReviewReupload).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(needReviewReupload).at("/msg").asText()).contains("评审进行中不可重新上传");
        assertThat(reviewMapper.selectById(needReviewId).getStatus()).isEqualTo("NEED_REVIEW");
        assertThat(taskCount(needReviewId)).isEqualTo(needReviewTaskCount);
    }

    @Test
    void returnedVideoCanBeReuploadedReassignedAndSettledWithAudit() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");

        String year = "P7-RETURN";
        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, year);
        assign(auditor.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), reviewId), 50, "FAIL");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), reviewId), 55, "FAIL");
        VideoReview failed = reviewMapper.selectById(reviewId);
        assertThat(failed.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(failed.getFinalConclusion()).isEqualTo("FAIL");

        returnVideo(auditor.accessToken(), reviewId, "退回重传修改");
        VideoReview returned = reviewMapper.selectById(reviewId);
        assertThat(returned.getStatus()).isEqualTo("RETURNED");
        assertThat(returned.getFinalScore()).isNull();
        assertThat(returned.getFinalConclusion()).isNull();
        assertThat(returned.getLocked()).isZero();
        SysAuditLog returnAudit = auditLogMapper.selectOne(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "video")
                .eq(SysAuditLog::getBizId, reviewId)
                .eq(SysAuditLog::getOperation, "return")
                .eq(SysAuditLog::getOldStatus, "REVIEW_COMPLETED")
                .eq(SysAuditLog::getNewStatus, "RETURNED")
                .last("LIMIT 1"));
        assertThat(returnAudit).isNotNull();
        assertThat(returnAudit.getComment()).isEqualTo("退回重传修改");
        assertThat(returnAudit.getOperatorId()).isNotNull();
        assertThat(returnAudit.getIp()).isNotBlank();
        assertThat(returnAudit.getTarget()).contains(String.valueOf(reviewId)).contains(year);

        byte[] replacement = mp4("returned-replacement");
        String fingerprint = videoFingerprint(replacement);
        JsonNode init = initUpload(student.accessToken(), 9001L, year,
                "lesson-" + year + "-reupload.mp4", "video/mp4", replacement.length, VIDEO_PART_SIZE, fingerprint, 900);
        assertThat(init.at("/instantHit").asBoolean()).isFalse();
        String uploadId = init.at("/uploadId").asText();
        List<PresignedMultipartUploadTestClient.CompletedPart> parts =
                PresignedMultipartUploadTestClient.putAll(init, replacement);
        JsonNode completed = complete(student.accessToken(), uploadId, 900, parts);
        assertThat(completed.at("/id").asLong()).isEqualTo(reviewId);
        assertThat(completed.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        assertThat(taskCount(reviewId)).isZero();
        VideoReview reset = reviewMapper.selectById(reviewId);
        assertThat(reset.getFinalScore()).isNull();
        assertThat(reset.getFinalConclusion()).isNull();
        assertThat(reset.getVideoFileName()).isEqualTo("lesson-" + year + "-reupload.mp4");

        assign(auditor.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), reviewId), 88, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), reviewId), 84, "PASS");
        VideoReview settled = reviewMapper.selectById(reviewId);
        assertThat(settled.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(settled.getFinalScore()).isEqualTo(86);
        assertThat(settled.getFinalConclusion()).isEqualTo("PASS");
    }

    @Test
    void confirmedVideoCannotBeReturnedOrReuploaded() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");

        String year = "P7-CONFIRMED";
        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, year);
        assign(auditor.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), reviewId), 85, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), reviewId), 80, "PASS");
        confirmVideo(auditor.accessToken(), reviewId);
        assertThat(reviewMapper.selectById(reviewId).getStatus()).isEqualTo("CONFIRMED");

        ResponseEntity<String> returned = exchange("/api/video/reviews/" + reviewId + "/return", HttpMethod.POST,
                auditor.accessToken(), Map.of("comment", "已确认后退回"));
        assertThat(json(returned).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(returned).at("/msg").asText()).contains("已确认视频不可退回");

        byte[] replacement = mp4("confirmed-replacement");
        ResponseEntity<String> reupload = exchange("/api/video/upload/init", HttpMethod.POST, student.accessToken(),
                initBody(9001L, year, "confirmed-replacement.mp4", "video/mp4",
                        replacement.length, VIDEO_PART_SIZE, videoFingerprint(replacement), 900));
        assertThat(json(reupload).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(reupload).at("/msg").asText()).contains("评审进行中不可重新上传");
        assertThat(reviewMapper.selectById(reviewId).getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void sysParamChangesAffectThresholdAndReviewerCount() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");
        LoginResult reviewerC = readyLogin("test_review_teacher_c");
        updateParam("video.diffThreshold", "30");
        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-PARAM-DIFF");
        assign(auditor.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), reviewId), 85, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), reviewId), 60, "PASS");
        assertThat(reviewMapper.selectById(reviewId).getStatus()).isEqualTo("REVIEW_COMPLETED");

        updateParam("video.reviewerCount", "3");
        long threeReviewerId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-PARAM-COUNT");
        assign(auditor.accessToken(), threeReviewerId, 800000000000003005L, REVIEWER_B_USER_ID, REVIEWER_C_USER_ID);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), threeReviewerId), 80, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), threeReviewerId), 82, "PASS");
        score(reviewerC.accessToken(), taskIdByReview(reviewerC.accessToken(), threeReviewerId), 84, "PASS");
        VideoReview threeReviewerSettled = reviewMapper.selectById(threeReviewerId);
        assertThat(threeReviewerSettled.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(threeReviewerSettled.getFinalScore()).isEqualTo(82);
        assertThat(threeReviewerSettled.getFinalConclusion()).isEqualTo("PASS");
    }

    @Test
    void reviewerGroupAssignAndDirectAssignBothSettleWithScopeChecks() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");

        long groupId = createReviewerGroup(auditor.accessToken(), "WP-D评审组");
        addReviewerGroupMember(auditor.accessToken(), groupId, 800000000000003005L);
        addReviewerGroupMember(auditor.accessToken(), groupId, REVIEWER_B_USER_ID);

        long groupReviewId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-GROUP");
        assignGroup(auditor.accessToken(), groupReviewId, groupId);
        assertThat(taskCount(groupReviewId)).isEqualTo(2L);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), groupReviewId), 90, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), groupReviewId), 86, "PASS");
        VideoReview groupSettled = reviewMapper.selectById(groupReviewId);
        assertThat(groupSettled.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(groupSettled.getFinalScore()).isEqualTo(88);

        long directReviewId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-DIRECT");
        assign(auditor.accessToken(), directReviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), directReviewId), 82, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), directReviewId), 80, "PASS");
        VideoReview directSettled = reviewMapper.selectById(directReviewId);
        assertThat(directSettled.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(directSettled.getFinalScore()).isEqualTo(81);

        long shortGroupId = createReviewerGroup(auditor.accessToken(), "WP-D人数不足组");
        addReviewerGroupMember(auditor.accessToken(), shortGroupId, 800000000000003005L);
        long shortReviewId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-GROUP-SHORT");
        ResponseEntity<String> shortAssign = exchange("/api/video/reviews/" + shortReviewId + "/assign", HttpMethod.POST,
                auditor.accessToken(), Map.of("groupId", shortGroupId));
        assertThat(json(shortAssign).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(shortAssign).at("/msg").asText()).contains("评审教师人数需等于系统参数");

        ResponseEntity<String> crossMember = exchange("/api/video/reviewer-groups/" + groupId + "/members", HttpMethod.POST,
                auditor.accessToken(), Map.of("reviewerUserId", REVIEWER_D_USER_ID));
        assertThat(json(crossMember).at("/code").asInt()).isEqualTo(403);
        assertThat(json(crossMember).at("/msg").asText()).contains("评审教师不属于本学院");

        long otherReviewId = uploadValidatedVideo(studentB.accessToken(), 9002L, "P7-GROUP-CROSS");
        ResponseEntity<String> crossGroupAssign = exchange("/api/video/reviews/" + otherReviewId + "/assign", HttpMethod.POST,
                auditor.accessToken(), Map.of("groupId", groupId));
        assertThat(json(crossGroupAssign).at("/code").asInt()).isIn(403, 404);

        ResponseEntity<String> directCrossReviewer = exchange("/api/video/reviews/" + shortReviewId + "/assign", HttpMethod.POST,
                auditor.accessToken(), Map.of("reviewerIds", List.of(800000000000003005L, REVIEWER_D_USER_ID)));
        assertThat(json(directCrossReviewer).at("/code").asInt()).isEqualTo(403);
        assertThat(json(directCrossReviewer).at("/msg").asText()).contains("评审教师不属于该视频学院");
    }

    @Test
    void reviewerCandidatesAreScopedAndSupportDirectAssignSettlement() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");
        LoginResult reviewerD = readyLogin("test_review_teacher_d");

        JsonNode candidates = json(exchange("/api/video/reviewer-candidates", HttpMethod.GET,
                auditor.accessToken(), null)).at("/data");
        assertThat(candidates.size()).isGreaterThanOrEqualTo(2);
        assertThat(hasCandidate(candidates, 800000000000003005L)).isTrue();
        assertThat(hasCandidate(candidates, REVIEWER_B_USER_ID)).isTrue();
        assertThat(hasCandidate(candidates, REVIEWER_D_USER_ID)).isFalse();
        assertThat(hasCandidate(candidates, COLLEGE_AUDITOR_USER_ID)).isFalse();

        ResponseEntity<String> reviewerForbidden = exchange("/api/video/reviewer-candidates", HttpMethod.GET,
                reviewerD.accessToken(), null);
        assertThat(reviewerForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        long reviewerAUserId = 800000000000003005L;
        long reviewerBUserId = REVIEWER_B_USER_ID;
        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-CANDIDATES");
        assign(auditor.accessToken(), reviewId, reviewerAUserId, reviewerBUserId);
        score(reviewerA.accessToken(), taskIdByReview(reviewerA.accessToken(), reviewId), 84, "PASS");
        score(reviewerB.accessToken(), taskIdByReview(reviewerB.accessToken(), reviewId), 80, "PASS");
        VideoReview settled = reviewMapper.selectById(reviewId);
        assertThat(settled.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(settled.getFinalScore()).isEqualTo(82);
        assertThat(settled.getFinalConclusion()).isEqualTo("PASS");
    }

    @Test
    void readWriteScopeReviewerScopeAndPlaybackAuthAreEnforced() throws Exception {
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");

        ResponseEntity<String> crossStudent = exchange("/api/video/upload/init", HttpMethod.POST, studentA.accessToken(),
                initBody(9002L, YEAR, "cross.mp4", "video/mp4", 8, VIDEO_PART_SIZE,
                        videoFingerprint(mp4("cross")), 900));
        assertThat(json(crossStudent).at("/code").asInt()).isEqualTo(403);

        long own = uploadValidatedVideo(studentA.accessToken(), 9001L, "P7-SCOPE-A");
        long other = uploadValidatedVideo(studentB.accessToken(), 9002L, "P7-SCOPE-B");
        JsonNode auditorList = json(exchange("/api/video/reviews?assessmentYear=P7-SCOPE-A",
                HttpMethod.GET, auditor.accessToken(), null)).at("/data/records");
        assertThat(auditorList.toString()).contains(String.valueOf(own));
        assertThat(auditorList.toString()).doesNotContain(String.valueOf(other));

        JsonNode studentList = json(exchange("/api/video/reviews?assessmentYear=P7-SCOPE-A",
                HttpMethod.GET, studentA.accessToken(), null)).at("/data/records");
        assertThat(studentList.size()).isEqualTo(1);
        assertThat(studentList.at("/0/studentId").asLong()).isEqualTo(9001L);

        assign(auditor.accessToken(), own, 800000000000003005L, REVIEWER_B_USER_ID);
        JsonNode reviewerTasks = json(exchange("/api/video/tasks/my", HttpMethod.GET, reviewerA.accessToken(), null)).at("/data/records");
        assertThat(reviewerTasks.toString()).contains(String.valueOf(own));

        ResponseEntity<String> noToken = rest.getForEntity(url("/api/video/reviews/" + own + "/play"), String.class);
        assertThat(noToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        JsonNode play = json(exchange("/api/video/reviews/" + own + "/play", HttpMethod.GET, reviewerA.accessToken(), null)).at("/data");
        assertThat(play.at("/url").asText()).contains("X-Amz-");
        assertThat(play.at("/watermarkText").asText()).contains("test_review_teacher");
    }

    @Test
    void reviewListSupportsRealServerSidePaginationScopedToCollege() throws Exception {
        // P1-1 真分页 rollout（Endpoint A / variant A）：GET /api/video/reviews 改用 selectPage 后，
        // 分页与 @DataScope(alias="video_review", permission="video:play") 的学院域需同时生效。
        LoginResult student = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        LoginResult auditor = readyLogin("test_college_auditor");

        long a1 = uploadValidatedVideo(student.accessToken(), 9001L, "P7PAGEA1");
        long a2 = uploadValidatedVideo(student.accessToken(), 9001L, "P7PAGEA2");
        long a3 = uploadValidatedVideo(student.accessToken(), 9001L, "P7PAGEA3");
        long b1 = uploadValidatedVideo(studentB.accessToken(), 9002L, "P7PAGEB1");

        JsonNode page1 = json(exchange("/api/video/reviews?keyword=P7PAGE&page=1&size=2",
                HttpMethod.GET, auditor.accessToken(), null)).at("/data");
        JsonNode page2 = json(exchange("/api/video/reviews?keyword=P7PAGE&page=2&size=2",
                HttpMethod.GET, auditor.accessToken(), null)).at("/data");

        // COLLEGE_A 学院负责人：total 只计学院内 3 条（P7PAGEA1..3），跨学院的 b1（COLLEGE_B）被排除。
        assertThat(page1.at("/total").asLong()).isEqualTo(3);
        assertThat(page2.at("/total").asLong()).isEqualTo(3);
        assertThat(page1.at("/records").size()).isEqualTo(2);
        assertThat(page2.at("/records").size()).isEqualTo(1);

        List<Long> ids = new ArrayList<>();
        for (JsonNode node : page1.at("/records")) {
            ids.add(node.at("/id").asLong());
        }
        for (JsonNode node : page2.at("/records")) {
            ids.add(node.at("/id").asLong());
        }
        assertThat(ids).containsExactlyInAnyOrder(a1, a2, a3);
        assertThat(ids).doesNotContain(b1);
    }

    @Test
    void myTasksSupportsRealServerSidePaginationScopedToCurrentReviewer() throws Exception {
        // P1-1 真分页 rollout（Endpoint B / variant B'）：GET /api/video/tasks/my 改用 selectPage 后，
        // 「本人任务」域仍须是 wrapper 内 eq(reviewerId, currentUserId)，不是查询后按当前用户做 Java 过滤。
        // WS-1（审计#2）：/api/video/tasks/my 无年度/关键字过滤，无法在服务端排除常驻 demo 任务；而 demo 恰好把 3 条待办
        // 挂在共享种子账号 test_review_teacher(3005) 名下（会把其 total 撑成 6）。故改用两个「测试专属、demo 从不指派」的
        // 评审教师 B(3010)/C(3011) 作夹具——任务集完全由本测试掌控，可精确断言 total==3：既不放宽阈值，又保住
        // 「真分页 + 本人任务域按 reviewerId 隔离」的被测语义。
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");
        LoginResult reviewerC = readyLogin("test_review_teacher_c");

        long r1 = uploadValidatedVideo(student.accessToken(), 9001L, "P7TASKPAGE1");
        long r2 = uploadValidatedVideo(student.accessToken(), 9001L, "P7TASKPAGE2");
        long r3 = uploadValidatedVideo(student.accessToken(), 9001L, "P7TASKPAGE3");
        assign(auditor.accessToken(), r1, REVIEWER_B_USER_ID, REVIEWER_C_USER_ID);
        assign(auditor.accessToken(), r2, REVIEWER_B_USER_ID, REVIEWER_C_USER_ID);
        assign(auditor.accessToken(), r3, REVIEWER_B_USER_ID, REVIEWER_C_USER_ID);

        JsonNode page1 = json(exchange("/api/video/tasks/my?page=1&size=2",
                HttpMethod.GET, reviewerB.accessToken(), null)).at("/data");
        JsonNode page2 = json(exchange("/api/video/tasks/my?page=2&size=2",
                HttpMethod.GET, reviewerB.accessToken(), null)).at("/data");

        assertThat(page1.at("/total").asLong()).isEqualTo(3);
        assertThat(page2.at("/total").asLong()).isEqualTo(3);
        assertThat(page1.at("/records").size()).isEqualTo(2);
        assertThat(page2.at("/records").size()).isEqualTo(1);

        List<Long> reviewIds = new ArrayList<>();
        for (JsonNode task : page1.at("/records")) {
            reviewIds.add(task.at("/videoReviewId").asLong());
        }
        for (JsonNode task : page2.at("/records")) {
            reviewIds.add(task.at("/videoReviewId").asLong());
        }
        assertThat(reviewIds).containsExactlyInAnyOrder(r1, r2, r3);

        // 同一批 3 条 review 也都指派了 reviewerC；reviewerC 查自己的 my tasks 应只看到 reviewerId=自己的任务行，
        // 证明「本人任务」域是 wrapper 内 eq(reviewerId, currentUserId)，而非查询后按当前用户过滤。
        JsonNode reviewerCTasks = json(exchange("/api/video/tasks/my?page=1&size=50",
                HttpMethod.GET, reviewerC.accessToken(), null)).at("/data");
        assertThat(reviewerCTasks.at("/total").asLong()).isEqualTo(3);
        for (JsonNode task : reviewerCTasks.at("/records")) {
            assertThat(task.at("/reviewerId").asLong()).isEqualTo(REVIEWER_C_USER_ID);
        }
    }

    @Test
    void concurrentFinalScoreSubmissionsSettleExactlyOnceWithoutStuckReviewing() throws Exception {
        // Phase 42.3 settle 丢失更新（§7.1）：两评审并发提交末分 → 恰一次结算、review 落定 REVIEW_COMPLETED、不卡 REVIEWING。
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");

        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, "P7-RACE-SETTLE");
        assign(auditor.accessToken(), reviewId, 800000000000003005L, REVIEWER_B_USER_ID);
        long taskA = taskIdByReview(reviewerA.accessToken(), reviewId);
        long taskB = taskIdByReview(reviewerB.accessToken(), reviewId);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> submitA = () -> {
                barrier.await();
                return scoreRaw(reviewerA.accessToken(), taskA, 85, "PASS");
            };
            Callable<Integer> submitB = () -> {
                barrier.await();
                return scoreRaw(reviewerB.accessToken(), taskB, 80, "PASS");
            };
            List<Future<Integer>> futures = executor.invokeAll(List.of(submitA, submitB));
            // 两评审各提交自己的任务，互不冲突，均应成功（结算只发生在观测到票数已满的那个事务里）
            assertThat(futures.get(0).get()).isEqualTo(0);
            assertThat(futures.get(1).get()).isEqualTo(0);
        } finally {
            executor.shutdownNow();
        }

        VideoReview settled = reviewMapper.selectById(reviewId);
        assertThat(settled.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(settled.getFinalScore()).isEqualTo(83);
        assertThat(settled.getFinalConclusion()).isEqualTo("PASS");
        // 恰一次自动结算：未结算的那次提交 oldStatus==newStatus 不记审计
        assertThat(auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "video")
                .eq(SysAuditLog::getBizId, reviewId)
                .eq(SysAuditLog::getOperation, "settle"))).isEqualTo(1L);
        assertThat(taskMapper.selectCount(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, reviewId)
                .eq(VideoReviewTask::getSubmitted, 1))).isEqualTo(2L);
    }

    @Test
    void completeRejectsTamperedEtagAndAllowsCorrectRetry() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P7-ETAG";
        byte[] content = mp4(year);
        JsonNode init = initUpload(student.accessToken(), 9001L, year, "lesson-" + year + ".mp4",
                "video/mp4", content.length, VIDEO_PART_SIZE, videoFingerprint(content), 900);
        String uploadId = init.at("/uploadId").asText();
        PresignedMultipartUploadTestClient.CompletedPart uploaded =
                PresignedMultipartUploadTestClient.put(init.at("/parts/0"), content);
        PresignedMultipartUploadTestClient.CompletedPart tampered =
                new PresignedMultipartUploadTestClient.CompletedPart(1, "\"00000000000000000000000000000000\"", content.length);

        ResponseEntity<String> rejected = exchange("/api/video/upload/complete", HttpMethod.POST,
                student.accessToken(), completeBody(uploadId, 900, List.of(tampered)));
        assertThat(json(rejected).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(rejected).at("/msg").asText()).contains("ETag校验失败");
        VideoUploadSession retryable = sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, uploadId)
                .last("LIMIT 1"));
        assertThat(retryable.getStatus()).isEqualTo("UPLOADING");

        JsonNode completed = complete(student.accessToken(), uploadId, 900, List.of(uploaded));
        assertThat(completed.at("/status").asText()).isEqualTo("WAIT_REVIEW");
    }

    @Test
    void presignedPartRejectsUnexpectedContentLength() throws Exception {
        LoginResult student = readyLogin("test_student");
        String truncatedYear = "P7-LENGTH";
        byte[] truncatedContent = mp4(truncatedYear);
        JsonNode truncatedInit = initUpload(student.accessToken(), 9001L, truncatedYear,
                "length-truncated.mp4", "video/mp4", truncatedContent.length, VIDEO_PART_SIZE,
                videoFingerprint(truncatedContent), 900);
        byte[] truncated = new byte[truncatedContent.length - 1];
        System.arraycopy(truncatedContent, 0, truncated, 0, truncated.length);

        assertThat(PresignedMultipartUploadTestClient.putStatus(
                truncatedInit.at("/parts/0"), truncated)).isEqualTo(403);
        PresignedMultipartUploadTestClient.CompletedPart truncatedRetry =
                PresignedMultipartUploadTestClient.put(truncatedInit.at("/parts/0"), truncatedContent);
        JsonNode truncatedCompleted = complete(student.accessToken(), truncatedInit.at("/uploadId").asText(),
                900, List.of(truncatedRetry));
        assertThat(truncatedCompleted.at("/status").asText()).isEqualTo("WAIT_REVIEW");

        String oversizedYear = "P7-LENGTH-OVER";
        byte[] oversizedContent = mp4(oversizedYear);
        JsonNode oversizedInit = initUpload(student.accessToken(), 9001L, oversizedYear,
                "length-oversized.mp4", "video/mp4", oversizedContent.length, VIDEO_PART_SIZE,
                videoFingerprint(oversizedContent), 900);
        byte[] oversized = new byte[oversizedContent.length + 1];
        System.arraycopy(oversizedContent, 0, oversized, 0, oversizedContent.length);

        assertThat(PresignedMultipartUploadTestClient.putStatus(
                oversizedInit.at("/parts/0"), oversized)).isEqualTo(403);
        PresignedMultipartUploadTestClient.CompletedPart oversizedRetry =
                PresignedMultipartUploadTestClient.put(oversizedInit.at("/parts/0"), oversizedContent);
        JsonNode oversizedCompleted = complete(student.accessToken(), oversizedInit.at("/uploadId").asText(),
                900, List.of(oversizedRetry));
        assertThat(oversizedCompleted.at("/status").asText()).isEqualTo("WAIT_REVIEW");
    }

    @Test
    void expiredPresignedPartUrlIsRejectedByMinio() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P7-EXPIRY";
        byte[] content = mp4(year);
        int originalExpiry = minioProperties.getPresignExpirySeconds();
        JsonNode init;
        try {
            minioProperties.setPresignExpirySeconds(1);
            init = initUpload(student.accessToken(), 9001L, year, "expired.mp4",
                    "video/mp4", content.length, VIDEO_PART_SIZE, videoFingerprint(content), 900);
        } finally {
            minioProperties.setPresignExpirySeconds(originalExpiry);
        }
        String uploadId = init.at("/uploadId").asText();
        VideoUploadSession session = sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, uploadId)
                .last("LIMIT 1"));
        try {
            Thread.sleep(2_100L);
            assertThat(PresignedMultipartUploadTestClient.putStatus(init.at("/parts/0"), content)).isEqualTo(403);
        } finally {
            multipartObjectService.abortUpload(session.getObjectKey(), session.getS3UploadId());
        }
    }

    @Test
    void completeRetryProducesExactlyOneFileObject() throws Exception {
        // WS-3 complete 幂等：客户端因响应丢失重试同一份 ETag 清单，仍只落一个 file_object 和一个 review。
        LoginResult student = readyLogin("test_student");
        Set<Long> preExistingVideoFiles = teachingVideoFileObjectIds();
        String year = "P7-COMP-RETRY";
        byte[] content = mp4(year);
        String hash = videoFingerprint(content);
        JsonNode init = initUpload(student.accessToken(), 9001L, year, "lesson-" + year + ".mp4",
                "video/mp4", content.length, VIDEO_PART_SIZE, hash, 900);
        String uploadId = init.at("/uploadId").asText();
        List<PresignedMultipartUploadTestClient.CompletedPart> parts =
                PresignedMultipartUploadTestClient.putAll(init, content);

        JsonNode first = complete(student.accessToken(), uploadId, 900, parts);
        JsonNode retry = complete(student.accessToken(), uploadId, 900, parts);
        assertThat(retry.at("/id").asLong()).isEqualTo(first.at("/id").asLong());
        assertThat(retry.at("/status").asText()).isEqualTo("WAIT_REVIEW");

        // 核心不变式：本次定稿恰新增一个 teaching-video file_object。
        // WS-1（审计#2）：由「全表 COUNT==1」改为「合并前后快照做差==1」，对共享库常驻的 demo teaching-video
        // file_object 健壮（demo 常驻是本 WS 目标态），仍精确校验「不产生重复行/孤儿」原语义。
        Set<Long> newVideoFiles = teachingVideoFileObjectIds();
        newVideoFiles.removeAll(preExistingVideoFiles);
        assertThat(newVideoFiles).hasSize(1);
        VideoReview review = reviewMapper.selectOne(new LambdaQueryWrapper<VideoReview>()
                .eq(VideoReview::getStudentId, 9001L)
                .eq(VideoReview::getAssessmentYear, year)
                .last("LIMIT 1"));
        assertThat(review).isNotNull();
        assertThat(review.getStatus()).isEqualTo("WAIT_REVIEW");
        assertThat(review.getVideoFileId()).isNotNull();
        // 会话落定 MERGED（未卡 MERGING）
        VideoUploadSession session = sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, uploadId)
                .last("LIMIT 1"));
        assertThat(session.getStatus()).isEqualTo("MERGED");
    }

    @Test
    void mergingSessionBeforeS3CompletionResumesWithoutReleasingActiveSlot() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P7-REC-PENDING";
        String fileName = "lesson-recovery-pending.mp4";
        byte[] content = mp4(year);
        String fingerprint = videoFingerprint(content);
        JsonNode init = initUpload(student.accessToken(), 9001L, year, fileName, "video/mp4",
                content.length, VIDEO_PART_SIZE, fingerprint, 900);
        String uploadId = init.at("/uploadId").asText();
        List<PresignedMultipartUploadTestClient.CompletedPart> uploaded =
                PresignedMultipartUploadTestClient.putAll(init, content);
        VideoUploadSession merging = forceMerging(uploadId, uploaded);

        assertThat(multipartObjectService.findObject(merging.getObjectKey())).isEmpty();
        ResponseEntity<String> differentFile = exchange("/api/video/upload/init", HttpMethod.POST,
                student.accessToken(), initBody(9001L, year, fileName, "video/mp4", content.length,
                        VIDEO_PART_SIZE, videoFingerprint(mp4("different-recovery-file")), 900));
        assertThat(json(differentFile).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(differentFile).at("/msg").asText()).contains("已有其他视频正在上传");

        ResponseEntity<String> cancel = exchange("/api/video/upload/" + uploadId, HttpMethod.DELETE,
                student.accessToken(), null);
        assertThat(json(cancel).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(cancel).at("/msg").asText()).contains("当前不可取消");
        assertThat(uploadSession(uploadId).getStatus()).isEqualTo("MERGING");

        JsonNode resumed = initUpload(student.accessToken(), 9001L, year, fileName, "video/mp4",
                content.length, VIDEO_PART_SIZE, fingerprint, 900);
        assertMergingResume(resumed, uploadId, uploaded);
        JsonNode completed = complete(student.accessToken(), uploadId, 900,
                completedPartsFromResume(resumed.at("/uploadedParts")));

        assertThat(completed.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        VideoUploadSession finalized = uploadSession(uploadId);
        assertThat(finalized.getStatus()).isEqualTo("MERGED");
        assertThat(finalized.getFileId()).isNotNull();
        assertThat(multipartObjectService.findObject(finalized.getObjectKey())).isPresent();
    }

    @Test
    void mergingSessionAfterS3CompletionFinalizesMissingDatabaseMetadataExactlyOnce() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P7-REC-OBJECT";
        String fileName = "lesson-recovery-object.mp4";
        byte[] content = mp4(year);
        String fingerprint = videoFingerprint(content);
        JsonNode init = initUpload(student.accessToken(), 9001L, year, fileName, "video/mp4",
                content.length, VIDEO_PART_SIZE, fingerprint, 900);
        String uploadId = init.at("/uploadId").asText();
        List<PresignedMultipartUploadTestClient.CompletedPart> uploaded =
                PresignedMultipartUploadTestClient.putAll(init, content);
        VideoUploadSession merging = forceMerging(uploadId, uploaded);

        multipartObjectService.completeUpload(merging.getObjectKey(), merging.getS3UploadId(),
                toMultipartUploadedParts(uploaded));
        assertThat(multipartObjectService.findObject(merging.getObjectKey())).isPresent();
        assertThat(uploadSession(uploadId).getFileId()).isNull();
        assertThat(fileObjectMapper.selectCount(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getObjectKey, merging.getObjectKey()))).isZero();
        assertThat(reviewMapper.selectCount(new LambdaQueryWrapper<VideoReview>()
                .eq(VideoReview::getStudentId, 9001L)
                .eq(VideoReview::getAssessmentYear, year))).isZero();

        JsonNode resumed = initUpload(student.accessToken(), 9001L, year, fileName, "video/mp4",
                content.length, VIDEO_PART_SIZE, fingerprint, 900);
        assertMergingResume(resumed, uploadId, uploaded);
        List<PresignedMultipartUploadTestClient.CompletedPart> recoveredParts =
                completedPartsFromResume(resumed.at("/uploadedParts"));
        JsonNode completed = complete(student.accessToken(), uploadId, 900, recoveredParts);
        JsonNode retried = complete(student.accessToken(), uploadId, 900, recoveredParts);

        assertThat(completed.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        assertThat(retried.at("/id").asLong()).isEqualTo(completed.at("/id").asLong());
        VideoUploadSession finalized = uploadSession(uploadId);
        assertThat(finalized.getStatus()).isEqualTo("MERGED");
        assertThat(finalized.getFileId()).isNotNull();
        assertThat(fileObjectMapper.selectCount(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getObjectKey, merging.getObjectKey()))).isEqualTo(1L);
        assertThat(reviewMapper.selectCount(new LambdaQueryWrapper<VideoReview>()
                .eq(VideoReview::getStudentId, 9001L)
                .eq(VideoReview::getAssessmentYear, year))).isEqualTo(1L);
    }

    @Test
    void directCompleteBusinessConflictFailsSessionDeletesObjectAndReleasesSlot() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P7-COMP-CONFLICT";
        long reviewId = uploadValidatedVideo(student.accessToken(), 9001L, year);
        VideoReview returned = reviewMapper.selectById(reviewId);
        Long originalFileId = returned.getVideoFileId();
        returned.setStatus("RETURNED");
        returned.setLocked(0);
        assertThat(reviewMapper.updateById(returned)).isEqualTo(1);

        byte[] replacement = mp4("complete-conflict-replacement");
        JsonNode init = initUpload(student.accessToken(), 9001L, year,
                "complete-conflict-replacement.mp4", "video/mp4", replacement.length,
                VIDEO_PART_SIZE, videoFingerprint(replacement), 900);
        String uploadId = init.at("/uploadId").asText();
        List<PresignedMultipartUploadTestClient.CompletedPart> uploaded =
                PresignedMultipartUploadTestClient.putAll(init, replacement);
        VideoUploadSession merging = forceMerging(uploadId, uploaded);
        assertThat(multipartObjectService.findObject(merging.getObjectKey())).isEmpty();

        VideoReviewTask conflictingTask = new VideoReviewTask();
        conflictingTask.setVideoReviewId(reviewId);
        conflictingTask.setStudentId(returned.getStudentId());
        conflictingTask.setCollegeId(returned.getCollegeId());
        conflictingTask.setReviewerId(REVIEWER_B_USER_ID);
        conflictingTask.setReviewerRole("REVIEWER");
        conflictingTask.setSubmitted(0);
        assertThat(taskMapper.insert(conflictingTask)).isEqualTo(1);
        returned.setStatus("REVIEWING");
        assertThat(reviewMapper.updateById(returned)).isEqualTo(1);

        ResponseEntity<String> rejected = exchange("/api/video/upload/complete", HttpMethod.POST,
                student.accessToken(), completeBody(uploadId, 900, uploaded));
        assertThat(json(rejected).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(rejected).at("/msg").asText()).contains("评审进行中不可重新上传");

        VideoUploadSession failed = uploadSession(uploadId);
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getValidationMessage()).contains("评审进行中不可重新上传");
        assertThat(failed.getFileId()).isNull();
        assertThat(multipartObjectService.findObject(failed.getObjectKey())).isEmpty();
        assertThat(fileObjectMapper.selectCount(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getBucket, minioProperties.getBucket())
                .eq(FileObject::getObjectKey, failed.getObjectKey()))).isZero();
        VideoReview unchanged = reviewMapper.selectById(reviewId);
        assertThat(unchanged.getStatus()).isEqualTo("REVIEWING");
        assertThat(unchanged.getVideoFileId()).isEqualTo(originalFileId);

        assertThat(taskMapper.deleteById(conflictingTask.getId())).isEqualTo(1);
        unchanged.setStatus("RETURNED");
        assertThat(reviewMapper.updateById(unchanged)).isEqualTo(1);
        byte[] recovery = mp4("complete-conflict-recovery");
        JsonNode recovered = initUpload(student.accessToken(), 9001L, year,
                "complete-conflict-recovery.mp4", "video/mp4", recovery.length,
                VIDEO_PART_SIZE, videoFingerprint(recovery), 900);
        String recoveredUploadId = recovered.at("/uploadId").asText();
        assertThat(recoveredUploadId).isNotEqualTo(uploadId);
        assertThat(uploadSession(recoveredUploadId).getStatus()).isEqualTo("UPLOADING");

        ResponseEntity<String> cancelled = exchange("/api/video/upload/" + recoveredUploadId,
                HttpMethod.DELETE, student.accessToken(), null);
        assertThat(json(cancelled).at("/code").asInt()).isZero();
    }

    @Test
    void serverChunkFallbackStillUploadsAndMergesWhenDirectUploadIsDisabled() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P7-SERVER-FALL";
        byte[] content = mp4(year);
        boolean originalDirectUploadEnabled = minioProperties.isDirectUploadEnabled();
        JsonNode init;
        try {
            minioProperties.setDirectUploadEnabled(false);
            init = initUpload(student.accessToken(), 9001L, year, "server-fallback.mp4", "video/mp4",
                    content.length, VIDEO_PART_SIZE, videoFingerprint(content), 900);
        } finally {
            minioProperties.setDirectUploadEnabled(originalDirectUploadEnabled);
        }

        assertThat(init.at("/uploadMode").asText()).isEqualTo("SERVER_CHUNK");
        assertThat(init.at("/instantHit").asBoolean()).isFalse();
        assertThat(init.at("/parts")).isEmpty();
        String uploadId = init.at("/uploadId").asText();
        uploadServerChunk(student.accessToken(), uploadId, 0, content);
        JsonNode merged = mergeServerChunks(student.accessToken(), uploadId, 900);

        assertThat(merged.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        assertThat(uploadSession(uploadId).getStatus()).isEqualTo("MERGED");
        FileObject file = fileObjectMapper.selectById(merged.at("/videoFileId").asLong());
        assertThat(file).isNotNull();
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(file.getBucket()).object(file.getObjectKey()).build())) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    @Test
    void serverChunkFinalizationRecoversAfterEveryDurableCrashBoundary() throws Exception {
        LoginResult student = readyLogin("test_student");
        for (VideoFinalizationHook.ServerChunkStage stage
                : VideoFinalizationHook.ServerChunkStage.values()) {
            String year = "P7-SR-" + stage.ordinal();
            byte[] content = mp4(year);
            JsonNode init;
            boolean originalDirectUploadEnabled = minioProperties.isDirectUploadEnabled();
            try {
                minioProperties.setDirectUploadEnabled(false);
                init = initUpload(student.accessToken(), 9001L, year,
                        "server-recovery-" + stage.name().toLowerCase() + ".mp4", "video/mp4",
                        content.length, VIDEO_PART_SIZE, videoFingerprint(content), 900);
            } finally {
                minioProperties.setDirectUploadEnabled(originalDirectUploadEnabled);
            }
            String uploadId = init.at("/uploadId").asText();
            uploadServerChunk(student.accessToken(), uploadId, 0, content);

            controlledVideoFinalizationHook.arm(uploadId, stage);
            ResponseEntity<String> interrupted = exchange("/api/video/upload/merge", HttpMethod.POST,
                    student.accessToken(), Map.of("uploadId", uploadId, "durationSeconds", 900));
            assertThat(json(interrupted).at("/code").asInt()).isNotZero();

            VideoUploadSession recoveryPoint = uploadSession(uploadId);
            assertThat(recoveryPoint.getStatus()).isEqualTo("MERGING");
            assertThat(recoveryPoint.getFinalizationToken()).isPositive();
            assertThat(recoveryPoint.getObjectKey())
                    .isEqualTo("teaching-video/server-finalized/" + uploadId + ".mp4");

            JsonNode recovered = mergeServerChunks(student.accessToken(), uploadId, 900);
            JsonNode idempotentRetry = mergeServerChunks(student.accessToken(), uploadId, 900);
            assertThat(recovered.at("/status").asText()).isEqualTo("WAIT_REVIEW");
            assertThat(idempotentRetry.at("/id").asLong()).isEqualTo(recovered.at("/id").asLong());

            VideoUploadSession completed = uploadSession(uploadId);
            assertThat(completed.getStatus()).isEqualTo("MERGED");
            assertThat(completed.getFinalizationToken()).isNull();
            assertThat(completed.getObjectKey()).isEqualTo(recoveryPoint.getObjectKey());
            assertThat(fileObjectMapper.selectCount(new LambdaQueryWrapper<FileObject>()
                    .eq(FileObject::getObjectKey, completed.getObjectKey()))).isEqualTo(1L);
            assertThat(reviewMapper.selectCount(new LambdaQueryWrapper<VideoReview>()
                    .eq(VideoReview::getStudentId, 9001L)
                    .eq(VideoReview::getAssessmentYear, year))).isEqualTo(1L);
        }
    }

    @Test
    void activeFinalizationLeaseRenewsAndReleasedLeaseGetsHigherFencingToken()
            throws InterruptedException {
        String uploadId = "P7-LEASE-" + System.nanoTime();
        long firstToken;
        try (VideoFinalizeSingleFlight.Lease first = videoFinalizeSingleFlight.tryAcquire(uploadId)) {
            assertThat(first).isNotNull();
            firstToken = first.token();
            Thread.sleep(1_500L);
            first.assertOwned();
            assertThat(videoFinalizeSingleFlight.tryAcquire(uploadId)).isNull();
        }

        try (VideoFinalizeSingleFlight.Lease successor = videoFinalizeSingleFlight.tryAcquire(uploadId)) {
            assertThat(successor).isNotNull();
            successor.assertOwned();
            assertThat(successor.token()).isGreaterThan(firstToken);
        }
    }

    @Test
    void largePresignedMultipartUploadCompletesWithExpectedObjectMetadataAndBytes() throws Exception {
        // 两片浏览器直传：首片满足 S3 5MiB 下限，末片可小于下限；最终对象保留 multipart ETag。
        LoginResult studentLogin = readyLogin("test_student");
        Set<Long> preExistingVideoFiles = teachingVideoFileObjectIds();
        String year = "P7-MULTIPART";
        int partSize = 5 * 1024 * 1024;                      // 恰 MinIO 部件下限 MIN_MULTIPART_SIZE(=5MiB)
        byte[] content = filledMp4Payload(partSize + 4096);  // 2 片：首片 5MiB(≥下限)、末片 4096B(<下限，允许)
        String hash = videoFingerprint(content);

        JsonNode init = initUpload(studentLogin.accessToken(), 9001L, year, "lesson-" + year + ".mp4",
                "video/mp4", content.length, partSize, hash, 900);
        assertThat(init.at("/parts").size()).isEqualTo(2);
        String uploadId = init.at("/uploadId").asText();
        List<PresignedMultipartUploadTestClient.CompletedPart> parts =
                PresignedMultipartUploadTestClient.putAll(init, content);

        JsonNode completed = complete(studentLogin.accessToken(), uploadId, 900, parts);
        assertThat(completed.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        assertThat(completed.at("/formatCheck").asText()).isEqualTo("PASS");

        // ② 本次合并恰新增一个 teaching-video file_object（WS-1：合并前后快照做差，对常驻 demo file_object 健壮）
        Set<Long> newVideoFiles = teachingVideoFileObjectIds();
        newVideoFiles.removeAll(preExistingVideoFiles);
        assertThat(newVideoFiles).hasSize(1);

        VideoReview review = reviewMapper.selectOne(new LambdaQueryWrapper<VideoReview>()
                .eq(VideoReview::getStudentId, 9001L)
                .eq(VideoReview::getAssessmentYear, year)
                .last("LIMIT 1"));
        assertThat(review).isNotNull();
        FileObject fileObject = fileObjectMapper.selectById(review.getVideoFileId());
        assertThat(fileObject).isNotNull();

        String bucket = minioProperties.getBucket();
        StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket).object(fileObject.getObjectKey()).build());
        assertThat(stat.size()).isEqualTo((long) content.length);
        assertThat(stat.contentType()).isEqualTo("video/mp4");
        assertThat(stat.etag()).contains("-");

        // 字节级正确：下载最终对象与浏览器 PUT 的原始内容逐字节一致。
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(fileObject.getObjectKey()).build())) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }
    }

    private int scoreRaw(String token, long taskId, int score, String conclusion) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/tasks/" + taskId + "/score", HttpMethod.POST,
                token, scoreBody(score, conclusion));
        return json(response).at("/code").asInt();
    }

    private long uploadValidatedVideo(String token, long studentId, String year) throws Exception {
        byte[] content = mp4(year);
        String hash = videoFingerprint(content);
        JsonNode init = initUpload(token, studentId, year, "lesson-" + year + ".mp4", "video/mp4",
                content.length, VIDEO_PART_SIZE, hash, 900);
        String uploadId = init.at("/uploadId").asText();
        List<PresignedMultipartUploadTestClient.CompletedPart> parts =
                PresignedMultipartUploadTestClient.putAll(init, content);
        JsonNode completed = complete(token, uploadId, 900, parts);
        assertThat(completed.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        return completed.at("/id").asLong();
    }

    private VideoUploadSession forceMerging(
            String uploadId, List<PresignedMultipartUploadTestClient.CompletedPart> parts) {
        VideoUploadSession session = uploadSession(uploadId);
        assertThat(session.getStatus()).isEqualTo("UPLOADING");
        for (PresignedMultipartUploadTestClient.CompletedPart part : parts) {
            VideoUploadChunk chunk = new VideoUploadChunk();
            chunk.setUploadId(uploadId);
            chunk.setChunkIndex(part.partNumber() - 1);
            chunk.setPartNumber(part.partNumber());
            chunk.setEtag(part.etag());
            chunk.setChunkSize(part.size());
            chunk.setObjectKey(session.getObjectKey());
            chunk.setUploadedAt(LocalDateTime.now());
            assertThat(chunkMapper.insert(chunk)).isEqualTo(1);
        }
        session.setUploadedChunks(parts.size());
        session.setUploadedBytes(parts.stream()
                .mapToLong(PresignedMultipartUploadTestClient.CompletedPart::size)
                .sum());
        session.setStatus("MERGING");
        assertThat(sessionMapper.updateById(session)).isEqualTo(1);
        return uploadSession(uploadId);
    }

    private VideoUploadSession uploadSession(String uploadId) {
        VideoUploadSession session = sessionMapper.selectOne(new LambdaQueryWrapper<VideoUploadSession>()
                .eq(VideoUploadSession::getUploadId, uploadId)
                .last("LIMIT 1"));
        assertThat(session).isNotNull();
        return session;
    }

    private void assertMergingResume(JsonNode resumed, String uploadId,
                                     List<PresignedMultipartUploadTestClient.CompletedPart> expectedParts) {
        assertThat(resumed.at("/uploadId").asText()).isEqualTo(uploadId);
        assertThat(resumed.at("/uploadMode").asText()).isEqualTo("PRESIGNED_MULTIPART");
        assertThat(resumed.at("/status").asText()).isEqualTo("MERGING");
        assertThat(resumed.at("/parts")).isEmpty();
        assertThat(resumed.at("/uploadedChunks")).hasSize(expectedParts.size());
        assertThat(resumed.at("/uploadedParts")).hasSize(expectedParts.size());
        for (int index = 0; index < expectedParts.size(); index++) {
            PresignedMultipartUploadTestClient.CompletedPart expected = expectedParts.get(index);
            assertThat(resumed.at("/uploadedChunks/" + index).asInt()).isEqualTo(expected.partNumber() - 1);
            JsonNode actual = resumed.at("/uploadedParts/" + index);
            assertThat(actual.at("/partNumber").asInt()).isEqualTo(expected.partNumber());
            assertThat(actual.at("/etag").asText()).isEqualTo(expected.etag());
            assertThat(actual.at("/size").asLong()).isEqualTo(expected.size());
        }
    }

    private List<PresignedMultipartUploadTestClient.CompletedPart> completedPartsFromResume(JsonNode parts) {
        List<PresignedMultipartUploadTestClient.CompletedPart> completed = new ArrayList<>();
        for (JsonNode part : parts) {
            completed.add(new PresignedMultipartUploadTestClient.CompletedPart(
                    part.at("/partNumber").asInt(), part.at("/etag").asText(), part.at("/size").asLong()));
        }
        return completed;
    }

    private List<MultipartUploadedPart> toMultipartUploadedParts(
            List<PresignedMultipartUploadTestClient.CompletedPart> parts) {
        return parts.stream()
                .map(part -> new MultipartUploadedPart(part.partNumber(), part.etag(), part.size()))
                .toList();
    }

    private JsonNode initUpload(String token, long studentId, String year, String fileName, String contentType,
                                long size, long chunkSize, String md5, int duration) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/upload/init", HttpMethod.POST, token,
                initBody(studentId, year, fileName, contentType, size, chunkSize, md5, duration));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data");
    }

    private Map<String, Object> initBody(long studentId, String year, String fileName, String contentType,
                                         long size, long chunkSize, String md5, int duration) {
        return Map.of(
                "studentId", studentId,
                "assessmentYear", year,
                "fileMd5", md5,
                "fileName", fileName,
                "contentType", contentType,
                "size", size,
                "chunkSize", chunkSize,
                "durationSeconds", duration
        );
    }

    private JsonNode complete(String token, String uploadId, int duration,
                              List<PresignedMultipartUploadTestClient.CompletedPart> parts) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/upload/complete", HttpMethod.POST, token,
                completeBody(uploadId, duration, parts));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data");
    }

    private Map<String, Object> completeBody(String uploadId, int duration,
                                             List<PresignedMultipartUploadTestClient.CompletedPart> parts) {
        return Map.of(
                "uploadId", uploadId,
                "durationSeconds", duration,
                "parts", PresignedMultipartUploadTestClient.completionParts(parts)
        );
    }

    private void uploadServerChunk(String token, String uploadId, int index, byte[] content) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("uploadId", uploadId);
        body.add("index", String.valueOf(index));
        body.add("md5", md5(content));
        body.add("file", multipartResource("chunk-" + index, "application/octet-stream", content));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.exchange(url("/api/video/upload/chunk"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private JsonNode mergeServerChunks(String token, String uploadId, int duration) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/upload/merge", HttpMethod.POST, token,
                Map.of("uploadId", uploadId, "durationSeconds", duration));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data");
    }

    private void assign(String token, long reviewId, Long... reviewerIds) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/reviews/" + reviewId + "/assign", HttpMethod.POST,
                token, Map.of("reviewerIds", List.of(reviewerIds)));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void assignGroup(String token, long reviewId, long groupId) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/reviews/" + reviewId + "/assign", HttpMethod.POST,
                token, Map.of("groupId", groupId));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private long createReviewerGroup(String token, String name) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/reviewer-groups", HttpMethod.POST,
                token, Map.of("name", name, "status", "ENABLED"));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
        return json(response).at("/data/id").asLong();
    }

    private void addReviewerGroupMember(String token, long groupId, long reviewerUserId) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/reviewer-groups/" + groupId + "/members", HttpMethod.POST,
                token, Map.of("reviewerUserId", reviewerUserId));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void score(String token, long taskId, int score, String conclusion) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/tasks/" + taskId + "/score", HttpMethod.POST,
                token, scoreBody(score, conclusion));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void thirdReview(String token, long reviewId, long reviewerId, int score, String conclusion) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(scoreBody(score, conclusion));
        body.put("reviewerId", reviewerId);
        ResponseEntity<String> response = exchange("/api/video/reviews/" + reviewId + "/third-review", HttpMethod.POST,
                token, body);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void confirmVideo(String token, long reviewId) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/reviews/" + reviewId + "/confirm", HttpMethod.POST,
                token, Map.of());
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void returnVideo(String token, long reviewId, String comment) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/reviews/" + reviewId + "/return", HttpMethod.POST,
                token, Map.of("comment", comment));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private Map<String, Object> scoreBody(int score, String conclusion) {
        Map<String, Integer> dimensions = new LinkedHashMap<>();
        dimensions.put("lesson_design", 10);
        dimensions.put("teaching_objective", 10);
        dimensions.put("key_difficulty", 10);
        dimensions.put("teaching_implementation", 10);
        dimensions.put("classroom_organization", 10);
        dimensions.put("subject_literacy", 10);
        dimensions.put("language_expression", 10);
        dimensions.put("courseware_blackboard", 10);
        dimensions.put("teaching_reflection", 10);
        return Map.of(
                "score", score,
                "dimensionScores", dimensions,
                "comment", "评分意见-" + score,
                "conclusion", conclusion
        );
    }

    private long taskId(String token) throws Exception {
        JsonNode tasks = json(exchange("/api/video/tasks/my", HttpMethod.GET, token, null)).at("/data/records");
        assertThat(tasks.size()).isGreaterThan(0);
        return tasks.at("/0/id").asLong();
    }

    private long taskIdByReview(String token, long reviewId) throws Exception {
        JsonNode tasks = json(exchange("/api/video/tasks/my", HttpMethod.GET, token, null)).at("/data/records");
        for (JsonNode task : tasks) {
            if (task.at("/videoReviewId").asLong() == reviewId) {
                return task.at("/id").asLong();
            }
        }
        throw new AssertionError("task not found for review " + reviewId);
    }

    private Long taskCount(long reviewId) {
        return taskMapper.selectCount(new LambdaQueryWrapper<VideoReviewTask>()
                .eq(VideoReviewTask::getVideoReviewId, reviewId));
    }

    private boolean hasCandidate(JsonNode candidates, long userId) {
        for (JsonNode candidate : candidates) {
            if (candidate.at("/id").asLong() == userId) {
                return true;
            }
        }
        return false;
    }

    private HttpEntity<ByteArrayResource> multipartResource(
            String filename, String contentType, byte[] content) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        return new HttpEntity<>(resource, headers);
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private LoginResult readyLogin(String username) throws Exception {
        LoginResult result = loginFlexibly(username);
        if (result.mustChangePwd()) {
            changePassword(result.accessToken(), INITIAL_PASSWORD, CHANGED_PASSWORD);
            result = login(username, CHANGED_PASSWORD);
        }
        return result;
    }

    private LoginResult loginFlexibly(String username) throws Exception {
        ResponseEntity<String> initial = loginRaw(username, INITIAL_PASSWORD);
        assertThat(initial.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode initialRoot = json(initial);
        if (initialRoot.at("/code").asInt() == 0) {
            JsonNode data = initialRoot.at("/data");
            return new LoginResult(
                    data.at("/accessToken").asText(),
                    data.at("/refreshToken").asText(),
                    data.at("/mustChangePwd").asBoolean()
            );
        }
        return login(username, CHANGED_PASSWORD);
    }

    private LoginResult login(String username, String password) throws Exception {
        ResponseEntity<String> response = loginRaw(username, password);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        JsonNode data = root.at("/data");
        return new LoginResult(
                data.at("/accessToken").asText(),
                data.at("/refreshToken").asText(),
                data.at("/mustChangePwd").asBoolean()
        );
    }

    private ResponseEntity<String> loginRaw(String username, String password) throws Exception {
        JsonNode captcha = json(rest.getForEntity(url("/api/auth/captcha"), String.class)).at("/data");
        return rest.postForEntity(url("/api/auth/login"), Map.of(
                "username", username,
                "password", password,
                "captchaId", captcha.at("/captchaId").asText(),
                "captchaCode", captchaCode(captcha.at("/image").asText())
        ), String.class);
    }

    private void changePassword(String accessToken, String oldPassword, String newPassword) {
        ResponseEntity<String> response = exchange("/api/auth/change-pwd", HttpMethod.POST, accessToken, Map.of(
                "oldPassword", oldPassword,
                "newPassword", newPassword
        ));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String captchaCode(String image) {
        String svg = new String(java.util.Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1)),
                java.nio.charset.StandardCharsets.UTF_8);
        return svg.replaceAll("(?s).*<text[^>]*>([^<]+)</text>.*", "$1").trim();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private void resetUser(String username, boolean mustChangePwd) {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            return;
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setStatus("ENABLED");
        user.setMustChangePwd(mustChangePwd ? 1 : 0);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(null);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getLastLoginAt, null));
    }

    private void resetSeedStudents() {
        Student studentA = studentMapper.selectById(9001L);
        if (studentA != null) {
            studentA.setCollegeId(COLLEGE_A);
            studentA.setStatus("DRAFT");
            studentA.setLocked(0);
            studentMapper.updateById(studentA);
        }
        Student studentB = studentMapper.selectById(9002L);
        if (studentB != null) {
            studentB.setCollegeId(COLLEGE_B);
            studentB.setStatus("DRAFT");
            studentB.setLocked(0);
            studentMapper.updateById(studentB);
        }
    }

    private void ensureSecondCollegeStudent() {
        SysUser user = userMapper.selectByUsername("test_student_b");
        if (user == null) {
            user = new SysUser();
            user.setId(STUDENT_B_USER_ID);
            user.setUsername("test_student_b");
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setRealName("学生测试账号B");
        user.setStatus("ENABLED");
        user.setUserType("STUDENT");
        user.setCollegeId(COLLEGE_B);
        user.setStudentId(9002L);
        user.setMustChangePwd(1);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(null);
        if (userMapper.selectByUsername("test_student_b") == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        userRoleMapper.upsert(STUDENT_B_ROLE_ID, user.getId(), STUDENT_ROLE_ID, 0L);
    }

    private void ensureReviewer(String username, long userId, long userRoleId, String realName) {
        ensureReviewer(username, userId, userRoleId, realName, COLLEGE_A);
    }

    private void ensureReviewer(String username, long userId, long userRoleId, String realName, long collegeId) {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            user = new SysUser();
            user.setId(userId);
            user.setUsername(username);
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setRealName(realName);
        user.setWorkNo(username.toUpperCase());
        user.setStatus("ENABLED");
        user.setUserType("STAFF");
        user.setCollegeId(collegeId);
        user.setStudentId(null);
        user.setMustChangePwd(1);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(null);
        if (userMapper.selectByUsername(username) == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        userRoleMapper.upsert(userRoleId, user.getId(), REVIEW_TEACHER_ROLE_ID, 0L);
    }

    private void resetParam(String key, String value) {
        SysParam param = paramMapper.selectOne(new LambdaQueryWrapper<SysParam>().eq(SysParam::getParamKey, key).last("LIMIT 1"));
        if (param == null) {
            return;
        }
        param.setParamValue(value);
        paramMapper.updateById(param);
        // Phase 44c（§7.3）：测试越过 service 直接改库，须与生产 updateParam 一样逐出参数缓存，否则经 ParamService 读到旧值。
        org.springframework.cache.Cache paramCache = cacheManager.getCache("sysParam");
        if (paramCache != null) {
            paramCache.clear();
        }
    }

    private void updateParam(String key, String value) {
        resetParam(key, value);
    }

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM reviewer_group_member WHERE group_id IN (SELECT id FROM reviewer_group WHERE name LIKE 'WP-D%')");
        jdbcTemplate.update("DELETE FROM reviewer_group WHERE name LIKE 'WP-D%'");
        // WS-1（审计#2）：先收集本测试（P7% 年度）自建 teaching-video file_object 的 id，稍后只删这些——保留 demo
        // 常驻的 teaching-video file_object（demo video_review 经 video_file_id 引用它，全表删会毁掉 demo 视频可播放性，
        // 与「WS-1 落地后 demo 可常驻共享库」的目标冲突）。
        List<Long> ownVideoFileIds = jdbcTemplate.queryForList(
                "SELECT file_id FROM video_upload_session WHERE assessment_year LIKE 'P7%' AND file_id IS NOT NULL "
                        + "UNION SELECT video_file_id FROM video_review WHERE assessment_year LIKE 'P7%' AND video_file_id IS NOT NULL",
                Long.class);
        jdbcTemplate.update("DELETE FROM video_review_task WHERE video_review_id IN (SELECT id FROM video_review WHERE assessment_year LIKE 'P7%')");
        jdbcTemplate.update("DELETE FROM video_review WHERE assessment_year LIKE 'P7%'");
        jdbcTemplate.update("DELETE FROM video_upload_chunk WHERE upload_id IN (SELECT upload_id FROM video_upload_session WHERE assessment_year LIKE 'P7%')");
        jdbcTemplate.update("DELETE FROM video_upload_session WHERE assessment_year LIKE 'P7%'");
        if (!ownVideoFileIds.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(ownVideoFileIds.size(), "?"));
            jdbcTemplate.update("DELETE FROM file_object WHERE biz_type = 'teaching-video' AND id IN (" + placeholders + ")",
                    ownVideoFileIds.toArray());
        }
    }

    // WS-1：当前所有 teaching-video file_object 的 id 快照；两处「恰一个文件对象」不变式改为「合并前后做差==1」，
    // 对共享库常驻的 demo teaching-video file_object 健壮（demo 常驻是本 WS 的目标状态）。
    private Set<Long> teachingVideoFileObjectIds() {
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT id FROM file_object WHERE biz_type = 'teaching-video'", Long.class));
    }

    private byte[] mp4(String text) {
        byte[] source = sampleVideo();
        byte[] marker = text.getBytes(StandardCharsets.UTF_8);
        byte[] result = Arrays.copyOf(source, source.length + 8 + marker.length);
        ByteBuffer atom = ByteBuffer.wrap(result, source.length, 8 + marker.length);
        atom.putInt(8 + marker.length);
        atom.put("free".getBytes(StandardCharsets.US_ASCII));
        atom.put(marker);
        return result;
    }

    /** 在真实 H.264 MP4 后追加合法 free box，构造指定大小的多分片媒体。 */
    private byte[] filledMp4Payload(int size) {
        byte[] source = sampleVideo();
        if (size < source.length + 8) {
            throw new IllegalArgumentException("目标大小不足以容纳样例视频");
        }
        byte[] out = Arrays.copyOf(source, size);
        ByteBuffer atom = ByteBuffer.wrap(out, source.length, size - source.length);
        atom.putInt(size - source.length);
        atom.put("free".getBytes(StandardCharsets.US_ASCII));
        for (int index = source.length + 8; index < out.length; index++) {
            out[index] = (byte) ((index * 31 + 7) & 0xff);
        }
        return out;
    }

    private byte[] sampleVideo() {
        try (InputStream input = getClass().getResourceAsStream("/db/demo/sample-video.mp4")) {
            if (input == null) {
                throw new IllegalStateException("测试样例视频不存在");
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] shortVideo() {
        try (InputStream input = getClass().getResourceAsStream("/media/short-video.mp4")) {
            if (input == null) {
                throw new IllegalStateException("短视频测试样例不存在");
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] withTrackHeaderDuration(byte[] source, int durationSeconds) {
        byte[] result = Arrays.copyOf(source, source.length);
        for (int index = 4; index + 24 <= result.length; index++) {
            if (result[index] == 'm' && result[index + 1] == 'd'
                    && result[index + 2] == 'h' && result[index + 3] == 'd') {
                ByteBuffer box = ByteBuffer.wrap(result);
                int timescale = box.getInt(index + 16);
                long durationUnits = (long) timescale * durationSeconds;
                if (timescale < 1 || durationUnits > 0xffff_ffffL) {
                    throw new IllegalStateException("测试视频mdhd时间尺度不合法");
                }
                box.putInt(index + 20, (int) durationUnits);
                return result;
            }
        }
        throw new IllegalStateException("测试视频缺少mdhd轨道头");
    }

    private FileObject seedReadyVideoFile(String hash, byte[] content, String marker, Long uploaderId) {
        String storedName = "phase7-" + marker + ".mp4";
        FileObject file = new FileObject();
        file.setOriginalName(storedName);
        file.setStoredName(storedName);
        file.setBucket(minioProperties.getBucket());
        file.setObjectKey("teaching-video/" + storedName);
        file.setSize((long) content.length);
        file.setContentType("video/mp4");
        file.setMd5(hash);
        file.setBizType("teaching-video");
        file.setStatus("READY");
        file.setUploaderId(uploaderId);
        file.setUploadTime(LocalDateTime.now());
        assertThat(fileObjectMapper.insert(file)).isEqualTo(1);
        return file;
    }

    private String videoFingerprint(byte[] bytes) throws Exception {
        MessageDigest leafDigest = MessageDigest.getInstance("SHA-256");
        List<byte[]> leafDigests = new ArrayList<>();
        for (int offset = 0; offset < bytes.length; offset += VIDEO_PART_SIZE) {
            int length = Math.min(VIDEO_PART_SIZE, bytes.length - offset);
            leafDigest.update(bytes, offset, length);
            leafDigests.add(leafDigest.digest());
        }
        ByteBuffer root = ByteBuffer.allocate(VIDEO_FINGERPRINT_MARKER.length + 24 + leafDigests.size() * 32);
        root.put(VIDEO_FINGERPRINT_MARKER);
        root.putLong(bytes.length);
        root.putLong(VIDEO_PART_SIZE);
        root.putLong(leafDigests.size());
        leafDigests.forEach(root::put);
        return hex(MessageDigest.getInstance("SHA-256").digest(root.array()));
    }

    private String md5(byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance("MD5").digest(bytes));
    }

    private String hex(byte[] digest) {
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeTestConfiguration {

        @Bean
        @Primary
        CountingVideoMediaProbe countingVideoMediaProbe(JcodecVideoMediaProbe delegate) {
            return new CountingVideoMediaProbe(delegate);
        }

        @Bean
        @Primary
        ControlledVideoFinalizationHook controlledVideoFinalizationHook() {
            return new ControlledVideoFinalizationHook();
        }
    }

    static final class ControlledVideoFinalizationHook extends VideoFinalizationHook {

        private final AtomicReference<Failpoint> armed = new AtomicReference<>();

        void arm(String uploadId, ServerChunkStage stage) {
            armed.set(new Failpoint(uploadId, stage));
        }

        void reset() {
            armed.set(null);
        }

        @Override
        public void afterServerChunkStage(ServerChunkStage stage, String uploadId) {
            Failpoint failpoint = armed.get();
            if (failpoint != null && failpoint.uploadId().equals(uploadId)
                    && failpoint.stage() == stage && armed.compareAndSet(failpoint, null)) {
                throw new SimulatedNodeExitException("模拟节点退出: " + stage);
            }
        }

        private record Failpoint(String uploadId, ServerChunkStage stage) {
        }
    }

    static final class SimulatedNodeExitException extends RuntimeException {

        private SimulatedNodeExitException(String message) {
            super(message);
        }
    }

    static final class CountingVideoMediaProbe implements VideoMediaProbe {

        private final JcodecVideoMediaProbe delegate;
        private final AtomicInteger invocationCount = new AtomicInteger();
        private volatile CountDownLatch entered = new CountDownLatch(0);
        private volatile CountDownLatch release = new CountDownLatch(0);

        private CountingVideoMediaProbe(JcodecVideoMediaProbe delegate) {
            this.delegate = delegate;
        }

        void arm() {
            invocationCount.set(0);
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        boolean awaitEntered() throws InterruptedException {
            return entered.await(10, TimeUnit.SECONDS);
        }

        void release() {
            release.countDown();
        }

        int invocations() {
            return invocationCount.get();
        }

        void reset() {
            release();
            invocationCount.set(0);
            entered = new CountDownLatch(0);
            release = new CountDownLatch(0);
        }

        @Override
        public VideoMediaInspection inspect(String objectKey, long expectedSize, String declaredFingerprint) {
            invocationCount.incrementAndGet();
            CountDownLatch currentEntered = entered;
            CountDownLatch currentRelease = release;
            if (currentEntered.getCount() > 0) {
                currentEntered.countDown();
                try {
                    if (!currentRelease.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("测试媒体探测闸门等待超时");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("测试媒体探测闸门被中断", e);
                }
            }
            return delegate.inspect(objectKey, expectedSize, declaredFingerprint);
        }

        @Override
        public String currentPolicyHash() {
            return delegate.currentPolicyHash();
        }

        @Override
        public String probeVersion() {
            return delegate.probeVersion();
        }
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
