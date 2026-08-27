package cn.edu.gpnu.platform.business.video.service.impl;

import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.support.ReviewNotificationHelper;
import cn.edu.gpnu.platform.business.video.dto.VideoAssignRequest;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.mapper.SysRoleMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoReviewServiceAssignmentTest {

    private static final long REVIEW_ID = 101L;
    private static final long STUDENT_ID = 201L;
    private static final long COLLEGE_ID = 301L;
    private static final long REVIEWER_A = 401L;
    private static final long REVIEWER_B = 402L;
    private static final long REVIEWER_C = 403L;

    @Mock
    private VideoReviewMapper reviewMapper;
    @Mock
    private VideoReviewTaskMapper taskMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private ParamService paramService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ReviewNotificationHelper notificationHelper;

    @InjectMocks
    private VideoReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        VideoReview review = new VideoReview();
        review.setId(REVIEW_ID);
        review.setStudentId(STUDENT_ID);
        review.setCollegeId(COLLEGE_ID);
        review.setStatus("REVIEWING");
        review.setReviewerCount(2);
        when(reviewMapper.selectOne(any())).thenReturn(review);

        Student student = new Student();
        student.setId(STUDENT_ID);
        student.setCollegeId(COLLEGE_ID);
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student);

        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.SYSTEM);
        when(dataScopeService.resolve("video:assign")).thenReturn(scope);
        lenient().when(userMapper.selectById(anyLong()))
                .thenAnswer(invocation -> reviewer(invocation.getArgument(0)));
        lenient().when(roleMapper.selectCodesByUserId(anyLong())).thenReturn(List.of("REVIEW_TEACHER"));
        lenient().when(reviewMapper.updateById(any(VideoReview.class))).thenReturn(1);
    }

    @Test
    void unsubmittedChangeSoftDeletesRemovedAndAddsOnlyNewReviewer() {
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(11L, REVIEWER_A, 0), task(12L, REVIEWER_B, 0)));
        when(taskMapper.selectDeletedReviewerTaskForUpdate(REVIEW_ID, REVIEWER_C)).thenReturn(null);
        when(taskMapper.deleteById(11L)).thenReturn(1);
        when(taskMapper.insert(any(VideoReviewTask.class))).thenReturn(1);

        service.assign(REVIEW_ID, request(REVIEWER_B, REVIEWER_C));

        verify(taskMapper).deleteById(11L);
        ArgumentCaptor<VideoReviewTask> inserted = ArgumentCaptor.forClass(VideoReviewTask.class);
        verify(taskMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getReviewerId()).isEqualTo(REVIEWER_C);
        assertThat(inserted.getValue().getSubmitted()).isZero();
    }

    @Test
    void removedReviewerIsRestoredInsteadOfInsertedAgain() {
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(12L, REVIEWER_B, 0), task(13L, REVIEWER_C, 0)));
        when(taskMapper.deleteById(12L)).thenReturn(1);
        VideoReviewTask deleted = task(11L, REVIEWER_A, 0);
        deleted.setDeleted(1);
        when(taskMapper.selectDeletedReviewerTaskForUpdate(REVIEW_ID, REVIEWER_A)).thenReturn(deleted);
        when(taskMapper.restoreReviewerTask(anyLong(), anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(1);

        service.assign(REVIEW_ID, request(REVIEWER_A, REVIEWER_C));

        verify(taskMapper).restoreReviewerTask(
                eq(11L), eq(STUDENT_ID), eq(COLLEGE_ID), eq(0L), any());
        verify(taskMapper, never()).insert(any(VideoReviewTask.class));
    }

    @Test
    void submittedReviewerCannotBeRemovedFromAssignment() {
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(11L, REVIEWER_A, 1), task(12L, REVIEWER_B, 0)));

        assertThatThrownBy(() -> service.assign(REVIEW_ID, request(REVIEWER_B, REVIEWER_C)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已有评审成绩");

        verify(taskMapper, never()).deleteById(anyLong());
        verify(taskMapper, never()).insert(any(VideoReviewTask.class));
        verify(reviewMapper, never()).updateById(any(VideoReview.class));
    }

    @Test
    void unsubmittedReviewerCanBeReplacedWhenSubmittedReviewerIsPreserved() {
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(11L, REVIEWER_A, 1), task(12L, REVIEWER_B, 0)));
        when(taskMapper.selectDeletedReviewerTaskForUpdate(REVIEW_ID, REVIEWER_C)).thenReturn(null);
        when(taskMapper.deleteById(12L)).thenReturn(1);
        when(taskMapper.insert(any(VideoReviewTask.class))).thenReturn(1);

        service.assign(REVIEW_ID, request(REVIEWER_A, REVIEWER_C));

        verify(taskMapper).deleteById(12L);
        verify(taskMapper, never()).deleteById(11L);
        ArgumentCaptor<VideoReviewTask> inserted = ArgumentCaptor.forClass(VideoReviewTask.class);
        verify(taskMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getReviewerId()).isEqualTo(REVIEWER_C);
        assertThat(inserted.getValue().getSubmitted()).isZero();
    }

    @Test
    void submittedSingleReviewerCanBePreservedWhileAddingMissingReviewer() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(11L, REVIEWER_A, 1)));
        when(taskMapper.selectDeletedReviewerTaskForUpdate(REVIEW_ID, REVIEWER_B)).thenReturn(null);
        when(taskMapper.insert(any(VideoReviewTask.class))).thenReturn(1);

        service.assign(REVIEW_ID, request(REVIEWER_A, REVIEWER_B));

        verify(taskMapper, never()).deleteById(anyLong());
        ArgumentCaptor<VideoReviewTask> inserted = ArgumentCaptor.forClass(VideoReviewTask.class);
        verify(taskMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getReviewerId()).isEqualTo(REVIEWER_B);
        assertThat(inserted.getValue().getSubmitted()).isZero();
        verify(reviewMapper).updateById(any(VideoReview.class));
    }

    @Test
    void submittedDisabledReviewerIsPreservedWhileOnlyOutstandingReviewersAreValidated() {
        when(taskMapper.selectList(any())).thenReturn(List.of(task(11L, REVIEWER_A, 1)));
        when(taskMapper.selectDeletedReviewerTaskForUpdate(REVIEW_ID, REVIEWER_B)).thenReturn(null);
        when(taskMapper.insert(any(VideoReviewTask.class))).thenReturn(1);
        SysUser disabledReviewer = reviewer(REVIEWER_A);
        disabledReviewer.setStatus("DISABLED");
        lenient().when(userMapper.selectById(REVIEWER_A)).thenReturn(disabledReviewer);

        service.assign(REVIEW_ID, request(REVIEWER_A, REVIEWER_B));

        verify(userMapper, never()).selectById(REVIEWER_A);
        verify(roleMapper, never()).selectCodesByUserId(REVIEWER_A);
        verify(userMapper).selectById(REVIEWER_B);
        verify(roleMapper).selectCodesByUserId(REVIEWER_B);
        verify(taskMapper).insert(any(VideoReviewTask.class));
    }

    @Test
    void assignmentUsesFrozenReviewerCountWithoutReadingCurrentGlobalValue() {
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(taskMapper.selectDeletedReviewerTaskForUpdate(anyLong(), anyLong())).thenReturn(null);
        when(taskMapper.insert(any(VideoReviewTask.class))).thenReturn(1);

        service.assign(REVIEW_ID, request(REVIEWER_A, REVIEWER_B));

        verify(paramService, never()).getInt("video.reviewerCount", 2);
        verify(taskMapper, times(2)).insert(any(VideoReviewTask.class));
    }

    @Test
    void threeReviewerSnapshotRequiresThreeReviewers() {
        VideoReview review = new VideoReview();
        review.setId(REVIEW_ID);
        review.setStudentId(STUDENT_ID);
        review.setCollegeId(COLLEGE_ID);
        review.setStatus("WAIT_REVIEW");
        review.setReviewerCount(3);
        when(reviewMapper.selectOne(any())).thenReturn(review);

        assertThatThrownBy(() -> service.assign(REVIEW_ID, request(REVIEWER_A, REVIEWER_B)))
                .isInstanceOf(BizException.class)
                .hasMessage("评审教师人数需等于本轮冻结人数（3人）");

        verify(taskMapper, never()).insert(any(VideoReviewTask.class));
        verify(paramService, never()).getInt("video.reviewerCount", 2);
    }

    @Test
    void historicalElevenReviewerSnapshotCanKeepSubmittedResultsAndAddMissingReviewers() {
        VideoReview review = new VideoReview();
        review.setId(REVIEW_ID);
        review.setStudentId(STUDENT_ID);
        review.setCollegeId(COLLEGE_ID);
        review.setStatus("REVIEWING");
        review.setReviewerCount(11);
        when(reviewMapper.selectOne(any())).thenReturn(review);
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task(11L, 401L, 1), task(12L, 402L, 1), task(13L, 403L, 1)));
        when(taskMapper.selectDeletedReviewerTaskForUpdate(anyLong(), anyLong())).thenReturn(null);
        when(taskMapper.insert(any(VideoReviewTask.class))).thenReturn(1);
        Long[] reviewerIds = LongStream.rangeClosed(401L, 411L).boxed().toArray(Long[]::new);

        service.assign(REVIEW_ID, request(reviewerIds));

        verify(taskMapper, times(8)).insert(any(VideoReviewTask.class));
        verify(taskMapper, never()).deleteById(anyLong());
        verify(paramService, never()).getInt("video.reviewerCount", 2);
    }

    @Test
    void assignmentSettlesWhenHistoricalTargetSetIsAlreadyFullySubmitted() {
        VideoReview review = new VideoReview();
        review.setId(REVIEW_ID);
        review.setStudentId(STUDENT_ID);
        review.setCollegeId(COLLEGE_ID);
        review.setAssessmentYear("2035");
        review.setStatus("REVIEWING");
        review.setReviewerCount(3);
        when(reviewMapper.selectOne(any())).thenReturn(review);
        List<VideoReviewTask> submitted = List.of(
                submittedTask(11L, REVIEWER_A, 80),
                submittedTask(12L, REVIEWER_B, 80),
                submittedTask(13L, REVIEWER_C, 80));
        when(taskMapper.selectList(any())).thenReturn(submitted);
        when(reviewMapper.update(any(VideoReview.class), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(paramService.getInt("video.diffThreshold", 12)).thenReturn(12);
        when(paramService.getInt("video.passLine", 60)).thenReturn(60);

        service.assign(REVIEW_ID, request(REVIEWER_A, REVIEWER_B, REVIEWER_C));

        assertThat(review.getStatus()).isEqualTo("REVIEW_COMPLETED");
        assertThat(review.getFinalScore()).isEqualTo(80);
        assertThat(review.getFinalConclusion()).isEqualTo("PASS");
        verify(auditLogService).record(eq("video"), eq(REVIEW_ID), any(), eq("settle"),
                eq("REVIEWING"), eq("REVIEW_COMPLETED"), eq("指派后自动结算"));
    }

    private VideoAssignRequest request(Long... reviewerIds) {
        VideoAssignRequest request = new VideoAssignRequest();
        request.setReviewerIds(List.of(reviewerIds));
        return request;
    }

    private VideoReviewTask task(long id, long reviewerId, int submitted) {
        VideoReviewTask task = new VideoReviewTask();
        task.setId(id);
        task.setVideoReviewId(REVIEW_ID);
        task.setReviewerId(reviewerId);
        task.setReviewerRole("REVIEWER");
        task.setSubmitted(submitted);
        return task;
    }

    private VideoReviewTask submittedTask(long id, long reviewerId, int score) {
        VideoReviewTask task = task(id, reviewerId, 1);
        task.setScore(score);
        task.setConclusion("PASS");
        task.setSubmitTime(LocalDateTime.of(2035, 1, 1, 10, 0).plusSeconds(id));
        return task;
    }

    private SysUser reviewer(long reviewerId) {
        SysUser user = new SysUser();
        user.setId(reviewerId);
        user.setCollegeId(COLLEGE_ID);
        user.setStatus("ENABLED");
        return user;
    }
}
