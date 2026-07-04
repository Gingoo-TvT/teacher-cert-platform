package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.SysAuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {

    /**
     * Phase 47（P1-9 定时清理）：物理删除 operate_time 早于 cutoff 的审计日志，单次至多 batchSize 行。
     * audit_log 追加写、无逻辑删除列，此处即真物理删除；LIMIT 分批（调用方循环）避免大清理长时间锁表。
     *
     * @return 本次实际删除行数（小于 batchSize 即表示已删尽）
     */
    @Delete("DELETE FROM audit_log WHERE operate_time < #{cutoff} LIMIT #{batchSize}")
    int deletePhysicalOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
