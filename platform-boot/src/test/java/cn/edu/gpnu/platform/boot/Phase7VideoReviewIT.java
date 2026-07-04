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
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
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
    }

    @Test
    void chunkUploadMergeResumeAndInstantHitWorkWithValidation() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] content = mp4("phase7-ok");
        String md5 = md5(content);

        JsonNode init = initUpload(student.accessToken(), 9001L, YEAR, "lesson.mp4", "video/mp4", content.length, 4, md5, 900);
        String uploadId = init.at("/uploadId").asText();
        uploadChunk(student.accessToken(), uploadId, 0, slice(content, 0, 4));
        JsonNode resume = initUpload(student.accessToken(), 9001L, YEAR, "lesson.mp4", "video/mp4", content.length, 4, md5, 900);
        assertThat(resume.at("/uploadedChunks").toString()).contains("0");
        for (int offset = 4, index = 1; offset < content.length; offset += 4, index++) {
            uploadChunk(student.accessToken(), uploadId, index, slice(content, offset, Math.min(4, content.length - offset)));
        }

        JsonNode merged = merge(student.accessToken(), uploadId, 900);
        assertThat(merged.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        assertThat(merged.at("/formatCheck").asText()).isEqualTo("PASS");

        JsonNode instant = initUpload(student.accessToken(), 9001L, "P7-INSTANT", "lesson.mp4", "video/mp4", content.length, 4, md5, 900);
        assertThat(instant.at("/instantHit").asBoolean()).isTrue();
        assertThat(instant.at("/status").asText()).isEqualTo("WAIT_REVIEW");
    }

    @Test
    void nonMp4OrInvalidDurationAreRejectedByServerValidation() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] avi = "RIFF-AVI".getBytes();
        ResponseEntity<String> badType = exchange("/api/video/upload/init", HttpMethod.POST, student.accessToken(),
                initBody(9001L, YEAR, "bad.avi", "video/x-msvideo", avi.length, 4, md5(avi), 900));
        assertThat(json(badType).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(badType).at("/msg").asText()).contains("视频格式必须为MP4");

        byte[] content = mp4("duration");
        JsonNode init = initUpload(student.accessToken(), 9001L, "P7-DURATION", "duration.mp4", "video/mp4",
                content.length, 4, md5(content), 1200);
        String uploadId = init.at("/uploadId").asText();
        uploadAll(student.accessToken(), uploadId, content, 4);
        JsonNode merged = merge(student.accessToken(), uploadId, 1200);
        assertThat(merged.at("/status").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(merged.at("/validationMessage").asText()).contains("视频时长超出容差");
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
        String replacementMd5 = md5(replacement);
        JsonNode replacementInit = initUpload(student.accessToken(), 9001L, reviewingYear,
                "replacement.mp4", "video/mp4", replacement.length, 4, replacementMd5, 900);
        String replacementUploadId = replacementInit.at("/uploadId").asText();
        uploadAll(student.accessToken(), replacementUploadId, replacement, 4);

        assign(auditor.accessToken(), reviewingId, 800000000000003005L, REVIEWER_B_USER_ID);
        VideoReview beforeReviewing = reviewMapper.selectById(reviewingId);
        Long beforeTaskCount = taskCount(reviewingId);

        byte[] original = mp4(reviewingYear);
        ResponseEntity<String> instantHit = exchange("/api/video/upload/init", HttpMethod.POST, student.accessToken(),
                initBody(9001L, reviewingYear, "lesson-" + reviewingYear + ".mp4",
                        "video/mp4", original.length, 4, md5(original), 900));
        assertThat(json(instantHit).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(instantHit).at("/msg").asText()).contains("评审进行中不可重新上传");

        ResponseEntity<String> mergeAgain = exchange("/api/video/upload/merge", HttpMethod.POST, student.accessToken(),
                Map.of("uploadId", replacementUploadId, "durationSeconds", 900));
        assertThat(json(mergeAgain).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(mergeAgain).at("/msg").asText()).contains("评审进行中不可重新上传");
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
                        "video/mp4", needReviewFile.length, 4, md5(needReviewFile), 900));
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
        String md5 = md5(replacement);
        JsonNode init = initUpload(student.accessToken(), 9001L, year,
                "lesson-" + year + "-reupload.mp4", "video/mp4", replacement.length, 4, md5, 900);
        assertThat(init.at("/instantHit").asBoolean()).isFalse();
        String uploadId = init.at("/uploadId").asText();
        uploadAll(student.accessToken(), uploadId, replacement, 4);
        JsonNode merged = merge(student.accessToken(), uploadId, 900);
        assertThat(merged.at("/id").asLong()).isEqualTo(reviewId);
        assertThat(merged.at("/status").asText()).isEqualTo("WAIT_REVIEW");
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
                        replacement.length, 4, md5(replacement), 900));
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
                initBody(9002L, YEAR, "cross.mp4", "video/mp4", 8, 4, md5(mp4("cross")), 900));
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
        LoginResult student = readyLogin("test_student");
        LoginResult auditor = readyLogin("test_college_auditor");
        LoginResult reviewerA = readyLogin("test_review_teacher");
        LoginResult reviewerB = readyLogin("test_review_teacher_b");

        long r1 = uploadValidatedVideo(student.accessToken(), 9001L, "P7TASKPAGE1");
        long r2 = uploadValidatedVideo(student.accessToken(), 9001L, "P7TASKPAGE2");
        long r3 = uploadValidatedVideo(student.accessToken(), 9001L, "P7TASKPAGE3");
        assign(auditor.accessToken(), r1, 800000000000003005L, REVIEWER_B_USER_ID);
        assign(auditor.accessToken(), r2, 800000000000003005L, REVIEWER_B_USER_ID);
        assign(auditor.accessToken(), r3, 800000000000003005L, REVIEWER_B_USER_ID);

        JsonNode page1 = json(exchange("/api/video/tasks/my?page=1&size=2",
                HttpMethod.GET, reviewerA.accessToken(), null)).at("/data");
        JsonNode page2 = json(exchange("/api/video/tasks/my?page=2&size=2",
                HttpMethod.GET, reviewerA.accessToken(), null)).at("/data");

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

        // 同一批 3 条 review 也都指派了 reviewerB；reviewerB 查自己的 my tasks 应只看到 reviewerId=自己的任务行，
        // 证明「本人任务」域是 wrapper 内 eq(reviewerId, currentUserId)，而非查询后按当前用户过滤。
        JsonNode reviewerBTasks = json(exchange("/api/video/tasks/my?page=1&size=50",
                HttpMethod.GET, reviewerB.accessToken(), null)).at("/data");
        assertThat(reviewerBTasks.at("/total").asLong()).isEqualTo(3);
        for (JsonNode task : reviewerBTasks.at("/records")) {
            assertThat(task.at("/reviewerId").asLong()).isEqualTo(REVIEWER_B_USER_ID);
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
    void concurrentMergeProducesExactlyOneFileObject() throws Exception {
        // Phase 42.3 merge 幂等（§7.1）：并发/重试合并同一会话 → 恰一个 teaching-video file_object，不产生重复行 + 孤儿。
        LoginResult student = readyLogin("test_student");
        String year = "P7-RACE-MERGE";
        byte[] content = mp4(year);
        String hash = md5(content);
        JsonNode init = initUpload(student.accessToken(), 9001L, year, "lesson-" + year + ".mp4",
                "video/mp4", content.length, 4, hash, 900);
        String uploadId = init.at("/uploadId").asText();
        uploadAll(student.accessToken(), uploadId, content, 4);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Integer> codes = new ArrayList<>();
        try {
            Callable<Integer> merge1 = () -> {
                barrier.await();
                return mergeRaw(student.accessToken(), uploadId, 900);
            };
            Callable<Integer> merge2 = () -> {
                barrier.await();
                return mergeRaw(student.accessToken(), uploadId, 900);
            };
            List<Future<Integer>> futures = executor.invokeAll(List.of(merge1, merge2));
            codes.add(futures.get(0).get());
            codes.add(futures.get(1).get());
        } finally {
            executor.shutdownNow();
        }

        // 至少一个成功；失败者（若命中合并中）为幂等拒绝 code=1000，绝不产生第二个文件对象
        assertThat(codes).contains(0);
        assertThat(codes).allMatch(code -> code == 0 || code == 1000);
        // 核心不变式：恰一个 teaching-video file_object（改前重复合并会产生 2 个 + MinIO 孤儿）
        Integer fileObjectCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_object WHERE biz_type = 'teaching-video'", Integer.class);
        assertThat(fileObjectCount).isEqualTo(1);
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
    void largeMultipartUploadTakesServerSideComposeFastPath() throws Exception {
        // P1-2 阶段1：分片 ≥5MiB 时 merge 走 MinIO 服务端 composeObject（字节不经应用逐片拉回），非流式回退慢路径。
        // 证据链：① 合并对象字节 == 原始拼接内容（合并正确）；② 恰一个 teaching-video file_object；
        // ③ 合并对象 content-type 仍为 video/mp4（分片以 octet-stream 存储，快路径显式回填、不退化）；
        // ④ 对象 ETag 形如 <hex>-<partCount>（S3/MinIO 多部件合并语义）——流式回退的单次 putObject 得纯 MD5（无 '-'），
        //    以此在活体 MinIO 上判别「确实走了服务端合并快路径」而非回退。
        LoginResult studentLogin = readyLogin("test_student");
        String year = "P7-COMPOSE";
        int partSize = 5 * 1024 * 1024;                      // 恰 MinIO 部件下限 MIN_MULTIPART_SIZE(=5MiB)
        byte[] content = filledMp4Payload(partSize + 4096);  // 2 片：首片 5MiB(≥下限)、末片 4096B(<下限，允许)
        String hash = md5(content);

        JsonNode init = initUpload(studentLogin.accessToken(), 9001L, year, "lesson-" + year + ".mp4",
                "video/mp4", content.length, partSize, hash, 900);
        String uploadId = init.at("/uploadId").asText();
        uploadAll(studentLogin.accessToken(), uploadId, content, partSize); // index0=5MiB, index1=4096B

        JsonNode merged = merge(studentLogin.accessToken(), uploadId, 900);
        assertThat(merged.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        assertThat(merged.at("/formatCheck").asText()).isEqualTo("PASS");

        // ② 恰一个 teaching-video file_object（并发/回退各变体均以此为核心不变式）
        Integer fileObjectCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_object WHERE biz_type = 'teaching-video'", Integer.class);
        assertThat(fileObjectCount).isEqualTo(1);

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
        assertThat(stat.size()).isEqualTo((long) content.length);   // 合并大小 == 两片之和
        assertThat(stat.contentType()).isEqualTo("video/mp4");      // ③ 快路径亦保留视频 content-type
        assertThat(stat.etag()).contains("-");                      // ④ 多部件合并 ETag ⇒ 走服务端 compose 快路径

        // ① 字节级正确：下载合并对象与原始拼接内容逐字节一致
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

    private int mergeRaw(String token, String uploadId, int duration) throws Exception {
        ResponseEntity<String> response = exchange("/api/video/upload/merge", HttpMethod.POST, token,
                Map.of("uploadId", uploadId, "durationSeconds", duration));
        return json(response).at("/code").asInt();
    }

    private long uploadValidatedVideo(String token, long studentId, String year) throws Exception {
        byte[] content = mp4(year);
        String hash = md5(content);
        JsonNode init = initUpload(token, studentId, year, "lesson-" + year + ".mp4", "video/mp4", content.length, 4, hash, 900);
        String uploadId = init.at("/uploadId").asText();
        uploadAll(token, uploadId, content, 4);
        JsonNode merged = merge(token, uploadId, 900);
        assertThat(merged.at("/status").asText()).isEqualTo("WAIT_REVIEW");
        return merged.at("/id").asLong();
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

    private void uploadAll(String token, String uploadId, byte[] content, int chunkSize) throws Exception {
        int index = 0;
        for (int offset = 0; offset < content.length; offset += chunkSize) {
            int length = Math.min(chunkSize, content.length - offset);
            uploadChunk(token, uploadId, index++, slice(content, offset, length));
        }
    }

    private void uploadChunk(String token, String uploadId, int index, byte[] content) throws Exception {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("uploadId", uploadId);
        body.add("index", String.valueOf(index));
        body.add("md5", md5(content));
        body.add("file", resource("chunk-" + index, "application/octet-stream", content));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> response = rest.exchange(url("/api/video/upload/chunk"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private JsonNode merge(String token, String uploadId, int duration) throws Exception {
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

    private HttpEntity<ByteArrayResource> resource(String filename, String contentType, byte[] content) {
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
        jdbcTemplate.update("DELETE FROM video_review_task WHERE video_review_id IN (SELECT id FROM video_review WHERE assessment_year LIKE 'P7%')");
        jdbcTemplate.update("DELETE FROM video_review WHERE assessment_year LIKE 'P7%'");
        jdbcTemplate.update("DELETE FROM video_upload_chunk WHERE upload_id IN (SELECT upload_id FROM video_upload_session WHERE assessment_year LIKE 'P7%')");
        jdbcTemplate.update("DELETE FROM video_upload_session WHERE assessment_year LIKE 'P7%'");
        jdbcTemplate.update("DELETE FROM file_object WHERE biz_type = 'teaching-video'");
    }

    private byte[] mp4(String text) {
        return ("....ftypmp42" + text + "-mdat").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 生成指定大小的确定性字节载荷（首部带 ftypmp42 特征；服务端校验只看扩展名/contentType，不解析真实 mp4 结构）。 */
    private byte[] filledMp4Payload(int size) {
        byte[] out = new byte[size];
        for (int i = 0; i < size; i++) {
            out[i] = (byte) ((i * 31 + 7) & 0xff);
        }
        byte[] head = "ftypmp42".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(head, 0, out, 0, Math.min(head.length, size));
        return out;
    }

    private byte[] slice(byte[] input, int offset, int length) {
        byte[] out = new byte[length];
        System.arraycopy(input, offset, out, 0, length);
        return out;
    }

    private String md5(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
