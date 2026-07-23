package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.common.exception.BizException;

/**
 * 媒体探测超过墙钟预算。超时属于可重试平台结果，不等同于内容校验失败。
 */
public class VideoProbeTimeoutException extends BizException {

    public VideoProbeTimeoutException(String message) {
        super(message);
    }
}
