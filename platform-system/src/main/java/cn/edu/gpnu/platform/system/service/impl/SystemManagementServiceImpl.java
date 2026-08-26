package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.api.PageQuery;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.cache.ReferenceCacheInvalidator;
import cn.edu.gpnu.platform.system.config.CacheConfig;
import cn.edu.gpnu.platform.system.dto.AuditLogQuery;
import cn.edu.gpnu.platform.system.dto.BackupTriggerRequest;
import cn.edu.gpnu.platform.system.dto.SysParamUpdateRequest;
import cn.edu.gpnu.platform.system.entity.BackupRecord;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.mapper.AuditQueryMapper;
import cn.edu.gpnu.platform.system.mapper.BackupRecordMapper;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.SystemManagementService;
import cn.edu.gpnu.platform.system.vo.AuditLogVO;
import cn.edu.gpnu.platform.system.vo.BackupRecordVO;
import cn.edu.gpnu.platform.system.vo.SysParamVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SystemManagementServiceImpl implements SystemManagementService {

    private static final Set<String> PARAM_GROUPS = Set.of(
            "cert", "video", "file", "review", "validate", "student", "global", "security"
    );

    private final SysParamMapper paramMapper;
    private final AuditQueryMapper auditQueryMapper;
    private final BackupRecordMapper backupRecordMapper;
    private final DataScopeService dataScopeService;
    private final AuditLogService auditLogService;
    private final DatabaseBackupService databaseBackupService;
    private final ReferenceCacheInvalidator referenceCacheInvalidator;

    // Phase 44e-contract（P1-1 真分页样例 · 非数据范围列表）：由全表 selectList 改为 selectPage 真分页。
    // 本接口无 @DataScope（系统参数为全局参考数据、无行级归属），DataScopeContext 为空、数据权限拦截器
    // 在此不追加过滤 → total 为「按 group/keyword 过滤后的真实总数」，records 为该页。分页拦截器独立生效。
    @Override
    public PageResult<SysParamVO> params(String group, String keyword, Integer page, Integer size) {
        LambdaQueryWrapper<SysParam> wrapper = new LambdaQueryWrapper<SysParam>()
                .orderByAsc(SysParam::getParamGroup)
                .orderByAsc(SysParam::getParamKey);
        if (StringUtils.hasText(group)) {
            wrapper.eq(SysParam::getParamGroup, group.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String key = keyword.trim();
            wrapper.and(w -> w.like(SysParam::getParamKey, key)
                    .or()
                    .like(SysParam::getDescription, key));
        }
        Page<SysParam> result = paramMapper.selectPage(PageQuery.of(page, size), wrapper);
        List<SysParamVO> records = result.getRecords().stream().map(this::toParamVO).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    // Phase 44c（§7.3）：参数唯一生产写路径 → 逐出整个 sysParam 缓存（key 含默认值无法精准逐单键，且参数写罕见，allEntries 简单可靠）。
    // Phase 44（PG-M4 整改）：由 @CacheEvict 改为 ReferenceCacheInvalidator。原因有二：
    // ①@CacheEvict 默认在方法返回后逐出，但此时仍在 @Transactional 内（且缓存切面与事务切面默认同序、
    //   相对顺序未定义），并发读可在逐出后把提交前旧值重填进缓存；②本方法回滚时 @CacheEvict 不执行，
    //   而失败前若已有读穿把未提交值缓存，脏值会留到 TTL。失效器改为事务完成后（提交/回滚都做）清空。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysParamVO updateParam(Long id, SysParamUpdateRequest request) {
        if (id == null) {
            throw new BizException("参数ID不能为空");
        }
        SysParam entity = paramMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "参数不存在");
        }
        if (entity.getEditable() == null || entity.getEditable() != 1) {
            throw new BizException("该参数不可编辑");
        }
        String value = requiredTrim(request.getParamValue(), "参数值不能为空");
        validateParam(entity, value);
        entity.setParamValue(value);
        if (StringUtils.hasText(request.getDescription())) {
            entity.setDescription(request.getDescription().trim());
        }
        paramMapper.updateById(entity);
        referenceCacheInvalidator.clearAfterCompletion(CacheConfig.SYS_PARAM);
        return toParamVO(paramMapper.selectById(id));
    }

    // Phase 44e-rollout（P1-1 真分页 · 自定义 @Select 手写 mapper）：selectLogs 原以 LIMIT 500 兜底防止
    // 无上限拉全表，rollout 改为标准 IPage 入参（首参数）+ 去掉硬编码 LIMIT，交给 PaginationInnerInterceptor
    // 自动改写 SQL 追加 COUNT 与真实 LIMIT/OFFSET。auditCollegeScope 仍在 Java 侧解析范围、把结果集
    // collegeIds 传入 mapper 的 SQL WHERE（非 selectList 后再 Java 过滤），故分页 COUNT 与分页数据同样精确
    // 遵循学院范围 —— 与其他 11 个 rollout 端点一致，不存在"范围过滤晚于分页"的问题。
    @Override
    public PageResult<AuditLogVO> auditLogs(AuditLogQuery query) {
        AuditLogQuery q = query == null ? new AuditLogQuery() : query;
        Set<Long> collegeIds = auditCollegeScope(q);
        if (collegeIds != null && collegeIds.isEmpty()) {
            return new PageResult<>(0, List.of());
        }
        IPage<Map<String, Object>> result = auditQueryMapper.selectLogs(
                PageQuery.of(q.getPage(), q.getSize()),
                trimToNull(q.getBizType()),
                q.getBizId(),
                trimToNull(q.getOperation()),
                q.getOperatorId(),
                q.getStudentId(),
                collegeIds,
                trimToNull(q.getBatchNo()),
                trimToNull(q.getKeyword()),
                normalizeDateTime(q.getStartTime()),
                normalizeDateTime(q.getEndTime())
        );
        List<AuditLogVO> records = result.getRecords().stream().map(this::toAuditVO).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public void rejectAuditDelete(Long id) {
        auditLogService.recordRequiresNew("audit", id, "auditLog/" + id, "deleteRejected",
                null, "REJECTED", "审计日志不可删除");
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "审计日志不可删除");
    }

    // Phase 44e-rollout（P1-1 真分页 · 无数据范围列表）：由全表 selectList 改为 selectPage 真分页。
    // 本接口无 @DataScope（备份记录为运维数据、无学院/学生归属），与 params(...) 同构：仅按 status 过滤。
    @Override
    public PageResult<BackupRecordVO> backups(String status, Integer page, Integer size) {
        LambdaQueryWrapper<BackupRecord> wrapper = new LambdaQueryWrapper<BackupRecord>()
                .orderByDesc(BackupRecord::getStartedAt)
                .orderByDesc(BackupRecord::getId);
        if (StringUtils.hasText(status)) {
            wrapper.eq(BackupRecord::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        Page<BackupRecord> result = backupRecordMapper.selectPage(PageQuery.of(page, size), wrapper);
        List<BackupRecordVO> records = result.getRecords().stream().map(this::toBackupVO).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    /**
     * Phase 41.2（P0-6 真备份）：不再伪造 COMPLETED，委托 {@link DatabaseBackupService} 执行真实的
     * JDBC 逻辑导出 → gzip → 上传 MinIO，记录关联真实产物（storageUri/byteSize/checksum/表数/行数）与
     * RUNNING→COMPLETED/FAILED 状态流。非事务方法：RUNNING/终态由 executor 内 TransactionTemplate 各自独立提交。
     */
    @Override
    public BackupRecordVO triggerBackup(BackupTriggerRequest request) {
        String type = requiredTrim(request.getBackupType(), "备份类型不能为空").toLowerCase(Locale.ROOT);
        BackupRecord record = databaseBackupService.backup(
                type, trimToNull(request.getScope()), trimToNull(request.getRemark()),
                UserContext.getUserIdOrSystem());
        return toBackupVO(record);
    }

    private Set<Long> auditCollegeScope(AuditLogQuery query) {
        DataScopeContext.Scope scope = dataScopeService.resolve("audit:view");
        if (scope == null || scope.getScopeType() == DataScopeContext.ScopeType.NONE) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权查看审计日志");
        }
        if (scope.allSchool()) {
            return query.getCollegeId() == null ? null : Set.of(query.getCollegeId());
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE) {
            if (query.getCollegeId() != null) {
                return scope.getCollegeIds().contains(query.getCollegeId()) ? Set.of(query.getCollegeId()) : Set.of();
            }
            return scope.getCollegeIds();
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权查看审计日志");
    }

    private void validateParam(SysParam entity, String value) {
        String group = trimToNull(entity.getParamGroup());
        if (group != null && !PARAM_GROUPS.contains(group)) {
            throw new BizException("参数分组不在允许范围: " + group);
        }
        String type = trimToNull(entity.getParamType());
        String normalizedType = type == null ? "string" : type.toLowerCase(Locale.ROOT);
        switch (normalizedType) {
            case "int", "long" -> parseLong(value, entity.getParamKey());
            case "bool", "boolean" -> validateBoolean(value, entity.getParamKey());
            case "enum" -> validateEnum(entity.getParamKey(), value);
            case "string" -> {
            }
            default -> throw new BizException("参数类型不支持: " + entity.getParamType());
        }
        validateKnownParam(entity.getParamKey(), value);
    }

    private void validateKnownParam(String key, String value) {
        if ("cert.seq.scope".equals(key) && !Set.of("SCHOOL_YEAR_SEGMENT", "SCHOOL_YEAR").contains(value)) {
            throw new BizException("证书序列作用域不支持");
        }
        if ("video.arbitrate.mode".equals(key) && !Set.of("thirdExpert", "collegeArbitrate").contains(value)) {
            throw new BizException("视频复评模式不支持");
        }
        if ("review.return.target".equals(key) && !Set.of("FIRST_REVIEW", "SECOND_REVIEW").contains(value)) {
            throw new BizException("退回目标不支持");
        }
        if ("validate.name.mode".equals(key) && !Set.of("strict", "loose").contains(value)) {
            throw new BizException("姓名校验模式不支持");
        }
        if (key != null && key.startsWith("cert.") && key.endsWith(".code") && !value.matches("^\\d+$")) {
            throw new BizException("证书编码参数必须为数字文本");
        }
    }

    private void validateEnum(String key, String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException("枚举参数不能为空: " + key);
        }
    }

    private void validateBoolean(String value, String key) {
        String text = value.toLowerCase(Locale.ROOT);
        if (!Set.of("true", "false", "1", "0", "yes", "no", "y", "n").contains(text)) {
            throw new BizException("布尔参数格式不正确: " + key);
        }
    }

    private long parseLong(String value, String key) {
        try {
            long result = Long.parseLong(value);
            if (result < 0) {
                throw new BizException("数字参数不能为负数: " + key);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new BizException("数字参数格式不正确: " + key);
        }
    }

    private SysParamVO toParamVO(SysParam entity) {
        SysParamVO vo = new SysParamVO();
        vo.setId(entity.getId());
        vo.setParamKey(entity.getParamKey());
        vo.setParamValue(entity.getParamValue());
        vo.setParamType(entity.getParamType());
        vo.setParamGroup(entity.getParamGroup());
        vo.setDescription(entity.getDescription());
        vo.setEditable(entity.getEditable());
        vo.setUpdatedAt(format(entity.getUpdatedAt()));
        return vo;
    }

    private AuditLogVO toAuditVO(Map<String, Object> row) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(toLong(rowValue(row, "id")));
        vo.setBizType(toString(rowValue(row, "biz_type", "bizType")));
        vo.setBizId(toLong(rowValue(row, "biz_id", "bizId")));
        vo.setTarget(toString(rowValue(row, "target")));
        vo.setOperatorId(toLong(rowValue(row, "operator_id", "operatorId")));
        vo.setOperatorName(toString(rowValue(row, "operator_name", "operatorName")));
        vo.setOperatorCollegeId(toLong(rowValue(row, "operator_college_id", "operatorCollegeId")));
        vo.setOperateTime(format(rowValue(row, "operate_time", "operateTime")));
        vo.setComment(toString(rowValue(row, "comment")));
        vo.setOldStatus(toString(rowValue(row, "old_status", "oldStatus")));
        vo.setNewStatus(toString(rowValue(row, "new_status", "newStatus")));
        vo.setOperation(toString(rowValue(row, "operation")));
        vo.setIp(toString(rowValue(row, "ip")));
        return vo;
    }

    private BackupRecordVO toBackupVO(BackupRecord entity) {
        BackupRecordVO vo = new BackupRecordVO();
        vo.setId(entity.getId());
        vo.setBackupType(entity.getBackupType());
        vo.setStatus(entity.getStatus());
        vo.setScope(entity.getScope());
        vo.setStorageUri(entity.getStorageUri());
        vo.setStartedAt(format(entity.getStartedAt()));
        vo.setFinishedAt(format(entity.getFinishedAt()));
        vo.setOperatorId(entity.getOperatorId());
        vo.setRemark(entity.getRemark());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setByteSize(entity.getByteSize());
        vo.setChecksum(entity.getChecksum());
        vo.setTableCount(entity.getTableCount());
        vo.setRowCount(entity.getRowCount());
        return vo;
    }

    private String normalizeDateTime(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.parse(text));
        } catch (DateTimeParseException ignored) {
            return text;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Object rowValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key)) {
                return row.get(key);
            }
        }
        return null;
    }

    private String format(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime time) {
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(time);
        }
        return String.valueOf(value).replace(' ', 'T');
    }

    private String requiredTrim(String value, String message) {
        String text = trimToNull(value);
        if (text == null) {
            throw new BizException(message);
        }
        return text;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
