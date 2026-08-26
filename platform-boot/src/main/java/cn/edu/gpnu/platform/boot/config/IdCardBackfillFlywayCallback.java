package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * WS-8 密钥相关回填。
 *
 * <p>结构迁移必须能由脱离 Spring 的恢复工具执行，因此 V33 只负责 DDL；本 callback 在所有版本迁移及
 * dev/test repeatable seed 结束后运行，把存量与测试种子统一收敛到密文。处理过程可重复执行，已加密值不会
 * 二次加密。密钥服务在 Flyway 启动前由 Spring 构造，配置不合法会在任何数据变更前拒绝启动。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdCardBackfillFlywayCallback extends BaseCallback {

    private static final String ID_CARD_FIELD = "idCardNo";
    private static final String KEYWORD_FIELD = "keyword";
    private static final String ID_CARD_LABEL = "身份证件号码";

    private final IdCardProtectionService idCardProtectionService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.AFTER_MIGRATE;
    }

    @Override
    public void handle(Event event, Context context) {
        Connection connection = context.getConnection();
        boolean ownTransaction = false;
        try {
            if (!hasColumn(connection, "student", "id_card_hmac")) {
                // 支持显式 spring.flyway.target=V32 的诊断/历史环境；V33 到位后才执行回填。
                return;
            }
            if (!hasColumn(connection, "certificate", "id_card_hmac")) {
                throw new IllegalStateException("WS-8 结构迁移不完整，拒绝身份证件号回填");
            }
            ownTransaction = connection.getAutoCommit();
            if (ownTransaction) {
                connection.setAutoCommit(false);
            }

            Set<String> knownPlaintexts = collectKnownPlaintexts(connection);
            assertNoActiveStudentHmacCollision(connection);
            int students = protectStudents(connection);
            int certificates = protectCertificates(connection);
            int exchangeValues = protectExchangeHistory(connection, knownPlaintexts);

            if (ownTransaction) {
                connection.commit();
            }
            log.info("WS-8 身份证件号存量回填完成：student={}，certificate={}，exchange={}",
                    students, certificates, exchangeValues);
        } catch (Exception e) {
            if (ownTransaction) {
                rollbackQuietly(connection);
            }
            log.error("WS-8 身份证件号存量回填失败（category={}）", e.getClass().getSimpleName());
            throw new IllegalStateException("WS-8 身份证件号存量回填失败，已拒绝启动");
        } finally {
            if (ownTransaction) {
                restoreAutoCommit(connection);
            }
        }
    }

    private Set<String> collectKnownPlaintexts(Connection connection) throws SQLException {
        Set<String> values = new HashSet<>();
        collectPlaintexts(connection, "student", values);
        collectPlaintexts(connection, "certificate", values);
        return values;
    }

    private void collectPlaintexts(Connection connection, String table, Set<String> values)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id_card_type, id_card_no FROM " + table + " WHERE id_card_no IS NOT NULL");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String stored = rows.getString("id_card_no");
                if (!StringUtils.hasText(stored)) {
                    continue;
                }
                String raw = rawPlainValue(stored).trim();
                values.add(raw);
                values.add(normalize(rows.getString("id_card_type"), raw));
            }
        }
    }

    private void assertNoActiveStudentHmacCollision(Connection connection) throws SQLException {
        Map<String, Long> owners = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, id_card_type, id_card_no FROM student WHERE deleted = 0 ORDER BY id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                Long id = rows.getLong("id");
                String plain = plainValue(rows.getString("id_card_type"), rows.getString("id_card_no"));
                String previous = idCardProtectionService.hmac(plain);
                Long priorOwner = owners.putIfAbsent(previous, id);
                if (priorOwner != null && !priorOwner.equals(id)) {
                    throw new IllegalStateException("存量未删学生证件号存在重复，拒绝 WS-8 回填");
                }
            }
        }
    }

    private int protectStudents(Connection connection) throws SQLException {
        int changed = 0;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, id_card_type, id_card_no, id_card_hmac, deleted FROM student ORDER BY id");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE student SET id_card_no = ?, id_card_hmac = ? WHERE id = ?");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String stored = rows.getString("id_card_no");
                String plain = plainValue(rows.getString("id_card_type"), stored);
                String encrypted = protectedValue(stored, plain);
                String hmac = rows.getInt("deleted") == 0
                        ? idCardProtectionService.hmac(plain) : null;
                if (encrypted.equals(stored) && java.util.Objects.equals(
                        hmac, rows.getString("id_card_hmac"))) {
                    continue;
                }
                update.setString(1, encrypted);
                update.setString(2, hmac);
                update.setLong(3, rows.getLong("id"));
                update.addBatch();
                changed++;
            }
            update.executeBatch();
        }
        return changed;
    }

    private int protectCertificates(Connection connection) throws SQLException {
        int changed = 0;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, id_card_type, id_card_no, id_card_hmac FROM certificate ORDER BY id");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE certificate SET id_card_no = ?, id_card_hmac = ? WHERE id = ?");
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String stored = rows.getString("id_card_no");
                if (!StringUtils.hasText(stored)) {
                    continue;
                }
                String plain = plainValue(rows.getString("id_card_type"), stored);
                String encrypted = protectedValue(stored, plain);
                String hmac = idCardProtectionService.hmac(plain);
                if (encrypted.equals(stored) && hmac.equals(rows.getString("id_card_hmac"))) {
                    continue;
                }
                update.setString(1, encrypted);
                update.setString(2, hmac);
                update.setLong(3, rows.getLong("id"));
                update.addBatch();
                changed++;
            }
            update.executeBatch();
        }
        return changed;
    }

    private int protectExchangeHistory(Connection connection, Set<String> knownPlaintexts)
            throws Exception {
        int changed = 0;
        changed += protectJsonColumn(
                connection, "import_export_batch", "preview_json", knownPlaintexts, false, false);
        changed += protectJsonColumn(
                connection, "import_export_batch", "scope_json", knownPlaintexts, false, true);
        changed += protectJsonColumn(
                connection, "import_record_ref", "before_json", knownPlaintexts, true, false);
        changed += protectJsonColumn(
                connection, "import_record_ref", "after_json", knownPlaintexts, true, false);
        changed += protectErrorValues(connection);
        return changed;
    }

    private int protectJsonColumn(
            Connection connection, String table, String column, Set<String> knownPlaintexts,
            boolean addSnapshotHmac, boolean protectKeyword)
            throws Exception {
        int changed = 0;
        String selectSql = "SELECT id, " + column + " FROM " + table
                + " WHERE " + column + " IS NOT NULL ORDER BY id";
        String updateSql = "UPDATE " + table + " SET " + column + " = CAST(? AS JSON) WHERE id = ?";
        try (PreparedStatement select = connection.prepareStatement(selectSql);
             PreparedStatement update = connection.prepareStatement(updateSql);
             ResultSet rows = select.executeQuery()) {
            while (rows.next()) {
                String original = rows.getString(column);
                JsonNode node = objectMapper.readTree(original);
                boolean modified = addSnapshotHmac && protectSnapshotHmac(node);
                modified |= protectJsonNode(node, null, knownPlaintexts, protectKeyword);
                if (!modified) {
                    continue;
                }
                update.setString(1, objectMapper.writeValueAsString(node));
                update.setLong(2, rows.getLong("id"));
                update.addBatch();
                changed++;
            }
            update.executeBatch();
        }
        return changed;
    }

    /**
     * V33 以前的 Student/Certificate 回滚快照没有 idCardHmac。若只加密 idCardNo，之后回滚会把
     * 活跃学生的查重键还原成 NULL；因此在同一次历史 JSON 迁移中补齐确定性 HMAC。
     */
    private boolean protectSnapshotHmac(JsonNode node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return false;
        }
        JsonNode idCardNode = objectNode.get(ID_CARD_FIELD);
        if (idCardNode == null || !idCardNode.isTextual()
                || !StringUtils.hasText(idCardNode.textValue())) {
            return false;
        }
        String type = objectNode.path("idCardType").asText("");
        String stored = idCardNode.textValue();
        String plain = normalize(type, rawPlainValue(stored));
        String encrypted = idCardProtectionService.isEncrypted(stored)
                ? stored : idCardProtectionService.encrypt(plain);
        String hmac = idCardProtectionService.hmac(plain);
        boolean changed = !encrypted.equals(stored)
                || !hmac.equals(objectNode.path("idCardHmac").asText(null));
        objectNode.put(ID_CARD_FIELD, encrypted);
        objectNode.put("idCardHmac", hmac);
        return changed;
    }

    private boolean protectJsonNode(
            JsonNode node, String fieldName, Set<String> knownPlaintexts, boolean protectKeyword) {
        boolean changed = false;
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                if (value.isTextual()) {
                    String text = value.textValue();
                    if (isProtectedField(field.getKey(), protectKeyword)
                            && idCardProtectionService.isEncrypted(text)) {
                        // v1 前缀是本保护协议的保留格式；重入时也必须认证，不能让伪造密文混过回填。
                        idCardProtectionService.decrypt(text);
                    } else if (shouldProtect(
                            field.getKey(), text, knownPlaintexts, protectKeyword)) {
                        objectNode.put(field.getKey(), idCardProtectionService.encrypt(text.trim()));
                        changed = true;
                    }
                } else {
                    changed |= protectJsonNode(
                            value, field.getKey(), knownPlaintexts, protectKeyword);
                }
            }
        } else if (node instanceof ArrayNode arrayNode) {
            for (JsonNode value : arrayNode) {
                changed |= protectJsonNode(value, fieldName, knownPlaintexts, protectKeyword);
            }
        }
        return changed;
    }

    private boolean shouldProtect(
            String fieldName, String value, Set<String> knownPlaintexts, boolean protectKeyword) {
        return StringUtils.hasText(value)
                && !idCardProtectionService.isEncrypted(value)
                && (ID_CARD_FIELD.equals(fieldName)
                || (protectKeyword && KEYWORD_FIELD.equals(fieldName))
                || knownPlaintexts.contains(value.trim()));
    }

    private boolean isProtectedField(String fieldName, boolean protectKeyword) {
        return ID_CARD_FIELD.equals(fieldName)
                || "idCardHmac".equals(fieldName)
                || (protectKeyword && KEYWORD_FIELD.equals(fieldName));
    }

    private int protectErrorValues(Connection connection) throws SQLException {
        int changed = 0;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, error_value FROM import_error_detail "
                        + "WHERE field_name = ? AND error_value IS NOT NULL ORDER BY id");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE import_error_detail SET error_value = ? WHERE id = ?")) {
            select.setString(1, ID_CARD_LABEL);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    String original = rows.getString("error_value");
                    if (!StringUtils.hasText(original)) {
                        continue;
                    }
                    if (idCardProtectionService.isEncrypted(original)) {
                        idCardProtectionService.decrypt(original);
                        continue;
                    }
                    update.setString(1, idCardProtectionService.encrypt(original));
                    update.setLong(2, rows.getLong("id"));
                    update.addBatch();
                    changed++;
                }
            }
            update.executeBatch();
        }
        return changed;
    }

    private String plainValue(String type, String stored) {
        return normalize(type, rawPlainValue(stored));
    }

    private String rawPlainValue(String stored) {
        if (!StringUtils.hasText(stored)) {
            throw new IllegalStateException("身份证件号码存量值为空，拒绝 WS-8 回填");
        }
        return idCardProtectionService.isEncrypted(stored)
                ? idCardProtectionService.decrypt(stored) : stored.trim();
    }

    private String normalize(String type, String plain) {
        String normalizedType = StringUtils.hasText(type)
                ? type.trim().toLowerCase(Locale.ROOT) : "";
        String normalized = plain.trim().toLowerCase(Locale.ROOT);
        if (("resident_id_card".equals(normalizedType)
                || "hmt_residence_permit".equals(normalizedType))
                && normalized.endsWith("x")) {
            return normalized.substring(0, normalized.length() - 1) + "X";
        }
        return normalized;
    }

    private String protectedValue(String stored, String plain) {
        return idCardProtectionService.isEncrypted(stored)
                ? stored : idCardProtectionService.encrypt(plain);
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() && row.getInt(1) == 1;
            }
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            log.error("WS-8 身份证件号回填事务回滚失败");
        }
    }

    private void restoreAutoCommit(Connection connection) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
            log.error("WS-8 身份证件号回填连接状态恢复失败");
        }
    }
}
