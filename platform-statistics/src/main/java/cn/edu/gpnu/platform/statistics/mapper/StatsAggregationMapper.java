package cn.edu.gpnu.platform.statistics.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Phase 44d（P1-3）：把统计聚合下推到 SQL（GROUP BY / COUNT / SUM），由数据库返回小体量聚合行，
 * 取代原先「全量 selectList 回内存再 groupingBy」的做法。
 *
 * <p>数据范围（school/college/self）由调用方 {@code StatsServiceImpl.scopedStudents(...)} 显式解析后，
 * 以已过滤的 {@code studentIds} 作为唯一入参下发；本 mapper 的每条聚合/明细语句都强制
 * {@code student_id IN (#{studentIds})}（视频任务经父表 video_review 的 student_id 子查询），
 * 因此聚合结果与原 Java 聚合具有相同的数据范围（不依赖 DataScopeSqlHandler 拦截器——统计链路 DataScopeContext 为空）。
 *
 * <p>调用方在 studentIds 为空时不得调用本 mapper（避免非法 {@code IN ()}），改用空聚合结果。
 * 逻辑删除列 deleted 需显式带上（自定义 @Select 不会自动追加 @TableLogic 条件）。
 */
public interface StatsAggregationMapper {

    // ---- 证书统计 ----

    /** 按证书状态分组计数（scope：student_id IN scopedIds）。 */
    @Select("""
            <script>
            SELECT status AS status, COUNT(*) AS cnt
              FROM certificate
             WHERE deleted = 0
               AND assessment_year = #{year}
               AND student_id IN
               <foreach collection="studentIds" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY status
             ORDER BY status
            </script>
            """)
    List<Map<String, Object>> countCertificatesByStatus(@Param("studentIds") Collection<Long> studentIds,
                                                         @Param("year") String year);

    /** 已生成证书的学生数 = 拥有非 VOIDED 证书的去重学生数。 */
    @Select("""
            <script>
            SELECT COUNT(DISTINCT student_id)
              FROM certificate
             WHERE deleted = 0
               AND assessment_year = #{year}
               AND status &lt;&gt; 'VOIDED'
               AND student_id IN
               <foreach collection="studentIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countGeneratedCertificateStudents(@Param("studentIds") Collection<Long> studentIds,
                                           @Param("year") String year);

    // ---- 视频评审统计 ----

    /** 按视频评审状态分组计数。 */
    @Select("""
            <script>
            SELECT status AS status, COUNT(*) AS cnt
              FROM video_review
             WHERE deleted = 0
               AND assessment_year = #{year}
               AND student_id IN
               <foreach collection="studentIds" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY status
             ORDER BY status
            </script>
            """)
    List<Map<String, Object>> countVideosByStatus(@Param("studentIds") Collection<Long> studentIds,
                                                   @Param("year") String year);

    /** 已上传人数 = video_file_id 非空的评审行数。 */
    @Select("""
            <script>
            SELECT COUNT(*)
              FROM video_review
             WHERE deleted = 0
               AND assessment_year = #{year}
               AND video_file_id IS NOT NULL
               AND student_id IN
               <foreach collection="studentIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countUploadedVideos(@Param("studentIds") Collection<Long> studentIds,
                             @Param("year") String year);

    /** 视频评审教师任务明细（有界 LIMIT 200），经父表 video_review 的 student_id 子查询保证数据范围。 */
    @Select("""
            <script>
            SELECT t.student_id AS studentId,
                   t.reviewer_id AS reviewerId,
                   t.score AS score,
                   t.submitted AS submitted
              FROM video_review_task t
             WHERE t.deleted = 0
               AND t.video_review_id IN (
                     SELECT v.id
                       FROM video_review v
                      WHERE v.deleted = 0
                        AND v.assessment_year = #{year}
                        AND v.student_id IN
                        <foreach collection="studentIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                   )
             ORDER BY t.id
             LIMIT 200
            </script>
            """)
    List<Map<String, Object>> selectVideoTaskDetails(@Param("studentIds") Collection<Long> studentIds,
                                                      @Param("year") String year);

    // ---- 培养信息交叉统计 ----

    /**
     * 任教学段/学科 × 身份类型 × 学历层次 交叉分组计数。
     * 身份类型取自 student 表，故 JOIN student；分组键用 COALESCE(...,'') 与 Java 的 safe(null->"") 对齐，
     * 保证 NULL 与 '' 合并到同一组（byte-identical）。
     */
    @Select("""
            <script>
            SELECT COALESCE(tp.teaching_segment, '') AS teachingSegment,
                   COALESCE(tp.teaching_subject_name, '') AS teachingSubjectName,
                   COALESCE(s.identity_type, '') AS identityType,
                   COALESCE(tp.education_level, '') AS educationLevel,
                   COUNT(*) AS cnt
              FROM training_profile tp
              JOIN student s ON s.id = tp.student_id AND s.deleted = 0
             WHERE tp.deleted = 0
               AND tp.assessment_year = #{year}
               AND tp.student_id IN
               <foreach collection="studentIds" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY COALESCE(tp.teaching_segment, ''),
                      COALESCE(tp.teaching_subject_name, ''),
                      COALESCE(s.identity_type, ''),
                      COALESCE(tp.education_level, '')
             ORDER BY teachingSegment, teachingSubjectName, identityType, educationLevel
            </script>
            """)
    List<Map<String, Object>> aggregateTrainingCross(@Param("studentIds") Collection<Long> studentIds,
                                                      @Param("year") String year);

    // ---- 免考统计 ----

    /**
     * 免考科目 × 复审状态 分组计数。
     * 维度标签沿用 labelOrCode 语义：subject_label 有内容则取之，否则取 subject 编码。
     */
    @Select("""
            <script>
            SELECT CASE WHEN subject_label IS NOT NULL AND TRIM(subject_label) &lt;&gt; ''
                        THEN subject_label ELSE subject END AS dimensionLabel,
                   final_status AS finalStatus,
                   COUNT(*) AS cnt
              FROM exemption_request
             WHERE deleted = 0
               AND assessment_year = #{year}
               AND student_id IN
               <foreach collection="studentIds" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY CASE WHEN subject_label IS NOT NULL AND TRIM(subject_label) &lt;&gt; ''
                           THEN subject_label ELSE subject END,
                      final_status
             ORDER BY dimensionLabel, finalStatus
            </script>
            """)
    List<Map<String, Object>> aggregateExemptionSubject(@Param("studentIds") Collection<Long> studentIds,
                                                         @Param("year") String year);
}
