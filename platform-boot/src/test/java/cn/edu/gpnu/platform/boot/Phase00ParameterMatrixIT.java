package cn.edu.gpnu.platform.boot;

import cn.edu.gpnu.platform.PlatformApplication;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 0 参数基线的真实迁移结果：逐项锁定 {@code docs/README.md §6} 的 key/value/type。
 *
 * <p>本测试依赖 fresh-schema Flyway 与真实 MySQL，只由获授权的 CI/独立复核环境执行；
 * 普通离线 {@code mvn test} 不会发现 {@code *IT}。
 */
@SpringBootTest(classes = PlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties =
        "platform.security.jwt.secret=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
class Phase00ParameterMatrixIT {

    private static final Map<String, ParamDefault> DOCUMENTED_DEFAULTS = Map.ofEntries(
            entry("cert.seq.scope", value("SCHOOL_YEAR_SEGMENT", "enum")),
            entry("cert.province.code", value("44", "string")),
            entry("cert.school.code", value("10588", "string")),
            entry("login.lockThreshold", value("5", "int")),
            entry("login.lockMinutes", value("15", "int")),
            entry("captcha.ttlSeconds", value("120", "int")),
            entry("file.material.allowedTypes", value("application/pdf,image/jpeg,image/png", "string")),
            entry("file.exemption.allowedTypes", value("application/pdf,image/jpeg,image/png", "string")),
            entry("file.maxSize.material", value("52428800", "int")),
            entry("file.maxSize.exemption", value("52428800", "int")),
            entry("file.maxSize.video", value("2147483648", "long")),
            entry("video.passLine", value("60", "int")),
            entry("video.diffThreshold", value("12", "int")),
            entry("video.reviewerCount", value("2", "int")),
            entry("video.durationTarget", value("900", "int")),
            entry("video.durationTolerance", value("60", "int")),
            entry("video.presign.expirySeconds", value("300", "int")),
            entry("video.allowedCodecs", value("H264", "string")),
            entry("video.timelineToleranceSeconds", value("2", "int")),
            entry("video.arbitrate.mode", value("thirdExpert", "string")),
            entry("video.finalizationCleanupSafetySeconds", value("60", "int")),
            entry("video.finalizationCleanupRetrySeconds", value("60", "int")),
            entry("video.finalizationCleanupClaimSeconds", value("1860", "int")),
            entry("video.finalizationCleanupBatchSize", value("100", "int")),
            entry("video.finalizationCleanupTombstoneCheckSeconds", value("3600", "int")),
            entry("video.required", value("true", "boolean")),
            entry("review.return.target", value("FIRST_REVIEW", "enum")),
            entry("validate.name.mode", value("loose", "enum")),
            entry("validate.idcard.checksum", value("false", "bool")),
            entry("student.autoCreateAccount", value("false", "bool")),
            entry("student.defaultPwd", value("random", "string")),
            entry("cleanup.backup.retentionDays", value("30", "int")),
            entry("current_assessment_year", value("2026", "int"))
    );

    @Autowired
    private SysParamMapper sysParamMapper;

    @Test
    void allDocumentedDefaultsHaveExpectedValueAndType() {
        assertThat(DOCUMENTED_DEFAULTS)
                .as("V1–V34 fresh-schema active sys_param 合同必须精确包含 33 项")
                .hasSize(33);

        // SysParam.deleted 使用 @TableLogic；空 wrapper 查询的是整张 active 参数集合，而不是预先按
        // DOCUMENTED_DEFAULTS 过滤后的子集，因此未知 key 和缺失 key 都会使下列 exact 断言失败。
        List<SysParam> rows = sysParamMapper.selectList(Wrappers.<SysParam>lambdaQuery()
                .orderByAsc(SysParam::getParamKey));
        assertThat(rows)
                .as("fresh-schema 不得出现文档外 active 参数，也不得缺失任一文档参数")
                .extracting(SysParam::getParamKey)
                .containsExactlyInAnyOrderElementsOf(DOCUMENTED_DEFAULTS.keySet());

        Map<String, ParamDefault> actual = new LinkedHashMap<>();
        for (SysParam row : rows) {
            actual.put(row.getParamKey(), value(row.getParamValue(), row.getParamType()));
        }

        assertThat(actual)
                .as("docs/README.md §6 的全部参数必须由 fresh-schema 迁移生成，且 value/type 精确一致")
                .containsExactlyInAnyOrderEntriesOf(DOCUMENTED_DEFAULTS);
    }

    private static ParamDefault value(String value, String type) {
        return new ParamDefault(value, type);
    }

    private record ParamDefault(String value, String type) {
    }
}
