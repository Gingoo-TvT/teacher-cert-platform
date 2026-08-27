package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.common.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 视频评审结算的无状态业务算法。
 *
 * <p>事务、任务查询、行锁和状态条件更新仍由 service 负责；这里只计算结算结果。
 */
public final class VideoReviewSettlement {

    private VideoReviewSettlement() {
    }

    public static InitialResult settleInitial(List<VideoReviewTask> tasks, int diffThreshold) {
        if (!allPairDiffWithin(tasks, diffThreshold) || !sameConclusion(tasks)) {
            return new InitialResult(false, null);
        }
        int sum = tasks.stream().map(VideoReviewTask::getScore).mapToInt(Integer::intValue).sum();
        return new InitialResult(true, Math.round(sum / (float) tasks.size()));
    }

    public static ThirdExpertResult settleThirdExpert(List<VideoReviewTask> tasks) {
        Pair best = bestPair(tasks);
        int finalScore = Math.round((best.left().getScore() + best.right().getScore()) / 2.0f);
        return new ThirdExpertResult(finalScore, best.thirdExpertId());
    }

    public static String normalizeConclusion(String conclusion) {
        if (!StringUtils.hasText(conclusion)) {
            throw new BizException("结论不能为空");
        }
        String value = conclusion.trim().toUpperCase(Locale.ROOT);
        if (!"PASS".equals(value) && !"FAIL".equals(value)) {
            throw new BizException("结论仅支持 PASS/FAIL");
        }
        return value;
    }

    private static Pair bestPair(List<VideoReviewTask> tasks) {
        Pair best = null;
        for (int i = 0; i < tasks.size(); i++) {
            for (int j = i + 1; j < tasks.size(); j++) {
                VideoReviewTask left = tasks.get(i);
                VideoReviewTask right = tasks.get(j);
                int diff = Math.abs(left.getScore() - right.getScore());
                if (best == null || diff < best.diff()) {
                    Long thirdExpertId = tasks.stream()
                            .filter(task -> "THIRD_EXPERT".equals(task.getReviewerRole()))
                            .map(VideoReviewTask::getReviewerId)
                            .findFirst()
                            .orElse(right.getReviewerId());
                    best = new Pair(left, right, diff, thirdExpertId);
                }
            }
        }
        if (best == null) {
            throw new BizException("复评分数不足");
        }
        return best;
    }

    private static boolean allPairDiffWithin(List<VideoReviewTask> tasks, int threshold) {
        for (int i = 0; i < tasks.size(); i++) {
            for (int j = i + 1; j < tasks.size(); j++) {
                if (Math.abs(tasks.get(i).getScore() - tasks.get(j).getScore()) > threshold) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean sameConclusion(List<VideoReviewTask> tasks) {
        if (tasks.isEmpty()) {
            return false;
        }
        String first = normalizeConclusion(tasks.get(0).getConclusion());
        return tasks.stream().allMatch(task -> first.equals(normalizeConclusion(task.getConclusion())));
    }

    public record InitialResult(boolean completed, Integer finalScore) {
    }

    public record ThirdExpertResult(int finalScore, Long thirdExpertId) {
    }

    private record Pair(VideoReviewTask left, VideoReviewTask right, int diff, Long thirdExpertId) {
    }
}
