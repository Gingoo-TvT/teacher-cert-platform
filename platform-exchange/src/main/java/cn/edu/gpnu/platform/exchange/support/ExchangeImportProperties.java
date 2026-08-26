package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.config.DatabaseBackupProperties;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 标准 Excel 导入的进程级资源预算。
 *
 * <p>这些上限必须在解析工作簿和构建整批预览前生效，避免合法导入权限被放大为进程级堆耗尽。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "platform.exchange.import")
public class ExchangeImportProperties {

    private static final long BACKUP_SQL_OVERHEAD_RESERVE_BYTES = 1024L * 1024;

    private final DatabaseBackupProperties backupProperties;

    private long maxFileBytes = 16L * 1024 * 1024;
    private long maxExpandedBytes = 64L * 1024 * 1024;
    private int maxRows = 20_000;
    private int maxCellCharacters = 4_096;
    private int maxErrorDetails = 20_000;
    private long maxPreviewJsonBytes = 20L * 1024 * 1024;

    /**
     * 仅供脱离 Spring 的纯单元测试使用；生产 bean 由下面的共享配置构造器创建。
     */
    public ExchangeImportProperties() {
        this(new DatabaseBackupProperties());
    }

    @Autowired
    public ExchangeImportProperties(DatabaseBackupProperties backupProperties) {
        this.backupProperties = backupProperties;
    }

    @PostConstruct
    void validate() {
        if (maxFileBytes < 1L
                || maxExpandedBytes < 1L
                || maxRows < 1
                || maxCellCharacters < 1
                || maxErrorDetails < 1
                || maxPreviewJsonBytes < 1L
                || maxPreviewJsonBytes > Integer.MAX_VALUE) {
            throw new IllegalStateException("platform.exchange.import 各资源预算必须为正数");
        }
        backupProperties.validate();
        long backupMaxSqlStatementBytes = backupProperties.getMaxSqlStatementBytes();
        long encodedPreviewBytes;
        try {
            encodedPreviewBytes = Math.multiplyExact(
                    4L, Math.floorDiv(Math.addExact(maxPreviewJsonBytes, 2L), 3L));
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("max-preview-json-bytes 超出可计算范围", exception);
        }
        if (encodedPreviewBytes
                > backupMaxSqlStatementBytes - BACKUP_SQL_OVERHEAD_RESERVE_BYTES) {
            throw new IllegalStateException("max-preview-json-bytes 超过备份单语句可表示范围");
        }
    }

    public void assertErrorDetailBudget(long existing, long incoming) {
        if (existing < 0 || incoming < 0 || existing + incoming > maxErrorDetails) {
            throw new BizException("Excel错误明细超过上限 " + maxErrorDetails);
        }
    }

}
