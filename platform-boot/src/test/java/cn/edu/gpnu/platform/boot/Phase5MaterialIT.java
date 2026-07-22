package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.exception.MultipartUploadNotFoundException;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;
import cn.edu.gpnu.platform.file.service.MultipartObjectService;
import cn.edu.gpnu.platform.system.entity.SysParam;
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
import io.minio.RemoveObjectArgs;
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

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase5MaterialIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final long COLLEGE_A = 800000000000000201L;
    private static final long COLLEGE_B = 800000000000000202L;
    private static final long STUDENT_ROLE_ID = 800000000000000001L;
    private static final long STUDENT_B_USER_ID = 800000000000003009L;
    private static final long STUDENT_B_ROLE_ID = 800000000000004009L;
    private static final String YEAR = "P5-2026";
    private static final String MORALITY = "morality_teacher_ethics";
    private static final String COURSE = "teacher_education_course";
    private static final String PRACTICE = "education_internship_practice";
    private static final String SKILL = "professional_ability_skill_training";
    private static final long DIRECT_UPLOAD_PART_SIZE = 8L * 1024 * 1024;

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
    private ProcessMaterialMapper materialMapper;

    @Autowired
    private SysParamMapper paramMapper;

    @Autowired
    private org.springframework.cache.CacheManager cacheManager;

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

    @BeforeEach
    @AfterEach
    void resetSeedUsers() {
        cleanupGeneratedData();
        resetSeedStudents();
        ensureSecondCollegeStudent();
        resetParam("file.maxSize.material", "52428800");
        resetUser("test_student", true);
        resetUser("test_student_b", true);
        resetUser("test_college_clerk", true);
        resetUser("test_college_auditor", true);
        resetUser("test_academic_admin", true);
    }

    @Test
    void uploadRejectsUnsupportedTypeAndOversize() throws Exception {
        LoginResult student = readyLogin("test_student");
        ResponseEntity<String> docx = upload(student.accessToken(), 9001L, YEAR, MORALITY,
                "bad.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "bad".getBytes());
        assertThat(docx.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(docx).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(docx).at("/msg").asText()).contains("附件类型不支持");

        updateParam("file.maxSize.material", "2");
        ResponseEntity<String> oversize = upload(student.accessToken(), 9001L, YEAR, MORALITY,
                "big.pdf", "application/pdf", "%PDF-1.4".getBytes());
        assertThat(oversize.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(oversize).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(oversize).at("/msg").asText()).contains("附件大小超过限制");
    }

    @Test
    void processQualifiedRequiresAllFourCategoriesPassed() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");

        long first = uploadOk(student.accessToken(), 9001L, YEAR, MORALITY, "morality.pdf");
        long second = uploadOk(student.accessToken(), 9001L, YEAR, COURSE, "course.pdf");
        long third = uploadOk(student.accessToken(), 9001L, YEAR, PRACTICE, "practice.pdf");
        approve(clerk.accessToken(), auditor.accessToken(), first);
        approve(clerk.accessToken(), auditor.accessToken(), second);
        approve(clerk.accessToken(), auditor.accessToken(), third);
        JsonNode missingOne = processStatus(student.accessToken(), 9001L, YEAR);
        assertThat(missingOne.at("/qualified").asBoolean()).isFalse();

        long failed = uploadOk(student.accessToken(), 9001L, YEAR, SKILL, "skill.pdf");
        submit(student.accessToken(), failed);
        firstReview(clerk.accessToken(), failed, "PASS", "初审通过");
        secondReview(auditor.accessToken(), failed, "FAIL", "不通过");
        JsonNode failedOne = processStatus(student.accessToken(), 9001L, YEAR);
        assertThat(failedOne.at("/qualified").asBoolean()).isFalse();

        String nextYear = "P5-2027";
        long a = uploadOk(student.accessToken(), 9001L, nextYear, MORALITY, "a.pdf");
        long b = uploadOk(student.accessToken(), 9001L, nextYear, COURSE, "b.pdf");
        long c = uploadOk(student.accessToken(), 9001L, nextYear, PRACTICE, "c.pdf");
        long d = uploadOk(student.accessToken(), 9001L, nextYear, SKILL, "d.pdf");
        approve(clerk.accessToken(), auditor.accessToken(), a);
        approve(clerk.accessToken(), auditor.accessToken(), b);
        approve(clerk.accessToken(), auditor.accessToken(), c);
        approve(clerk.accessToken(), auditor.accessToken(), d);
        JsonNode allPassed = processStatus(student.accessToken(), 9001L, nextYear);
        assertThat(allPassed.at("/qualified").asBoolean()).isTrue();
        assertThat(allPassed.at("/categories").size()).isEqualTo(4);
    }

    @Test
    void passedMaterialCannotBeReplacedUntilReturnedAndBatchDownloadHasManifest() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        long materialId = uploadOk(student.accessToken(), 9001L, YEAR, MORALITY, "locked.pdf");
        approve(clerk.accessToken(), auditor.accessToken(), materialId);

        ResponseEntity<String> replacePassed = replace(student.accessToken(), materialId, "new.pdf", "application/pdf", "%PDF-new".getBytes());
        assertThat(replacePassed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(replacePassed).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(replacePassed).at("/msg").asText()).contains("当前状态不可编辑");

        ResponseEntity<byte[]> download = exchangeBytes("/api/material/batch-download", HttpMethod.POST,
                clerk.accessToken(), Map.of("assessmentYear", YEAR));
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).isNotEmpty();
        assertZipContainsManifest(download.getBody());
    }

    @Test
    void materialReadAndWriteScopeAreEnforced() throws Exception {
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        LoginResult clerk = readyLogin("test_college_clerk");
        long own = uploadOk(studentA.accessToken(), 9001L, YEAR, MORALITY, "own.pdf");

        ResponseEntity<String> crossStudentWrite = upload(studentA.accessToken(), 9002L, YEAR, MORALITY,
                "cross.pdf", "application/pdf", "%PDF-cross".getBytes());
        assertThat(json(crossStudentWrite).at("/code").asInt()).isEqualTo(403);
        assertThat(materialMapper.selectCount(new LambdaQueryWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getStudentId, 9002L)
                .eq(ProcessMaterial::getAssessmentYear, YEAR))).isZero();

        long b = uploadOk(studentB.accessToken(), 9002L, YEAR, MORALITY, "b.pdf");
        ResponseEntity<String> list = exchange("/api/material?assessmentYear=" + YEAR, HttpMethod.GET, clerk.accessToken(), null);
        JsonNode records = json(list).at("/data/records");
        assertThat(records.toString()).contains(String.valueOf(own));
        assertThat(records.toString()).doesNotContain(String.valueOf(b));

        ResponseEntity<String> studentList = exchange("/api/material?assessmentYear=" + YEAR, HttpMethod.GET, studentA.accessToken(), null);
        JsonNode studentRecords = json(studentList).at("/data/records");
        assertThat(studentRecords.size()).isEqualTo(1);
        assertThat(studentRecords.at("/0/studentId").asLong()).isEqualTo(9001L);
    }

    @Test
    void presignedMaterialUploadCompletesIdempotentlyAndStoresExactObject() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] content = ("%PDF-1.7\n"
                + "WS-3 browser-to-MinIO material upload\n%%EOF").getBytes(StandardCharsets.UTF_8);

        JsonNode init = initDirectUpload(student.accessToken(), null, 9001L, YEAR, MORALITY,
                "direct.pdf", "application/pdf", content);
        long fileId = jsonLong(init.at("/fileId"));
        assertThat(init.at("/uploadMode").asText()).isEqualTo("PRESIGNED_MULTIPART");
        assertThat(jsonLong(init.at("/partSize"))).isEqualTo(DIRECT_UPLOAD_PART_SIZE);
        assertThat(init.at("/uploadedParts")).isEmpty();
        assertThat(init.at("/parts")).hasSize(1);
        assertThat(init.at("/parts/0/partNumber").asInt()).isEqualTo(1);

        String etag = putPresignedPart(init.at("/parts/0/url").asText(), content);
        Map<String, Object> completeBody = directCompleteBody(fileId, null, 9001L, YEAR, MORALITY, etag);
        ResponseEntity<String> completed = exchange("/api/material/upload/complete", HttpMethod.POST,
                student.accessToken(), completeBody);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(completed).at("/code").asInt()).isZero();
        long materialId = jsonLong(json(completed).at("/data"));

        ProcessMaterial material = materialMapper.selectById(materialId);
        assertThat(material).isNotNull();
        assertThat(material.getStudentId()).isEqualTo(9001L);
        assertThat(material.getAssessmentYear()).isEqualTo(YEAR);
        assertThat(material.getCategory()).isEqualTo(MORALITY);
        assertThat(material.getStatus()).isEqualTo("DRAFT");
        assertThat(material.getFileId()).isEqualTo(fileId);

        FileObject file = fileObjectMapper.selectById(fileId);
        assertThat(file).isNotNull();
        assertThat(file.getStatus()).isEqualTo("READY");
        assertThat(file.getBizType()).isEqualTo("process-material");
        assertThat(file.getSize()).isEqualTo((long) content.length);
        try (java.io.InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProperties.getBucket()).object(file.getObjectKey()).build())) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }

        ResponseEntity<String> retried = exchange("/api/material/upload/complete", HttpMethod.POST,
                student.accessToken(), completeBody);
        assertThat(json(retried).at("/code").asInt()).isZero();
        assertThat(jsonLong(json(retried).at("/data"))).isEqualTo(materialId);
        assertThat(materialMapper.selectCount(new LambdaQueryWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getFileId, fileId))).isEqualTo(1L);

        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");
        approve(clerk.accessToken(), auditor.accessToken(), materialId);
        ResponseEntity<String> lockedReplacement = initDirectUploadRaw(readyLogin("test_student").accessToken(), materialId,
                9001L, YEAR, MORALITY, "locked-replacement.pdf", "application/pdf", content);
        assertThat(json(lockedReplacement).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(lockedReplacement).at("/msg").asText()).contains("当前状态不可编辑");
    }

    @Test
    void presignedMaterialUploadRejectsCrossStudentAndTamperedCompletion() throws Exception {
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        byte[] content = "%PDF-1.7\nWS-3 authorization\n%%EOF".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<String> crossStudentInit = initDirectUploadRaw(studentA.accessToken(), null,
                9002L, YEAR, MORALITY, "cross-student.pdf", "application/pdf", content);
        assertThat(json(crossStudentInit).at("/code").asInt()).isEqualTo(403);

        String contextYear = "P5-CONTEXT";
        JsonNode contextInit = initDirectUpload(studentA.accessToken(), null, 9001L, contextYear, MORALITY,
                "context.pdf", "application/pdf", content);
        long contextFileId = jsonLong(contextInit.at("/fileId"));
        String contextEtag = putPresignedPart(contextInit.at("/parts/0/url").asText(), content);
        ResponseEntity<String> tamperedContext = exchange("/api/material/upload/complete", HttpMethod.POST,
                studentA.accessToken(), directCompleteBody(contextFileId, null, 9001L,
                        "P5-TAMPERED", MORALITY, contextEtag));
        assertThat(json(tamperedContext).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(tamperedContext).at("/msg").asText()).contains("业务绑定版本已变化");

        String ownerYear = "P5-OWNER";
        JsonNode ownerInit = initDirectUpload(studentB.accessToken(), null, 9002L, ownerYear, MORALITY,
                "owner.pdf", "application/pdf", content);
        long ownerFileId = jsonLong(ownerInit.at("/fileId"));
        String ownerEtag = putPresignedPart(ownerInit.at("/parts/0/url").asText(), content);
        ResponseEntity<String> foreignFile = exchange("/api/material/upload/complete", HttpMethod.POST,
                studentA.accessToken(), directCompleteBody(ownerFileId, null, 9001L,
                        ownerYear, MORALITY, ownerEtag));
        assertThat(json(foreignFile).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(foreignFile).at("/msg").asText()).contains("无权定稿");

        String etagYear = "P5-ETAG";
        JsonNode etagInit = initDirectUpload(studentA.accessToken(), null, 9001L, etagYear, MORALITY,
                "etag.pdf", "application/pdf", content);
        long etagFileId = jsonLong(etagInit.at("/fileId"));
        String validEtag = putPresignedPart(etagInit.at("/parts/0/url").asText(), content);
        ResponseEntity<String> forgedEtag = exchange("/api/material/upload/complete", HttpMethod.POST,
                studentA.accessToken(), directCompleteBody(etagFileId, null, 9001L,
                        etagYear, MORALITY, validEtag + "-forged"));
        assertThat(json(forgedEtag).at("/code").asInt()).isEqualTo(1000);
        assertThat(json(forgedEtag).at("/msg").asText()).contains("ETag校验失败");
        assertThat(materialMapper.selectCount(new LambdaQueryWrapper<ProcessMaterial>()
                .in(ProcessMaterial::getAssessmentYear, contextYear, ownerYear, etagYear))).isZero();
    }

    @Test
    void presignedMaterialUploadResumesUploadedPartsForNewAndReturnedContexts() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] newContent = "%PDF-1.7\nresume-new-context\n%%EOF".getBytes(StandardCharsets.UTF_8);
        assertDirectUploadResumes(student.accessToken(), null, "P5-RESUME", "resume-new.pdf", newContent);

        String returnedYear = "P5-RETURN";
        long materialId = uploadOk(student.accessToken(), 9001L, returnedYear, MORALITY, "returned.pdf");
        submit(student.accessToken(), materialId);
        LoginResult clerk = readyLogin("test_college_clerk");
        firstReview(clerk.accessToken(), materialId, "REJECT", "请修改后重新提交");
        assertThat(materialMapper.selectById(materialId).getStatus()).isEqualTo("FIRST_REJECTED");

        byte[] returnedContent = "%PDF-1.7\nresume-returned-context\n%%EOF".getBytes(StandardCharsets.UTF_8);
        assertDirectUploadResumes(student.accessToken(), materialId, returnedYear,
                "resume-returned.pdf", returnedContent);
    }

    @Test
    void presignedMaterialUploadCancelAbortsSessionAllowsRestartAndRejectsForeignOwner() throws Exception {
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        String year = "P5-CANCEL";
        byte[] content = "%PDF-1.7\ncancel-and-restart-A\n%%EOF".getBytes(StandardCharsets.UTF_8);

        JsonNode initial = initDirectUpload(studentA.accessToken(), null, 9001L, year, MORALITY,
                "cancel.pdf", "application/pdf", content);
        long initialFileId = jsonLong(initial.at("/fileId"));
        FileObject initialFile = fileObjectMapper.selectById(initialFileId);

        ResponseEntity<String> foreignCancel = exchange("/api/material/upload/" + initialFileId,
                HttpMethod.DELETE, studentB.accessToken(), null);
        assertThat(json(foreignCancel).at("/code").asInt()).isNotZero();
        assertThat(json(foreignCancel).at("/msg").asText()).contains("无权");
        assertThat(fileObjectMapper.selectById(initialFileId).getStatus()).isEqualTo("UPLOADING");
        assertThat(multipartObjectService.listUploadedParts(initialFile.getObjectKey(),
                initialFile.getMultipartUploadId())).isEmpty();

        cancelDirectUploadOk(studentA.accessToken(), initialFileId);
        assertThat(fileObjectMapper.selectById(initialFileId).getStatus()).isEqualTo("FAILED");
        assertThatThrownBy(() -> multipartObjectService.listUploadedParts(initialFile.getObjectKey(),
                initialFile.getMultipartUploadId()))
                .isInstanceOf(MultipartUploadNotFoundException.class);

        JsonNode sameHash = initDirectUpload(studentA.accessToken(), null, 9001L, year, MORALITY,
                "cancel.pdf", "application/pdf", content);
        long sameHashFileId = jsonLong(sameHash.at("/fileId"));
        assertThat(sameHashFileId).isNotEqualTo(initialFileId);
        cancelDirectUploadOk(studentA.accessToken(), sameHashFileId);

        byte[] differentHash = content.clone();
        differentHash[20] ^= 1;
        JsonNode changed = initDirectUpload(studentA.accessToken(), null, 9001L, year, MORALITY,
                "cancel.pdf", "application/pdf", differentHash);
        long changedFileId = jsonLong(changed.at("/fileId"));
        assertThat(changedFileId).isNotIn(initialFileId, sameHashFileId);
        cancelDirectUploadOk(studentA.accessToken(), changedFileId);
    }

    @Test
    void staleInitiatingMaterialUploadIsCasReclaimedBeforeNewSessionStarts() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P5-STALE";
        byte[] content = "%PDF-1.7\nstale-initiating\n%%EOF".getBytes(StandardCharsets.UTF_8);
        JsonNode initial = initDirectUpload(student.accessToken(), null, 9001L, year, MORALITY,
                "stale.pdf", "application/pdf", content);
        long staleFileId = jsonLong(initial.at("/fileId"));
        FileObject staleFile = fileObjectMapper.selectById(staleFileId);
        multipartObjectService.abortUpload(staleFile.getObjectKey(), staleFile.getMultipartUploadId());
        int constructed = jdbcTemplate.update("UPDATE file_object SET status = 'INITIATING', updated_at = ? "
                        + "WHERE id = ? AND status = 'UPLOADING'",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)), staleFileId);
        assertThat(constructed).isEqualTo(1);

        JsonNode replacement = initDirectUpload(student.accessToken(), null, 9001L, year, MORALITY,
                "stale.pdf", "application/pdf", content);
        long replacementFileId = jsonLong(replacement.at("/fileId"));
        assertThat(replacementFileId).isNotEqualTo(staleFileId);
        assertThat(fileObjectMapper.selectById(staleFileId).getStatus()).isEqualTo("FAILED");
        assertThat(fileObjectMapper.selectById(replacementFileId).getStatus()).isEqualTo("UPLOADING");
        cancelDirectUploadOk(student.accessToken(), replacementFileId);
    }

    @Test
    void completingMaterialUploadRecoversReadyObjectAndBindsWithEmptyPartsIdempotently() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P5-RECOVER";
        byte[] content = "%PDF-1.7\ncomplete-recovery\n%%EOF".getBytes(StandardCharsets.UTF_8);
        JsonNode initial = initDirectUpload(student.accessToken(), null, 9001L, year, MORALITY,
                "recover.pdf", "application/pdf", content);
        long fileId = jsonLong(initial.at("/fileId"));
        putPresignedPart(initial.at("/parts/0/url").asText(), content);
        completeMinioObjectAndSetDatabaseStatus(fileId, "COMPLETING");

        JsonNode recovered = initDirectUpload(student.accessToken(), null, 9001L, year, MORALITY,
                "recover.pdf", "application/pdf", content);
        assertThat(recovered.at("/uploadMode").asText()).isEqualTo("READY");
        assertThat(jsonLong(recovered.at("/fileId"))).isEqualTo(fileId);
        assertThat(recovered.at("/uploadedParts")).isEmpty();
        assertThat(recovered.at("/parts")).isEmpty();
        assertThat(fileObjectMapper.selectById(fileId).getStatus()).isEqualTo("READY");
        assertThat(fileObjectMapper.selectById(fileId).getMultipartUploadId()).isNull();

        Map<String, Object> completion = directCompleteBody(fileId, null, 9001L, year, MORALITY, List.of());
        ResponseEntity<String> completed = exchange("/api/material/upload/complete", HttpMethod.POST,
                student.accessToken(), completion);
        assertThat(json(completed).at("/code").asInt()).isZero();
        long materialId = jsonLong(json(completed).at("/data"));

        ResponseEntity<String> retried = exchange("/api/material/upload/complete", HttpMethod.POST,
                student.accessToken(), completion);
        assertThat(json(retried).at("/code").asInt()).isZero();
        assertThat(jsonLong(json(retried).at("/data"))).isEqualTo(materialId);
        assertThat(materialMapper.selectCount(new LambdaQueryWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getFileId, fileId))).isEqualTo(1L);
    }

    @Test
    void presignedMaterialPartRejectsPayloadWhoseLengthDiffersFromSignedLength() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] content = "%PDF-1.7\nsigned-content-length\n%%EOF".getBytes(StandardCharsets.UTF_8);
        JsonNode initial = initDirectUpload(student.accessToken(), null, 9001L, "P5-LENGTH", MORALITY,
                "length.pdf", "application/pdf", content);
        long fileId = jsonLong(initial.at("/fileId"));
        byte[] shortPayload = java.util.Arrays.copyOf(content, content.length - 1);

        int status = PresignedMultipartUploadTestClient.putStatus(initial.at("/parts/0"), shortPayload);
        assertThat(status).isBetween(400, 499);
        FileObject file = fileObjectMapper.selectById(fileId);
        assertThat(multipartObjectService.listUploadedParts(file.getObjectKey(), file.getMultipartUploadId())).isEmpty();
        cancelDirectUploadOk(student.accessToken(), fileId);
    }

    @Test
    void directMaterialUploadDtoBoundsReturnValidationCodesInsteadOfDatabaseErrors() throws Exception {
        LoginResult student = readyLogin("test_student");
        byte[] content = "%PDF-1.7\ndto-validation\n%%EOF".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> overlongName = directInitBody(null, 9001L, "P5-VALID", MORALITY,
                "x".repeat(252) + ".pdf", "application/pdf", content);
        ResponseEntity<String> overlongResponse = exchange("/api/material/upload/init", HttpMethod.POST,
                student.accessToken(), overlongName);
        assertApiValidation(overlongResponse, "文件名长度不能超过255");

        List<Map<String, Object>> tooManyParts = java.util.stream.IntStream.rangeClosed(1, 10_001)
                .mapToObj(part -> Map.<String, Object>of("partNumber", part, "etag", "etag-" + part))
                .toList();
        Map<String, Object> tooManyBody = directCompleteBody(1L, null, 9001L, "P5-VALID",
                MORALITY, tooManyParts);
        ResponseEntity<String> tooManyResponse = exchange("/api/material/upload/complete", HttpMethod.POST,
                student.accessToken(), tooManyBody);
        assertApiValidation(tooManyResponse, "上传分片数量超过限制");
    }

    @Test
    void disabledDirectMaterialUploadFallsBackToServerUploadAndReplace() throws Exception {
        LoginResult student = readyLogin("test_student");
        boolean directUploadEnabled = minioProperties.isDirectUploadEnabled();
        minioProperties.setDirectUploadEnabled(false);
        try {
            String year = "P5-FALLBACK";
            byte[] content = "%PDF-1.7\nserver-upload-fallback\n%%EOF".getBytes(StandardCharsets.UTF_8);
            JsonNode fallback = initDirectUpload(student.accessToken(), null, 9001L, year, MORALITY,
                    "fallback.pdf", "application/pdf", content);
            assertThat(fallback.at("/uploadMode").asText()).isEqualTo("SERVER_UPLOAD");
            assertThat(fallback.at("/fileId").isMissingNode() || fallback.at("/fileId").isNull()).isTrue();
            assertThat(fallback.at("/uploadedParts")).isEmpty();
            assertThat(fallback.at("/parts")).isEmpty();

            ResponseEntity<String> uploaded = upload(student.accessToken(), 9001L, year, MORALITY,
                    "fallback.pdf", "application/pdf", content);
            assertThat(json(uploaded).at("/code").asInt()).isZero();
            long materialId = jsonLong(json(uploaded).at("/data"));
            ProcessMaterial material = materialMapper.selectById(materialId);
            long firstFileId = material.getFileId();
            assertThat(fileObjectMapper.selectById(firstFileId).getStatus()).isEqualTo("READY");

            byte[] replacement = "%PDF-1.7\nserver-replace-fallback\n%%EOF".getBytes(StandardCharsets.UTF_8);
            ResponseEntity<String> replaced = replace(student.accessToken(), materialId,
                    "fallback-replaced.pdf", "application/pdf", replacement);
            assertThat(json(replaced).at("/code").asInt()).isZero();
            ProcessMaterial updated = materialMapper.selectById(materialId);
            assertThat(updated.getFileId()).isNotEqualTo(firstFileId);
            assertThat(updated.getFileName()).isEqualTo("fallback-replaced.pdf");
            assertThat(fileObjectMapper.selectById(updated.getFileId()).getStatus()).isEqualTo("READY");
        } finally {
            minioProperties.setDirectUploadEnabled(directUploadEnabled);
        }
    }

    @Test
    void lateReadyReplacementCannotOverwriteMaterialBoundByNewerReadyUpload() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P5-RACE";
        long materialId = uploadOk(student.accessToken(), 9001L, year, MORALITY, "original.pdf");
        long originalFileId = materialMapper.selectById(materialId).getFileId();

        byte[] lateContent = "%PDF-1.7\nlate-ready-replacement\n%%EOF".getBytes(StandardCharsets.UTF_8);
        JsonNode lateInit = initDirectUpload(student.accessToken(), materialId, 9001L, year, MORALITY,
                "late.pdf", "application/pdf", lateContent);
        long lateFileId = jsonLong(lateInit.at("/fileId"));
        putPresignedPart(lateInit.at("/parts/0/url").asText(), lateContent);
        completeMinioObjectAndSetDatabaseStatus(lateFileId, "READY");

        byte[] winnerContent = "%PDF-1.7\nnewer-ready-replacement\n%%EOF".getBytes(StandardCharsets.UTF_8);
        JsonNode winnerInit = initDirectUpload(student.accessToken(), materialId, 9001L, year, MORALITY,
                "winner.pdf", "application/pdf", winnerContent);
        long winnerFileId = jsonLong(winnerInit.at("/fileId"));
        String winnerEtag = putPresignedPart(winnerInit.at("/parts/0/url").asText(), winnerContent);
        ResponseEntity<String> winnerComplete = exchange("/api/material/upload/complete", HttpMethod.POST,
                student.accessToken(), directCompleteBody(winnerFileId, materialId, 9001L, year,
                        MORALITY, winnerEtag));
        assertThat(json(winnerComplete).at("/code").asInt()).isZero();
        assertThat(jsonLong(json(winnerComplete).at("/data"))).isEqualTo(materialId);
        assertThat(fileObjectMapper.selectById(lateFileId).getStatus()).isEqualTo("READY");
        assertThat(fileObjectMapper.selectById(winnerFileId).getStatus()).isEqualTo("READY");
        assertThat(materialMapper.selectById(materialId).getFileId()).isEqualTo(winnerFileId);
        assertThat(winnerFileId).isNotIn(originalFileId, lateFileId);

        ResponseEntity<String> lateComplete = exchange("/api/material/upload/complete", HttpMethod.POST,
                student.accessToken(), directCompleteBody(lateFileId, materialId, 9001L, year,
                        MORALITY, List.of()));
        JsonNode lateResult = json(lateComplete);
        assertThat(lateResult.at("/code").asInt()).isEqualTo(1000);
        assertThat(lateResult.at("/msg").asText())
                .containsAnyOf("业务绑定版本已变化", "业务上下文不一致", "材料已被其他上传替换");
        assertThat(materialMapper.selectById(materialId).getFileId()).isEqualTo(winnerFileId);
    }

    @Test
    void replacementBindingVersionMismatchFailsOldSessionAndReleasesUploadSlot() throws Exception {
        LoginResult student = readyLogin("test_student");
        String year = "P5-BINDING";
        long materialId = uploadOk(student.accessToken(), 9001L, year, MORALITY, "binding-original.pdf");

        byte[] firstContent = "%PDF-1.7\nfirst-ready-before-binding\n%%EOF".getBytes(StandardCharsets.UTF_8);
        JsonNode firstInit = initDirectUpload(student.accessToken(), materialId, 9001L, year, MORALITY,
                "binding-first.pdf", "application/pdf", firstContent);
        long firstFileId = jsonLong(firstInit.at("/fileId"));
        putPresignedPart(firstInit.at("/parts/0/url").asText(), firstContent);
        completeMinioObjectAndSetDatabaseStatus(firstFileId, "READY");

        byte[] staleContent = "%PDF-1.7\nstale-binding-version\n%%EOF".getBytes(StandardCharsets.UTF_8);
        JsonNode staleInit = initDirectUpload(student.accessToken(), materialId, 9001L, year, MORALITY,
                "binding-stale.pdf", "application/pdf", staleContent);
        long staleFileId = jsonLong(staleInit.at("/fileId"));
        String staleEtag = putPresignedPart(staleInit.at("/parts/0/url").asText(), staleContent);
        FileObject staleFile = fileObjectMapper.selectById(staleFileId);

        ResponseEntity<String> firstBinding = exchange("/api/material/upload/complete", HttpMethod.POST,
                student.accessToken(), directCompleteBody(firstFileId, materialId, 9001L, year,
                        MORALITY, List.of()));
        assertThat(json(firstBinding).at("/code").asInt()).isZero();
        assertThat(materialMapper.selectById(materialId).getFileId()).isEqualTo(firstFileId);

        ResponseEntity<String> staleComplete = exchange("/api/material/upload/complete", HttpMethod.POST,
                student.accessToken(), directCompleteBody(staleFileId, materialId, 9001L, year,
                        MORALITY, staleEtag));
        JsonNode staleResult = json(staleComplete);
        assertThat(staleResult.at("/code").asInt()).isEqualTo(1000);
        assertThat(staleResult.at("/msg").asText()).contains("业务绑定版本已变化");
        assertThat(fileObjectMapper.selectById(staleFileId).getStatus()).isEqualTo("FAILED");
        assertThat(materialMapper.selectById(materialId).getFileId()).isEqualTo(firstFileId);
        assertThat(multipartObjectService.findObject(staleFile.getObjectKey())).isEmpty();
        assertThatThrownBy(() -> multipartObjectService.listUploadedParts(staleFile.getObjectKey(),
                staleFile.getMultipartUploadId()))
                .isInstanceOf(MultipartUploadNotFoundException.class);

        byte[] nextContent = "%PDF-1.7\nnext-binding-version\n%%EOF".getBytes(StandardCharsets.UTF_8);
        JsonNode nextInit = initDirectUpload(student.accessToken(), materialId, 9001L, year, MORALITY,
                "binding-next.pdf", "application/pdf", nextContent);
        long nextFileId = jsonLong(nextInit.at("/fileId"));
        assertThat(nextFileId).isNotIn(firstFileId, staleFileId);
        assertThat(fileObjectMapper.selectById(nextFileId).getStatus()).isEqualTo("UPLOADING");
        cancelDirectUploadOk(student.accessToken(), nextFileId);
    }

    /**
     * Phase 44e（P1-1 真分页 rollout · 材料列表 · 数据范围 × 分页组合的正确性证明）：
     * 学院文员（学院A）对含跨学院同考核年度材料的列表做真分页——
     * ① total 为「已按学院范围过滤」的总数（3，学院B 那条不计入，证明分页 count SQL 也走了数据权限拦截器）；
     * ② 每页条数=请求 size；③ 各页均无学院B 材料；④ 页间记录不重叠（真 LIMIT/OFFSET，非全表包壳）。
     */
    @Test
    void paginatedMaterialListIsScopedAndPagedForCollegeUser() throws Exception {
        LoginResult studentA = readyLogin("test_student");
        LoginResult studentB = readyLogin("test_student_b");
        // assessment_year 列是 VARCHAR(16)：完整 nanoTime()（最多 19 位）拼接前缀会超长触发截断异常，
        // 故对 nanoTime 取模到 9 位以内，'P5PAGE'(6 位)+最多 9 位数字 <= 15 位，留有余量。
        String year = "P5PAGE" + Math.abs(System.nanoTime() % 1_000_000_000L);
        uploadOk(studentA.accessToken(), 9001L, year, MORALITY, "page-a1.pdf");
        uploadOk(studentA.accessToken(), 9001L, year, COURSE, "page-a2.pdf");
        uploadOk(studentA.accessToken(), 9001L, year, PRACTICE, "page-a3.pdf");
        // 学院B 同考核年度 1 条：eq(assessmentYear) 能命中，但学院文员的数据范围应把它排除在 total 与 records 之外。
        long b1 = uploadOk(studentB.accessToken(), 9002L, year, MORALITY, "page-b1.pdf");

        LoginResult clerk = readyLogin("test_college_clerk");

        JsonNode page1 = json(exchange("/api/material?assessmentYear=" + year + "&page=1&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page1.at("/total").asLong()).isEqualTo(3);
        assertThat(page1.at("/records").size()).isEqualTo(2);
        assertThat(page1.at("/records").toString()).doesNotContain(String.valueOf(COLLEGE_B));
        assertThat(page1.at("/records").toString()).doesNotContain(String.valueOf(b1));

        JsonNode page2 = json(exchange("/api/material?assessmentYear=" + year + "&page=2&size=2",
                HttpMethod.GET, clerk.accessToken(), null)).at("/data");
        assertThat(page2.at("/total").asLong()).isEqualTo(3);
        assertThat(page2.at("/records").size()).isEqualTo(1);
        assertThat(page2.at("/records").toString()).doesNotContain(String.valueOf(COLLEGE_B));
        assertThat(page2.at("/records").toString()).doesNotContain(String.valueOf(b1));

        assertThat(page1.at("/records/0/id").asLong())
                .isNotEqualTo(page2.at("/records/0/id").asLong());
    }

    @Test
    // Phase 43.3 / §7.5 Rule 11：某类别先被判 FAILED（终态、locked），学生为同一类别重传一份新材料并复审通过 →
    // 合格判定取"最新/有效"一份材料（更晚的 PASSED 取代更早的 FAILED）→ 该类别恢复合格。
    // 旧实现按全历史行 passedCount>0 && failedCount==0 计数 → 一条 FAILED 永久钉住不合格、无恢复路径（本用例即证阻断）。
    void failedCategoryRecoversWhenNewerMaterialPasses() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");

        long failed = uploadOk(student.accessToken(), 9001L, YEAR, MORALITY, "morality-v1.pdf");
        submit(student.accessToken(), failed);
        firstReview(clerk.accessToken(), failed, "PASS", "初审通过");
        secondReview(auditor.accessToken(), failed, "FAIL", "复审不通过");
        JsonNode afterFail = processStatus(student.accessToken(), 9001L, YEAR);
        assertThat(categoryPassed(afterFail, MORALITY)).isFalse();

        long recovered = uploadOk(student.accessToken(), 9001L, YEAR, MORALITY, "morality-v2.pdf");
        submit(student.accessToken(), recovered);
        firstReview(clerk.accessToken(), recovered, "PASS", "初审通过");
        secondReview(auditor.accessToken(), recovered, "PASS", "复审通过");
        JsonNode afterRecover = processStatus(student.accessToken(), 9001L, YEAR);
        JsonNode morality = category(afterRecover, MORALITY);
        assertThat(morality.at("/passed").asBoolean()).isTrue();
        // 历史计数口径不变（信息展示）：仍保留 1 条 FAILED、1 条 PASSED，但合格只看最新一份。
        assertThat(morality.at("/totalCount").asLong()).isEqualTo(2);
        assertThat(morality.at("/passedCount").asLong()).isEqualTo(1);
        assertThat(morality.at("/failedCount").asLong()).isEqualTo(1);
    }

    @Test
    // Phase 43.3 / §7.5 Rule 11 修复正确性边界（负向）：真失败仍不合格。
    // 情形一：类别唯一一份材料 FAILED、无更晚材料 → 不合格。
    // 情形二：先复审通过(PASSED) 再上传一份更晚材料被判 FAILED → "最新一份为 FAILED"取代先前 PASSED → 仍不合格
    //        （证明修复不是"只要有过 PASSED 就放行"，而是严格按最新/有效材料判定）。
    void genuineFailureStaysUnqualifiedIncludingLatestFailOverridingEarlierPass() throws Exception {
        LoginResult student = readyLogin("test_student");
        LoginResult clerk = readyLogin("test_college_clerk");
        LoginResult auditor = readyLogin("test_college_auditor");

        long onlyFailed = uploadOk(student.accessToken(), 9001L, YEAR, COURSE, "course.pdf");
        submit(student.accessToken(), onlyFailed);
        firstReview(clerk.accessToken(), onlyFailed, "PASS", "初审通过");
        secondReview(auditor.accessToken(), onlyFailed, "FAIL", "复审不通过");

        long earlierPass = uploadOk(student.accessToken(), 9001L, YEAR, PRACTICE, "practice-pass.pdf");
        submit(student.accessToken(), earlierPass);
        firstReview(clerk.accessToken(), earlierPass, "PASS", "初审通过");
        secondReview(auditor.accessToken(), earlierPass, "PASS", "复审通过");
        long laterFail = uploadOk(student.accessToken(), 9001L, YEAR, PRACTICE, "practice-fail.pdf");
        submit(student.accessToken(), laterFail);
        firstReview(clerk.accessToken(), laterFail, "PASS", "初审通过");
        secondReview(auditor.accessToken(), laterFail, "FAIL", "复审不通过");

        JsonNode status = processStatus(student.accessToken(), 9001L, YEAR);
        assertThat(categoryPassed(status, COURSE)).isFalse();
        JsonNode practice = category(status, PRACTICE);
        assertThat(practice.at("/passed").asBoolean()).isFalse();
        assertThat(practice.at("/passedCount").asLong()).isEqualTo(1);
        assertThat(practice.at("/failedCount").asLong()).isEqualTo(1);
        assertThat(status.at("/qualified").asBoolean()).isFalse();
    }

    private JsonNode category(JsonNode processStatus, String categoryCode) {
        for (JsonNode node : processStatus.at("/categories")) {
            if (categoryCode.equals(node.at("/category").asText())) {
                return node;
            }
        }
        throw new AssertionError("类别不存在于聚合状态: " + categoryCode);
    }

    private boolean categoryPassed(JsonNode processStatus, String categoryCode) {
        return category(processStatus, categoryCode).at("/passed").asBoolean();
    }

    private void approve(String clerkToken, String auditorToken, long id) throws Exception {
        submit(readyLogin("test_student").accessToken(), id);
        firstReview(clerkToken, id, "PASS", "初审通过");
        secondReview(auditorToken, id, "PASS", "复审通过");
    }

    private void submit(String token, long id) throws Exception {
        ResponseEntity<String> response = exchange("/api/material/" + id + "/submit", HttpMethod.POST, token, Map.of());
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void firstReview(String token, long id, String action, String comment) throws Exception {
        ResponseEntity<String> response = exchange("/api/material/" + id + "/first-review", HttpMethod.POST,
                token, Map.of("action", action, "comment", comment));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private void secondReview(String token, long id, String action, String comment) throws Exception {
        ResponseEntity<String> response = exchange("/api/material/" + id + "/second-review", HttpMethod.POST,
                token, Map.of("action", action, "comment", comment));
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private JsonNode processStatus(String token, long studentId, String year) throws Exception {
        ResponseEntity<String> response = exchange("/api/material/process-status/" + studentId + "?year=" + year,
                HttpMethod.GET, token, null);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
        return json(response).at("/data");
    }

    private long uploadOk(String token, long studentId, String year, String category, String filename) throws Exception {
        ResponseEntity<String> response = upload(token, studentId, year, category, filename, "application/pdf", "%PDF-1.4".getBytes());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data").asLong();
    }

    private void assertDirectUploadResumes(String token, Long materialId, String year,
                                           String filename, byte[] content) throws Exception {
        JsonNode initial = initDirectUpload(token, materialId, 9001L, year, MORALITY,
                filename, "application/pdf", content);
        long fileId = jsonLong(initial.at("/fileId"));
        String etag = putPresignedPart(initial.at("/parts/0/url").asText(), content);

        JsonNode resumed = initDirectUpload(token, materialId, 9001L, year, MORALITY,
                filename, "application/pdf", content);
        assertThat(jsonLong(resumed.at("/fileId"))).isEqualTo(fileId);
        assertThat(resumed.at("/uploadMode").asText()).isEqualTo("PRESIGNED_MULTIPART");
        assertThat(resumed.at("/uploadedParts")).hasSize(1);
        assertThat(resumed.at("/uploadedParts/0/partNumber").asInt()).isEqualTo(1);
        assertThat(jsonLong(resumed.at("/uploadedParts/0/size"))).isEqualTo(content.length);
        assertThat(resumed.at("/uploadedParts/0/etag").asText()).isEqualTo(etag);
        assertThat(resumed.at("/parts")).isEmpty();
        cancelDirectUploadOk(token, fileId);
    }

    private JsonNode initDirectUpload(String token, Long materialId, long studentId, String year,
                                      String category, String filename, String contentType, byte[] content) throws Exception {
        ResponseEntity<String> response = initDirectUploadRaw(token, materialId, studentId, year,
                category, filename, contentType, content);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isZero();
        return root.at("/data");
    }

    private ResponseEntity<String> initDirectUploadRaw(String token, Long materialId, long studentId, String year,
                                                       String category, String filename, String contentType,
                                                       byte[] content) throws Exception {
        return exchange("/api/material/upload/init", HttpMethod.POST, token,
                directInitBody(materialId, studentId, year, category, filename, contentType, content));
    }

    private Map<String, Object> directInitBody(Long materialId, long studentId, String year,
                                               String category, String filename, String contentType,
                                               byte[] content) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (materialId != null) {
            body.put("materialId", materialId);
        }
        body.put("studentId", studentId);
        body.put("assessmentYear", year);
        body.put("category", category);
        body.put("fileName", filename);
        body.put("contentType", contentType);
        body.put("size", content.length);
        body.put("fileHash", sha256(content));
        body.put("partSize", DIRECT_UPLOAD_PART_SIZE);
        return body;
    }

    private Map<String, Object> directCompleteBody(long fileId, Long materialId, long studentId,
                                                    String year, String category, String etag) {
        return directCompleteBody(fileId, materialId, studentId, year, category,
                List.of(Map.<String, Object>of("partNumber", 1, "etag", etag)));
    }

    private Map<String, Object> directCompleteBody(long fileId, Long materialId, long studentId,
                                                    String year, String category,
                                                    List<Map<String, Object>> parts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fileId", fileId);
        if (materialId != null) {
            body.put("materialId", materialId);
        }
        body.put("studentId", studentId);
        body.put("assessmentYear", year);
        body.put("category", category);
        body.put("parts", parts);
        return body;
    }

    private void cancelDirectUploadOk(String token, long fileId) throws Exception {
        ResponseEntity<String> response = exchange("/api/material/upload/" + fileId,
                HttpMethod.DELETE, token, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isZero();
    }

    private void completeMinioObjectAndSetDatabaseStatus(long fileId, String databaseStatus) {
        assertThat(databaseStatus).isIn("COMPLETING", "READY");
        FileObject file = fileObjectMapper.selectById(fileId);
        assertThat(file).isNotNull();
        List<MultipartUploadedPart> storedParts = multipartObjectService.listUploadedParts(
                file.getObjectKey(), file.getMultipartUploadId());
        assertThat(storedParts).hasSize(file.getUploadTotalParts());
        multipartObjectService.completeUpload(file.getObjectKey(), file.getMultipartUploadId(), storedParts);

        int changed;
        if ("READY".equals(databaseStatus)) {
            changed = jdbcTemplate.update("UPDATE file_object SET status = 'READY', multipart_upload_id = NULL, "
                    + "upload_expires_at = NULL, updated_at = NOW() WHERE id = ? AND status = 'UPLOADING'", fileId);
        } else {
            changed = jdbcTemplate.update("UPDATE file_object SET status = 'COMPLETING', updated_at = NOW() "
                    + "WHERE id = ? AND status = 'UPLOADING'", fileId);
        }
        assertThat(changed).isEqualTo(1);
    }

    private void assertApiValidation(ResponseEntity<String> response, String expectedMessage) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode result = json(response);
        assertThat(result.at("/code").asInt()).isEqualTo(400);
        assertThat(result.at("/msg").asText()).contains(expectedMessage);
    }

    private String putPresignedPart(String uploadUrl, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        ResponseEntity<String> response = rest.getRestTemplate().exchange(java.net.URI.create(uploadUrl), HttpMethod.PUT,
                new HttpEntity<>(content, headers), String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .withFailMessage("预签名 PUT 失败: status=%s, body=%s",
                        response.getStatusCode(), response.getBody())
                .isTrue();
        String etag = response.getHeaders().getFirst(HttpHeaders.ETAG);
        assertThat(etag).isNotBlank();
        return etag;
    }

    private long jsonLong(JsonNode node) {
        return Long.parseLong(node.asText());
    }

    private String sha256(byte[] content) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private ResponseEntity<String> upload(String token, long studentId, String year, String category,
                                          String filename, String contentType, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("studentId", String.valueOf(studentId));
        body.add("assessmentYear", year);
        body.add("category", category);
        body.add("file", resource(filename, contentType, content));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url("/api/material/upload"), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<String> replace(String token, long materialId, String filename, String contentType, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource(filename, contentType, content));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return rest.exchange(url("/api/material/" + materialId + "/replace"), HttpMethod.PUT, new HttpEntity<>(body, headers), String.class);
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

    private void assertZipContainsManifest(byte[] zipBytes) throws Exception {
        boolean manifest = false;
        boolean materialFile = false;
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes), java.nio.charset.StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("manifest.csv".equals(entry.getName())) {
                    manifest = true;
                }
                if (entry.getName().endsWith(".pdf")) {
                    materialFile = true;
                }
            }
        }
        assertThat(manifest).isTrue();
        assertThat(materialFile).isTrue();
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<byte[]> exchangeBytes(String path, HttpMethod method, String accessToken, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), byte[].class);
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
        List<FileObject> materialFiles = fileObjectMapper.selectList(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getBizType, "process-material"));
        for (FileObject file : materialFiles) {
            if (file.getMultipartUploadId() != null && !file.getMultipartUploadId().isBlank()) {
                try {
                    multipartObjectService.abortUpload(file.getObjectKey(), file.getMultipartUploadId());
                } catch (RuntimeException ignored) {
                    // The upload may already have been completed or aborted by the exercised recovery path.
                }
            }
            if (file.getBucket() != null && file.getObjectKey() != null) {
                try {
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(file.getBucket()).object(file.getObjectKey()).build());
                } catch (Exception ignored) {
                    // Cleanup remains best-effort so the original test failure is preserved.
                }
            }
        }
        jdbcTemplate.update("DELETE FROM process_material WHERE assessment_year LIKE 'P5%'");
        jdbcTemplate.update("DELETE FROM file_object WHERE biz_type = 'process-material'");
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
