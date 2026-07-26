package cn.edu.gpnu.platform.system.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 44（PG-M4 第二轮整改）：字典缓存 Redis 键的<b>命名空间不相交</b>反例。
 *
 * <p>上一轮版本键前缀是 {@code dict:items:ver:}，与负载前缀 {@code dict:items:} 嵌套：合法 typeCode
 * {@code "ver:X"} 会让「类型 {@code ver:X} 的负载键」与「类型 {@code X} 的版本键」变成同一个键——
 * 负载 JSON 会被当成版本令牌、版本令牌会被当成负载，缓存语义直接错乱。{@code type_code} 只是
 * {@code VARCHAR(64)}，应用层没有字符集约束，故这是合法输入而非畸形输入。
 *
 * <p>现前缀改为 {@code dict:items-version:}：两者在第 11 个字符上分别是 {@code ':'} 与 {@code '-'}，
 * 无论 typeCode 取什么值都不可能相等。本类用对抗性 typeCode 直接验证该性质。
 */
class DictCacheKeyTest {

    @ParameterizedTest
    @ValueSource(strings = {"X", "ver:X", "version:X", "items:X", "dict:items:X", ":", "", "  ", "中文类型",
            "-version:X", "items-version:X"})
    void payloadKeyNeverCollidesWithVersionKeyOfAnyOtherTypeCode(String typeCode) {
        List<String> allTypeCodes = adversarialTypeCodes();

        String payloadKey = DictServiceImpl.cacheKey(typeCode);
        String versionKey = DictServiceImpl.versionKey(typeCode);

        assertThat(payloadKey).isNotEqualTo(versionKey);
        for (String other : allTypeCodes) {
            assertThat(payloadKey)
                    .as("类型 [%s] 的负载键不得等于类型 [%s] 的版本键", typeCode, other)
                    .isNotEqualTo(DictServiceImpl.versionKey(other));
            if (!other.equals(typeCode)) {
                assertThat(payloadKey)
                        .as("不同类型的负载键必须互不相同：[%s] vs [%s]", typeCode, other)
                        .isNotEqualTo(DictServiceImpl.cacheKey(other));
                assertThat(versionKey)
                        .as("不同类型的版本键必须互不相同：[%s] vs [%s]", typeCode, other)
                        .isNotEqualTo(DictServiceImpl.versionKey(other));
            }
        }
    }

    @Test
    void oldNestedPrefixWouldHaveCollided() {
        // 记录上一轮的真实缺陷形态：旧前缀下 "ver:X" 的负载键 == "X" 的版本键。
        String oldPayloadKey = "dict:items:" + "ver:X";
        String oldVersionKey = "dict:items:ver:" + "X";
        assertThat(oldPayloadKey).as("旧前缀确实会碰撞，这正是本次改名的原因").isEqualTo(oldVersionKey);

        assertThat(DictServiceImpl.cacheKey("ver:X"))
                .as("新前缀下同一输入不得再碰撞")
                .isNotEqualTo(DictServiceImpl.versionKey("X"));
    }

    @Test
    void keyPrefixesDifferAtTheFirstDivergingCharacter() {
        // 不相交性的机械证明：两个前缀在同一位置上分别是 ':' 与 '-'，任何后缀都无法弥合。
        String payloadPrefix = DictServiceImpl.cacheKey("");
        String versionPrefix = DictServiceImpl.versionKey("");
        int divergence = 0;
        while (divergence < Math.min(payloadPrefix.length(), versionPrefix.length())
                && payloadPrefix.charAt(divergence) == versionPrefix.charAt(divergence)) {
            divergence++;
        }
        assertThat(divergence).isLessThan(payloadPrefix.length());
        assertThat(divergence).isLessThan(versionPrefix.length());
        assertThat(payloadPrefix.charAt(divergence)).isNotEqualTo(versionPrefix.charAt(divergence));
    }

    private List<String> adversarialTypeCodes() {
        List<String> codes = new ArrayList<>(List.of("X", "ver:X", "version:X", "items:X", "dict:items:X",
                ":", "中文类型", "-version:X", "items-version:X"));
        codes.add("");
        codes.add("  ");
        codes.add("a".repeat(64));
        return codes;
    }
}
