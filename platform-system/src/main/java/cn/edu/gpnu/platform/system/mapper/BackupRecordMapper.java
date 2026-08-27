package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.BackupRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface BackupRecordMapper extends BaseMapper<BackupRecord> {

    /**
     * 物理删除 cutoff 之前的终态备份记录，单次至多 batchSize 行。
     * 备份产物由 MinIO 生命周期独立管理；本方法只删除记录行，不处理对象。
     * RUNNING/PENDING 不属于历史归档，避免清理仍在执行或待执行的备份。
     *
     * @return 本次实际删除行数（小于 batchSize 即表示已删尽）
     */
    @Delete("DELETE FROM backup_record"
            + " WHERE status IN ('COMPLETED', 'FAILED')"
            + " AND finished_at < #{cutoff} LIMIT #{batchSize}")
    int deletePhysicalTerminalOlderThan(@Param("cutoff") LocalDateTime cutoff,
                                        @Param("batchSize") int batchSize);
}
