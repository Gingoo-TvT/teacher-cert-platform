package cn.edu.gpnu.platform.business.certificate.mapper;

import cn.edu.gpnu.platform.business.certificate.entity.CertSequence;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CertSequenceMapper extends BaseMapper<CertSequence> {

    @Insert("""
            INSERT INTO cert_sequence
                (id, scope_key, current_seq, created_by, created_at, updated_by, updated_at, deleted)
            VALUES
                (#{id}, #{scopeKey}, 0, 0, NOW(), 0, NOW(), 0)
            ON DUPLICATE KEY UPDATE
                scope_key = VALUES(scope_key),
                updated_at = updated_at
            """)
    void ensureScopeRow(@Param("id") Long id, @Param("scopeKey") String scopeKey);

    @Select("""
            SELECT *
              FROM cert_sequence
             WHERE scope_key = #{scopeKey}
               AND deleted = 0
             FOR UPDATE
            """)
    CertSequence selectByScopeKeyForUpdate(@Param("scopeKey") String scopeKey);
}
