package cn.edu.gpnu.platform.business.video.support;

/**
 * 服务端读取对象内容后的可信媒体探测结果。
 */
public record VideoMediaInspection(
        boolean valid,
        String message,
        String fingerprint,
        Integer durationSeconds,
        String codec,
        Integer frameCount,
        String policyHash,
        String probeVersion) {

    public static VideoMediaInspection valid(String fingerprint, int durationSeconds,
                                             String codec, int frameCount,
                                             String policyHash, String probeVersion) {
        return new VideoMediaInspection(true, null, fingerprint, durationSeconds, codec, frameCount,
                policyHash, probeVersion);
    }

    public static VideoMediaInspection invalid(String message, String fingerprint,
                                               Integer durationSeconds, String codec, Integer frameCount,
                                               String policyHash, String probeVersion) {
        return new VideoMediaInspection(false, message, fingerprint, durationSeconds, codec, frameCount,
                policyHash, probeVersion);
    }
}
