package cn.edu.gpnu.platform.business.video.support;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/**
 * 在主业务 JVM 之外解析不可信媒体；超时后必须能终止承载解析的进程。
 */
public interface VideoMediaWorker {

    TrackInspection inspect(Path mediaFile, Set<String> allowedCodecs,
                            int timelineToleranceSeconds, int maxPackets, Duration timeout);

    record TrackInspection(boolean valid, String message, Integer durationSeconds,
                           String codec, Integer frameCount) {

        public static TrackInspection valid(int durationSeconds, String codec, int frameCount) {
            return new TrackInspection(true, null, durationSeconds, codec, frameCount);
        }

        public static TrackInspection invalid(String message, Integer durationSeconds,
                                              String codec, Integer frameCount) {
            return new TrackInspection(false, message, durationSeconds, codec, frameCount);
        }
    }
}
