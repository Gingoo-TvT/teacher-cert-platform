package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.config.SecurityProperties;
import cn.edu.gpnu.platform.system.vo.UserSecurityVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String PRECISE_ISSUED_AT_CLAIM = "iatMs";
    private static final String CREDENTIAL_VERSION_CLAIM = "credentialVersion";
    private static final String SESSION_GENERATION_CLAIM = "sessionGeneration";

    private final SecurityProperties securityProperties;
    private SecretKey key;

    @PostConstruct
    public void init() {
        String secret = securityProperties.getJwt().getSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BizException("JWT密钥未配置，请通过环境变量 JWT_SECRET 注入");
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException | DecodingException ignored) {
            // 非 Base64（如人类可读口令、含 '-' 等非 Base64 字符）→ 按原始 UTF-8 字节使用。
            // jjwt 的 Decoders.BASE64 抛 io.jsonwebtoken.io.DecodingException（非 IllegalArgumentException 子类），
            // 若不一并捕获，任何非 Base64 的 JWT_SECRET 都会以晦涩的 base64 报错崩溃启动。
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new BizException("JWT密钥长度不足，至少32字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issueAccessToken(UserSecurityVO user, long sessionGeneration) {
        return issue(user, "access", securityProperties.getJwt().getAccessTtlSeconds(), sessionGeneration);
    }

    public String issueRefreshToken(UserSecurityVO user, long sessionGeneration) {
        return issue(user, "refresh", securityProperties.getJwt().getRefreshTtlSeconds(), sessionGeneration);
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(cn.edu.gpnu.platform.common.api.ResultCode.UNAUTHORIZED.getCode(), "token无效或已过期");
        }
    }

    public Long userId(String token, String expectedType) {
        Claims claims = parse(token);
        if (!expectedType.equals(claims.get("typ", String.class))) {
            throw new BizException(cn.edu.gpnu.platform.common.api.ResultCode.UNAUTHORIZED.getCode(), "token类型不正确");
        }
        return Long.valueOf(claims.getSubject());
    }

    /** 返回新 token 的毫秒级签发时间；历史 token 不含该 claim 时返回 {@code null}。 */
    public Long preciseIssuedAtMillis(Claims claims) {
        Object value = claims == null ? null : claims.get(PRECISE_ISSUED_AT_CLAIM);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 返回 token 的会话代次；旧 token 或非法 claim 返回 {@code null} 并由鉴权层 fail closed。 */
    public Long sessionGeneration(Claims claims) {
        Object value = claims == null ? null : claims.get(SESSION_GENERATION_CLAIM);
        if (value instanceof Number || value instanceof String) {
            try {
                long generation = Long.parseLong(value.toString());
                return generation < 0 ? null : generation;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 新版 token 必须绑定当前口令哈希；缺少或不匹配版本均拒绝。 */
    public boolean hasCurrentCredentialVersion(Claims claims, UserSecurityVO user) {
        Object claim = claims == null ? null : claims.get(CREDENTIAL_VERSION_CLAIM);
        if (!(claim instanceof String claimedVersion)
                || user == null
                || !StringUtils.hasText(user.getPasswordHash())) {
            return false;
        }
        return MessageDigest.isEqual(
                credentialVersion(user.getPasswordHash()).getBytes(StandardCharsets.UTF_8),
                claimedVersion.getBytes(StandardCharsets.UTF_8));
    }

    /** 登录验密后 reload 必须仍是同一份口令哈希快照。 */
    public boolean hasSameCredential(UserSecurityVO expected, UserSecurityVO current) {
        if (expected == null || current == null
                || !StringUtils.hasText(expected.getPasswordHash())
                || !StringUtils.hasText(current.getPasswordHash())) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getPasswordHash().getBytes(StandardCharsets.UTF_8),
                current.getPasswordHash().getBytes(StandardCharsets.UTF_8));
    }

    private String issue(UserSecurityVO user, String type, long ttlSeconds, long sessionGeneration) {
        if (sessionGeneration < 0) {
            throw new IllegalArgumentException("sessionGeneration 不能为负数");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("typ", type)
                .claim("username", user.getUsername())
                .claim("roles", List.copyOf(user.getRoles()))
                .claim(PRECISE_ISSUED_AT_CLAIM, now.toEpochMilli())
                .claim(CREDENTIAL_VERSION_CLAIM, credentialVersion(user.getPasswordHash()))
                .claim(SESSION_GENERATION_CLAIM, sessionGeneration)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }

    private String credentialVersion(String passwordHash) {
        if (!StringUtils.hasText(passwordHash)) {
            throw new IllegalStateException("用户凭据哈希缺失，无法签发 token");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
