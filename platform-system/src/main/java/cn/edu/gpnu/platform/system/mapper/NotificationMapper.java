package cn.edu.gpnu.platform.system.mapper;

import cn.edu.gpnu.platform.system.entity.Notification;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * Phase 47（P1-9 定时清理）：物理删除 created_at 早于 cutoff 的通知，单次至多 batchSize 行。
     * 刻意走原生 SQL——绕开实体 {@code @TableLogic} 的软删（UPDATE deleted=1），对过期运营数据做真物理
     * 删除以真正回收空间（不涉及业务数据）；LIMIT 分批（调用方循环）避免大清理长时间锁表。
     *
     * @return 本次实际删除行数（小于 batchSize 即表示已删尽）
     */
    @Delete("DELETE FROM notification WHERE created_at < #{cutoff} LIMIT #{batchSize}")
    int deletePhysicalOlderThan(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
