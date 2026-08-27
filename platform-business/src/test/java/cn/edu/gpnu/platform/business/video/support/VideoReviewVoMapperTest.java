package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.vo.ReviewerCandidateVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewTaskVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewVO;
import cn.edu.gpnu.platform.system.entity.SysUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VideoReviewVoMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsReviewFieldsAndOnlyReturnsTheCurrentReviewersTask() {
        LocalDateTime confirmedAt = LocalDateTime.of(2026, 8, 20, 10, 30);
        VideoReview review = review(confirmedAt);
        Student student = new Student();
        student.setStudentNo("20260001");
        student.setName("学生甲");
        VideoReviewTask first = task(301L, 11L, "{\"教学设计\":80}");
        VideoReviewTask second = task(302L, 22L, "{\"教学设计\":85}");
        SysUser firstReviewer = reviewer(11L, "专家甲", "T001");
        SysUser secondReviewer = reviewer(22L, "专家乙", "T002");

        VideoReviewVO actual = VideoReviewVoMapper.review(review, student, List.of(first, second),
                Map.of(11L, firstReviewer, 22L, secondReviewer), false, 22L, objectMapper);

        VideoReviewVO expected = expectedReview(confirmedAt);
        VideoReviewTaskVO expectedTask = expectedTask(second, "专家乙", Map.of("教学设计", 85));
        expected.setTasks(List.of(expectedTask));
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void managementViewReturnsEveryTaskAndRevealsScores() {
        VideoReviewTask first = task(301L, 11L, "{\"教学设计\":80}");
        VideoReviewTask second = task(302L, 22L, "{\"教学设计\":85}");

        VideoReviewVO actual = VideoReviewVoMapper.review(review(null), null, List.of(first, second),
                Map.of(), true, 99L, objectMapper);

        assertThat(actual.getTasks()).hasSize(2);
        assertThat(actual.getTasks()).extracting(VideoReviewTaskVO::getScore).containsExactly(86, 87);
        assertThat(actual.getTasks()).extracting(VideoReviewTaskVO::getReviewerName).containsOnlyNulls();
    }

    @Test
    void hidesScoreDetailsFromAnotherReviewer() {
        VideoReviewTask task = task(301L, 11L, "{\"教学设计\":80}");

        VideoReviewTaskVO actual = VideoReviewVoMapper.task(task, false, reviewer(11L, "专家甲", "T001"),
                22L, objectMapper);

        assertThat(actual.getReviewerName()).isEqualTo("专家甲");
        assertThat(actual.getScore()).isNull();
        assertThat(actual.getDimensionScores()).isNull();
        assertThat(actual.getComment()).isNull();
        assertThat(actual.getConclusion()).isNull();
    }

    @Test
    void malformedDimensionJsonFallsBackToEmptyMap() {
        VideoReviewTask task = task(301L, 11L, "not-json");

        VideoReviewTaskVO actual = VideoReviewVoMapper.task(task, true, null, null, objectMapper);

        assertThat(actual.getDimensionScores()).isEmpty();
    }

    @Test
    void mapsReviewerCandidateFields() {
        ReviewerCandidateVO actual = VideoReviewVoMapper.reviewerCandidate(reviewer(11L, "专家甲", "T001"));

        assertThat(actual.getId()).isEqualTo(11L);
        assertThat(actual.getRealName()).isEqualTo("专家甲");
        assertThat(actual.getWorkNo()).isEqualTo("T001");
    }

    private VideoReview review(LocalDateTime confirmedAt) {
        VideoReview review = new VideoReview();
        review.setId(101L);
        review.setStudentId(201L);
        review.setCollegeId(9L);
        review.setAssessmentYear("2026");
        review.setVideoFileId(401L);
        review.setVideoFileName("lesson.mp4");
        review.setDurationSeconds(600);
        review.setFormatCheck("PASS");
        review.setValidationMessage("校验通过");
        review.setStatus(VideoReviewStatus.REVIEW_COMPLETED.name());
        review.setFinalScore(86);
        review.setFinalConclusion("PASS");
        review.setArbitrateReviewer(22L);
        review.setArbitrateMode("thirdExpert");
        review.setConfirmedBy(88L);
        review.setConfirmedAt(confirmedAt);
        review.setLocked(1);
        return review;
    }

    private VideoReviewTask task(Long id, Long reviewerId, String dimensions) {
        VideoReviewTask task = new VideoReviewTask();
        task.setId(id);
        task.setVideoReviewId(101L);
        task.setReviewerId(reviewerId);
        task.setReviewerRole("REVIEWER");
        task.setScore(id.equals(301L) ? 86 : 87);
        task.setDimensionScoresJson(dimensions);
        task.setComment("评审意见");
        task.setConclusion("PASS");
        task.setSubmitted(1);
        task.setSubmitTime(LocalDateTime.of(2026, 8, 20, 9, id.equals(301L) ? 10 : 20));
        return task;
    }

    private SysUser reviewer(Long id, String name, String workNo) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRealName(name);
        user.setWorkNo(workNo);
        return user;
    }

    private VideoReviewVO expectedReview(LocalDateTime confirmedAt) {
        VideoReviewVO expected = new VideoReviewVO();
        expected.setId(101L);
        expected.setStudentId(201L);
        expected.setStudentNo("20260001");
        expected.setStudentName("学生甲");
        expected.setCollegeId(9L);
        expected.setAssessmentYear("2026");
        expected.setVideoFileId(401L);
        expected.setVideoFileName("lesson.mp4");
        expected.setDurationSeconds(600);
        expected.setFormatCheck("PASS");
        expected.setValidationMessage("校验通过");
        expected.setStatus(VideoReviewStatus.REVIEW_COMPLETED.name());
        expected.setStatusLabel(VideoReviewStatus.REVIEW_COMPLETED.label());
        expected.setFinalScore(86);
        expected.setFinalConclusion("PASS");
        expected.setArbitrateReviewer(22L);
        expected.setArbitrateMode("thirdExpert");
        expected.setConfirmedBy(88L);
        expected.setConfirmedAt(confirmedAt);
        expected.setLocked(1);
        return expected;
    }

    private VideoReviewTaskVO expectedTask(VideoReviewTask task, String reviewerName,
                                           Map<String, Integer> dimensions) {
        VideoReviewTaskVO expected = new VideoReviewTaskVO();
        expected.setId(task.getId());
        expected.setVideoReviewId(task.getVideoReviewId());
        expected.setReviewerId(task.getReviewerId());
        expected.setReviewerName(reviewerName);
        expected.setReviewerRole(task.getReviewerRole());
        expected.setScore(task.getScore());
        expected.setDimensionScores(dimensions);
        expected.setComment(task.getComment());
        expected.setConclusion(task.getConclusion());
        expected.setSubmitted(task.getSubmitted());
        expected.setSubmitTime(task.getSubmitTime());
        return expected;
    }
}
