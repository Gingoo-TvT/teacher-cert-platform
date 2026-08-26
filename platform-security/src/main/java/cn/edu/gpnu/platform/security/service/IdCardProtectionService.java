package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.security.config.IdCardProtectionProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 身份证件号码的应用层加密与确定性查重摘要。
 *
 * <p>AES-GCM 每次写入生成独立随机 IV；HMAC 只用于等值查询和唯一约束，不能用于还原明文。
 * 本类不记录输入、密文或密钥，密码学失败统一以不含敏感值的异常关闭。
 */
@Service
public class IdCardProtectionService {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String ENCRYPTED_PREFIX = "v1:";
    private static final byte[] AAD = "teacher-cert:id-card:v1".getBytes(StandardCharsets.UTF_8);
    private static final int AES_KEY_BYTES = 32;
    private static final int MIN_HMAC_PEPPER_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_TAG_BYTES = GCM_TAG_BITS / Byte.SIZE;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec hmacKey;
    private final SecureRandom secureRandom;

    public IdCardProtectionService(IdCardProtectionProperties properties) {
        if (properties == null) {
            throw configurationError("platform.security.id-card 未配置");
        }
        String encryptionKeyValue = trimmedConfigurationValue(
                properties.getEncryptionKey(),
                "platform.security.id-card.encryption-key 未配置");
        String hmacPepperValue = trimmedConfigurationValue(
                properties.getHmacPepper(),
                "platform.security.id-card.hmac-pepper 未配置");
        if (encryptionKeyValue.equals(hmacPepperValue)) {
            throw configurationError("platform.security.id-card.encryption-key 与 "
                    + "platform.security.id-card.hmac-pepper 必须使用不同值");
        }
        this.encryptionKey = new SecretKeySpec(decodeEncryptionKey(encryptionKeyValue), "AES");
        this.hmacKey = new SecretKeySpec(validatePepper(hmacPepperValue), HMAC_ALGORITHM);
        this.secureRandom = new SecureRandom();
    }

    /**
     * 加密明文。已加密值会先完成认证校验再原样返回，便于迁移安全重入。
     */
    public String encrypt(String plaintext) {
        requireValue(plaintext, "身份证件号码明文不能为空");
        if (isEncrypted(plaintext)) {
            decrypt(plaintext);
            return plaintext;
        }

        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return ENCRYPTED_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw protectionError(exception);
        }
    }

    /**
     * 解密并验证 AES-GCM 认证标签。非 v1 数据、篡改数据和错误密钥均失败关闭。
     */
    public String decrypt(String protectedValue) {
        requireValue(protectedValue, "身份证件号码密文不能为空");
        if (!isEncrypted(protectedValue)) {
            throw protectionError(null);
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(protectedValue.substring(ENCRYPTED_PREFIX.length()));
            if (payload.length <= GCM_IV_BYTES + GCM_TAG_BYTES) {
                throw protectionError(null);
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(payload, GCM_IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(AAD);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (AEADBadTagException | IllegalArgumentException exception) {
            throw protectionError(exception);
        } catch (GeneralSecurityException exception) {
            throw protectionError(exception);
        }
    }

    /**
     * 为规范化后的明文生成稳定的 64 位小写十六进制 HMAC-SHA256。
     */
    public String hmac(String plaintext) {
        requireValue(plaintext, "身份证件号码明文不能为空");
        if (isEncrypted(plaintext)) {
            throw new IllegalArgumentException("HMAC 输入必须是身份证件号码明文");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw protectionError(exception);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    private static byte[] decodeEncryptionKey(String encodedKey) {
        requireConfigurationValue(encodedKey, "platform.security.id-card.encryption-key 未配置");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException exception) {
            throw configurationError("platform.security.id-card.encryption-key 必须是 Base64 编码");
        }
        if (decoded.length != AES_KEY_BYTES) {
            throw configurationError("platform.security.id-card.encryption-key 必须解码为 32 字节");
        }
        return decoded;
    }

    private static byte[] validatePepper(String pepper) {
        requireConfigurationValue(pepper, "platform.security.id-card.hmac-pepper 未配置");
        byte[] pepperBytes = pepper.trim().getBytes(StandardCharsets.UTF_8);
        if (pepperBytes.length < MIN_HMAC_PEPPER_BYTES) {
            throw configurationError("platform.security.id-card.hmac-pepper 必须至少为 32 个 UTF-8 字节");
        }
        return pepperBytes;
    }

    private static void requireConfigurationValue(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw configurationError(message);
        }
    }

    private static String trimmedConfigurationValue(String value, String message) {
        requireConfigurationValue(value, message);
        return value.trim();
    }

    private static void requireValue(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static IllegalStateException configurationError(String message) {
        return new IllegalStateException(message);
    }

    private static IllegalStateException protectionError(Exception cause) {
        if (cause == null) {
            return new IllegalStateException("身份证件号码保护数据不可用");
        }
        return new IllegalStateException("身份证件号码保护数据不可用", cause);
    }
}
