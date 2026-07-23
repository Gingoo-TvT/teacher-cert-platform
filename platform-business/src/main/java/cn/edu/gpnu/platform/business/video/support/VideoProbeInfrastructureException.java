package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.common.exception.BizException;

/**
 * 对象存储、临时盘或工作进程故障。该类失败可重试，不得落成学生视频内容不合法。
 */
public class VideoProbeInfrastructureException extends BizException {

    public VideoProbeInfrastructureException(String message) {
        super(message);
    }

    public VideoProbeInfrastructureException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
