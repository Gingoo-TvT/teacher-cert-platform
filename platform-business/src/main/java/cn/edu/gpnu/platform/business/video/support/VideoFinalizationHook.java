package cn.edu.gpnu.platform.business.video.support;

import org.springframework.stereotype.Component;

/**
 * 定稿阶段观察点。生产实现为空；集成测试用安全 failpoint 验证节点退出后的持久恢复。
 */
@Component
public class VideoFinalizationHook {

    public void afterServerChunkStage(ServerChunkStage stage, String uploadId) {
        // 生产不注入行为。
    }

    public enum ServerChunkStage {
        CLAIMED,
        OBJECT_READY,
        PROBED
    }
}
