package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.entity.BackupRecord;
import cn.edu.gpnu.platform.system.mapper.BackupRecordMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Phase 41.2（P0-6 真备份）：应用内 JDBC 逻辑导出 → gzip → 上传 MinIO 备份前缀，产出真实可核验的备份产物。
 *
 * <p>做法（app-driven，不 shell out mysqldump，保持可移植）：以单一 REPEATABLE_READ 只读连接对
 * {@link #BACKUP_TABLES 恢复关键表}逐表读取 JDBC 元数据，仅选择可写列（生成列由目标库重算），
 * 序列化为可回放的事务性 replace-restore 脚本：
 * 先反序 {@code DELETE} 受管表，再正序 {@code INSERT} 快照（含逻辑删除行，{@code deleted} 标记原样保留）。
 * 产物流式写入本地临时文件的 GZIP 流并同步计算 SHA-256，再 {@code putObject} 到 MinIO（默认复用业务桶 +
 * {@code db-backup/} 前缀），最后把 {@link BackupRecord} 更新为真实
 * {@code storageUri/byteSize/checksum/tableCount/rowCount} 与 {@code RUNNING→COMPLETED}（失败置 {@code FAILED}）。
 *
 * <p>状态与产物元数据经 {@link TransactionTemplate} 各自独立提交：先提交 {@code RUNNING}（运行期即可见），
 * 导出/上传在事务外执行（长 I/O 不占 DB 连接），末尾再提交 {@code COMPLETED/FAILED}——故失败也留痕，绝不再伪造
 * {@code COMPLETED}。同时被 {@code /api/system/backup/trigger} 与定时任务 {@code BackupScheduleConfig} 复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseBackupService {

    /**
     * 恢复关键表（curated，显式枚举、稳定有序；刻意不含 Flyway 的 {@code flyway_schema_history}——
     * schema 由迁移在恢复库重建，备份只承载业务/系统数据行）。覆盖 RBAC/字典/组织/参数/审计/通知 + 全部业务域。
     */
    static final List<String> BACKUP_TABLES = List.of(
            // 参考/组织/字典
            "sys_region", "sys_dict_type", "sys_dict_item", "teaching_subject",
            "sys_college", "sys_major", "training_goal_config", "major_training_goal",
            // RBAC / 用户 / 数据范围 / 评审组
            "sys_permission", "sys_role", "sys_role_permission",
            "sys_user", "sys_user_role", "sys_user_data_scope",
            "reviewer_group", "reviewer_group_member",
            // 参数 / 审计 / 通知 / 备份记录
            "sys_param", "audit_log", "notification", "backup_record",
            // 业务域：学生 / 培养 / 材料 / 免考
            "student", "training_profile", "process_material",
            "exemption_request", "exemption_material",
            // 视频评审 / 上传
            "video_review", "video_review_task", "video_upload_session", "video_upload_chunk",
            "video_finalization_object_candidate",
            // 能力测试 / 证书 / 序列
            "ability_test_result", "certificate", "cert_sequence",
            // 导入导出批次 / 明细 / 文件对象
            "import_export_batch", "import_record_ref", "import_error_detail", "file_object"
    );

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int MIN_SQL_STATEMENT_BYTES = 1024;
    private static final int MAX_SUPPORTED_SQL_STATEMENT_BYTES = 32 * 1024 * 1024;
    private static final int REQUIRED_PACKET_BYTES = 64 * 1024 * 1024;
    private static final int MYSQL_STREAMING_FETCH_SIZE = Integer.MIN_VALUE;
    private static final String ARTIFACT_FORMAT_VERSION = "TCP_SQL_V2";
    private static final String BINARY_LITERAL_PREFIX = "FROM_BASE64('";
    private static final String BINARY_LITERAL_SUFFIX = "')";
    private static final String TEXT_LITERAL_PREFIX = "CONVERT(FROM_BASE64('";
    private static final String TEXT_LITERAL_SUFFIX = "') USING utf8mb4)";
    private static final String JSON_LITERAL_PREFIX = "CAST(";
    private static final String JSON_LITERAL_SUFFIX = " AS JSON)";
    private static final int STATEMENT_TAIL_BYTES = 3; // ");\n"

    private final DataSource dataSource;
    private final MinioClient minioClient;
    private final BackupRecordMapper backupRecordMapper;
    private final TransactionTemplate transactionTemplate;

    /** 备份产物目标桶：默认复用业务桶（minio.bucket），prod 可用 platform.backup.bucket 指向独立桶。 */
    @Value("${platform.backup.bucket:${minio.bucket}}")
    private String bucket;

    /** 备份对象 key 前缀（同桶内隔离），默认 db-backup/。 */
    @Value("${platform.backup.prefix:db-backup/}")
    private String prefix;

    /**
     * 单条恢复语句的 UTF-8 字节上限。允许向下收紧用于 fail-closed 验证，但不得超过已验证的 32 MiB；
     * 恢复客户端和目标 MySQL 均须支持至少 64 MiB packet。
     */
    @Value("${platform.backup.max-sql-statement-bytes:33554432}")
    private int maxSqlStatementBytes;

    /**
     * 执行一次真实备份并落库。RUNNING 立即提交可见；导出/上传在事务外；末尾提交 COMPLETED/FAILED。
     *
     * @return 已落库的 BackupRecord（COMPLETED，含真实产物元数据）；失败抛 BizException（记录已置 FAILED）。
     */
    public BackupRecord backup(String backupType, String scope, String remark, Long operatorId) {
        BackupRecord record = new BackupRecord();
        record.setBackupType(backupType);
        record.setScope(scope);
        record.setRemark(remark);
        record.setOperatorId(operatorId);
        record.setStatus("RUNNING");
        record.setStartedAt(LocalDateTime.now());
        transactionTemplate.executeWithoutResult(s -> backupRecordMapper.insert(record));

        Path tmp = null;
        try {
            tmp = Files.createTempFile("tcp-backup-", ".sql.gz");
            Export export = exportToFile(tmp, record.getId(), backupType, scope);
            ensureBucket();
            String objectKey = prefix() + "teacher_cert_" + STAMP.format(LocalDateTime.now())
                    + "_" + record.getId() + ".sql.gz";
            try (InputStream in = Files.newInputStream(tmp)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(in, export.byteSize, -1)
                        .contentType("application/gzip")
                        .userMetadata(Map.of(
                                "tcp-record-id", record.getId().toString(),
                                "tcp-sha256", export.sha256,
                                "tcp-restore-mode", "replace-after-flyway",
                                "tcp-table-count", Integer.toString(export.tableCount),
                                "tcp-row-count", Long.toString(export.rowCount),
                                "tcp-format-version", ARTIFACT_FORMAT_VERSION,
                                "tcp-max-statement-bytes", Integer.toString(export.maxStatementBytes),
                                "tcp-required-packet-bytes", Integer.toString(REQUIRED_PACKET_BYTES)))
                        .build());
            }
            record.setStatus("COMPLETED");
            record.setFinishedAt(LocalDateTime.now());
            record.setStorageUri("minio://" + bucket + "/" + objectKey);
            record.setByteSize(export.byteSize);
            record.setChecksum("sha256:" + export.sha256);
            record.setTableCount(export.tableCount);
            record.setRowCount(export.rowCount);
            transactionTemplate.executeWithoutResult(s -> backupRecordMapper.updateById(record));
            log.info("备份完成 id={} key={} size={} tables={} rows={}",
                    record.getId(), objectKey, export.byteSize, export.tableCount, export.rowCount);
            return record;
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setFinishedAt(LocalDateTime.now());
            record.setErrorMessage(truncate(e.getMessage()));
            transactionTemplate.executeWithoutResult(s -> backupRecordMapper.updateById(record));
            log.error("备份失败 id={}", record.getId(), e);
            throw new BizException("备份执行失败: " + e.getMessage());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (Exception ignore) {
                    log.warn("清理备份临时文件失败: {}", tmp);
                }
            }
        }
    }

    /** JDBC 逻辑导出（单一 REPEATABLE_READ 只读快照）→ 临时文件（gzip + SHA-256）。 */
    private Export exportToFile(Path tmp, Long recordId, String backupType, String scope) throws Exception {
        int statementLimit = statementLimit();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        int tableCount = 0;
        long rowCount = 0;
        try (OutputStream fileOut = Files.newOutputStream(tmp);
             DigestOutputStream digestOut = new DigestOutputStream(fileOut, sha);
             GZIPOutputStream gzip = new GZIPOutputStream(digestOut);
             Writer w = new BufferedWriter(new OutputStreamWriter(gzip, StandardCharsets.UTF_8))) {
            w.write("-- Teacher-Cert-Platform 逻辑备份（JDBC 数据导出）\n");
            w.write("-- backupType=" + commentValue(backupType) + " scope=" + commentValue(scope)
                    + " recordId=" + recordId + "\n");
            w.write("-- generatedAt=" + LocalDateTime.now() + "\n");
            w.write("-- restoreMode=REPLACE_AFTER_FLYWAY transaction=SINGLE "
                    + "backupRecordPolicy=TERMINAL_ONLY\n");
            w.write("-- formatVersion=" + ARTIFACT_FORMAT_VERSION
                    + " generatedColumns=OMITTED textEncoding=BASE64"
                    + " maxStatementBytes=" + statementLimit
                    + " requiredPacketBytes=" + REQUIRED_PACKET_BYTES + "\n");
            w.write("-- 内容：事务内反序清空受管表并回放完整快照；仅允许用于同版本 Flyway 已迁移的隔离恢复库。\n");
            w.write("SET NAMES utf8mb4;\n");
            w.write("SET @TCP_OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS;\n");
            w.write("SET FOREIGN_KEY_CHECKS=0;\n");
            w.write("START TRANSACTION;\n");
            w.write("-- 清空目标受管表，删除 Flyway 种子和快照外数据；DELETE 可随事务失败回滚。\n");
            for (int i = BACKUP_TABLES.size() - 1; i >= 0; i--) {
                w.write("DELETE FROM `" + BACKUP_TABLES.get(i) + "`;\n");
            }
            try (Connection conn = dataSource.getConnection()) {
                boolean oldAuto = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    for (String table : BACKUP_TABLES) {
                        rowCount += dumpTable(conn, table, w, statementLimit);
                        tableCount++;
                    }
                    conn.rollback(); // 只读快照，释放
                } finally {
                    conn.setAutoCommit(oldAuto);
                }
            }
            w.write("COMMIT;\n");
            w.write("SET FOREIGN_KEY_CHECKS=@TCP_OLD_FOREIGN_KEY_CHECKS;\n");
            w.write("-- EOF tables=" + tableCount + " rows=" + rowCount + "\n");
        }
        Export export = new Export();
        export.byteSize = Files.size(tmp);
        export.sha256 = HexFormat.of().formatHex(sha.digest());
        export.tableCount = tableCount;
        export.rowCount = rowCount;
        export.maxStatementBytes = statementLimit;
        return export;
    }

    private long dumpTable(
            Connection conn,
            String table,
            Writer w,
            int statementLimit) throws Exception {
        long count = 0;
        w.write("\n-- ---------- " + table + " ----------\n");
        List<String> writableColumns = writableColumns(conn, table);
        String colList = writableColumns.stream()
                .map(this::quoteIdentifier)
                .reduce((left, right) -> left + "," + right)
                .orElseThrow(() -> new IllegalStateException("表没有可备份的可写列: " + table));
        boolean terminalBackupRecordsOnly = "backup_record".equals(table);
        String query = "SELECT " + colList + " FROM " + quoteIdentifier(table)
                + (terminalBackupRecordsOnly
                ? " WHERE status IN ('COMPLETED','FAILED')"
                : "");
        try (PreparedStatement st = conn.prepareStatement(
                query,
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY)) {
            // Connector/J 专用流式读取契约：避免 audit_log 等增长表被整表缓存在 JVM 堆内。
            st.setFetchDirection(ResultSet.FETCH_FORWARD);
            st.setFetchSize(MYSQL_STREAMING_FETCH_SIZE);
            try (ResultSet rs = st.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                if (cols != writableColumns.size()) {
                    throw new IllegalStateException("可写列数量与结果集不一致: table=" + table);
                }
                for (int i = 1; i <= cols; i++) {
                    if (!writableColumns.get(i - 1).equals(md.getColumnName(i))) {
                        throw new IllegalStateException(
                                "可写列顺序与结果集不一致: table=" + table + " column=" + i);
                    }
                }
                String insertPrefix = "INSERT INTO " + quoteIdentifier(table)
                        + " (" + colList + ") VALUES ";
                long prefixBytes = utf8Length(insertPrefix) + 1L; // opening '('
                while (rs.next()) {
                    long statementBytes = prefixBytes + STATEMENT_TAIL_BYTES;
                    List<SqlLiteral> literals = new ArrayList<>(cols);
                    for (int i = 1; i <= cols; i++) {
                        if (i > 1) {
                            statementBytes++;
                        }
                        long remaining = statementLimit - statementBytes;
                        SqlLiteral literal;
                        try {
                            literal = sqlLiteral(rs, md, i, remaining);
                        } catch (SqlLiteralTooLargeException e) {
                            throw statementTooLarge(
                                    table,
                                    count + 1,
                                    statementBytes + e.literalBytes(),
                                    statementLimit);
                        }
                        statementBytes += literal.utf8Bytes();
                        literals.add(literal);
                    }
                    if (statementBytes > statementLimit) {
                        throw statementTooLarge(
                                table, count + 1, statementBytes, statementLimit);
                    }
                    w.write(insertPrefix);
                    w.write('(');
                    for (int i = 0; i < literals.size(); i++) {
                        if (i > 0) {
                            w.write(',');
                        }
                        w.write(literals.get(i).value());
                    }
                    w.write(");\n");
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 按列类型生成 MySQL 字面量：NULL / 数值 / 位 / Base64 二进制 /
     * Base64 UTF-8 文本（JSON 显式 CAST）。Basic Base64 不含引号、反斜杠或换行，不依赖 SQL mode。
     */
    private SqlLiteral sqlLiteral(
            ResultSet rs,
            ResultSetMetaData md,
            int i,
            long remainingBytes) throws Exception {
        int type = md.getColumnType(i);
        switch (type) {
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                byte[] b = rs.getBytes(i);
                if (rs.wasNull() || b == null) {
                    return checkedLiteral("NULL", 4, remainingBytes);
                }
                long literalBytes = BINARY_LITERAL_PREFIX.length()
                        + base64Length(b.length)
                        + BINARY_LITERAL_SUFFIX.length();
                ensureLiteralFits(literalBytes, remainingBytes);
                return new SqlLiteral(
                        BINARY_LITERAL_PREFIX
                                + Base64.getEncoder().encodeToString(b)
                                + BINARY_LITERAL_SUFFIX,
                        literalBytes);
            }
            case Types.BIT, Types.BOOLEAN -> {
                boolean v = rs.getBoolean(i);
                return rs.wasNull()
                        ? checkedLiteral("NULL", 4, remainingBytes)
                        : checkedLiteral(v ? "1" : "0", 1, remainingBytes);
            }
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT,
                    Types.FLOAT, Types.REAL, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL -> {
                String s = rs.getString(i);
                if (rs.wasNull() || s == null) {
                    return checkedLiteral("NULL", 4, remainingBytes);
                }
                return checkedLiteral(s, utf8Length(s), remainingBytes);
            }
            default -> {
                String s = rs.getString(i);
                if (rs.wasNull() || s == null) {
                    return checkedLiteral("NULL", 4, remainingBytes);
                }
                return textLiteral(
                        s,
                        "JSON".equalsIgnoreCase(md.getColumnTypeName(i)),
                        remainingBytes);
            }
        }
    }

    private SqlLiteral textLiteral(String s, boolean json, long remainingBytes) {
        long rawUtf8Bytes = utf8Length(s);
        long literalBytes = TEXT_LITERAL_PREFIX.length()
                + base64Length(rawUtf8Bytes)
                + TEXT_LITERAL_SUFFIX.length()
                + (json ? JSON_LITERAL_PREFIX.length() + JSON_LITERAL_SUFFIX.length() : 0);
        ensureLiteralFits(literalBytes, remainingBytes);
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        String decoded = TEXT_LITERAL_PREFIX
                + Base64.getEncoder().encodeToString(bytes)
                + TEXT_LITERAL_SUFFIX;
        return new SqlLiteral(
                json ? JSON_LITERAL_PREFIX + decoded + JSON_LITERAL_SUFFIX : decoded,
                literalBytes);
    }

    private List<String> writableColumns(Connection conn, String table) throws Exception {
        String catalog = conn.getCatalog();
        if (catalog == null || catalog.isBlank()) {
            throw new IllegalStateException("无法确定备份 schema: table=" + table);
        }
        List<String> columns = new ArrayList<>();
        DatabaseMetaData metadata = conn.getMetaData();
        String tablePattern = metadataLiteralPattern(metadata, table);
        try (ResultSet rs = metadata.getColumns(catalog, null, tablePattern, "%")) {
            while (rs.next()) {
                String column = rs.getString("COLUMN_NAME");
                String generated = rs.getString("IS_GENERATEDCOLUMN");
                if (column == null || column.isBlank()
                        || generated == null
                        || (!"YES".equalsIgnoreCase(generated)
                        && !"NO".equalsIgnoreCase(generated))) {
                    throw new IllegalStateException(
                            "列生成属性不可判定，拒绝生成备份: table=" + table
                                    + " column=" + column);
                }
                if ("NO".equalsIgnoreCase(generated)) {
                    columns.add(column);
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("未发现可写列，拒绝生成备份: table=" + table);
        }
        return columns;
    }

    private String metadataLiteralPattern(DatabaseMetaData metadata, String literal)
            throws Exception {
        String escape = metadata.getSearchStringEscape();
        if (escape == null || escape.isEmpty()) {
            throw new IllegalStateException("数据库元数据未提供搜索转义符");
        }
        return literal
                .replace(escape, escape + escape)
                .replace("_", escape + "_")
                .replace("%", escape + "%");
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    private int statementLimit() {
        if (maxSqlStatementBytes < MIN_SQL_STATEMENT_BYTES
                || maxSqlStatementBytes > MAX_SUPPORTED_SQL_STATEMENT_BYTES) {
            throw new IllegalStateException(
                    "platform.backup.max-sql-statement-bytes 必须在 "
                            + MIN_SQL_STATEMENT_BYTES + ".."
                            + MAX_SUPPORTED_SQL_STATEMENT_BYTES + " 之间");
        }
        return maxSqlStatementBytes;
    }

    private SqlLiteral checkedLiteral(String value, long utf8Bytes, long remainingBytes) {
        ensureLiteralFits(utf8Bytes, remainingBytes);
        return new SqlLiteral(value, utf8Bytes);
    }

    private void ensureLiteralFits(long literalBytes, long remainingBytes) {
        if (remainingBytes < 0 || literalBytes > remainingBytes) {
            throw new SqlLiteralTooLargeException(literalBytes);
        }
    }

    private long base64Length(long rawBytes) {
        return Math.multiplyExact(Math.floorDiv(Math.addExact(rawBytes, 2L), 3L), 4L);
    }

    /**
     * 不分配中间 byte[] 的 UTF-8 长度预算。数据库 utf8mb4 不会返回孤立代理项；
     * 若未来驱动返回畸形 UTF-16，则按 3 字节保守计数并优先失败关闭。
     */
    private long utf8Length(CharSequence value) {
        long bytes = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 0x7F) {
                bytes++;
            } else if (c <= 0x7FF) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c)
                    && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    private IllegalStateException statementTooLarge(
            String table,
            long rowOrdinal,
            long minimumBytes,
            int statementLimit) {
        return new IllegalStateException(
                "备份行 SQL 超出单语句上限: table=" + table
                        + " rowOrdinal=" + rowOrdinal
                        + " minimumBytes=" + minimumBytes
                        + " maxBytes=" + statementLimit);
    }

    /**
     * 备份类型/范围只写入 SQL 行注释，必须压成单行，避免控制字符把非可信元数据变成恢复语句。
     */
    private String commentValue(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder safe = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int type = Character.getType(c);
            if (Character.isISOControl(c)
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                safe.append(' ');
            } else {
                safe.append(c);
            }
        }
        return safe.toString();
    }

    private void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("备份桶已创建: {}", bucket);
            }
        } catch (Exception e) {
            throw new BizException("备份桶不可用: " + e.getMessage());
        }
    }

    private String prefix() {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private String truncate(String msg) {
        if (msg == null) {
            return "unknown error";
        }
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }

    /** 导出结果元数据。 */
    private static final class Export {
        private long byteSize;
        private String sha256;
        private int tableCount;
        private long rowCount;
        private int maxStatementBytes;
    }

    private record SqlLiteral(String value, long utf8Bytes) {
    }

    private static final class SqlLiteralTooLargeException extends RuntimeException {
        private final long literalBytes;

        private SqlLiteralTooLargeException(long literalBytes) {
            this.literalBytes = literalBytes;
        }

        private long literalBytes() {
            return literalBytes;
        }
    }
}
