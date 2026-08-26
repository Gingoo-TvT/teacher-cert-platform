package cn.edu.gpnu.platform.system.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据库逻辑备份格式的统一资源上限。
 *
 * <p>导入预览与实际备份必须读取同一个绑定结果，避免业务可写入而备份格式无法表示。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.backup")
public class DatabaseBackupProperties {

    public static final int MIN_SQL_STATEMENT_BYTES = 1024;
    public static final int MAX_SQL_STATEMENT_BYTES = 32 * 1024 * 1024;

    private int maxSqlStatementBytes = MAX_SQL_STATEMENT_BYTES;

    @PostConstruct
    public void validate() {
        if (maxSqlStatementBytes < MIN_SQL_STATEMENT_BYTES
                || maxSqlStatementBytes > MAX_SQL_STATEMENT_BYTES) {
            throw new IllegalStateException(
                    "platform.backup.max-sql-statement-bytes 必须在 "
                            + MIN_SQL_STATEMENT_BYTES + ".."
                            + MAX_SQL_STATEMENT_BYTES + " 之间");
        }
    }
}
