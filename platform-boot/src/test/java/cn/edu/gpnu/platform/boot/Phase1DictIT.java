package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 43.4 回归：字典编码软删盲区。
 * uk_sys_dict_type_code / uk_sys_dict_item_type_code_year 均不含 deleted，
 * 软删后同码重建此前会绕过 existsTypeCode/existsItem（仅按 deleted=0 过滤）的应用层预检，
 * 直接撞库抛出裸 DuplicateKeyException，最终经兜底处理器返回 500 系统异常。
 * 修复后：DictServiceImpl 改用 *IncludingDeleted 查询，应用层能在写库前发现冲突，
 * 返回友好的业务错误（code=1000），不再是裸 500。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "platform.security.jwt.access-ttl-seconds=30"
})
class Phase1DictIT {

    private static final String INITIAL_PASSWORD = "ChangeMe123!";
    private static final String CHANGED_PASSWORD = "Changed123!";
    private static final String TEST_TYPE_CODE = "p43_dict_softdel_type";
    private static final String TEST_ITEM_TYPE_CODE = "p43_dict_softdel_item_type";
    private static final String TEST_ITEM_CODE = "p43_dict_softdel_item";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void resetFixture() {
        cleanupGeneratedData();
        resetUser("test_academic_admin", true);
    }

    @Test
    void deleteThenRecreateSameTypeCode_returnsFriendlyErrorNotServerError() throws Exception {
        LoginResult admin = readyLogin("test_academic_admin");

        long firstId = createType(admin.accessToken(), TEST_TYPE_CODE, "P43软删回归-类型");
        deleteType(admin.accessToken(), firstId);

        ResponseEntity<String> recreate = exchange("/api/dict/type", HttpMethod.POST, admin.accessToken(),
                typeRequest(TEST_TYPE_CODE, "P43软删回归-类型-重建"));
        assertThat(recreate.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json(recreate);
        assertThat(body.at("/code").asInt()).isEqualTo(1000);
        assertThat(body.at("/msg").asText()).isEqualTo("字典类型编码已存在");
    }

    @Test
    void deleteThenRecreateSameItemCode_returnsFriendlyErrorNotServerError() throws Exception {
        LoginResult admin = readyLogin("test_academic_admin");

        createType(admin.accessToken(), TEST_ITEM_TYPE_CODE, "P43软删回归-字典项所属类型");
        long firstItemId = createItem(admin.accessToken(), TEST_ITEM_TYPE_CODE, TEST_ITEM_CODE, "P43软删回归-字典项");
        deleteItem(admin.accessToken(), firstItemId);

        ResponseEntity<String> recreate = exchange("/api/dict/item", HttpMethod.POST, admin.accessToken(),
                itemRequest(TEST_ITEM_TYPE_CODE, TEST_ITEM_CODE, "P43软删回归-字典项-重建"));
        assertThat(recreate.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json(recreate);
        assertThat(body.at("/code").asInt()).isEqualTo(1000);
        assertThat(body.at("/msg").asText()).isEqualTo("字典项编码在当前年度版本已存在");
    }

    private long createType(String token, String typeCode, String typeName) throws Exception {
        ResponseEntity<String> response = exchange("/api/dict/type", HttpMethod.POST, token,
                typeRequest(typeCode, typeName));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data").asLong();
    }

    private void deleteType(String token, long id) throws Exception {
        ResponseEntity<String> response = exchange("/api/dict/type/" + id, HttpMethod.DELETE, token, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private long createItem(String token, String typeCode, String itemCode, String itemValue) throws Exception {
        ResponseEntity<String> response = exchange("/api/dict/item", HttpMethod.POST, token,
                itemRequest(typeCode, itemCode, itemValue));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode root = json(response);
        assertThat(root.at("/code").asInt()).isEqualTo(0);
        return root.at("/data").asLong();
    }

    private void deleteItem(String token, long id) throws Exception {
        ResponseEntity<String> response = exchange("/api/dict/item/" + id, HttpMethod.DELETE, token, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(response).at("/code").asInt()).isEqualTo(0);
    }

    private Map<String, Object> typeRequest(String typeCode, String typeName) {
        Map<String, Object> body = new HashMap<>();
        body.put("typeCode", typeCode);
        body.put("typeName", typeName);
        body.put("sort", 999);
        body.put("status", 1);
        return body;
    }

    private Map<String, Object> itemRequest(String typeCode, String itemCode, String itemValue) {
        Map<String, Object> body = new HashMap<>();
        body.put("typeCode", typeCode);
        body.put("itemCode", itemCode);
        body.put("itemValue", itemValue);
        body.put("sort", 999);
        body.put("status", 1);
        body.put("yearVersion", "GLOBAL");
        return body;
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

    private void cleanupGeneratedData() {
        jdbcTemplate.update("DELETE FROM sys_dict_item WHERE type_code IN (?, ?)", TEST_TYPE_CODE, TEST_ITEM_TYPE_CODE);
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE type_code IN (?, ?)", TEST_TYPE_CODE, TEST_ITEM_TYPE_CODE);
    }

    private record LoginResult(String accessToken, String refreshToken, boolean mustChangePwd) {
    }
}
