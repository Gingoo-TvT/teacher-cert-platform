package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoReviewSettlementTest {

    @Test
    void initialCompletesWhenScoresAndConclusionsAgree() {
        var result = VideoReviewSettlement.settleInitial(List.of(
                task(1L, "REVIEWER", 85, " pass "),
                task(2L, "REVIEWER", 80, "PASS")), 12);

        assertThat(result.completed()).isTrue();
        assertThat(result.finalScore()).isEqualTo(83);
    }

    @Test
    void initialRequiresReviewWhenDiffExceedsThreshold() {
        var result = VideoReviewSettlement.settleInitial(List.of(
                task(1L, "REVIEWER", 73, "PASS"),
                task(2L, "REVIEWER", 60, "PASS")), 12);

        assertThat(result.completed()).isFalse();
        assertThat(result.finalScore()).isNull();
    }

    @Test
    void initialRequiresReviewWhenConclusionsConflict() {
        var result = VideoReviewSettlement.settleInitial(List.of(
                task(1L, "REVIEWER", 85, "PASS"),
                task(2L, "REVIEWER", 80, "FAIL")), 12);

        assertThat(result.completed()).isFalse();
        assertThat(result.finalScore()).isNull();
    }

    @Test
    void initialAcceptsDiffEqualToThreshold() {
        var result = VideoReviewSettlement.settleInitial(List.of(
                task(1L, "REVIEWER", 72, "PASS"),
                task(2L, "REVIEWER", 60, "PASS")), 12);

        assertThat(result.completed()).isTrue();
        assertThat(result.finalScore()).isEqualTo(66);
    }

    @Test
    void thirdExpertUsesClosestPairAndThirdExpertReviewer() {
        var result = VideoReviewSettlement.settleThirdExpert(List.of(
                task(1L, "REVIEWER", 85, "PASS"),
                task(2L, "REVIEWER", 60, "PASS"),
                task(3L, "THIRD_EXPERT", 81, "PASS")));

        assertThat(result.finalScore()).isEqualTo(83);
        assertThat(result.thirdExpertId()).isEqualTo(3L);
    }

    @Test
    void thirdExpertKeepsFirstPairWhenMinimumDiffTies() {
        var result = VideoReviewSettlement.settleThirdExpert(List.of(
                task(1L, "REVIEWER", 70, "PASS"),
                task(2L, "REVIEWER", 80, "PASS"),
                task(3L, "THIRD_EXPERT", 90, "PASS")));

        assertThat(result.finalScore()).isEqualTo(75);
        assertThat(result.thirdExpertId()).isEqualTo(3L);
    }

    @Test
    void normalizeConclusionKeepsExistingValidationContract() {
        assertThat(VideoReviewSettlement.normalizeConclusion(" fail ")).isEqualTo("FAIL");
        assertThatThrownBy(() -> VideoReviewSettlement.normalizeConclusion("UNKNOWN"))
                .isInstanceOf(BizException.class)
                .hasMessage("结论仅支持 PASS/FAIL");
    }

    private VideoReviewTask task(Long reviewerId, String role, int score, String conclusion) {
        VideoReviewTask task = new VideoReviewTask();
        task.setReviewerId(reviewerId);
        task.setReviewerRole(role);
        task.setScore(score);
        task.setConclusion(conclusion);
        return task;
    }
}
