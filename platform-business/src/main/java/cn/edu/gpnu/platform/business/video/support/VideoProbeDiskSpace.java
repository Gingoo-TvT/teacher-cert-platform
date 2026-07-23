package cn.edu.gpnu.platform.business.video.support;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 媒体探测临时目录的可用空间来源，抽象后可用小数值做确定性容量反例。
 */
@FunctionalInterface
public interface VideoProbeDiskSpace {

    long usableSpace(Path directory) throws IOException;
}
