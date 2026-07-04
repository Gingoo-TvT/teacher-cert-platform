package cn.edu.gpnu.platform.system.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;
import java.util.Set;

public interface AuditQueryMapper {

    @Select("""
            <script>
            SELECT l.id,
                   l.biz_type,
                   l.biz_id,
                   l.target,
                   l.operator_id,
                   u.real_name AS operator_name,
                   u.college_id AS operator_college_id,
                   l.operate_time,
                   l.`comment`,
                   l.old_status,
                   l.new_status,
                   l.operation,
                   l.ip
              FROM audit_log l
              LEFT JOIN sys_user u ON u.id = l.operator_id AND u.deleted = 0
             WHERE 1 = 1
               <if test="bizType != null and bizType != ''">
                 AND l.biz_type = #{bizType}
               </if>
               <if test="bizId != null">
                 AND l.biz_id = #{bizId}
               </if>
               <if test="operation != null and operation != ''">
                 AND l.operation = #{operation}
               </if>
               <if test="operatorId != null">
                 AND l.operator_id = #{operatorId}
               </if>
               <if test="startTime != null and startTime != ''">
                 AND l.operate_time &gt;= #{startTime}
               </if>
               <if test="endTime != null and endTime != ''">
                 AND l.operate_time &lt;= #{endTime}
               </if>
               <if test="keyword != null and keyword != ''">
                 AND (l.target LIKE CONCAT('%', #{keyword}, '%')
                      OR l.`comment` LIKE CONCAT('%', #{keyword}, '%')
                      OR l.operation LIKE CONCAT('%', #{keyword}, '%')
                      OR l.biz_type LIKE CONCAT('%', #{keyword}, '%'))
               </if>
               <if test="batchNo != null and batchNo != ''">
                 AND (l.target LIKE CONCAT('%', #{batchNo}, '%')
                      OR (l.biz_type IN ('exchange','import_export_batch')
                          AND l.biz_id IN (SELECT b.id FROM import_export_batch b
                                           WHERE b.batch_no = #{batchNo} AND b.deleted = 0)))
               </if>
               <if test="collegeIds != null and collegeIds.size() &gt; 0">
                 AND (
                   u.college_id IN
                   <foreach collection="collegeIds" item="collegeId" open="(" separator="," close=")">
                     #{collegeId}
                   </foreach>
                   OR EXISTS (SELECT 1 FROM student s
                               WHERE l.biz_id = s.id AND s.deleted = 0
                                 AND (l.biz_type IN ('student','student_info') OR l.biz_type IS NULL)
                                 AND s.college_id IN
                                 <foreach collection="collegeIds" item="collegeId" open="(" separator="," close=")">
                                   #{collegeId}
                                 </foreach>)
                   OR EXISTS (SELECT 1 FROM process_material m
                               WHERE l.biz_id = m.id AND m.deleted = 0
                                 AND l.biz_type IN ('material','process_material','process-material')
                                 AND m.college_id IN
                                 <foreach collection="collegeIds" item="collegeId" open="(" separator="," close=")">
                                   #{collegeId}
                                 </foreach>)
                   OR EXISTS (SELECT 1 FROM training_profile t
                               WHERE l.biz_id = t.id AND t.deleted = 0
                                 AND l.biz_type IN ('training','training_profile')
                                 AND t.college_id IN
                                 <foreach collection="collegeIds" item="collegeId" open="(" separator="," close=")">
                                   #{collegeId}
                                 </foreach>)
                   OR EXISTS (SELECT 1 FROM exemption_request e
                               WHERE l.biz_id = e.id AND e.deleted = 0
                                 AND l.biz_type IN ('exemption','exemption_request')
                                 AND e.college_id IN
                                 <foreach collection="collegeIds" item="collegeId" open="(" separator="," close=")">
                                   #{collegeId}
                                 </foreach>)
                   OR EXISTS (SELECT 1 FROM video_review v
                               WHERE l.biz_id = v.id AND v.deleted = 0
                                 AND l.biz_type IN ('video','video_review')
                                 AND v.college_id IN
                                 <foreach collection="collegeIds" item="collegeId" open="(" separator="," close=")">
                                   #{collegeId}
                                 </foreach>)
                   OR EXISTS (SELECT 1 FROM ability_test_result a
                               WHERE l.biz_id = a.id AND a.deleted = 0
                                 AND l.biz_type IN ('test','ability_test_result')
                                 AND a.college_id IN
                                 <foreach collection="collegeIds" item="collegeId" open="(" separator="," close=")">
                                   #{collegeId}
                                 </foreach>)
                   OR EXISTS (SELECT 1 FROM certificate c
                               WHERE l.biz_id = c.id AND c.deleted = 0
                                 AND l.biz_type IN ('cert','certificate')
                                 AND c.college_id IN
                                 <foreach collection="collegeIds" item="collegeId" open="(" separator="," close=")">
                                   #{collegeId}
                                 </foreach>)
                 )
               </if>
               <if test="studentId != null">
                 AND (
                   EXISTS (SELECT 1 FROM student s
                            WHERE s.id = #{studentId} AND s.deleted = 0
                              AND ((l.biz_type IN ('student','student_info') AND l.biz_id = s.id)
                                OR (l.target LIKE CONCAT('%', s.student_no, '%'))))
                   OR EXISTS (SELECT 1 FROM process_material m
                               WHERE m.student_id = #{studentId} AND m.deleted = 0
                                 AND l.biz_type IN ('material','process_material','process-material') AND l.biz_id = m.id)
                   OR EXISTS (SELECT 1 FROM training_profile t
                               WHERE t.student_id = #{studentId} AND t.deleted = 0
                                 AND l.biz_type IN ('training','training_profile') AND l.biz_id = t.id)
                   OR EXISTS (SELECT 1 FROM exemption_request e
                               WHERE e.student_id = #{studentId} AND e.deleted = 0
                                 AND l.biz_type IN ('exemption','exemption_request') AND l.biz_id = e.id)
                   OR EXISTS (SELECT 1 FROM video_review v
                               WHERE v.student_id = #{studentId} AND v.deleted = 0
                                 AND l.biz_type IN ('video','video_review') AND l.biz_id = v.id)
                   OR EXISTS (SELECT 1 FROM ability_test_result a
                               WHERE a.student_id = #{studentId} AND a.deleted = 0
                                 AND l.biz_type IN ('test','ability_test_result') AND l.biz_id = a.id)
                   OR EXISTS (SELECT 1 FROM certificate c
                               WHERE c.student_id = #{studentId} AND c.deleted = 0
                                 AND l.biz_type IN ('cert','certificate') AND l.biz_id = c.id)
                 )
               </if>
             ORDER BY l.operate_time DESC, l.id DESC
            </script>
            """)
    IPage<Map<String, Object>> selectLogs(@Param("page") IPage<Map<String, Object>> page,
                                          @Param("bizType") String bizType,
                                          @Param("bizId") Long bizId,
                                          @Param("operation") String operation,
                                          @Param("operatorId") Long operatorId,
                                          @Param("studentId") Long studentId,
                                          @Param("collegeIds") Set<Long> collegeIds,
                                          @Param("batchNo") String batchNo,
                                          @Param("keyword") String keyword,
                                          @Param("startTime") String startTime,
                                          @Param("endTime") String endTime);
}
