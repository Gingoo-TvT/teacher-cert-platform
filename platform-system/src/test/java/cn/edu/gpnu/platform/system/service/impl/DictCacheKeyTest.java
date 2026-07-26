package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 44（PG-M4 第三轮整改）：字典 identity 与 Redis 键的反例。
 *
 * <p>MySQL {@code *_ai_ci} 把大小写别名视为同一字典；测试必须证明 Java、Redis 与 Caffeine 也使用同一个
 * {@code trim + Locale.ROOT 小写} identity，不能再用 raw String 不相等推导“不同类型”。同时保留三个 Redis
 * 命名空间（负载、版本、writer owners）的机械不碰撞证明。
 */
class DictCacheKeyTest {

    @Test
    void databaseEquivalentCaseAliasesShareOneCanonicalIdentityAndEveryRedisKey() {
        String canonical = "material_category";

        for (String alias : List.of(canonical, "MATERIAL_CATEGORY", " Material_Category ")) {
            assertThat(DictServiceImpl.normalizeTypeCode(alias)).isEqualTo(canonical);
            assertThat(DictServiceImpl.cacheKey(alias)).isEqualTo(DictServiceImpl.cacheKey(canonical));
            assertThat(DictServiceImpl.versionKey(alias)).isEqualTo(DictServiceImpl.versionKey(canonical));
            assertThat(DictServiceImpl.writersKey(alias)).isEqualTo(DictServiceImpl.writersKey(canonical));
        }
    }

    @Test
    void payloadVersionAndWriterNamespacesArePairwiseDisjointForEveryValidIdentity() {
        List<String> allKeys = new ArrayList<>();
        List<String> canonicalTypes = List.of("x", "ver_x", "version_x", "items_x", "dict_items_x", "a".repeat(64));
        for (String typeCode : canonicalTypes) {
            allKeys.add(DictServiceImpl.cacheKey(typeCode));
            allKeys.add(DictServiceImpl.versionKey(typeCode));
            allKeys.add(DictServiceImpl.writersKey(typeCode));
        }

        assertThat(allKeys)
                .as("每个 canonical type 的三类键及不同 type 之间都不得碰撞")
                .hasSize(canonicalTypes.size() * 3)
                .doesNotHaveDuplicates();
    }

    @Test
    void oldNestedPrefixWouldHaveCollidedButItsInputIsNowRejected() {
        String oldPayloadKey = "dict:items:" + "ver:X";
        String oldVersionKey = "dict:items:ver:" + "X";
        assertThat(oldPayloadKey).as("旧前缀确实会碰撞，这正是改名与字符集约束的原因").isEqualTo(oldVersionKey);

        assertThatThrownBy(() -> DictServiceImpl.cacheKey("ver:X"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("仅支持英文、数字、下划线");
    }

    @Test
    void invalidOrNonAsciiAliasesCannotExploitAiCiEquivalenceOrExpandTheKeyspace() {
        List<String> invalid = new ArrayList<>(List.of("", "  ", "ver:X", "dict:items:X", "中文类型",
                "matérial_category", "-version_x"));
        invalid.add("a".repeat(65));

        for (String typeCode : invalid) {
            assertThatThrownBy(() -> DictServiceImpl.normalizeTypeCode(typeCode))
                    .as("非法 typeCode 必须在进入 DB/cache 前失败关闭：[%s]", typeCode)
                    .isInstanceOf(BizException.class);
        }
    }
}
