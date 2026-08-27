package cn.edu.gpnu.platform.business.video.support;

import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.vo.ReviewerCandidateVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewTaskVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewVO;
import cn.edu.gpnu.platform.system.entity.SysUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 视频评审视图的纯字段映射，不负责查询、权限判定或事务。
 */
public final class VideoReviewVoMapper {

    private VideoReviewVoMapper() {
    }

    public static VideoReviewVO review(VideoReview entity, Student student,
                                       List<VideoReviewTask> tasks, Map<Long, SysUser> reviewers,
                                       boolean managementView, Long currentUserId, ObjectMapper objectMapper) {
        VideoReviewVO vo = new VideoReviewVO();
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setStudentNo(student == null ? null : student.getStudentNo());
        vo.setStudentName(student == null ? null : student.getName());
        vo.setCollegeId(entity.getCollegeId());
        vo.setAssessmentYear(entity.getAssessmentYear());
        vo.setVideoFileId(entity.getVideoFileId());
        vo.setVideoFileName(entity.getVideoFileName());
        vo.setDurationSeconds(entity.getDurationSeconds());
        vo.setFormatCheck(entity.getFormatCheck());
        vo.setValidationMessage(entity.getValidationMessage());
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(VideoReviewStatus.of(entity.getStatus()).label());
        vo.setFinalScore(entity.getFinalScore());
        vo.setFinalConclusion(entity.getFinalConclusion());
        vo.setArbitrateReviewer(entity.getArbitrateReviewer());
        vo.setArbitrateMode(entity.getArbitrateMode());
        vo.setConfirmedBy(entity.getConfirmedBy());
        vo.setConfirmedAt(entity.getConfirmedAt());
        vo.setLocked(entity.getLocked());
        vo.setTasks(tasks.stream()
                .filter(task -> managementView
                        || (currentUserId != null && currentUserId.equals(task.getReviewerId())))
                .map(task -> task(task, managementView, reviewers.get(task.getReviewerId()),
                        currentUserId, objectMapper))
                .toList());
        return vo;
    }

    public static VideoReviewTaskVO task(VideoReviewTask task, boolean revealScore,
                                         SysUser reviewer, Long currentUserId, ObjectMapper objectMapper) {
        boolean owner = currentUserId != null && currentUserId.equals(task.getReviewerId());
        boolean reveal = revealScore || owner;
        VideoReviewTaskVO vo = new VideoReviewTaskVO();
        vo.setId(task.getId());
        vo.setVideoReviewId(task.getVideoReviewId());
        vo.setReviewerId(task.getReviewerId());
        vo.setReviewerName(reviewer == null ? null : reviewer.getRealName());
        vo.setReviewerRole(task.getReviewerRole());
        vo.setSubmitted(task.getSubmitted());
        vo.setSubmitTime(task.getSubmitTime());
        if (reveal) {
            vo.setScore(task.getScore());
            vo.setDimensionScores(readDimensions(task.getDimensionScoresJson(), objectMapper));
            vo.setComment(task.getComment());
            vo.setConclusion(task.getConclusion());
        }
        return vo;
    }

    public static ReviewerCandidateVO reviewerCandidate(SysUser user) {
        ReviewerCandidateVO vo = new ReviewerCandidateVO();
        vo.setId(user.getId());
        vo.setRealName(user.getRealName());
        vo.setWorkNo(user.getWorkNo());
        return vo;
    }

    private static Map<String, Integer> readDimensions(String json, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
