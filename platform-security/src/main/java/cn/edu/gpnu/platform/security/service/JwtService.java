package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.config.SecurityProperties;
import cn.edu.gpnu.platform.system.vo.UserSecurityVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JwtService {

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
        } catch (IllegalArgumentException ignored) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new BizException("JWT密钥长度不足，至少32字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String issueAccessToken(UserSecurityVO user) {
        return issue(user, "access", securityProperties.getJwt().getAccessTtlSeconds());
    }

    public String issueRefreshToken(UserSecurityVO user) {
        return issue(user, "refresh", securityProperties.getJwt().getRefreshTtlSeconds());
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

    private String issue(UserSecurityVO user, String type, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("typ", type)
                .claim("username", user.getUsername())
                .claim("roles", List.copyOf(user.getRoles()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key)
                .compact();
    }
}
