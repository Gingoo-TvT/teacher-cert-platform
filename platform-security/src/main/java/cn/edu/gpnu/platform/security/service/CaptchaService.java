package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.config.SecurityProperties;
import cn.edu.gpnu.platform.system.service.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String KEY_PREFIX = "auth:captcha:";
    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final StringRedisTemplate redisTemplate;
    private final SecurityProperties securityProperties;
    private final ParamService paramService;
    private final SecureRandom random = new SecureRandom();

    public Captcha create() {
        String id = UUID.randomUUID().toString();
        String code = randomCode();
        int ttl = paramService.getInt("captcha.ttlSeconds", securityProperties.getLogin().getCaptchaTtlSeconds());
        redisTemplate.opsForValue().set(KEY_PREFIX + id, code, Duration.ofSeconds(ttl));
        return new Captcha(id, code, "data:image/svg+xml;base64," + java.util.Base64.getEncoder().encodeToString(svg(code).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public void validate(String captchaId, String captchaCode) {
        String key = KEY_PREFIX + captchaId;
        String expected = redisTemplate.opsForValue().get(key);
        if (expected == null) {
            throw new BizException("验证码已过期");
        }
        redisTemplate.delete(key);
        if (captchaCode == null || !expected.equals(captchaCode.trim().toUpperCase(Locale.ROOT))) {
            throw new BizException("验证码错误");
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS[random.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }

    private String svg(String code) {
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="112" height="40">
                  <rect width="112" height="40" fill="#f5f7fa"/>
                  <text x="16" y="27" font-family="Arial" font-size="24" fill="#2f3542">%s</text>
                </svg>
                """.formatted(code);
    }

    public record Captcha(String captchaId, String code, String image) {
    }
}
