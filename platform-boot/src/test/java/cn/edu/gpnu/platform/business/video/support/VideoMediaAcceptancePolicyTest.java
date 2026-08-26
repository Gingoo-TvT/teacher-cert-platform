package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.system.service.ParamService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoMediaAcceptancePolicyTest {

    private static final String POLICY_HASH = "policy-v1";
    private static final String PROBE_VERSION = "probe-v1";
    private static final long DEFAULT_MAX_VIDEO_SIZE = 2_147_483_648L;

    private final TestParamService paramService = new TestParamService();
    private final VideoMediaProbe probe = new StubVideoMediaProbe(POLICY_HASH, PROBE_VERSION);

    @Test
    void rejectsNonMp4ContentType() {
        VideoMediaAcceptancePolicy.Result result = validate(
                "application/octet-stream", 1L, validInspection(900));

        assertRejected(result, "视频格式必须为MP4");
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        paramService.put("file.maxSize.video", "100");

        VideoMediaAcceptancePolicy.Result result =
                validate("video/mp4", 101L, validInspection(900));

        assertRejected(result, "视频大小超过限制");
    }

    @Test
    void rejectsStaleProbeVersion() {
        VideoMediaInspection inspection = VideoMediaInspection.valid(
                "fingerprint", 900, "H264", 900, POLICY_HASH, "probe-v0");

        VideoMediaAcceptancePolicy.Result result =
                validate("video/mp4", 1L, inspection);

        assertRejected(result, "视频校验策略已变化，请重新提交");
    }

    @Test
    void rejectsStalePolicyHash() {
        VideoMediaInspection inspection = VideoMediaInspection.valid(
                "fingerprint", 900, "H264", 900, "policy-v0", PROBE_VERSION);

        VideoMediaAcceptancePolicy.Result result =
                validate("video/mp4", 1L, inspection);

        assertRejected(result, "视频校验策略已变化，请重新提交");
    }

    @Test
    void propagatesTechnicalInspectionFailure() {
        VideoMediaInspection inspection = VideoMediaInspection.invalid(
                "首帧不可解码", "fingerprint", 900, "H264", 900,
                POLICY_HASH, PROBE_VERSION);

        VideoMediaAcceptancePolicy.Result result =
                validate("video/mp4", 1L, inspection);

        assertRejected(result, "首帧不可解码");
    }

    @Test
    void rejectsMissingDuration() {
        VideoMediaInspection inspection = new VideoMediaInspection(
                true, null, "fingerprint", null, "H264", 900,
                POLICY_HASH, PROBE_VERSION);

        VideoMediaAcceptancePolicy.Result result =
                validate("video/mp4", 1L, inspection);

        assertRejected(result, "视频时长不能为空");
    }

    @Test
    void rejectsDurationOutsideConfiguredTolerance() {
        paramService.put("video.durationTarget", "900");
        paramService.put("video.durationTolerance", "10");

        VideoMediaAcceptancePolicy.Result result =
                validate("video/mp4", 1L, validInspection(911));

        assertRejected(result, "视频时长超出容差");
    }

    @Test
    void acceptsDurationAtToleranceBoundary() {
        paramService.put("file.maxSize.video", "100");
        paramService.put("video.durationTarget", "900");
        paramService.put("video.durationTolerance", "10");

        VideoMediaAcceptancePolicy.Result result =
                validate("video/mp4", 100L, validInspection(910));

        assertTrue(result.accepted());
        assertNull(result.message());
    }

    @Test
    void invalidMaximumSizeParameterFallsBackToProductionDefault() {
        paramService.put("file.maxSize.video", "not-a-long");

        VideoMediaAcceptancePolicy.Result atDefaultLimit =
                validate("video/mp4", DEFAULT_MAX_VIDEO_SIZE, validInspection(900));
        VideoMediaAcceptancePolicy.Result aboveDefaultLimit =
                validate("video/mp4", DEFAULT_MAX_VIDEO_SIZE + 1L, validInspection(900));

        assertTrue(atDefaultLimit.accepted());
        assertRejected(aboveDefaultLimit, "视频大小超过限制");
    }

    @Test
    void invalidDurationParametersFallBackToProductionDefaults() {
        paramService.put("video.durationTarget", "not-an-int");
        paramService.put("video.durationTolerance", "also-not-an-int");

        VideoMediaAcceptancePolicy.Result atDefaultBoundary =
                validate("video/mp4", 1L, validInspection(960));
        VideoMediaAcceptancePolicy.Result outsideDefaultBoundary =
                validate("video/mp4", 1L, validInspection(961));

        assertTrue(atDefaultBoundary.accepted());
        assertRejected(outsideDefaultBoundary, "视频时长超出容差");
    }

    @Test
    void playbackCookieLifetimeCoversAcceptedVideoAndFinalRangeBuffer() {
        paramService.put("video.durationTarget", "900");
        paramService.put("video.durationTolerance", "60");

        assertEquals(1020, VideoMediaAcceptancePolicy.playbackCookieLifetimeSeconds(paramService));
    }

    private VideoMediaAcceptancePolicy.Result validate(
            String contentType, Long fileSize, VideoMediaInspection inspection) {
        return VideoMediaAcceptancePolicy.validate(
                contentType, fileSize, inspection, probe, paramService);
    }

    private VideoMediaInspection validInspection(int durationSeconds) {
        return VideoMediaInspection.valid(
                "fingerprint", durationSeconds, "H264", 900,
                POLICY_HASH, PROBE_VERSION);
    }

    private void assertRejected(VideoMediaAcceptancePolicy.Result result, String message) {
        assertFalse(result.accepted());
        assertEquals(message, result.message());
    }

    private static final class StubVideoMediaProbe implements VideoMediaProbe {

        private final String policyHash;
        private final String probeVersion;

        private StubVideoMediaProbe(String policyHash, String probeVersion) {
            this.policyHash = policyHash;
            this.probeVersion = probeVersion;
        }

        @Override
        public VideoMediaInspection inspect(
                String objectKey, long expectedSize, String declaredFingerprint) {
            throw new UnsupportedOperationException("本策略单测不执行媒体探测");
        }

        @Override
        public String currentPolicyHash() {
            return policyHash;
        }

        @Override
        public String probeVersion() {
            return probeVersion;
        }
    }

    private static final class TestParamService implements ParamService {

        private final Map<String, String> values = new HashMap<>();

        private void put(String key, String value) {
            values.put(key, value);
        }

        @Override
        public int getInt(String key, int defaultValue) {
            String value = values.get(key);
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            throw new UnsupportedOperationException("本策略单测不读取布尔参数");
        }

        @Override
        public String getString(String key, String defaultValue) {
            String value = values.get(key);
            return value == null || value.isBlank() ? defaultValue : value.trim();
        }
    }
}
