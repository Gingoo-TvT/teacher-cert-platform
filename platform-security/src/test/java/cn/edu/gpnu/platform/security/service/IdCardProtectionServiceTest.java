package cn.edu.gpnu.platform.security.service;

import cn.edu.gpnu.platform.security.config.IdCardProtectionProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdCardProtectionServiceTest {

    private static final String PRIMARY_KEY =
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String ALTERNATIVE_KEY =
            "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=";
    private static final String PEPPER =
            "unit-test-id-card-hmac-pepper-at-least-thirty-two-bytes";
    private static final String ID_CARD_NO = "44010620000101001X";

    @Test
    void aesGcmRoundTripUsesVersionedCiphertextWithoutPlaintext() {
        IdCardProtectionService service = service(PRIMARY_KEY, PEPPER);

        String encrypted = service.encrypt(ID_CARD_NO);

        assertThat(encrypted)
                .startsWith("v1:")
                .doesNotContain(ID_CARD_NO);
        assertThat(service.isEncrypted(encrypted)).isTrue();
        assertThat(service.decrypt(encrypted)).isEqualTo(ID_CARD_NO);
        assertThat(service.encrypt(encrypted)).isEqualTo(encrypted);
    }

    @Test
    void randomIvProducesDifferentCiphertextForSamePlaintext() {
        IdCardProtectionService service = service(PRIMARY_KEY, PEPPER);

        String first = service.encrypt(ID_CARD_NO);
        String second = service.encrypt(ID_CARD_NO);

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo(ID_CARD_NO);
        assertThat(service.decrypt(second)).isEqualTo(ID_CARD_NO);
    }

    @Test
    void hmacIsStableLowercaseHexAndChangesWithPlaintext() {
        IdCardProtectionService service = service(PRIMARY_KEY, PEPPER);

        String first = service.hmac(ID_CARD_NO);

        assertThat(first)
                .hasSize(64)
                .matches("[0-9a-f]{64}")
                .isEqualTo("a2aabfea93b78cba2c59246aff261a880e9eaeccac71b19f8c67c1faf7957ff9")
                .isEqualTo(service.hmac(ID_CARD_NO))
                .isNotEqualTo(service.hmac("440106200001010028"));
    }

    @Test
    void tamperingAndWrongKeyFailClosedWithoutSensitiveValueInMessage() {
        IdCardProtectionService service = service(PRIMARY_KEY, PEPPER);
        String encrypted = service.encrypt(ID_CARD_NO);
        int changedIndex = encrypted.length() - 5;
        char replacement = encrypted.charAt(changedIndex) == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, changedIndex)
                + replacement
                + encrypted.substring(changedIndex + 1);

        assertProtectedValueRejected(service, tampered);
        assertProtectedValueRejected(service(ALTERNATIVE_KEY, PEPPER), encrypted);
    }

    @Test
    void missingAndWeakConfigurationFailBeforeUse() {
        assertThatThrownBy(() -> service(null, PEPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption-key")
                .hasMessageNotContaining(PEPPER);
        assertThatThrownBy(() -> service("bm90LTMyLWJ5dGVz", PEPPER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 字节")
                .hasMessageNotContaining(PEPPER);
        assertThatThrownBy(() -> service(PRIMARY_KEY, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hmac-pepper")
                .hasMessageNotContaining(PRIMARY_KEY);
        assertThatThrownBy(() -> service(PRIMARY_KEY, "too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("至少为 32")
                .hasMessageNotContaining(PRIMARY_KEY);
    }

    @Test
    void encryptionKeyAndHmacPepperMustRemainIndependentAfterTrimming() {
        assertThatThrownBy(() -> service("  " + PRIMARY_KEY + "  ", "\t" + PRIMARY_KEY + " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encryption-key")
                .hasMessageContaining("hmac-pepper")
                .hasMessageNotContaining(PRIMARY_KEY);
    }

    @Test
    void plaintextAndEncryptedHmacInputAreRejected() {
        IdCardProtectionService service = service(PRIMARY_KEY, PEPPER);
        String encrypted = service.encrypt(ID_CARD_NO);

        assertThatThrownBy(() -> service.decrypt(ID_CARD_NO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("身份证件号码保护数据不可用")
                .hasMessageNotContaining(ID_CARD_NO);
        assertThatThrownBy(() -> service.hmac(encrypted))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是身份证件号码明文")
                .hasMessageNotContaining(encrypted);
    }

    private void assertProtectedValueRejected(IdCardProtectionService service, String value) {
        assertThatThrownBy(() -> service.decrypt(value))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("身份证件号码保护数据不可用")
                .hasMessageNotContaining(value)
                .hasMessageNotContaining(ID_CARD_NO);
    }

    private IdCardProtectionService service(String encryptionKey, String hmacPepper) {
        IdCardProtectionProperties properties = new IdCardProtectionProperties();
        properties.setEncryptionKey(encryptionKey);
        properties.setHmacPepper(hmacPepper);
        return new IdCardProtectionService(properties);
    }
}
