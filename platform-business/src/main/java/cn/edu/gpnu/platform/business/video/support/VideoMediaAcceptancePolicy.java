package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.system.service.ParamService;

import java.util.Objects;

/**
 * 视频定稿与演示媒体共用的业务验收规则。
 *
 * <p>技术探测成功只代表容器、编码、时间线和首帧可用；本策略继续校验 MP4 类型、
 * 文件上限、当前探测策略/版本以及业务时长目标，避免不同入口各自复制参数默认值。
 */
public final class VideoMediaAcceptancePolicy {

    private static final long DEFAULT_MAX_VIDEO_SIZE = 2_147_483_648L;
    private static final int DEFAULT_DURATION_TARGET = 900;
    private static final int DEFAULT_DURATION_TOLERANCE = 60;

    private VideoMediaAcceptancePolicy() {
    }

    public static Result validate(
            String normalizedContentType,
            Long fileSize,
            VideoMediaInspection inspection,
            VideoMediaProbe videoMediaProbe,
            ParamService paramService) {
        if (!"video/mp4".equals(normalizedContentType)) {
            return Result.reject("视频格式必须为MP4");
        }
        if (fileSize != null && fileSize > maxVideoSize(paramService)) {
            return Result.reject("视频大小超过限制");
        }
        if (!Objects.equals(inspection.probeVersion(), videoMediaProbe.probeVersion())
                || !Objects.equals(inspection.policyHash(), videoMediaProbe.currentPolicyHash())) {
            return Result.reject("视频校验策略已变化，请重新提交");
        }
        if (!inspection.valid()) {
            return Result.reject(inspection.message());
        }
        Integer durationSeconds = inspection.durationSeconds();
        if (durationSeconds == null) {
            return Result.reject("视频时长不能为空");
        }
        int target = paramService.getInt("video.durationTarget", DEFAULT_DURATION_TARGET);
        int tolerance = paramService.getInt(
                "video.durationTolerance", DEFAULT_DURATION_TOLERANCE);
        if (Math.abs((long) durationSeconds - target) > tolerance) {
            return Result.reject("视频时长超出容差");
        }
        return Result.allow();
    }

    public static long maxVideoSize(ParamService paramService) {
        String value = paramService.getString(
                "file.maxSize.video", Long.toString(DEFAULT_MAX_VIDEO_SIZE));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return DEFAULT_MAX_VIDEO_SIZE;
        }
    }

    public record Result(boolean accepted, String message) {

        private static Result allow() {
            return new Result(true, null);
        }

        private static Result reject(String message) {
            return new Result(false, message);
        }
    }
}
