package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysAuditLogMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 退回整改（U-004）：脚手架验收清单的<b>可重复运行证据</b>。
 *
 * <p>Phase 0 独立复核（`docs/reviews/phase-00-review.md`）退回项 3 要求把 `/doc.html`、统一异常、审计、
 * 预签名与过期反例等做成可重复运行的证据，而不是一次性人工截图。本类按 `docs/phase-00-脚手架.md` §8/§9
 * 的验收清单逐条建立自动化断言（对应关系标注在每个用例上），随任意一次 `mvn verify` 在真实
 * MySQL/Redis/MinIO 上重放：
 * <ul>
 *   <li>§8-2 `/doc.html` 可访问、OpenAPI 文档可取（无需登录，SecurityConfig 显式放行）；</li>
 *   <li>§8-3 / T-RESP-1 统一响应三条合同：BizException → HTTP 200 + code≠0；@Valid 失败 → 字段级错误；
 *       未捕获异常 → HTTP 500 + 统一 Result 且不泄漏堆栈（P1-10 修订后的现行合同，替代原「HTTP 200」口径）；</li>
 *   <li>§8-6 / T-FILE-2 上传返回 fileId；预签名 URL 限时可访问；<b>过期后同一 URL 被 MinIO 拒绝</b>；</li>
 *   <li>§8-7 / T-AUDIT-1 `@AuditLog` 样例端点成功后写 `audit_log`，操作人/时间/IP 全部非空；</li>
 *   <li>通用上传退役全局摘要秒传：同内容再次上传仍生成独立 fileId/objectKey/元数据行。</li>
 * </ul>
 * §8-8 / T-DS-1 的 handler 分支由
 * {@code cn.edu.gpnu.platform.boot.config.DataScopeSqlHandlerTest} 覆盖；注解、上下文、Mapper 与
 * 分页 count/data SQL 的组合链由
 * {@code cn.edu.gpnu.platform.boot.config.DataScopeMapperChainTest} 覆盖，二者均无需外部容器。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=60"
})
class Phase00ScaffoldIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final String BIZ_TYPE = "phase00scaffold";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    private FileObjectMapper fileObjectMapper;

    @Autowired
    private SysAuditLogMapper auditLogMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @BeforeEach
    void resetFixture() {
        cleanFixture();
        resetUser("test_sys_admin");
    }

    @AfterEach
    void cleanUp() {
        cleanFixture();
    }

    // ---------------------------------------------------------------- §8-2 /doc.html 与 OpenAPI

    @Test
    void docHtmlAndOpenApiAreServedWithoutAuthentication() {
        ResponseEntity<String> doc = rest.getForEntity(url("/doc.html"), String.class);
        assertThat(doc.getStatusCode()).as("/doc.html 应可访问（SecurityConfig 放行）").isEqualTo(HttpStatus.OK);
        assertThat(doc.getBody()).as("应是 Knife4j 文档页而非错误页").containsIgnoringCase("<html");

        ResponseEntity<String> apiDocs = rest.getForEntity(url("/v3/api-docs"), String.class);
        assertThat(apiDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apiDocs.getBody())
                .as("OpenAPI 文档应包含 openapi 版本字段与真实业务路径")
                .contains("\"openapi\"")
                .contains("/api/auth/login");
    }

    // ---------------------------------------------------------------- §8-3 统一响应/异常合同

    @Test
    void bizExceptionReturnsHttp200WithBusinessCode() throws Exception {
        // 未认证访问业务端点走的是安全链（401），不适合验证 BizException 合同；
        // 用登录端点的「验证码不存在」业务失败：AuthService 显式抛 BizException。
        ResponseEntity<String> response = postJson("/api/auth/login", Map.of(
                "username", "test_sys_admin",
                "password", INITIAL_PASSWORD,
                "captchaId", "no-such-captcha-" + System.nanoTime(),
                "captchaCode", "0000"));
        assertThat(response.getStatusCode()).as("BizException 语义是客户端可修正，HTTP 层保持 200").isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).as("业务码必须非 0").isNotZero();
        assertThat(root.at("/msg").asText()).isNotBlank();
    }

    @Test
    void validationFailureReturnsFieldLevelError() throws Exception {
        // @Valid：username @NotBlank 缺失 → GlobalExceptionHandler.handleValid 返回「字段名: 消息」。
        ResponseEntity<String> response = postJson("/api/auth/login", Map.of(
                "password", "x",
                "captchaId", "cid",
                "captchaCode", "0000"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(400);
        assertThat(root.at("/msg").asText())
                .as("必须是字段级错误（含字段名），而不是笼统的参数错误")
                .startsWith("username");
    }

    @Test
    void uncaughtExceptionReturnsUnifiedResultWithoutStackTraceLeak() throws Exception {
        String token = readyLogin("test_sys_admin");

        // 未映射路径（带登录态才能越过安全链）：统一 404 Result，不泄漏堆栈（P1-10 收尾口径，Phase 51 定契约）。
        HttpHeaders authOnly = new HttpHeaders();
        authOnly.setBearerAuth(token);
        ResponseEntity<String> notFound = rest.exchange(url("/api/no-such-endpoint-phase00"),
                HttpMethod.GET, new HttpEntity<>(authOnly), String.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode notFoundRoot = json(notFound);
        assertThat(notFoundRoot.at("/code").asInt()).isEqualTo(404);
        assertThat(notFound.getBody()).doesNotContain("java.lang.", "at cn.edu.gpnu", "Exception");

        // 兜底 500 合同（T-RESP-1 的 P1-10 修订口径）：构造一个没有专用 @ExceptionHandler 的异常——
        // multipart 请求缺失 "file" part 抛 MissingServletRequestPartException，必然落 Exception 兜底：
        // HTTP 500 + 统一 Result（监控可见），响应体不得携带异常类名/堆栈帧。
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("bizType", BIZ_TYPE); // 刻意不带 "file" part
        ResponseEntity<String> broken = rest.exchange(url("/api/file/upload"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(broken.getStatusCode())
                .as("无专用 handler 的异常必须落兜底并对监控可见（HTTP 500）")
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        JsonNode brokenRoot = json(broken);
        assertThat(brokenRoot.at("/code").asInt()).isNotZero();
        assertThat(brokenRoot.at("/msg").asText()).isNotBlank();
        assertThat(broken.getBody())
                .as("兜底响应不得泄漏异常类名或堆栈帧")
                .doesNotContain("at cn.edu.gpnu", "java.lang.", "MissingServletRequestPartException");
    }

    // ---------------------------------------------------------------- §8-6/§8-7 上传→审计→预签名→过期

    @Test
    @Timeout(120)
    void uploadWritesAuditRowAndPresignedUrlExpiresAfterTtl() throws Exception {
        String token = readyLogin("test_sys_admin");
        long beforeUpload = auditCount("file", "upload");

        // §8-6 前半：上传返回 fileId（经真实带 @AuditLog 的端点，@PreAuthorize isAuthenticated）
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        byte[] content = ("phase00-presign-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "phase00-presign.txt";
            }
        });
        body.add("bizType", BIZ_TYPE);
        ResponseEntity<String> uploaded = rest.exchange(url("/api/file/upload"), HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(uploaded.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode uploadRoot = json(uploaded);
        assertThat(uploadRoot.at("/code").asInt()).isZero();
        long fileId = uploadRoot.at("/data/id").asLong();
        assertThat(fileId).as("上传必须返回 fileId").isPositive();

        // §8-7 / T-AUDIT-1：@AuditLog 成功后落 audit_log，操作人/时间/IP 非空
        assertThat(auditCount("file", "upload")).isEqualTo(beforeUpload + 1);
        SysAuditLog audit = auditLogMapper.selectOne(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, "file")
                .eq(SysAuditLog::getOperation, "upload")
                .orderByDesc(SysAuditLog::getId)
                .last("LIMIT 1"));
        assertThat(audit).isNotNull();
        assertThat(audit.getOperatorId()).as("操作人").isNotNull().isPositive();
        assertThat(audit.getOperateTime()).as("操作时间").isNotNull();
        assertThat(audit.getIp()).as("IP").isNotBlank();

        // §8-6 后半 / T-FILE-2：预签名限时可访问；过期后同一 URL 被拒绝。
        // 3 秒 TTL 使过期分支总耗时可控；正向访问在签发后立刻进行。
        String presigned = fileService.presignedGet(fileId, 3);
        HttpResponse<byte[]> fresh = httpClient.send(HttpRequest.newBuilder(URI.create(presigned)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(fresh.statusCode()).as("有效期内应可下载").isEqualTo(200);
        assertThat(fresh.body()).as("下载内容必须逐字节一致").isEqualTo(content);

        Thread.sleep(4_000); // 越过 3 秒签名有效期（MinIO 按签名内嵌的 X-Amz-Expires 判定）
        HttpResponse<String> expired = httpClient.send(HttpRequest.newBuilder(URI.create(presigned)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(expired.statusCode())
                .as("过期后同一 URL 必须被 MinIO 拒绝（403）")
                .isEqualTo(403);
        assertThat(expired.body()).contains("expired");
    }

    // ---------------------------------------------------------------- T-FILE-1（R10）：通用摘要秒传已退役

    @Test
    void sameContentUploadsRemainIndependentWithoutGlobalDeduplication() throws Exception {
        byte[] content = ("phase00-md5-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
        FileObject first = fileService.upload(new java.io.ByteArrayInputStream(content),
                "phase00-md5.txt", "text/plain", content.length, BIZ_TYPE);
        FileObject second = fileService.upload(new java.io.ByteArrayInputStream(content),
                "phase00-md5.txt", "text/plain", content.length, BIZ_TYPE);

        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isNotNull().isNotEqualTo(first.getId());
        assertThat(second.getObjectKey()).isNotEqualTo(first.getObjectKey());
        assertThat(fileObjectMapper.selectCount(new LambdaQueryWrapper<FileObject>()
                .eq(FileObject::getBizType, BIZ_TYPE)
                .eq(FileObject::getStatus, "READY")))
                .as("普通上传不提供全局摘要去重；两次请求必须保留两条独立元数据")
                .isEqualTo(2L);
    }

    // ---------------------------------------------------------------- helpers

    private long auditCount(String bizType, String operation) {
        return auditLogMapper.selectCount(new LambdaQueryWrapper<SysAuditLog>()
                .eq(SysAuditLog::getBizType, bizType)
                .eq(SysAuditLog::getOperation, operation));
    }

    private String readyLogin(String username) throws Exception {
        ResponseEntity<String> initial = loginRaw(username, INITIAL_PASSWORD);
        JsonNode root = json(initial);
        if (root.at("/code").asInt() != 0) {
            // 密码可能已被其它 IT 改为 CHANGED_PASSWORD
            root = json(loginRaw(username, CHANGED_PASSWORD));
        }
        assertThat(root.at("/code").asInt()).as("登录必须成功: %s", root).isZero();
        if (root.at("/data/mustChangePwd").asBoolean()) {
            String token = root.at("/data/accessToken").asText();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(url("/api/auth/change-pwd"), HttpMethod.POST, new HttpEntity<>(Map.of(
                    "oldPassword", INITIAL_PASSWORD,
                    "newPassword", CHANGED_PASSWORD), headers), String.class);
            root = json(loginRaw(username, CHANGED_PASSWORD));
            assertThat(root.at("/code").asInt()).isZero();
        }
        return root.at("/data/accessToken").asText();
    }

    /** 与 Phase13SystemAuditIT 同一套真实登录流：取验证码 → 从 dev/test SVG stub 解析答案 → 登录。 */
    private ResponseEntity<String> loginRaw(String username, String password) throws Exception {
        JsonNode captcha = json(rest.getForEntity(url("/api/auth/captcha"), String.class)).at("/data");
        return postJson("/api/auth/login", Map.of(
                "username", username,
                "password", password,
                "captchaId", captcha.at("/captchaId").asText(),
                "captchaCode", captchaCode(captcha.at("/image").asText())));
    }

    private String captchaCode(String image) {
        String svg = new String(java.util.Base64.getDecoder().decode(image.substring(image.indexOf(',') + 1)),
                StandardCharsets.UTF_8);
        return svg.replaceAll("(?s).*<text[^>]*>([^<]+)</text>.*", "$1").trim();
    }

    private ResponseEntity<String> postJson(String path, Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private void resetUser(String username) {
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            return;
        }
        user.setPasswordHash(passwordEncoder.encode(INITIAL_PASSWORD));
        user.setStatus("ENABLED");
        user.setMustChangePwd(0);
        user.setFailedLoginCount(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    private void cleanFixture() {
        jdbcTemplate.update("DELETE FROM file_object WHERE biz_type = ?", BIZ_TYPE);
    }
}
