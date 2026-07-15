package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.security.config.SecurityProperties;
import cn.edu.gpnu.platform.system.vo.UserSecurityVO;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenRevocationServiceTest {

    private static final long USER_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-07-15T12:34:56.789Z");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SecurityProperties securityProperties;
    private TokenRevocationService service;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.getJwt().setAccessTtlSeconds(3600);
        securityProperties.getJwt().setRefreshTtlSeconds(7200);
        service = new TokenRevocationService(
                redisTemplate, securityProperties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void revokeAtomicallyKeepsMaximumCutoffAndRefreshesLongestTokenLifetime() {
        service.revoke(USER_ID);

        ArgumentCaptor<RedisScript<Long>> scriptCaptor =
                (ArgumentCaptor) ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                eq(List.of(
                        "auth:revoke-after:" + USER_ID,
                        "auth:session-generation:" + USER_ID)),
                eq(Long.toString(NOW.toEpochMilli())),
                eq(Long.toString(Duration.ofSeconds(7200).toMillis())));

        String script = scriptCaptor.getValue().getScriptAsString();
        assertThat(script)
                .contains("redis.call('INCR', KEYS[2])")
                .contains("redis.call('PERSIST', KEYS[2])")
                .contains("existing = existing * 1000 + 999")
                .contains("selected = math.max(incoming, existing)")
                .contains("redis.call('SET', KEYS[1], selectedRaw)")
                .contains("redis.call('PEXPIRE', KEYS[1], ttlMillis)")
                .doesNotContain("PEXPIRE', KEYS[2]");
        assertThat(script.indexOf("math.max"))
                .isLessThan(script.indexOf("PEXPIRE"));
    }

    @Test
    void missingGenerationDefaultsToZeroAndClaimMustMatchCurrentValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:session-generation:" + USER_ID))
                .thenReturn(null, null, "4", "4");

        assertThat(service.currentSessionGeneration(USER_ID)).isZero();
        assertThat(service.isCurrentSessionGeneration(USER_ID, 0L)).isTrue();
        assertThat(service.isCurrentSessionGeneration(USER_ID, 3L)).isFalse();
        assertThat(service.isCurrentSessionGeneration(USER_ID, 4L)).isTrue();
        assertThat(service.isCurrentSessionGeneration(USER_ID, null)).isFalse();
    }

    @Test
    void malformedOrNegativeStoredGenerationFailsClosed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:session-generation:" + USER_ID))
                .thenReturn("invalid", "-1");

        assertThat(service.isCurrentSessionGeneration(USER_ID, 0L)).isFalse();
        assertThat(service.isCurrentSessionGeneration(USER_ID, 0L)).isFalse();
    }

    @Test
    void millisecondCutoffRevokesAtBoundaryAndAllowsLaterToken() {
        long cutoff = NOW.toEpochMilli();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:revoke-after:" + USER_ID)).thenReturn(Long.toString(cutoff));

        assertThat(service.isRevoked(USER_ID, Date.from(NOW), cutoff)).isTrue();
        assertThat(service.isRevoked(USER_ID, Date.from(NOW), cutoff + 1L)).isFalse();

        // 历史 token 没有 iatMs 时，cutoff 所在整秒保守失效，下一秒恢复可判定。
        assertThat(service.isRevoked(USER_ID, Date.from(NOW), null)).isTrue();
        assertThat(service.isRevoked(USER_ID,
                Date.from(Instant.ofEpochSecond(NOW.getEpochSecond() + 1L)), null)).isFalse();
    }

    @Test
    void legacySecondCutoffIsConservativeForNewAndOldTokenFormats() {
        long legacyCutoff = NOW.getEpochSecond();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:revoke-after:" + USER_ID)).thenReturn(Long.toString(legacyCutoff));

        long lastMillisInCutoffSecond = (legacyCutoff + 1L) * 1000L - 1L;
        long firstMillisAfterCutoffSecond = (legacyCutoff + 1L) * 1000L;
        assertThat(service.isRevoked(USER_ID, Date.from(NOW), lastMillisInCutoffSecond)).isTrue();
        assertThat(service.isRevoked(USER_ID, Date.from(NOW), firstMillisAfterCutoffSecond)).isFalse();
        assertThat(service.isRevoked(USER_ID, Date.from(NOW), null)).isTrue();
        assertThat(service.isRevoked(USER_ID,
                Date.from(Instant.ofEpochSecond(legacyCutoff + 1L)), null)).isFalse();
    }

    @Test
    void missingTokenTimestampFailsClosedOnlyWhenValidCutoffExists() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:revoke-after:" + USER_ID))
                .thenReturn(Long.toString(NOW.toEpochMilli()), "invalid-cutoff", null);

        assertThat(service.isRevoked(USER_ID, null, null)).isTrue();
        assertThat(service.isRevoked(USER_ID, null, null)).isFalse();
        assertThat(service.isRevoked(USER_ID, null, null)).isFalse();
    }

    @Test
    void jwtKeepsStandardIssuedAtAndBindsMillisecondAndCredentialClaims() {
        securityProperties.getJwt().setSecret(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        JwtService jwtService = new JwtService(securityProperties);
        jwtService.init();
        UserSecurityVO user = new UserSecurityVO();
        user.setId(USER_ID);
        user.setUsername("token-boundary-user");
        user.setPasswordHash("$2a$10$original-password-hash-snapshot-for-token-version");
        user.getRoles().add("STUDENT");

        long beforeIssue = System.currentTimeMillis();
        Claims claims = jwtService.parse(jwtService.issueAccessToken(user, 7L));
        long afterIssue = System.currentTimeMillis();

        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(jwtService.preciseIssuedAtMillis(claims)).isBetween(beforeIssue, afterIssue);
        assertThat(claims.getIssuedAt().toInstant().getEpochSecond())
                .isEqualTo(Math.floorDiv(jwtService.preciseIssuedAtMillis(claims), 1000L));
        assertThat(claims.get("typ", String.class)).isEqualTo("access");
        assertThat(jwtService.sessionGeneration(claims)).isEqualTo(7L);
        assertThat(jwtService.hasCurrentCredentialVersion(claims, user)).isTrue();

        user.setPasswordHash("$2a$10$concurrently-reset-password-hash-for-token-version");
        assertThat(jwtService.hasCurrentCredentialVersion(claims, user)).isFalse();

        Claims legacyClaims = mock(Claims.class);
        assertThat(jwtService.sessionGeneration(legacyClaims)).isNull();
        assertThat(jwtService.hasCurrentCredentialVersion(legacyClaims, user)).isFalse();
    }
}
