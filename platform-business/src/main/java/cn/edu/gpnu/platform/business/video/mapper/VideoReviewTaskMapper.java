package cn.edu.gpnu.platform.business.video.mapper;

import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface VideoReviewTaskMapper extends BaseMapper<VideoReviewTask> {

    /**
     * 查询曾被移出当前评审集合的任务。普通 MP 查询会自动过滤逻辑删除行，改派时需要显式找到原行复用，
     * 否则重新指派同一教师会与唯一键冲突。
     */
    @Select("""
            SELECT id, video_review_id, student_id, college_id, reviewer_id, reviewer_role,
                   score, dimension_scores_json, comment, conclusion, submitted, submit_time,
                   created_by, created_at, updated_by, updated_at, deleted
              FROM video_review_task
             WHERE video_review_id = #{reviewId}
               AND reviewer_id = #{reviewerId}
               AND reviewer_role = 'REVIEWER'
               AND deleted <> 0
             ORDER BY id DESC
             LIMIT 1
             FOR UPDATE
            """)
    VideoReviewTask selectDeletedReviewerTaskForUpdate(
            @Param("reviewId") Long reviewId,
            @Param("reviewerId") Long reviewerId);

    /** 复用被移出的未提交任务，并清空可能残留的评分字段。 */
    @Update("""
            UPDATE video_review_task
               SET student_id = #{studentId},
                   college_id = #{collegeId},
                   reviewer_role = 'REVIEWER',
                   score = NULL,
                   dimension_scores_json = NULL,
                   comment = NULL,
                   conclusion = NULL,
                   submitted = 0,
                   submit_time = NULL,
                   updated_by = #{operatorId},
                   updated_at = #{updatedAt},
                   deleted = 0
             WHERE id = #{taskId}
               AND deleted <> 0
            """)
    int restoreReviewerTask(
            @Param("taskId") Long taskId,
            @Param("studentId") Long studentId,
            @Param("collegeId") Long collegeId,
            @Param("operatorId") Long operatorId,
            @Param("updatedAt") LocalDateTime updatedAt);
}
