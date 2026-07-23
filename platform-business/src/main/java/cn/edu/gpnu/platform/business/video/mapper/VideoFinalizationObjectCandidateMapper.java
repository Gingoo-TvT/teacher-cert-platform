package cn.edu.gpnu.platform.business.video.mapper;

import cn.edu.gpnu.platform.business.video.entity.VideoFinalizationObjectCandidate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface VideoFinalizationObjectCandidateMapper
        extends BaseMapper<VideoFinalizationObjectCandidate> {

    @Select("""
            SELECT id
            FROM video_finalization_object_candidate
            WHERE deleted = 0
              AND cleanup_not_before <= #{now}
              AND (
                    (state = 'CLEANUP_PENDING' AND next_retry_at <= #{now})
                 OR (state = 'CLEANING' AND claim_expires_at <= #{now})
              )
            ORDER BY next_retry_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<Long> selectDueCleanupIds(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("""
            SELECT id
            FROM video_finalization_object_candidate
            WHERE deleted = 0
              AND state = 'CLEANED'
              AND cleanup_not_before <= #{now}
              AND next_retry_at <= #{now}
            ORDER BY next_retry_at ASC, id ASC
            LIMIT #{limit}
            """)
    List<Long> selectDueTombstoneIds(
            @Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM video_finalization_object_candidate
            WHERE id = #{id} AND deleted = 0
            FOR UPDATE
            """)
    VideoFinalizationObjectCandidate selectForUpdate(@Param("id") Long id);

    @Select("""
            SELECT s.upload_id
            FROM video_upload_session s
            WHERE s.deleted = 0
              AND s.status IN ('MERGING', 'FAILED')
              AND s.object_key IS NOT NULL
              AND s.object_key <> ''
              AND NOT EXISTS (
                    SELECT 1
                    FROM video_finalization_object_candidate c
                    WHERE c.deleted = 0 AND c.upload_id = s.upload_id
              )
            ORDER BY s.id ASC
            LIMIT #{limit}
            """)
    List<String> selectLegacyUploadIds(@Param("limit") int limit);
}
