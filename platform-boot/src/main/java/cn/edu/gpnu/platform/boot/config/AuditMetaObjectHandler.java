package cn.edu.gpnu.platform.boot.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充：createdAt/updatedAt/createdBy/updatedBy。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        Long uid = currentUserId();
        this.strictInsertFill(metaObject, "createdBy", Long.class, uid);
        this.strictInsertFill(metaObject, "updatedBy", Long.class, uid);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        this.strictUpdateFill(metaObject, "updatedBy", Long.class, currentUserId());
    }

    /**
     * 当前用户 id。Phase 2 接入 SecurityContext 后替换；现阶段返回系统账号 0。
     */
    private Long currentUserId() {
        // TODO: 待 Phase 2 接入登录上下文(确认单无关)
        return 0L;
    }
}
