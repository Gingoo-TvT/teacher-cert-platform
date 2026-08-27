package cn.edu.gpnu.platform.exchange.service.impl;

import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.certificate.service.CertificateService;
import cn.edu.gpnu.platform.business.certificate.support.CertificateStatus;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionMaterial;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionRequest;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionMaterialMapper;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionRequestMapper;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.student.support.SensitiveMasker;
import cn.edu.gpnu.platform.business.student.support.StudentStatus;
import cn.edu.gpnu.platform.business.training.dto.TrainingProfileSaveRequest;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.business.training.support.TrainingLinkValidator;
import cn.edu.gpnu.platform.business.training.support.TrainingStatus;
import cn.edu.gpnu.platform.business.testresult.entity.AbilityTestResult;
import cn.edu.gpnu.platform.business.testresult.mapper.AbilityTestResultMapper;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.common.api.PageQuery;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.exchange.dto.ExchangeQuery;
import cn.edu.gpnu.platform.exchange.dto.ImportConfirmRequest;
import cn.edu.gpnu.platform.exchange.entity.ImportErrorDetail;
import cn.edu.gpnu.platform.exchange.entity.ImportExportBatch;
import cn.edu.gpnu.platform.exchange.entity.ImportRecordRef;
import cn.edu.gpnu.platform.exchange.mapper.ImportErrorDetailMapper;
import cn.edu.gpnu.platform.exchange.mapper.ImportExportBatchMapper;
import cn.edu.gpnu.platform.exchange.mapper.ImportRecordRefMapper;
import cn.edu.gpnu.platform.exchange.model.ExchangeColumn;
import cn.edu.gpnu.platform.exchange.model.ExchangeStandardRow;
import cn.edu.gpnu.platform.exchange.service.ExchangeService;
import cn.edu.gpnu.platform.exchange.support.ExchangeBatchStatus;
import cn.edu.gpnu.platform.exchange.support.BoundedPreviewJsonWriter;
import cn.edu.gpnu.platform.exchange.support.ExchangeDictionaryHelper;
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.exchange.support.ExchangeExportType;
import cn.edu.gpnu.platform.exchange.support.ExchangeExportRowBuilder;
import cn.edu.gpnu.platform.exchange.support.ExchangeImportHook;
import cn.edu.gpnu.platform.exchange.support.ExchangeImportProperties;
import cn.edu.gpnu.platform.exchange.support.ExchangeImportValidator;
import cn.edu.gpnu.platform.exchange.support.ExchangeRollbackService;
import cn.edu.gpnu.platform.exchange.support.ImportStrategy;
import cn.edu.gpnu.platform.exchange.vo.BatchVO;
import cn.edu.gpnu.platform.exchange.vo.ExchangeFile;
import cn.edu.gpnu.platform.exchange.vo.ImportErrorVO;
import cn.edu.gpnu.platform.exchange.vo.ImportPreviewRowVO;
import cn.edu.gpnu.platform.exchange.vo.ImportResultVO;
import cn.edu.gpnu.platform.exchange.vo.PrevalidateResultVO;
import cn.edu.gpnu.platform.exchange.vo.RollbackResultVO;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.SysMajor;
import cn.edu.gpnu.platform.system.entity.SysUser;
import cn.edu.gpnu.platform.system.entity.TeachingSubject;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.CollegeParentGuard;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.NotificationService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeServiceImpl implements ExchangeService {

    private static final String SCHOOL_NAME = "广东技术师范大学";
    private static final String DEFAULT_SCHOOL_CODE = "10588";
    private static final TypeReference<List<PreviewPayload>> PREVIEW_LIST_TYPE = new TypeReference<>() {
    };
    // Phase 44b（§7.3 证书导出内存）：selectCertificates 只喂 export/exportAttachments 两个导出入口
    // （无其他调用方），故直接在此设置单次导出上限，早于逐条 matchTrainingAndStudent 后过滤即拦截，
    // 避免筛选条件过宽（或未按学年/学院收窄）时把过大结果集整体驻留堆内存；未超限时行为、返回值不变。
    private static final int MAX_EXPORT_ROWS = 20000;
    private static final String STANDARD_EXPORT_PERMISSION = "exchange:export:standard";
    private static final String FULL_EXPORT_PERMISSION = "exchange:export:full";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** 已导出、已归档证书可重导且保持原状态；仅首次导出的已签发证书流转为 EXPORTED。 */
    private static final Set<String> STANDARD_REPORTABLE_STATUSES = Set.of(
            CertificateStatus.ISSUED.name(),
            CertificateStatus.EXPORTED.name(),
            CertificateStatus.ARCHIVED.name());
    private static final List<String> MATERIAL_CATEGORIES = List.of(
            "morality_teacher_ethics",
            "teacher_education_course",
            "education_internship_practice",
            "professional_ability_skill_training");

    private final ImportExportBatchMapper batchMapper;
    private final ImportErrorDetailMapper errorMapper;
    private final ImportRecordRefMapper recordRefMapper;
    private final StudentMapper studentMapper;
    private final TrainingProfileMapper trainingProfileMapper;
    private final CertificateMapper certificateMapper;
    private final CertificateService certificateService;
    private final ProcessMaterialMapper materialMapper;
    private final ExemptionRequestMapper exemptionRequestMapper;
    private final ExemptionMaterialMapper exemptionMaterialMapper;
    private final AbilityTestResultMapper abilityTestResultMapper;
    private final VideoReviewMapper videoReviewMapper;
    private final VideoReviewTaskMapper videoReviewTaskMapper;
    private final FileService fileService;
    private final SysUserMapper sysUserMapper;
    private final ExchangeDictionaryHelper dictionaryHelper;
    private final CollegeParentGuard collegeParentGuard;
    private final DataScopeService dataScopeService;
    private final NotificationService notificationService;
    private final ParamService paramService;
    private final TrainingLinkValidator trainingLinkValidator;
    private final ExchangeExcelHelper excelHelper;
    private final ExchangeImportProperties importProperties;
    private final ExchangeImportValidator importValidator;
    private final ExchangeRollbackService rollbackService;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final AuditLogService auditLogService;
    private final ExchangeImportHook exchangeImportHook;
    private final IdCardProtectionService idCardProtectionService;

    @Override
    public ExchangeFile template(ExchangeQuery query) {
        byte[] content = excelHelper.writeStandardWorkbook(List.of(), dropdowns());
        recordExportBatch("template", "template", query, 0, 0, 0, "模板下载");
        return new ExchangeFile("教育部标准导入模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrevalidateResultVO prevalidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("导入文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename) || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BizException("仅支持Excel .xlsx导入");
        }
        String batchNo = nextBatchNo("IMP");
        List<ExchangeExcelHelper.ReadRow> rows = excelHelper.readStandardRows(file);
        ImportExportBatch batch = new ImportExportBatch();
        batch.setBatchNo(batchNo);
        batch.setType("import");
        batch.setFileName(filename);
        batch.setOperatorId(UserContext.getUserIdOrSystem());
        batch.setOperateTime(LocalDateTime.now());
        batch.setTotal(rows.size());
        batch.setSuccessCount(0);
        batch.setFailCount(0);
        batch.setStatus(ExchangeBatchStatus.PREVALIDATED.name());
        batchMapper.insert(batch);

        PrevalidateResultVO result = new PrevalidateResultVO();
        result.setBatchId(batch.getId());
        result.setBatchNo(batchNo);
        result.setTotal(rows.size());
        List<PreviewPayload> previews = new ArrayList<>();
        Map<String, Long> idCardCounts = rows.stream()
                .map(item -> importValidator.canonicalIdCardNo(item.row()))
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> certNoCounts = rows.stream()
                .map(item -> trim(item.row().getCertNo()))
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        int invalidRowCount = 0;
        for (ExchangeExcelHelper.ReadRow readRow : rows) {
            List<ExchangeImportValidator.ValidationError> errors =
                    importValidator.validate(readRow.row(), idCardCounts, certNoCounts);
            if (errors.isEmpty()) {
                ImportPreviewRowVO preview = new ImportPreviewRowVO();
                preview.setRowNo(readRow.rowNo());
                preview.setRow(readRow.row());
                result.getPreviewRows().add(preview);
                previews.add(protectedPreview(readRow.rowNo(), readRow.row()));
            } else {
                invalidRowCount++;
                importProperties.assertErrorDetailBudget(result.getErrors().size(), errors.size());
                for (ExchangeImportValidator.ValidationError error : errors) {
                    ImportErrorDetail detail = toErrorDetail(batch, readRow, error);
                    errorMapper.insert(detail);
                    result.getErrors().add(toErrorVO(detail));
                }
            }
        }
        // 预留确认阶段的最坏情况：每条预览成功行至多追加一条导入失败明细。
        // 该方法处于同一事务，超限会连同已写入的批次与预校验错误一起回滚。
        importProperties.assertErrorDetailBudget(
                result.getErrors().size(), result.getPreviewRows().size());
        result.setSuccessCount(result.getPreviewRows().size());
        result.setFailCount(invalidRowCount);
        batch.setSuccessCount(result.getSuccessCount());
        batch.setFailCount(result.getFailCount());
        String previewJson = BoundedPreviewJsonWriter.write(
                objectMapper, previews, importProperties.getMaxPreviewJsonBytes());
        batch.setPreviewJson(previewJson);
        batchMapper.updateById(batch);
        return result;
    }

    @Override
    public ExchangeFile errorReport(Long batchId) {
        ImportExportBatch batch = requireBatch(batchId);
        ensureBatchAccessible(batch, "exchange:prevalidate");
        boolean sensitive = UserContext.hasPermission("exchange:export:sensitive");
        List<ImportErrorDetail> errors = errorMapper.selectList(new LambdaQueryWrapper<ImportErrorDetail>()
                .eq(ImportErrorDetail::getBatchId, batchId)
                .orderByAsc(ImportErrorDetail::getRowNo)
                .orderByAsc(ImportErrorDetail::getId));
        byte[] content = excelHelper.writeErrorWorkbook(errors.stream()
                .map(item -> new ExchangeExcelHelper.ErrorRow(batch.getBatchNo(), item.getRowNo(),
                        item.getStudentNo(), item.getStudentName(), item.getFieldName(),
                        projectErrorValue(item.getFieldName(), item.getErrorValue(), sensitive),
                        item.getErrorReason(), item.getSuggestion()))
                .toList());
        return new ExchangeFile(batch.getBatchNo() + "-异常报告.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    @Override
    public ImportResultVO confirmImport(Long batchId, ImportConfirmRequest request) {
        ImportExportBatch batch = requireBatch(batchId);
        ensureBatchAccessible(batch, "exchange:import");
        ImportStrategy strategy = ImportStrategy.of(request.getStrategy());
        List<PreviewPayload> previews = readPreviews(batch.getPreviewJson());
        if (previews.isEmpty()) {
            throw new BizException("无可导入的预校验成功行");
        }
        // Phase 42.2：原子认领 PREVALIDATED→IMPORTING，杜绝两次并发确认全量重复导入。
        // confirmImport 无 @Transactional，该 update 立即自动提交、对并发调用者立即可见；
        // 仅首个认领成功者（claimed=1）继续执行导入循环，其余 claimed=0 直接被拒、不再进入循环。
        int claimed = batchMapper.update(null, new LambdaUpdateWrapper<ImportExportBatch>()
                .eq(ImportExportBatch::getId, batchId)
                .eq(ImportExportBatch::getStatus, ExchangeBatchStatus.PREVALIDATED.name())
                .set(ImportExportBatch::getStatus, ExchangeBatchStatus.IMPORTING.name()));
        if (claimed == 0) {
            throw new BizException("当前批次不可确认导入（可能正在导入或状态已变更）");
        }
        ImportResultVO vo = new ImportResultVO();
        vo.setBatchId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setTotal(previews.size());
        int success = 0;
        int fail = 0;
        for (PreviewPayload preview : previews) {
            ImportDecision decision;
            try {
                decision = importOneInNewTransaction(batch, preview, strategy);
            } catch (ImportExecutionStoppedException e) {
                throw importStopped(e);
            } catch (Exception e) {
                log.error("确认导入行执行失败 batchId={} rowNo={}", batch.getId(), preview.rowNo(), e);
                String message = importFailureMessage(e);
                fail++;
                vo.getMessages().add("第" + preview.rowNo() + "行: " + message);
                try {
                    exchangeImportHook.beforeErrorDetailLock(batch.getId(), preview.rowNo());
                    addErrorInNewTransaction(batch, preview.rowNo(), preview.row(), message);
                    exchangeImportHook.afterErrorDetailCommitted(batch.getId(), preview.rowNo());
                } catch (ImportExecutionStoppedException stopped) {
                    throw importStopped(stopped);
                }
                continue;
            }
            exchangeImportHook.afterRowCommitted(batch.getId(), preview.rowNo());
            if (decision.success()) {
                success++;
            } else {
                fail++;
                vo.getMessages().add(decision.message());
            }
        }
        batch.setStrategy(strategy.name());
        batch.setSuccessCount(success);
        batch.setFailCount(batch.getFailCount() == null ? fail : batch.getFailCount() + fail);
        batch.setStatus(fail > 0 ? ExchangeBatchStatus.FAILED.name() : ExchangeBatchStatus.IMPORTED.name());
        // 收尾 CAS 与逐行事务、rollback 竞争同一批次行锁；命中 0 行必须按数据库真实状态失败，
        // 禁止把本地累计出的 IMPORTED/FAILED 作为成功结果返回。
        int finalized = batchMapper.update(batch, new LambdaUpdateWrapper<ImportExportBatch>()
                .eq(ImportExportBatch::getId, batch.getId())
                .eq(ImportExportBatch::getStatus, ExchangeBatchStatus.IMPORTING.name()));
        if (finalized != 1) {
            ImportExportBatch persisted = requireBatch(batch.getId());
            throw new BizException("导入已停止，批次当前状态为 " + persisted.getStatus());
        }
        vo.setSuccessCount(success);
        vo.setFailCount(fail);
        vo.setStatus(batch.getStatus());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RollbackResultVO rollback(Long batchId) {
        exchangeImportHook.beforeRollbackLock(batchId);
        // 必须作为本事务的第一条数据库语句锁住批次：等待在途行提交，并阻断后续行开始写入。
        ImportExportBatch batch = requireBatchForUpdate(batchId);
        exchangeImportHook.afterRollbackBatchLocked(batchId);
        ensureBatchAccessible(batch, "exchange:import");
        // Phase 42.2：IMPORTING 纳入可回滚态——进程在 confirmImport 中途崩溃会残留 IMPORTING，
        // 其已通过 REQUIRES_NEW 提交的部分导入行需可被回收；rollback 按 import_record_ref 追溯撤销即可。
        if (ExchangeBatchStatus.of(batch.getStatus()) != ExchangeBatchStatus.IMPORTED
                && ExchangeBatchStatus.of(batch.getStatus()) != ExchangeBatchStatus.FAILED
                && ExchangeBatchStatus.of(batch.getStatus()) != ExchangeBatchStatus.PARTIAL_ROLLBACK
                && ExchangeBatchStatus.of(batch.getStatus()) != ExchangeBatchStatus.IMPORTING) {
            throw new BizException("当前批次不可回滚");
        }
        String oldStatus = batch.getStatus();
        // MyBatis-Plus 会把 .last("FOR UPDATE") 放在 ORDER BY 前，因此锁定查询不携带排序；
        // 先锁住完整引用集，再在 Java 侧倒序补偿，语义等价且保持 MySQL 语法合法。
        List<ImportRecordRef> refs = recordRefMapper.selectList(new LambdaQueryWrapper<ImportRecordRef>()
                .eq(ImportRecordRef::getBatchId, batchId)
                .ne(ImportRecordRef::getAction, "SKIP")
                .last("FOR UPDATE"));
        refs.sort(Comparator.comparing(ImportRecordRef::getId).reversed());
        // 历史版本允许导入把既有记录跨学院迁移；持久 before_json 不受当前在线校验保护。
        // 因此必须在任何业务子行锁之前一次性预锁全部恢复目标，固定顺序为
        // batch → refs → college IDs 升序 → business child。
        RollbackResultVO vo = new RollbackResultVO();
        vo.setBatchId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        ExchangeRollbackService.RollbackSummary summary = rollbackService.compensateLocked(refs);
        int conflicts = summary.conflicts().size();
        vo.getConflicts().addAll(summary.conflicts());
        batch.setStatus(conflicts > 0 ? ExchangeBatchStatus.PARTIAL_ROLLBACK.name() : ExchangeBatchStatus.ROLLED_BACK.name());
        batch.setRemark(conflicts > 0 ? "部分记录回滚冲突（后续修改或目标学院无效），已跳过" : "已回滚");
        batchMapper.updateById(batch);
        auditLogService.record("exchange", batch.getId(), batch.getBatchNo(), "rollback",
                oldStatus, batch.getStatus(), batch.getRemark());
        vo.setRolledBackCount(summary.rolledBackCount());
        vo.setConflictCount(conflicts);
        vo.setStatus(batch.getStatus());
        return vo;
    }

    @Override
    public PageResult<BatchVO> batches(String type, String status, Integer page, Integer size) {
        LambdaQueryWrapper<ImportExportBatch> wrapper = new LambdaQueryWrapper<ImportExportBatch>()
                .orderByDesc(ImportExportBatch::getOperateTime);
        if (StringUtils.hasText(type)) {
            wrapper.eq(ImportExportBatch::getType, type.trim());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ImportExportBatch::getStatus, status.trim());
        }
        // P0-7：非全校范围只能看到本人创建的批次（批次以 operatorId 归属）。Phase 44e-rollout：真分页前，
        // 原先「selectList 取全量 → Java stream().filter 按 operatorId 过滤」必须下推进 wrapper 的 SQL
        // WHERE，否则分页拦截器生成的 COUNT/LIMIT 会先于范围过滤执行，造成 total 与他人批次一起被计入、
        // 跨用户泄露。uid 为 null 时用哨兵 -1L（不会等于任何真实 operatorId）复现原逻辑「必然不匹配」的语义。
        DataScopeContext.Scope scope = dataScopeService.resolve("exchange:import");
        boolean allSchool = scope != null && scope.allSchool();
        if (!allSchool) {
            Long uid = UserContext.getUserId();
            wrapper.eq(ImportExportBatch::getOperatorId, uid == null ? -1L : uid);
        }
        Page<ImportExportBatch> result = batchMapper.selectPage(PageQuery.of(page, size), wrapper);
        List<BatchVO> records = result.getRecords().stream().map(this::toBatchVO).toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    public ExchangeFile export(String type, ExchangeQuery query) {
        ExchangeExportType exportType = ExchangeExportType.of(type);
        if (exportType == ExchangeExportType.ERROR) {
            return exportErrors(query == null ? new ExchangeQuery() : query);
        }
        if (exportType == ExchangeExportType.ATTACHMENT_LIST) {
            return exportAttachments(query);
        }
        ExchangeQuery effectiveQuery = query == null ? new ExchangeQuery() : query;
        boolean sensitive = UserContext.hasPermission("exchange:export:sensitive");
        if (exportType == ExchangeExportType.FULL_REVIEW) {
            AuditExportContext context = loadAuditExportContext(effectiveQuery, FULL_EXPORT_PERMISSION);
            List<ExchangeStandardRow> rows = auditStandardRows(context, sensitive);
            byte[] content = excelHelper.writeTableWorkbook("完整审核表", fullReviewHeaders(),
                    fullReviewRows(context, rows));
            String fileName = "完整审核表.xlsx";
            recordExportBatch("export", exportType.name(), effectiveQuery,
                    context.rows().size(), context.rows().size(), 0, fileName);
            return new ExchangeFile(fileName, XLSX_CONTENT_TYPE, content);
        }

        Set<String> allowedStatuses = exportType == ExchangeExportType.STANDARD
                ? STANDARD_REPORTABLE_STATUSES : null;
        String permission = exportType == ExchangeExportType.STANDARD
                ? STANDARD_EXPORT_PERMISSION : FULL_EXPORT_PERMISSION;
        List<Certificate> certificates = selectCertificates(effectiveQuery, permission, allowedStatuses);
        Set<Long> studentIds = certificates.stream().map(Certificate::getStudentId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Student> students = studentsByIds(studentIds);
        Map<Long, List<TrainingProfile>> trainingByStudent = trainingByStudentIds(studentIds);
        List<ExchangeStandardRow> rows = new ArrayList<>();
        for (int i = 0; i < certificates.size(); i++) {
            rows.add(rowFromCertificate(certificates.get(i), i + 1, sensitive, students, trainingByStudent));
        }
        byte[] content;
        String fileName;
        if (exportType == ExchangeExportType.STANDARD) {
            validateStandardExportRows(rows);
            content = excelHelper.writeStandardWorkbook(rows, null);
            fileName = "标准上报表.xlsx";
            byte[] completedWorkbook = content;
            return ExchangeFile.streaming(fileName, XLSX_CONTENT_TYPE, out -> {
                out.write(completedWorkbook);
                completeStandardExport(certificates, effectiveQuery, fileName);
            });
        } else {
            content = excelHelper.writeTableWorkbook("证书获得者汇总表", certSummaryHeaders(), certSummaryRows(certificates, sensitive, students));
            fileName = "证书获得者汇总表.xlsx";
        }
        recordExportBatch("export", exportType.name(), effectiveQuery,
                certificates.size(), certificates.size(), 0, fileName);
        return new ExchangeFile(fileName, XLSX_CONTENT_TYPE, content);
    }

    @Override
    public ExchangeFile exportAttachments(ExchangeQuery query) {
        ExchangeQuery effectiveQuery = query == null ? new ExchangeQuery() : query;
        AuditExportContext context = loadAuditExportContext(effectiveQuery, STANDARD_EXPORT_PERMISSION);
        List<AttachmentExportItem> items = attachmentItems(context, effectiveQuery);
        List<List<String>> rows = items.stream().map(AttachmentExportItem::manifestRow).toList();
        byte[] workbook = excelHelper.writeTableWorkbook("附件清单表", attachmentHeaders(), rows);
        String fileName = "附件视频打包.zip";
        return ExchangeFile.streaming(fileName, "application/zip", out -> {
            writeAttachmentZip(out, workbook, items);
            recordExportBatch("export", ExchangeExportType.ATTACHMENT_LIST.name(), effectiveQuery,
                    rows.size(), rows.size(), 0, fileName);
        });
    }

    private ImportDecision importOneInNewTransaction(ImportExportBatch batch, PreviewPayload preview, ImportStrategy strategy) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            requireImportingBatchForUpdate(batch.getId());
            exchangeImportHook.afterBatchLocked(batch.getId(), preview.rowNo());
            return importOne(batch, preview, strategy);
        });
    }

    private void addErrorInNewTransaction(ImportExportBatch batch, Integer rowNo,
                                          ExchangeStandardRow row, String message) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            requireImportingBatchForUpdate(batch.getId());
            exchangeImportHook.afterErrorDetailBatchLocked(batch.getId(), rowNo);
            addError(batch, rowNo, row, "导入", "", message, "请修正后重新预校验");
            exchangeImportHook.afterErrorDetailWrite(batch.getId(), rowNo);
        });
    }

    private ImportDecision importOne(ImportExportBatch batch, PreviewPayload preview, ImportStrategy strategy) {
        ExchangeStandardRow row = preview.row();
        Long collegeId = resolveCollegeId(row);
        ensureCanImportCollege(collegeId);
        // 每行都在独立 REQUIRES_NEW 事务内；先锁父学院，再直接按业务键锁定当前聚合，
        // 避免“先读旧 ID、并发更正业务键、再按旧 ID 加锁”把更正后的值覆盖回旧快照。
        collegeParentGuard.lockExisting(collegeId, CollegeParentGuard.Operation.IMPORT_STUDENT);
        Student existingStudent = studentByNoForUpdate(row.getStudentNo());
        Student duplicateId = studentByIdCardForUpdate(row.getIdCardNo());
        Certificate existingCertificate = certificateByNoForUpdate(row.getCertNo());
        boolean duplicate = (existingStudent != null)
                || (duplicateId != null && (existingStudent == null || !duplicateId.getId().equals(existingStudent.getId())))
                || existingCertificate != null;
        if (duplicate && strategy == ImportStrategy.INSERT_ONLY) {
            throw new BizException("导入策略为新增，记录已存在");
        }
        if (duplicate && strategy == ImportStrategy.SKIP_DUPLICATE) {
            recordRef(batch, preview.rowNo(), "student", existingStudent == null ? 0L : existingStudent.getId(),
                    "SKIP", null, null, "重复数据已跳过");
            addError(batch, preview.rowNo(), row, "重复数据", row.getStudentNo(), "策略=跳过重复，记录未导入", "如需更新请选择覆盖或仅更新空字段");
            return new ImportDecision(false, "第" + preview.rowNo() + "行重复，已跳过");
        }
        Student student = existingStudent == null ? new Student() : existingStudent;
        boolean studentExisting = existingStudent != null;
        ensureCanUpdateExisting(studentExisting ? student : null, collegeId, "现有学生");
        assertCertificateAggregate(existingCertificate, student, assessmentYear(row));
        String beforeStudent = studentExisting ? snapshot(student) : null;
        applyStudent(student, row, collegeId, strategy, studentExisting);
        if (studentExisting) {
            if (studentMapper.updateById(student) != 1) {
                throw new BizException("现有学生发生并发冲突，请重新预校验");
            }
        } else {
            try {
                studentMapper.insert(student);
            } catch (DuplicateKeyException e) {
                if (violatesIndex(e, "uk_student_idcard")) {
                    throw new BizException("证件号码已存在");
                }
                throw new BizException("学生学号已存在");
            }
        }
        recordRef(batch, preview.rowNo(), "student", student.getId(), studentExisting ? "UPDATE" : "INSERT",
                beforeStudent, snapshot(student), studentExisting ? "导入更新学生" : "导入新增学生");

        TrainingProfile training = trainingByStudentYearForUpdate(student.getId(), assessmentYear(row));
        boolean trainingExisting = training != null;
        ensureCanUpdateExisting(training, student.getCollegeId(), "现有培养信息");
        if (!trainingExisting) {
            training = new TrainingProfile();
            training.setStudentId(student.getId());
        }
        String beforeTraining = trainingExisting ? snapshot(training) : null;
        applyTraining(training, row, student, strategy, trainingExisting);
        if (trainingExisting) {
            if (trainingProfileMapper.updateById(training) != 1) {
                throw new BizException("现有培养信息发生并发冲突，请重新预校验");
            }
        } else {
            trainingProfileMapper.insert(training);
        }
        recordRef(batch, preview.rowNo(), "training_profile", training.getId(), trainingExisting ? "UPDATE" : "INSERT",
                beforeTraining, snapshot(training), trainingExisting ? "导入更新培养信息" : "导入新增培养信息");

        Certificate certificate = existingCertificate;
        if (certificate == null) {
            certificate = certificateByStudentYearForUpdate(student.getId(), assessmentYear(row));
        }
        boolean certExisting = certificate != null;
        ensureCanUpdateExisting(certificate, student.getCollegeId(), "现有证书");
        // Phase 48 §7.4：导入不得静默改写终态证书。与 CertificateServiceImpl.correct() 的终态守卫一致
        //（VOIDED/REISSUED/ARCHIVED 不可更正）——否则导入经 certificateByNo/certificateByStudentYear 命中
        // 已作废/已重开/已归档证书后会覆盖其快照与状态（数据完整性漏洞）。抛 BizException 由 confirmImport
        // 循环回滚本行 REQUIRES_NEW 事务并计入逐行错误，被命中证书保持不变。
        if (certExisting) {
            CertificateStatus certStatus = CertificateStatus.of(certificate.getStatus());
            if (certStatus == CertificateStatus.VOIDED || certStatus == CertificateStatus.REISSUED
                    || certStatus == CertificateStatus.ARCHIVED) {
                throw new BizException("证书" + certStatus.label() + "，不可通过导入修改");
            }
        }
        if (!certExisting) {
            certificate = new Certificate();
            certificate.setStudentId(student.getId());
            certificate.setStatus(CertificateStatus.ISSUED.name());
            certificate.setLocked(1);
        }
        String beforeCert = certExisting ? snapshot(certificate) : null;
        applyCertificate(certificate, row, student, training, strategy, certExisting);
        if (certExisting) {
            if (certificateMapper.updateById(certificate) != 1) {
                throw new BizException("现有证书发生并发冲突，请重新预校验");
            }
        } else {
            certificateMapper.insert(certificate);
        }
        recordRef(batch, preview.rowNo(), "certificate", certificate.getId(), certExisting ? "UPDATE" : "INSERT",
                beforeCert, snapshot(certificate), certExisting ? "导入更新证书快照" : "导入新增证书快照");
        // Phase 43.2 §7.4：把导入的证书号纳入序列占用，杜绝与后续自动生成永久撞号
        // （否则 generate 反复命中已被导入占用的号、回滚又不推进序列 → “证书编号已存在，请重试”永远失败）。
        // 与本行导入同一 REQUIRES_NEW 事务：证书落库与序列推进同提交/同回滚，保持一致。
        certificateService.reserveImportedSequence(certificate.getCertNo());
        return new ImportDecision(true, "导入成功");
    }

    static void assertCertificateAggregate(Certificate certificate, Student student, String assessmentYear) {
        if (certificate == null) {
            return;
        }
        if (!Objects.equals(certificate.getStudentId(), student.getId())
                || !Objects.equals(certificate.getAssessmentYear(), assessmentYear)) {
            throw new BizException("证书编号已绑定其他学生或考核年度，不能通过导入换绑");
        }
    }

    private void applyStudent(Student student, ExchangeStandardRow row, Long collegeId, ImportStrategy strategy, boolean existing) {
        if (!existing || overwrite(strategy, student.getStudentNo())) {
            student.setStudentNo(required(row.getStudentNo(), "学号不能为空"));
        }
        if (!existing || overwrite(strategy, student.getName())) {
            student.setName(required(row.getName(), "姓名不能为空"));
        }
        if (!existing || overwrite(strategy, student.getGender())) {
            student.setGender(required(row.getGender(), "性别不能为空"));
        }
        if (!existing || overwrite(strategy, student.getIdCardType())) {
            student.setIdCardType(required(row.getIdCardType(), "证件类型不能为空"));
        }
        if (!existing || overwrite(strategy, student.getIdCardNo())) {
            String plainIdCardNo = required(row.getIdCardNo(), "证件号码不能为空");
            student.setIdCardNo(idCardProtectionService.encrypt(plainIdCardNo));
            student.setIdCardHmac(idCardProtectionService.hmac(plainIdCardNo));
        }
        if (!existing || overwrite(strategy, student.getBirthDate())) {
            student.setBirthDate(required(row.getBirthDate(), "出生日期不能为空"));
        }
        if (!existing || overwrite(strategy, student.getIdentityType())) {
            student.setIdentityType(required(row.getIdentityType(), "身份类型不能为空"));
        }
        if (!existing || overwrite(strategy, student.getSourceFull())) {
            student.setSourceFull(trim(row.getSourcePlace()));
        }
        if (existing) {
            if (!Objects.equals(student.getCollegeId(), collegeId)) {
                throw new BizException("导入行所属学院与现有学生学院不一致");
            }
        } else {
            student.setCollegeId(collegeId);
        }
        if (!StringUtils.hasText(student.getStatus())) {
            student.setStatus(StudentStatus.PASSED.name());
        }
        if (student.getLocked() == null) {
            student.setLocked(1);
        }
    }

    private void applyTraining(TrainingProfile training, ExchangeStandardRow row, Student student,
                               ImportStrategy strategy, boolean existing) {
        training.setStudentId(student.getId());
        training.setCollegeId(student.getCollegeId());
        training.setAssessmentYear(assessmentYear(row));
        setIfAllowed(existing, strategy, training::getSecondDisciplineCode, training::setSecondDisciplineCode, row.getSecondDisciplineCode());
        setIfAllowed(existing, strategy, training::getSecondDisciplineName, training::setSecondDisciplineName, row.getSecondDisciplineName());
        setIfAllowed(existing, strategy, training::getInternalMajorCode, training::setInternalMajorCode, row.getInternalMajorCode());
        setIfAllowed(existing, strategy, training::getInternalMajorName, training::setInternalMajorName, row.getInternalMajorName());
        setIfAllowed(existing, strategy, training::getEducationLevel, training::setEducationLevel, row.getEducationLevel());
        setIfAllowed(existing, strategy, training::getTrainingGoal, training::setTrainingGoal, row.getTrainingGoal());
        setIfAllowed(existing, strategy, training::getInternshipOrgMode, training::setInternshipOrgMode, row.getInternshipOrgMode());
        setIfAllowed(existing, strategy, training::getInternshipLocation, training::setInternshipLocation, row.getInternshipLocation());
        setIfAllowed(existing, strategy, training::getTeachingSegment, training::setTeachingSegment, row.getTeachingSegment());
        setIfAllowed(existing, strategy, training::getInterviewOrgMode, training::setInterviewOrgMode, row.getInterviewOrgMode());

        boolean subjectGroupEmpty = training.getTeachingSubjectId() == null
                && !StringUtils.hasText(training.getTeachingSubjectCode())
                && !StringUtils.hasText(training.getTeachingSubjectName());
        boolean replaceSubject = !existing || strategy == ImportStrategy.OVERWRITE
                || strategy == ImportStrategy.INSERT_ONLY
                || (strategy == ImportStrategy.UPDATE_EMPTY && subjectGroupEmpty);
        String finalSubjectCode = replaceSubject ? trim(row.getTeachingSubject()) : training.getTeachingSubjectCode();
        TrainingProfileSaveRequest finalRequest = trainingRequestForFinal(training, student, finalSubjectCode);
        SysMajor finalMajor = "education_master".equals(student.getIdentityType())
                ? null
                : majorByCodeName(student.getCollegeId(), training.getInternalMajorCode(), training.getInternalMajorName());
        TeachingSubject validatedSubject = trainingLinkValidator.validate(finalRequest, finalMajor);
        if (replaceSubject) {
            training.setTeachingSubjectId(validatedSubject.getId());
            training.setTeachingSubjectCode(validatedSubject.getSubjectCode());
            training.setTeachingSubjectName(validatedSubject.getSubjectName());
        }
        if (!StringUtils.hasText(training.getStatus())) {
            training.setStatus(TrainingStatus.PASSED.name());
        }
        if (training.getLocked() == null) {
            training.setLocked(1);
        }
    }

    private TrainingProfileSaveRequest trainingRequestForFinal(
            TrainingProfile training, Student student, String subjectCode) {
        TrainingProfileSaveRequest request = new TrainingProfileSaveRequest();
        request.setStudentId(student.getId());
        request.setCollegeId(student.getCollegeId());
        request.setAssessmentYear(training.getAssessmentYear());
        request.setSecondDisciplineCode(training.getSecondDisciplineCode());
        request.setSecondDisciplineName(training.getSecondDisciplineName());
        request.setInternalMajorCode(training.getInternalMajorCode());
        request.setInternalMajorName(training.getInternalMajorName());
        request.setEducationLevel(training.getEducationLevel());
        request.setTrainingGoal(training.getTrainingGoal());
        request.setInternshipOrgMode(training.getInternshipOrgMode());
        request.setInternshipLocation(training.getInternshipLocation());
        request.setTeachingSegment(training.getTeachingSegment());
        request.setTeachingSubjectCode(subjectCode);
        request.setInterviewOrgMode(training.getInterviewOrgMode());
        request.setAbilityTestConclusion(training.getAbilityTestConclusion());
        return request;
    }

    private void applyCertificate(Certificate certificate, ExchangeStandardRow row, Student student,
                                  TrainingProfile training, ImportStrategy strategy, boolean existing) {
        if (!existing) {
            certificate.setStudentId(student.getId());
            certificate.setCollegeId(student.getCollegeId());
            certificate.setAssessmentYear(assessmentYear(row));
        }
        setIfAllowed(existing, strategy, certificate::getCertNo, certificate::setCertNo, row.getCertNo());
        certificate.setStudentNo(student.getStudentNo());
        certificate.setStudentName(student.getName());
        certificate.setIdCardType(student.getIdCardType());
        String plainIdCardNo = idCardProtectionService.decrypt(student.getIdCardNo());
        certificate.setIdCardNo(idCardProtectionService.encrypt(plainIdCardNo));
        certificate.setIdCardHmac(idCardProtectionService.hmac(plainIdCardNo));
        certificate.setEducationLevel(training.getEducationLevel());
        certificate.setTrainingGoal(training.getTrainingGoal());
        certificate.setTeachingSegment(training.getTeachingSegment());
        certificate.setTeachingSubjectCode(training.getTeachingSubjectCode());
        certificate.setTeachingSubjectName(training.getTeachingSubjectName());
        setIfAllowed(existing, strategy, certificate::getIssuer, certificate::setIssuer, row.getIssuer());
        setIfAllowed(existing, strategy, certificate::getValidUntil, certificate::setValidUntil, row.getValidUntil());
        if (!StringUtils.hasText(certificate.getStatus())) {
            certificate.setStatus(CertificateStatus.ISSUED.name());
        }
        // Phase 43.2 §7.4：导入的「已签发」证书必须有签发日期（否则汇总表/证书详情的签发日期为空、
        // 且无补设路径）。缺失时由证书年度 + 有效期上/下半年推导一个与系统 validUntil 规则自洽的签发日期
        // （对该日期再套 validUntil 规则可复现导入的有效期）；已有签发日期则不覆盖。
        if (CertificateStatus.ISSUED.name().equals(certificate.getStatus())
                && !StringUtils.hasText(certificate.getIssueDate())) {
            certificate.setIssueDate(importedIssueDate(certificate.getAssessmentYear(), certificate.getValidUntil()));
        }
        if (certificate.getLocked() == null) {
            certificate.setLocked(1);
        }
    }

    private String importedIssueDate(String assessmentYear, String validUntil) {
        Integer issueYear = null;
        if (StringUtils.hasText(assessmentYear) && assessmentYear.trim().matches("^\\d{4}$")) {
            issueYear = Integer.parseInt(assessmentYear.trim());
        } else if (StringUtils.hasText(validUntil) && validUntil.trim().length() >= 4
                && validUntil.trim().substring(0, 4).matches("^\\d{4}$")) {
            // 有效期经校验为 证书年度+3 的上/下半年；反推证书年度。
            issueYear = Integer.parseInt(validUntil.trim().substring(0, 4)) - 3;
        }
        if (issueYear == null) {
            return null;
        }
        boolean secondHalf = StringUtils.hasText(validUntil) && validUntil.trim().endsWith("12/31");
        return issueYear + (secondHalf ? "/12/31" : "/6/30");
    }

    private List<Certificate> selectCertificates(
            ExchangeQuery query, String permission, Set<String> allowedStatuses) {
        ExchangeQuery q = query == null ? new ExchangeQuery() : query;
        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<Certificate>()
                .orderByAsc(Certificate::getAssessmentYear)
                .orderByAsc(Certificate::getStudentNo);
        applyCertificateScope(wrapper, dataScopeService.resolve(permission));
        if (StringUtils.hasText(q.getAssessmentYear())) {
            wrapper.eq(Certificate::getAssessmentYear, q.getAssessmentYear().trim());
        }
        if (q.getCollegeId() != null) {
            wrapper.eq(Certificate::getCollegeId, q.getCollegeId());
        }
        if (StringUtils.hasText(q.getCertStatus())) {
            wrapper.eq(Certificate::getStatus, q.getCertStatus().trim());
        }
        if (allowedStatuses != null) {
            wrapper.in(Certificate::getStatus, allowedStatuses);
        }
        if (StringUtils.hasText(q.getTrainingGoal())) {
            wrapper.eq(Certificate::getTrainingGoal, q.getTrainingGoal().trim());
        }
        if (StringUtils.hasText(q.getTeachingSegment())) {
            wrapper.eq(Certificate::getTeachingSegment, q.getTeachingSegment().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String keyword = q.getKeyword().trim();
            String normalizedIdCardKeyword = normalizeIdCardLookup(keyword);
            wrapper.and(w -> w.like(Certificate::getStudentNo, keyword)
                    .or()
                    .like(Certificate::getStudentName, keyword)
                    .or()
                    .like(Certificate::getCertNo, keyword)
                    .or()
                    .eq(Certificate::getIdCardHmac,
                            idCardProtectionService.hmac(normalizedIdCardKeyword)));
        }
        List<Certificate> certificates = certificateMapper.selectList(wrapper);
        if (certificates.size() > MAX_EXPORT_ROWS) {
            throw new BizException("本次筛选命中 " + certificates.size() + " 条证书记录，超过单次导出上限 "
                    + MAX_EXPORT_ROWS + " 条，请按学年/学院等条件缩小筛选范围后重试");
        }
        if (!StringUtils.hasText(q.getInternalMajorCode()) && !StringUtils.hasText(q.getClassName())
                && !StringUtils.hasText(q.getAuditStatus())) {
            return certificates;
        }
        return certificates.stream().filter(cert -> matchTrainingAndStudent(cert, q)).toList();
    }

    private void applyCertificateScope(
            LambdaQueryWrapper<Certificate> wrapper, DataScopeContext.Scope scope) {
        if (scope != null && scope.allSchool()) {
            return;
        }
        if (scope != null && scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && !scope.getCollegeIds().isEmpty()) {
            wrapper.in(Certificate::getCollegeId, scope.getCollegeIds());
            return;
        }
        if (scope != null && scope.getScopeType() == DataScopeContext.ScopeType.SELF
                && scope.getStudentId() != null) {
            wrapper.eq(Certificate::getStudentId, scope.getStudentId());
            return;
        }
        wrapper.eq(Certificate::getId, -1L);
    }

    private boolean matchTrainingAndStudent(Certificate cert, ExchangeQuery q) {
        TrainingProfile training = trainingByStudentYear(cert.getStudentId(), cert.getAssessmentYear());
        Student student = studentMapper.selectById(cert.getStudentId());
        if (StringUtils.hasText(q.getInternalMajorCode())
                && (training == null || !q.getInternalMajorCode().trim().equals(training.getInternalMajorCode()))) {
            return false;
        }
        if (StringUtils.hasText(q.getAuditStatus())
                && (training == null || !q.getAuditStatus().trim().equals(training.getStatus()))) {
            return false;
        }
        return !StringUtils.hasText(q.getClassName())
                || (student != null && q.getClassName().trim().equals(student.getClassName()));
    }

    private ExchangeStandardRow rowFromCertificate(Certificate cert, int sequence, boolean sensitive,
            Map<Long, Student> students, Map<Long, List<TrainingProfile>> trainingByStudent) {
        Student student = students.get(cert.getStudentId());
        TrainingProfile training = trainingFor(trainingByStudent, cert.getStudentId(), cert.getAssessmentYear());
        String schoolCode = paramService.getString("cert.school.code", DEFAULT_SCHOOL_CODE);
        SysDictItem school = dictItem("school", schoolCode);
        String plainIdCardNo = idCardProtectionService.decrypt(cert.getIdCardNo());
        // Phase 43.2 §7.4：备注列承载「学院ID」，与导入端 resolveCollegeId 读备注解析学院ID 对齐，
        // 保证 导出→导入 学院标识无损往返。旧实现把证书状态写入备注（与导入语义冲突：
        // 重导出文件时该列被当作学院ID parseLong，破坏学院识别）；证书状态另有汇总表/完整审核表的
        // 「证书状态」专列承载，不再复用备注一列表达两种含义。
        return ExchangeExportRowBuilder.standardRow(cert, student, training, sequence, sensitive,
                schoolCode, school == null ? SCHOOL_NAME : school.getItemValue(), plainIdCardNo);
    }

    private ExchangeFile exportErrors(ExchangeQuery query) {
        if (query.getBatchId() == null) {
            throw new BizException("导出异常数据必须指定导入批次");
        }
        ImportExportBatch sourceBatch = requireBatch(query.getBatchId());
        if (!"import".equalsIgnoreCase(sourceBatch.getType())) {
            throw new BizException("仅支持导出导入批次的异常数据");
        }
        ensureBatchAccessible(sourceBatch, "exchange:export:full");
        LambdaQueryWrapper<ImportErrorDetail> wrapper = new LambdaQueryWrapper<ImportErrorDetail>()
                .eq(ImportErrorDetail::getBatchId, sourceBatch.getId())
                .orderByDesc(ImportErrorDetail::getCreatedAt)
                .orderByAsc(ImportErrorDetail::getBatchNo)
                .orderByAsc(ImportErrorDetail::getRowNo);
        String fileName = sourceBatch.getBatchNo() + "-异常数据表.xlsx";
        List<ImportErrorDetail> errors = errorMapper.selectList(wrapper);
        boolean sensitive = UserContext.hasPermission("exchange:export:sensitive");
        byte[] content = excelHelper.writeErrorWorkbook(errors.stream()
                .map(item -> new ExchangeExcelHelper.ErrorRow(item.getBatchNo(), item.getRowNo(), item.getStudentNo(),
                        item.getStudentName(), item.getFieldName(),
                        projectErrorValue(item.getFieldName(), item.getErrorValue(), sensitive),
                        item.getErrorReason(), item.getSuggestion()))
                .toList());
        recordExportBatch("export", ExchangeExportType.ERROR.name(), query,
                errors.size(), errors.size(), 0, fileName);
        return new ExchangeFile(fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    private AuditExportContext loadAuditExportContext(ExchangeQuery query, String permission) {
        List<Student> students = selectAuthorizedStudents(query, permission);
        if (students.isEmpty()) {
            return AuditExportContext.empty();
        }
        Set<Long> studentIds = students.stream().map(Student::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<TrainingProfile> trainings = selectAuditTrainings(studentIds, query);
        List<Certificate> certificates = selectAuditCertificates(studentIds, query);
        List<ProcessMaterial> materials = selectAuditMaterials(studentIds, query);
        List<ExemptionRequest> exemptions = selectAuditExemptions(studentIds, query);
        List<AbilityTestResult> abilities = selectAuditAbilities(studentIds, query);
        List<VideoReview> videos = selectAuditVideos(studentIds, query);

        Map<Long, Student> studentMap = students.stream()
                .collect(Collectors.toMap(Student::getId, Function.identity()));
        Map<StudentYearKey, TrainingProfile> trainingMap = latestByKey(
                trainings, item -> key(item.getStudentId(), item.getAssessmentYear()));
        Map<StudentYearKey, List<Certificate>> certificatesByKey = certificates.stream()
                .filter(item -> key(item.getStudentId(), item.getAssessmentYear()) != null)
                .collect(Collectors.groupingBy(
                        item -> key(item.getStudentId(), item.getAssessmentYear()), LinkedHashMap::new, Collectors.toList()));
        Map<StudentYearKey, List<ProcessMaterial>> materialsByKey = materials.stream()
                .filter(item -> key(item.getStudentId(), item.getAssessmentYear()) != null)
                .collect(Collectors.groupingBy(
                        item -> key(item.getStudentId(), item.getAssessmentYear()), LinkedHashMap::new, Collectors.toList()));
        Map<StudentYearKey, List<ExemptionRequest>> exemptionsByKey = exemptions.stream()
                .filter(item -> key(item.getStudentId(), item.getAssessmentYear()) != null)
                .collect(Collectors.groupingBy(
                        item -> key(item.getStudentId(), item.getAssessmentYear()), LinkedHashMap::new, Collectors.toList()));
        Map<StudentYearKey, AbilityTestResult> abilityMap = latestByKey(
                abilities, item -> key(item.getStudentId(), item.getAssessmentYear()));
        Map<StudentYearKey, VideoReview> videoMap = latestByKey(
                videos, item -> key(item.getStudentId(), item.getAssessmentYear()));

        Set<StudentYearKey> annualKeys = new LinkedHashSet<>();
        annualKeys.addAll(trainingMap.keySet());
        annualKeys.addAll(certificatesByKey.keySet());
        annualKeys.addAll(materialsByKey.keySet());
        annualKeys.addAll(exemptionsByKey.keySet());
        annualKeys.addAll(abilityMap.keySet());
        annualKeys.addAll(videoMap.keySet());

        List<AuditExportRow> rows = annualKeys.stream()
                .filter(Objects::nonNull)
                .filter(item -> matchesAuditQuery(item, studentMap.get(item.studentId()),
                        trainingMap.get(item), certificatesByKey.getOrDefault(item, List.of()), query))
                .sorted(Comparator.comparing(StudentYearKey::assessmentYear)
                        .thenComparing(item -> nvl(studentMap.get(item.studentId()).getStudentNo())))
                .map(item -> new AuditExportRow(item, studentMap.get(item.studentId()), trainingMap.get(item),
                        selectAuditCertificate(certificatesByKey.getOrDefault(item, List.of()), query.getCertStatus())))
                .toList();
        if (rows.size() > MAX_EXPORT_ROWS) {
            throw new BizException("本次筛选命中 " + rows.size() + " 条年度学生记录，超过单次导出上限 "
                    + MAX_EXPORT_ROWS + " 条，请按学年/学院等条件缩小筛选范围后重试");
        }

        Set<Long> videoIds = rows.stream().map(AuditExportRow::key).map(videoMap::get)
                .filter(Objects::nonNull).map(VideoReview::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<VideoReviewTask>> tasksByVideo = videoIds.isEmpty() ? Map.of()
                : videoReviewTaskMapper.selectList(new LambdaQueryWrapper<VideoReviewTask>()
                        .in(VideoReviewTask::getVideoReviewId, videoIds)
                        .orderByAsc(VideoReviewTask::getId)).stream()
                .collect(Collectors.groupingBy(VideoReviewTask::getVideoReviewId));
        Map<Long, String> reviewerNames = reviewerNames(rows, materialsByKey, exemptionsByKey,
                abilityMap, videoMap, tasksByVideo);
        return new AuditExportContext(rows, materialsByKey, exemptionsByKey, abilityMap,
                videoMap, tasksByVideo, reviewerNames);
    }

    private List<Student> selectAuthorizedStudents(ExchangeQuery query, String permission) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<Student>()
                .orderByAsc(Student::getStudentNo);
        applyStudentScope(wrapper, dataScopeService.resolve(permission));
        if (query.getCollegeId() != null) {
            wrapper.eq(Student::getCollegeId, query.getCollegeId());
        }
        if (StringUtils.hasText(query.getClassName())) {
            wrapper.eq(Student::getClassName, query.getClassName().trim());
        }
        return studentMapper.selectList(wrapper);
    }

    private void applyStudentScope(LambdaQueryWrapper<Student> wrapper, DataScopeContext.Scope scope) {
        if (scope != null && scope.allSchool()) {
            return;
        }
        if (scope != null && scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && !scope.getCollegeIds().isEmpty()) {
            wrapper.in(Student::getCollegeId, scope.getCollegeIds());
            return;
        }
        if (scope != null && scope.getScopeType() == DataScopeContext.ScopeType.SELF
                && scope.getStudentId() != null) {
            wrapper.eq(Student::getId, scope.getStudentId());
            return;
        }
        wrapper.eq(Student::getId, -1L);
    }

    private List<TrainingProfile> selectAuditTrainings(Set<Long> ids, ExchangeQuery query) {
        LambdaQueryWrapper<TrainingProfile> wrapper = new LambdaQueryWrapper<TrainingProfile>()
                .in(TrainingProfile::getStudentId, ids);
        if (StringUtils.hasText(query.getAssessmentYear())) {
            wrapper.eq(TrainingProfile::getAssessmentYear, query.getAssessmentYear().trim());
        }
        return trainingProfileMapper.selectList(wrapper);
    }

    private List<Certificate> selectAuditCertificates(Set<Long> ids, ExchangeQuery query) {
        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<Certificate>()
                .in(Certificate::getStudentId, ids);
        if (StringUtils.hasText(query.getAssessmentYear())) {
            wrapper.eq(Certificate::getAssessmentYear, query.getAssessmentYear().trim());
        }
        return certificateMapper.selectList(wrapper);
    }

    private List<ProcessMaterial> selectAuditMaterials(Set<Long> ids, ExchangeQuery query) {
        LambdaQueryWrapper<ProcessMaterial> wrapper = new LambdaQueryWrapper<ProcessMaterial>()
                .in(ProcessMaterial::getStudentId, ids)
                .orderByAsc(ProcessMaterial::getStudentId)
                .orderByAsc(ProcessMaterial::getCategory)
                .orderByAsc(ProcessMaterial::getId);
        if (StringUtils.hasText(query.getAssessmentYear())) {
            wrapper.eq(ProcessMaterial::getAssessmentYear, query.getAssessmentYear().trim());
        }
        return materialMapper.selectList(wrapper);
    }

    private List<ExemptionRequest> selectAuditExemptions(Set<Long> ids, ExchangeQuery query) {
        LambdaQueryWrapper<ExemptionRequest> wrapper = new LambdaQueryWrapper<ExemptionRequest>()
                .in(ExemptionRequest::getStudentId, ids)
                .orderByAsc(ExemptionRequest::getStudentId)
                .orderByAsc(ExemptionRequest::getSubject);
        if (StringUtils.hasText(query.getAssessmentYear())) {
            wrapper.eq(ExemptionRequest::getAssessmentYear, query.getAssessmentYear().trim());
        }
        return exemptionRequestMapper.selectList(wrapper);
    }

    private List<AbilityTestResult> selectAuditAbilities(Set<Long> ids, ExchangeQuery query) {
        LambdaQueryWrapper<AbilityTestResult> wrapper = new LambdaQueryWrapper<AbilityTestResult>()
                .in(AbilityTestResult::getStudentId, ids);
        if (StringUtils.hasText(query.getAssessmentYear())) {
            wrapper.eq(AbilityTestResult::getAssessmentYear, query.getAssessmentYear().trim());
        }
        return abilityTestResultMapper.selectList(wrapper);
    }

    private List<VideoReview> selectAuditVideos(Set<Long> ids, ExchangeQuery query) {
        LambdaQueryWrapper<VideoReview> wrapper = new LambdaQueryWrapper<VideoReview>()
                .in(VideoReview::getStudentId, ids);
        if (StringUtils.hasText(query.getAssessmentYear())) {
            wrapper.eq(VideoReview::getAssessmentYear, query.getAssessmentYear().trim());
        }
        return videoReviewMapper.selectList(wrapper);
    }

    private <T extends cn.edu.gpnu.platform.common.entity.BaseEntity> Map<StudentYearKey, T> latestByKey(
            List<T> values, Function<T, StudentYearKey> keyFunction) {
        return values.stream().filter(item -> keyFunction.apply(item) != null)
                .collect(Collectors.toMap(keyFunction, Function.identity(),
                        (left, right) -> left.getId() > right.getId() ? left : right,
                        LinkedHashMap::new));
    }

    private StudentYearKey key(Long studentId, String assessmentYear) {
        return studentId == null || !StringUtils.hasText(assessmentYear)
                ? null : new StudentYearKey(studentId, assessmentYear.trim());
    }

    private boolean matchesAuditQuery(StudentYearKey key, Student student, TrainingProfile training,
                                      List<Certificate> certificates, ExchangeQuery query) {
        if (student == null) {
            return false;
        }
        if (StringUtils.hasText(query.getInternalMajorCode())
                && (training == null || !query.getInternalMajorCode().trim().equals(training.getInternalMajorCode()))) {
            return false;
        }
        if (StringUtils.hasText(query.getAuditStatus())
                && (training == null || !query.getAuditStatus().trim().equals(training.getStatus()))) {
            return false;
        }
        Certificate certificate = selectAuditCertificate(certificates, query.getCertStatus());
        if (StringUtils.hasText(query.getCertStatus()) && certificate == null) {
            return false;
        }
        String trainingGoal = certificate == null && training != null
                ? training.getTrainingGoal() : certificate == null ? null : certificate.getTrainingGoal();
        if (StringUtils.hasText(query.getTrainingGoal())
                && !query.getTrainingGoal().trim().equals(trainingGoal)) {
            return false;
        }
        String segment = certificate == null && training != null
                ? training.getTeachingSegment() : certificate == null ? null : certificate.getTeachingSegment();
        if (StringUtils.hasText(query.getTeachingSegment())
                && !query.getTeachingSegment().trim().equals(segment)) {
            return false;
        }
        if (!StringUtils.hasText(query.getKeyword())) {
            return true;
        }
        String keyword = query.getKeyword().trim();
        String lowered = keyword.toLowerCase(Locale.ROOT);
        boolean studentMatch = containsIgnoreCase(student.getStudentNo(), lowered)
                || containsIgnoreCase(student.getName(), lowered)
                || Objects.equals(student.getIdCardHmac(),
                idCardProtectionService.hmac(normalizeIdCardLookup(keyword)));
        return studentMatch || certificates.stream().anyMatch(item -> containsIgnoreCase(item.getCertNo(), lowered));
    }

    private boolean containsIgnoreCase(String value, String loweredKeyword) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(loweredKeyword);
    }

    private Certificate selectAuditCertificate(List<Certificate> certificates, String requestedStatus) {
        if (certificates == null || certificates.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(requestedStatus)) {
            return certificates.stream().filter(item -> requestedStatus.trim().equals(item.getStatus()))
                    .max(Comparator.comparing(Certificate::getId)).orElse(null);
        }
        return certificates.stream()
                .filter(item -> !CertificateStatus.VOIDED.name().equals(item.getStatus())
                        && !CertificateStatus.REISSUED.name().equals(item.getStatus()))
                .max(Comparator.comparing(Certificate::getId))
                .orElseGet(() -> certificates.stream().max(Comparator.comparing(Certificate::getId)).orElse(null));
    }

    private List<ExchangeStandardRow> auditStandardRows(AuditExportContext context, boolean sensitive) {
        String schoolCode = paramService.getString("cert.school.code", DEFAULT_SCHOOL_CODE);
        SysDictItem school = dictItem("school", schoolCode);
        String schoolName = school == null ? SCHOOL_NAME : school.getItemValue();
        List<ExchangeStandardRow> rows = new ArrayList<>();
        for (int index = 0; index < context.rows().size(); index++) {
            AuditExportRow item = context.rows().get(index);
            String plainIdCardNo = idCardProtectionService.decrypt(item.student().getIdCardNo());
            rows.add(ExchangeExportRowBuilder.standardRow(item.certificate(), item.student(), item.training(),
                    index + 1, sensitive, schoolCode, schoolName, plainIdCardNo));
        }
        return rows;
    }

    private Map<Long, String> reviewerNames(
            List<AuditExportRow> rows,
            Map<StudentYearKey, List<ProcessMaterial>> materials,
            Map<StudentYearKey, List<ExemptionRequest>> exemptions,
            Map<StudentYearKey, AbilityTestResult> abilities,
            Map<StudentYearKey, VideoReview> videos,
            Map<Long, List<VideoReviewTask>> tasks) {
        Set<Long> ids = new LinkedHashSet<>();
        for (AuditExportRow row : rows) {
            addIds(ids, row.student().getFirstReviewerId(), row.student().getSecondReviewerId());
            if (row.training() != null) {
                addIds(ids, row.training().getFirstReviewerId(), row.training().getSecondReviewerId());
            }
            materials.getOrDefault(row.key(), List.of()).forEach(item ->
                    addIds(ids, item.getFirstReviewerId(), item.getSecondReviewerId()));
            exemptions.getOrDefault(row.key(), List.of()).forEach(item ->
                    addIds(ids, item.getFirstReviewerId(), item.getSecondReviewerId()));
            AbilityTestResult ability = abilities.get(row.key());
            if (ability != null) {
                addIds(ids, ability.getUpdatedBy());
            }
            VideoReview video = videos.get(row.key());
            if (video != null) {
                addIds(ids, video.getArbitrateReviewer(), video.getConfirmedBy());
                tasks.getOrDefault(video.getId(), List.of()).forEach(item -> addIds(ids, item.getReviewerId()));
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(SysUser::getId,
                item -> StringUtils.hasText(item.getRealName()) ? item.getRealName()
                        : StringUtils.hasText(item.getUsername()) ? item.getUsername() : String.valueOf(item.getId())));
    }

    private void addIds(Set<Long> target, Long... values) {
        for (Long value : values) {
            if (value != null) {
                target.add(value);
            }
        }
    }

    private String reviewerName(AuditExportContext context, Long reviewerId) {
        if (reviewerId == null) {
            return "";
        }
        return context.reviewerNames().getOrDefault(reviewerId, String.valueOf(reviewerId));
    }

    private String materialCategoryLabel(String category) {
        SysDictItem item = dictItem("material_category", category);
        return item == null ? nvl(category) : nvl(item.getItemValue());
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private void validateStandardExportRows(List<ExchangeStandardRow> rows) {
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            ExchangeStandardRow row = rows.get(rowIndex);
            for (ExchangeColumn column : ExchangeColumn.ALL) {
                if (column.required() && !StringUtils.hasText(column.value(row))) {
                    throw new BizException("标准上报表第" + (rowIndex + 2) + "行“"
                            + column.header() + "”不能为空");
                }
            }
            if (!"education_master".equals(trim(row.getIdentityType()))) {
                requireStandardExportText(rowIndex, row.getInternalMajorCode(), "校内专业代码");
                requireStandardExportText(rowIndex, row.getInternalMajorName(), "校内专业名称");
            }
        }
    }

    private void requireStandardExportText(int rowIndex, String value, String header) {
        if (!StringUtils.hasText(value)) {
            throw new BizException("标准上报表第" + (rowIndex + 2) + "行“" + header + "”不能为空");
        }
    }

    private void completeStandardExport(
            List<Certificate> certificates, ExchangeQuery query, String fileName) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            ImportExportBatch batch = recordExportBatch("export", ExchangeExportType.STANDARD.name(), query,
                    certificates.size(), certificates.size(), 0, fileName);
            for (Certificate certificate : certificates) {
                String oldStatus = certificate.getStatus();
                String newStatus = oldStatus;
                if (CertificateStatus.ISSUED.name().equals(oldStatus)) {
                    Certificate patch = new Certificate();
                    patch.setStatus(CertificateStatus.EXPORTED.name());
                    patch.setLocked(1);
                    if (certificateMapper.update(patch, new LambdaUpdateWrapper<Certificate>()
                            .eq(Certificate::getId, certificate.getId())
                            .eq(Certificate::getStatus, CertificateStatus.ISSUED.name())) != 1) {
                        throw new BizException("证书状态已变化，请重新导出");
                    }
                    newStatus = CertificateStatus.EXPORTED.name();
                }
                auditLogService.record("certificate", certificate.getId(),
                        "certificate:" + certificate.getId() + ":export-batch:" + batch.getId(),
                        "export", oldStatus, newStatus, "标准上报导出批次：" + batch.getBatchNo());
            }
        });
    }

    private List<String> fullReviewHeaders() {
        List<String> headers = new ArrayList<>(ExchangeColumn.ALL.stream().map(ExchangeColumn::header).toList());
        headers.addAll(List.of(
                "基本信息初审人", "基本信息初审时间", "基本信息初审状态",
                "基本信息复审人", "基本信息复审时间", "基本信息复审状态",
                "培养信息初审人", "培养信息初审时间", "培养信息初审状态",
                "培养信息复审人", "培养信息复审时间", "培养信息复审状态"));
        for (String category : MATERIAL_CATEGORIES) {
            String label = materialCategoryLabel(category);
            headers.addAll(List.of(label + "状态", label + "初审人", label + "初审时间",
                    label + "复审人", label + "复审时间"));
        }
        headers.addAll(List.of(
                "免考科目", "免考状态", "免考初审人", "免考初审时间", "免考复审人", "免考复审时间",
                "视频教师1分", "视频教师2分", "视频复评分", "视频终分", "视频结论", "视频确认人", "视频确认时间",
                "测试成绩", "测试结论", "测试确认人",
                "证书状态", "证书编号", "签发人", "签发日期", "有效期限"));
        return headers;
    }

    private List<List<String>> fullReviewRows(
            AuditExportContext context, List<ExchangeStandardRow> standardRows) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < context.rows().size(); i++) {
            AuditExportRow auditRow = context.rows().get(i);
            ExchangeStandardRow standard = standardRows.get(i);
            List<String> values = ExchangeColumn.ALL.stream().map(col -> nvl(col.value(standard))).collect(Collectors.toCollection(ArrayList::new));
            Student student = auditRow.student();
            TrainingProfile training = auditRow.training();
            values.add(reviewerName(context, student.getFirstReviewerId()));
            values.add(time(student.getFirstReviewTime()));
            values.add(firstReviewStatus(student.getStatus(), student.getFirstReviewTime(), student.getSecondReviewTime()));
            values.add(reviewerName(context, student.getSecondReviewerId()));
            values.add(time(student.getSecondReviewTime()));
            values.add(secondReviewStatus(student.getStatus(), student.getSecondReviewTime()));
            values.add(training == null ? "" : reviewerName(context, training.getFirstReviewerId()));
            values.add(training == null ? "" : time(training.getFirstReviewTime()));
            values.add(training == null ? "" : firstReviewStatus(
                    training.getStatus(), training.getFirstReviewTime(), training.getSecondReviewTime()));
            values.add(training == null ? "" : reviewerName(context, training.getSecondReviewerId()));
            values.add(training == null ? "" : time(training.getSecondReviewTime()));
            values.add(training == null ? "" : secondReviewStatus(training.getStatus(), training.getSecondReviewTime()));
            Map<String, ProcessMaterial> effectiveMaterials = context.materials()
                    .getOrDefault(auditRow.key(), List.of()).stream()
                    .collect(Collectors.toMap(ProcessMaterial::getCategory, Function.identity(),
                            (left, right) -> left.getId() > right.getId() ? left : right,
                            LinkedHashMap::new));
            for (String category : MATERIAL_CATEGORIES) {
                ProcessMaterial material = effectiveMaterials.get(category);
                values.add(material == null ? "" : nvl(material.getStatus()));
                values.add(material == null ? "" : reviewerName(context, material.getFirstReviewerId()));
                values.add(material == null ? "" : time(material.getFirstReviewTime()));
                values.add(material == null ? "" : reviewerName(context, material.getSecondReviewerId()));
                values.add(material == null ? "" : time(material.getSecondReviewTime()));
            }
            appendExemptionColumns(values, context, auditRow.key());
            VideoReview video = context.videos().get(auditRow.key());
            List<VideoReviewTask> tasks = video == null
                    ? List.of() : context.videoTasks().getOrDefault(video.getId(), List.of());
            List<VideoReviewTask> reviewers = tasks.stream()
                    .filter(item -> "REVIEWER".equals(item.getReviewerRole()))
                    .sorted(Comparator.comparing(VideoReviewTask::getId)).toList();
            VideoReviewTask third = tasks.stream()
                    .filter(item -> "THIRD_EXPERT".equals(item.getReviewerRole()))
                    .max(Comparator.comparing(VideoReviewTask::getId)).orElse(null);
            values.add(taskScore(reviewers, 0));
            values.add(taskScore(reviewers, 1));
            values.add(third == null || third.getScore() == null ? "" : String.valueOf(third.getScore()));
            values.add(video == null || video.getFinalScore() == null ? "" : String.valueOf(video.getFinalScore()));
            values.add(video == null ? "" : nvl(video.getFinalConclusion()));
            values.add(video == null ? "" : reviewerName(context, video.getConfirmedBy()));
            values.add(video == null ? "" : time(video.getConfirmedAt()));
            AbilityTestResult ability = context.abilities().get(auditRow.key());
            values.add(ability == null ? "" : nvl(ability.getScore()));
            values.add(ability == null ? "" : nvl(ability.getConclusion()));
            values.add(ability == null ? "" : reviewerName(context, ability.getUpdatedBy()));
            Certificate cert = auditRow.certificate();
            values.add(cert == null ? "" : nvl(cert.getStatus()));
            values.add(cert == null ? "" : nvl(cert.getCertNo()));
            values.add(cert == null ? "" : nvl(cert.getIssuer()));
            values.add(cert == null ? "" : nvl(cert.getIssueDate()));
            values.add(cert == null ? "" : nvl(cert.getValidUntil()));
            rows.add(values);
        }
        return rows;
    }

    private void appendExemptionColumns(
            List<String> values, AuditExportContext context, StudentYearKey key) {
        List<ExemptionRequest> exemptions = context.exemptions().getOrDefault(key, List.of()).stream()
                .sorted(Comparator.comparing(ExemptionRequest::getSubject)).toList();
        values.add(joinExemptions(exemptions, item -> StringUtils.hasText(item.getSubjectLabel())
                ? item.getSubjectLabel() : item.getSubject()));
        values.add(joinExemptions(exemptions, ExemptionRequest::getFinalStatus));
        values.add(joinExemptions(exemptions, item -> reviewerName(context, item.getFirstReviewerId())));
        values.add(joinExemptions(exemptions, item -> time(item.getFirstReviewTime())));
        values.add(joinExemptions(exemptions, item -> reviewerName(context, item.getSecondReviewerId())));
        values.add(joinExemptions(exemptions, item -> time(item.getSecondReviewTime())));
    }

    private String joinExemptions(List<ExemptionRequest> exemptions, Function<ExemptionRequest, String> value) {
        return exemptions.stream().map(value).map(this::nvl).collect(Collectors.joining(";"));
    }

    private String taskScore(List<VideoReviewTask> tasks, int index) {
        return tasks.size() <= index || tasks.get(index).getScore() == null
                ? "" : String.valueOf(tasks.get(index).getScore());
    }

    private String firstReviewStatus(String currentStatus, LocalDateTime firstTime, LocalDateTime secondTime) {
        if (firstTime == null) {
            return "";
        }
        if ("FIRST_REJECTED".equals(currentStatus)) {
            return "RETURN";
        }
        if ("FAILED".equals(currentStatus) && secondTime == null) {
            return "FAIL";
        }
        return "PASS";
    }

    private String secondReviewStatus(String currentStatus, LocalDateTime secondTime) {
        if (secondTime == null) {
            return "";
        }
        if ("SECOND_REJECTED".equals(currentStatus)) {
            return "RETURN";
        }
        if ("FAILED".equals(currentStatus)) {
            return "FAIL";
        }
        return "PASSED".equals(currentStatus) ? "PASS" : nvl(currentStatus);
    }

    private List<String> certSummaryHeaders() {
        return List.of("学号", "姓名", "身份证件号码", "身份类型", "学历层次", "培养目标", "任教学段",
                "任教学科", "证书编号", "签发人", "签发日期", "有效期限", "证书状态");
    }

    private List<List<String>> certSummaryRows(List<Certificate> certificates, boolean sensitive, Map<Long, Student> students) {
        List<List<String>> rows = new ArrayList<>();
        for (Certificate cert : certificates) {
            Student student = students.get(cert.getStudentId());
            String plainIdCardNo = idCardProtectionService.decrypt(cert.getIdCardNo());
            rows.add(List.of(
                    nvl(cert.getStudentNo()),
                    nvl(cert.getStudentName()),
                    sensitive ? nvl(plainIdCardNo) : nvl(SensitiveMasker.idCard(plainIdCardNo)),
                    student == null ? "" : nvl(student.getIdentityType()),
                    nvl(cert.getEducationLevel()),
                    nvl(cert.getTrainingGoal()),
                    nvl(cert.getTeachingSegment()),
                    nvl(cert.getTeachingSubjectName()),
                    nvl(cert.getCertNo()),
                    nvl(cert.getIssuer()),
                    nvl(cert.getIssueDate()),
                    nvl(cert.getValidUntil()),
                    nvl(cert.getStatus())
            ));
        }
        return rows;
    }

    private List<String> attachmentHeaders() {
        return List.of("学号", "姓名", "材料类别", "文件名", "材料状态", "审核人", "审核时间", "下载链接");
    }

    private List<AttachmentExportItem> attachmentItems(
            AuditExportContext context, ExchangeQuery query) {
        List<AttachmentExportItem> items = new ArrayList<>();
        Map<Long, List<ExemptionMaterial>> exemptionMaterials = exemptionMaterialsByRequest(context);
        for (AuditExportRow row : context.rows()) {
            Student student = row.student();
            String studentDir = safeZipPart(student.getStudentNo()) + "_" + student.getId();
            for (ProcessMaterial material : context.materials().getOrDefault(row.key(), List.of())) {
                if (material.getFileId() == null || material.getFileId() <= 0) {
                    continue;
                }
                FileObject file = fileService.readyFile(material.getFileId());
                String fileName = StringUtils.hasText(material.getFileName())
                        ? material.getFileName() : file.getOriginalName();
                String link = nvl(query.getContentBaseUrl())
                        + "/api/material/preview/" + material.getId() + "/content";
                List<String> manifestRow = List.of(
                        nvl(student.getStudentNo()), nvl(student.getName()),
                        materialCategoryLabel(material.getCategory()), nvl(fileName), nvl(material.getStatus()),
                        reviewerName(context, material.getSecondReviewerId()), time(material.getSecondReviewTime()), link);
                String entry = "学生材料/" + studentDir + "/材料/" + material.getId()
                        + "-" + safeZipPart(fileName);
                items.add(new AttachmentExportItem(manifestRow, material.getFileId(), file.getSize(), entry));
            }
            for (ExemptionRequest request : context.exemptions().getOrDefault(row.key(), List.of())) {
                for (ExemptionMaterial material : exemptionMaterials.getOrDefault(request.getId(), List.of())) {
                    if (material.getFileId() == null || material.getFileId() <= 0) {
                        continue;
                    }
                    FileObject file = fileService.readyFile(material.getFileId());
                    String fileName = StringUtils.hasText(material.getFileName())
                            ? material.getFileName() : file.getOriginalName();
                    String subject = StringUtils.hasText(request.getSubjectLabel())
                            ? request.getSubjectLabel() : nvl(request.getSubject());
                    String link = nvl(query.getContentBaseUrl())
                            + "/api/exemption/materials/" + material.getId() + "/content";
                    List<String> manifestRow = List.of(
                            nvl(student.getStudentNo()), nvl(student.getName()),
                            "免考佐证-" + subject, nvl(fileName), nvl(request.getFinalStatus()),
                            reviewerName(context, request.getSecondReviewerId()),
                            time(request.getSecondReviewTime()), link);
                    String entry = "学生材料/" + studentDir + "/免考佐证/" + request.getId()
                            + "-" + material.getId() + "-" + safeZipPart(fileName);
                    items.add(new AttachmentExportItem(manifestRow, material.getFileId(), file.getSize(), entry));
                }
            }
            VideoReview video = context.videos().get(row.key());
            if (video == null || video.getVideoFileId() == null || video.getVideoFileId() <= 0) {
                continue;
            }
            FileObject file = fileService.readyFile(video.getVideoFileId());
            String fileName = StringUtils.hasText(video.getVideoFileName())
                    ? video.getVideoFileName() : file.getOriginalName();
            String link = nvl(query.getContentBaseUrl())
                    + "/api/video/reviews/" + video.getId() + "/content";
            List<String> manifestRow = List.of(
                    nvl(student.getStudentNo()), nvl(student.getName()), "教学能力视频", nvl(fileName),
                    nvl(video.getStatus()), reviewerName(context, video.getConfirmedBy()),
                    time(video.getConfirmedAt()), link);
            String entry = "学生材料/" + studentDir + "/视频/" + video.getId()
                    + "-" + safeZipPart(fileName);
            items.add(new AttachmentExportItem(manifestRow, video.getVideoFileId(), file.getSize(), entry));
        }
        return items;
    }

    private Map<Long, List<ExemptionMaterial>> exemptionMaterialsByRequest(AuditExportContext context) {
        Map<Long, ExemptionRequest> requestsById = context.rows().stream()
                .flatMap(row -> context.exemptions().getOrDefault(row.key(), List.of()).stream())
                .filter(request -> request.getId() != null)
                .collect(Collectors.toMap(ExemptionRequest::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        if (requestsById.isEmpty()) {
            return Map.of();
        }
        Set<Long> studentIds = context.rows().stream().map(row -> row.student().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return exemptionMaterialMapper.selectList(new LambdaQueryWrapper<ExemptionMaterial>()
                        .in(ExemptionMaterial::getExemptionRequestId, requestsById.keySet())
                        .in(ExemptionMaterial::getStudentId, studentIds)
                        .orderByAsc(ExemptionMaterial::getExemptionRequestId)
                        .orderByAsc(ExemptionMaterial::getId)).stream()
                .filter(material -> {
                    ExemptionRequest request = requestsById.get(material.getExemptionRequestId());
                    return request != null && Objects.equals(request.getStudentId(), material.getStudentId());
                })
                .collect(Collectors.groupingBy(ExemptionMaterial::getExemptionRequestId,
                        LinkedHashMap::new, Collectors.toList()));
    }

    private void writeAttachmentZip(
            java.io.OutputStream out, byte[] workbook, List<AttachmentExportItem> items) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("附件清单.xlsx"));
            zip.write(workbook);
            zip.closeEntry();
            for (AttachmentExportItem item : items) {
                zip.putNextEntry(new ZipEntry(item.entryName()));
                try (InputStream input = fileService.openRange(item.fileId(), 0L, item.size())) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    private String safeZipPart(String value) {
        String source = StringUtils.hasText(value) ? value.trim() : "未命名文件";
        String safe = source.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return StringUtils.hasText(safe) ? safe : "未命名文件";
    }

    private Map<String, List<String>> dropdowns() {
        return dictionaryHelper.dropdowns();
    }

    private SysDictItem dictItem(String typeCode, String itemCode) {
        return dictionaryHelper.item(typeCode, itemCode);
    }

    private Long resolveCollegeId(ExchangeStandardRow row) {
        return importValidator.resolveCollegeId(row);
    }

    private void ensureCanImportCollege(Long collegeId) {
        importValidator.ensureCanImportCollege(collegeId);
    }

    // Phase 37a-part2 (P0-7)：批次越权修复。全校/系统范围可访问所有批次，否则仅限本人创建的批次。
    // 批次表以 operatorId 归属（无 college_id），以操作人归属做校验，避免 scopeJson 子串匹配漏洞。
    private void ensureBatchAccessible(ImportExportBatch batch, String permission) {
        DataScopeContext.Scope scope = dataScopeService.resolve(permission);
        if (scope != null && scope.allSchool()) {
            return;
        }
        Long uid = UserContext.getUserId();
        if (uid != null && uid.equals(batch.getOperatorId())) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该批次");
    }

    private void ensureCanUpdateExisting(Object existing, Long targetCollegeId, String label) {
        if (existing == null) {
            return;
        }
        Long currentCollegeId = collegeIdOf(existing);
        ensureCanImportCollege(currentCollegeId);
        if (!Objects.equals(currentCollegeId, targetCollegeId)) {
            throw new BizException(label + "所属学院与导入目标学院不一致");
        }
    }

    private Long collegeIdOf(Object value) {
        if (value instanceof Student student) {
            return student.getCollegeId();
        }
        if (value instanceof TrainingProfile training) {
            return training.getCollegeId();
        }
        if (value instanceof Certificate certificate) {
            return certificate.getCollegeId();
        }
        throw new BizException("不支持的数据范围校验对象");
    }

    private TrainingProfileSaveRequest trainingRequest(ExchangeStandardRow row, Long studentId) {
        return importValidator.trainingRequest(row, studentId);
    }

    private SysMajor majorByCodeName(Long collegeId, String code, String name) {
        return importValidator.majorByCodeName(collegeId, code, name);
    }

    private Student studentByNoForUpdate(String studentNo) {
        if (!StringUtils.hasText(studentNo)) {
            return null;
        }
        return studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, studentNo.trim())
                .last("FOR UPDATE"));
    }

    private Student studentByIdCardForUpdate(String idCardNo) {
        if (!StringUtils.hasText(idCardNo)) {
            return null;
        }
        return studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getIdCardHmac, idCardProtectionService.hmac(idCardNo.trim()))
                .last("FOR UPDATE"));
    }

    private Certificate certificateByNoForUpdate(String certNo) {
        if (!StringUtils.hasText(certNo)) {
            return null;
        }
        return certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertNo, certNo.trim())
                .last("FOR UPDATE"));
    }

    private Certificate certificateByStudentYearForUpdate(Long studentId, String year) {
        List<Certificate> matches = certificateMapper.selectList(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getStudentId, studentId)
                .eq(Certificate::getAssessmentYear, year)
                .last("FOR UPDATE"));
        // V24 只保证每个 student-year 至多一张非 VOIDED/REISSUED 证书；合法历史可以有多条。
        // 先返回唯一活跃记录；若只剩历史，仍返回最新终态记录，让现有 Phase 48 守卫拒绝通过导入改写。
        return matches.stream()
                .filter(item -> !CertificateStatus.VOIDED.name().equals(item.getStatus())
                        && !CertificateStatus.REISSUED.name().equals(item.getStatus()))
                .findFirst()
                .orElseGet(() -> matches.stream()
                        .max(Comparator.comparing(Certificate::getId))
                        .orElse(null));
    }

    private TrainingProfile trainingByStudentYearForUpdate(Long studentId, String year) {
        return trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, studentId)
                .eq(TrainingProfile::getAssessmentYear, year)
                .last("FOR UPDATE"));
    }

    private TrainingProfile trainingByStudentYear(Long studentId, String year) {
        return trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                .eq(TrainingProfile::getStudentId, studentId)
                .eq(TrainingProfile::getAssessmentYear, year)
                .last("LIMIT 1"));
    }

    // Phase 44a：以下为 rowFromCertificate/fullReviewRows/certSummaryRows/attachmentRows 的批量查询版本，
    // 用 .in(studentIds) 一次性取回后按 studentId 分组，行内再按 assessmentYear 过滤，
    // 替代原先逐证书 selectOne/selectById 的 N+1 查询；语义与旧的单行查询完全一致（至多一条命中）。
    private Map<Long, Student> studentsByIds(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Student::getId, item -> item));
    }

    private Map<Long, List<TrainingProfile>> trainingByStudentIds(Set<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return trainingProfileMapper.selectList(new LambdaQueryWrapper<TrainingProfile>()
                        .in(TrainingProfile::getStudentId, studentIds))
                .stream()
                .collect(Collectors.groupingBy(TrainingProfile::getStudentId));
    }

    private TrainingProfile trainingFor(Map<Long, List<TrainingProfile>> trainingByStudent, Long studentId, String year) {
        return trainingByStudent.getOrDefault(studentId, List.of()).stream()
                .filter(item -> Objects.equals(item.getAssessmentYear(), year))
                .findFirst().orElse(null);
    }

    private Map<Long, List<VideoReview>> videosByStudentIds(Set<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return videoReviewMapper.selectList(new LambdaQueryWrapper<VideoReview>()
                        .in(VideoReview::getStudentId, studentIds))
                .stream()
                .collect(Collectors.groupingBy(VideoReview::getStudentId));
    }

    private VideoReview videoFor(Map<Long, List<VideoReview>> videosByStudent, Long studentId, String year) {
        return videosByStudent.getOrDefault(studentId, List.of()).stream()
                .filter(item -> Objects.equals(item.getAssessmentYear(), year))
                .findFirst().orElse(null);
    }

    private Map<Long, List<ProcessMaterial>> materialsByStudentIds(Set<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        return materialMapper.selectList(new LambdaQueryWrapper<ProcessMaterial>()
                        .in(ProcessMaterial::getStudentId, studentIds))
                .stream()
                .collect(Collectors.groupingBy(ProcessMaterial::getStudentId));
    }

    private String materialSummaryFor(Map<Long, List<ProcessMaterial>> materialsByStudent, Long studentId, String year) {
        return materialsByStudent.getOrDefault(studentId, List.of()).stream()
                .filter(item -> Objects.equals(item.getAssessmentYear(), year))
                .sorted(Comparator.comparing(ProcessMaterial::getCategory))
                .map(item -> item.getCategory() + ":" + item.getStatus())
                .collect(Collectors.joining(";"));
    }

    private void recordRef(ImportExportBatch batch, Integer rowNo, String tableName, Long recordId, String action,
                           String beforeJson, String afterJson, String remark) {
        ImportRecordRef ref = new ImportRecordRef();
        ref.setBatchId(batch.getId());
        ref.setBatchNo(batch.getBatchNo());
        ref.setTableName(tableName);
        ref.setRecordId(recordId == null ? 0L : recordId);
        ref.setAction(action);
        ref.setBeforeJson(beforeJson);
        ref.setAfterJson(afterJson);
        ref.setRowNo(rowNo);
        ref.setRemark(remark);
        recordRefMapper.insert(ref);
    }

    private void addError(ImportExportBatch batch, Integer rowNo, ExchangeStandardRow row, String field,
                          String value, String reason, String suggestion) {
        ImportErrorDetail detail = new ImportErrorDetail();
        detail.setBatchId(batch.getId());
        detail.setBatchNo(batch.getBatchNo());
        detail.setRowNo(rowNo);
        detail.setStudentNo(dbText(row.getStudentNo(), 64));
        detail.setStudentName(dbText(row.getName(), 64));
        detail.setFieldName(dbText(field, 128));
        detail.setErrorValue(dbText(protectErrorValue(field, value), 512));
        detail.setErrorReason(dbText(reason, 512));
        detail.setSuggestion(dbText(suggestion, 512));
        errorMapper.insert(detail);
    }

    private ImportErrorDetail toErrorDetail(ImportExportBatch batch, ExchangeExcelHelper.ReadRow readRow,
                                            ExchangeImportValidator.ValidationError error) {
        ImportErrorDetail detail = new ImportErrorDetail();
        detail.setBatchId(batch.getId());
        detail.setBatchNo(batch.getBatchNo());
        detail.setRowNo(readRow.rowNo());
        detail.setStudentNo(dbText(readRow.row().getStudentNo(), 64));
        detail.setStudentName(dbText(readRow.row().getName(), 64));
        detail.setFieldName(dbText(error.fieldName(), 128));
        detail.setErrorValue(dbText(protectErrorValue(error.fieldName(), error.errorValue()), 512));
        detail.setErrorReason(dbText(error.errorReason(), 512));
        detail.setSuggestion(dbText(error.suggestion(), 512));
        return detail;
    }

    private String dbText(String value, int maxCodePoints) {
        if (value == null || value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    private ImportErrorVO toErrorVO(ImportErrorDetail detail) {
        ImportErrorVO vo = new ImportErrorVO();
        vo.setId(detail.getId());
        vo.setRowNo(detail.getRowNo());
        vo.setStudentNo(detail.getStudentNo());
        vo.setStudentName(detail.getStudentName());
        vo.setFieldName(detail.getFieldName());
        vo.setErrorValue(projectErrorValue(detail.getFieldName(), detail.getErrorValue(),
                UserContext.hasPermission("exchange:export:sensitive")));
        vo.setErrorReason(detail.getErrorReason());
        vo.setSuggestion(detail.getSuggestion());
        return vo;
    }

    private String projectErrorValue(String fieldName, String value, boolean sensitive) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if ("身份证件号码".equals(fieldName)) {
            String plainIdCardNo = idCardProtectionService.isEncrypted(value)
                    ? idCardProtectionService.decrypt(value) : value;
            return sensitive ? plainIdCardNo : SensitiveMasker.idCard(plainIdCardNo);
        }
        if (sensitive) {
            return value;
        }
        if ("出生日期".equals(fieldName)) {
            return SensitiveMasker.birthDate(value);
        }
        return value;
    }

    private String protectErrorValue(String fieldName, String value) {
        if (!"身份证件号码".equals(fieldName) || !StringUtils.hasText(value)
                || idCardProtectionService.isEncrypted(value)) {
            return value;
        }
        // 先界定明文长度再加密，避免事后截断密文导致 AES-GCM 认证标签不可用。
        return idCardProtectionService.encrypt(dbText(value.trim(), 64));
    }

    private ImportExportBatch requireBatch(Long batchId) {
        if (batchId == null) {
            throw new BizException("批次ID不能为空");
        }
        ImportExportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "批次不存在");
        }
        return batch;
    }

    private ImportExportBatch requireBatchForUpdate(Long batchId) {
        if (batchId == null) {
            throw new BizException("批次ID不能为空");
        }
        ImportExportBatch batch = batchMapper.selectByIdForUpdate(batchId);
        if (batch == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "批次不存在");
        }
        return batch;
    }

    private void requireImportingBatchForUpdate(Long batchId) {
        if (batchId == null) {
            throw new BizException("批次ID不能为空");
        }
        // 逐行事务只需要 batch 行锁与状态，不得重复装载整批 preview_json。
        String persistedStatus = batchMapper.selectStatusByIdForUpdate(batchId);
        if (persistedStatus == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "批次不存在");
        }
        if (ExchangeBatchStatus.of(persistedStatus) != ExchangeBatchStatus.IMPORTING) {
            throw new ImportExecutionStoppedException(persistedStatus);
        }
    }

    private BizException importStopped(ImportExecutionStoppedException stopped) {
        return new BizException("导入已停止，批次当前状态为 " + stopped.persistedStatus());
    }

    private BatchVO toBatchVO(ImportExportBatch batch) {
        BatchVO vo = new BatchVO();
        vo.setId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setType(batch.getType());
        vo.setFileName(batch.getFileName());
        vo.setOperateTime(batch.getOperateTime() == null ? null : batch.getOperateTime().toString());
        vo.setTotal(batch.getTotal());
        vo.setSuccessCount(batch.getSuccessCount());
        vo.setFailCount(batch.getFailCount());
        vo.setStrategy(batch.getStrategy());
        vo.setStatus(batch.getStatus());
        vo.setRemark(batch.getRemark());
        return vo;
    }

    private ImportExportBatch recordExportBatch(String type, String exportType, ExchangeQuery query,
                                                int total, int success, int fail, String fileName) {
        ImportExportBatch batch = new ImportExportBatch();
        batch.setBatchNo(nextBatchNo("EXP"));
        batch.setType(type);
        batch.setFileName(fileName);
        Long operatorId = UserContext.getUserIdOrSystem();
        batch.setOperatorId(operatorId);
        batch.setOperateTime(LocalDateTime.now());
        batch.setTotal(total);
        batch.setSuccessCount(success);
        batch.setFailCount(fail);
        batch.setScopeJson(writeJson(protectedExportScope(query)));
        batch.setStrategy(exportType);
        batch.setStatus(ExchangeBatchStatus.EXPORTED.name());
        batchMapper.insert(batch);
        if ("export".equals(type)) {
            notificationService.send(operatorId, "EXPORT_DONE", "导出完成",
                    StringUtils.hasText(fileName) ? "导出完成：" + fileName : "导出完成",
                    "import_export_batch", String.valueOf(batch.getId()));
        }
        return batch;
    }

    private String nextBatchNo(String prefix) {
        return prefix + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-" + IdWorker.getIdStr().substring(12);
    }

    /**
     * 导出 keyword 可能是完整证件号；批次审计只需保存筛选语义，不得把该原值再次明文落库。
     * 其它结构化筛选项保持可读，keyword 使用可认证密文保存且不改变本次实际查询对象。
     */
    private Map<String, Object> protectedExportScope(ExchangeQuery query) {
        ExchangeQuery source = query == null ? new ExchangeQuery() : query;
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("batchId", source.getBatchId());
        scope.put("keyword", StringUtils.hasText(source.getKeyword())
                ? idCardProtectionService.encrypt(source.getKeyword().trim()) : null);
        scope.put("assessmentYear", source.getAssessmentYear());
        scope.put("collegeId", source.getCollegeId());
        scope.put("internalMajorCode", source.getInternalMajorCode());
        scope.put("className", source.getClassName());
        scope.put("trainingGoal", source.getTrainingGoal());
        scope.put("teachingSegment", source.getTeachingSegment());
        scope.put("auditStatus", source.getAuditStatus());
        scope.put("certStatus", source.getCertStatus());
        return scope;
    }

    private String normalizeIdCardLookup(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 18 && normalized.endsWith("x")) {
            return normalized.substring(0, normalized.length() - 1) + "X";
        }
        return normalized;
    }

    private String assessmentYear(ExchangeStandardRow row) {
        return importValidator.assessmentYear(row);
    }

    private boolean overwrite(ImportStrategy strategy, String currentValue) {
        return strategy == ImportStrategy.OVERWRITE || strategy == ImportStrategy.INSERT_ONLY
                || (strategy == ImportStrategy.UPDATE_EMPTY && !StringUtils.hasText(currentValue));
    }

    private void setIfAllowed(boolean existing, ImportStrategy strategy, SupplierString getter,
                              ConsumerString setter, String value) {
        if (!existing || overwrite(strategy, getter.get())) {
            setter.accept(trim(value));
        }
    }

    private String snapshot(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException("导入快照序列化失败");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException("JSON序列化失败");
        }
    }

    private PreviewPayload protectedPreview(Integer rowNo, ExchangeStandardRow row) {
        ExchangeStandardRow stored = objectMapper.convertValue(row, ExchangeStandardRow.class);
        if (StringUtils.hasText(stored.getIdCardNo())
                && !idCardProtectionService.isEncrypted(stored.getIdCardNo())) {
            stored.setIdCardNo(idCardProtectionService.encrypt(stored.getIdCardNo().trim()));
        }
        return new PreviewPayload(rowNo, stored);
    }

    private List<PreviewPayload> readPreviews(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<PreviewPayload> previews = objectMapper.readValue(json, PREVIEW_LIST_TYPE);
            previews.forEach(preview -> {
                if (StringUtils.hasText(preview.row().getIdCardNo())) {
                    String stored = preview.row().getIdCardNo();
                    preview.row().setIdCardNo(idCardProtectionService.isEncrypted(stored)
                            ? idCardProtectionService.decrypt(stored) : stored);
                }
            });
            return previews;
        } catch (Exception e) {
            throw new BizException("预校验成功行解析失败");
        }
    }

    static String importFailureMessage(Exception e) {
        Throwable cursor = e;
        while (cursor != null) {
            if (cursor instanceof BizException bizException && StringUtils.hasText(bizException.getMessage())) {
                return bizException.getMessage();
            }
            cursor = cursor.getCause();
        }
        return "导入失败";
    }

    private String required(String value, String message) {
        String text = trim(value);
        if (text == null) {
            throw new BizException(message);
        }
        return text;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private static boolean violatesIndex(Throwable error, String indexName) {
        for (Throwable cursor = error; cursor != null; cursor = cursor.getCause()) {
            if (cursor.getMessage() != null && cursor.getMessage().contains(indexName)) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface SupplierString {
        String get();
    }

    @FunctionalInterface
    private interface ConsumerString {
        void accept(String value);
    }

    private record StudentYearKey(Long studentId, String assessmentYear) {
    }

    private record AuditExportRow(
            StudentYearKey key, Student student, TrainingProfile training, Certificate certificate) {
    }

    private record AuditExportContext(
            List<AuditExportRow> rows,
            Map<StudentYearKey, List<ProcessMaterial>> materials,
            Map<StudentYearKey, List<ExemptionRequest>> exemptions,
            Map<StudentYearKey, AbilityTestResult> abilities,
            Map<StudentYearKey, VideoReview> videos,
            Map<Long, List<VideoReviewTask>> videoTasks,
            Map<Long, String> reviewerNames) {

        private static AuditExportContext empty() {
            return new AuditExportContext(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }

    private record AttachmentExportItem(
            List<String> manifestRow, Long fileId, Long size, String entryName) {
    }

    private record PreviewPayload(Integer rowNo, ExchangeStandardRow row) {
    }

    private record ImportDecision(boolean success, String message) {
    }

    private static final class ImportExecutionStoppedException extends RuntimeException {
        private final String persistedStatus;

        private ImportExecutionStoppedException(String persistedStatus) {
            super("Import batch is no longer IMPORTING: " + persistedStatus);
            this.persistedStatus = persistedStatus;
        }

        private String persistedStatus() {
            return persistedStatus;
        }
    }
}
