package cn.edu.gpnu.platform.business.video.support;

/**
 * 定稿候选对象状态。只有 CLEANUP_PENDING/CLEANING 可被持久对账删除。
 */
public enum VideoFinalizationObjectState {
    ACTIVE,
    CLEANUP_PENDING,
    CLEANING,
    REGISTERED,
    CLEANED
}
