package cn.edu.gpnu.platform.exchange.service.impl;

import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.certificate.service.CertificateService;
import cn.edu.gpnu.platform.business.certificate.support.CertificateStatus;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.student.support.BirthDateValidator;
import cn.edu.gpnu.platform.business.student.support.IdCardValidator;
import cn.edu.gpnu.platform.business.student.support.NameValidator;
import cn.edu.gpnu.platform.business.student.support.SensitiveMasker;
import cn.edu.gpnu.platform.business.student.support.StudentStatus;
import cn.edu.gpnu.platform.business.training.dto.TrainingProfileSaveRequest;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.business.training.support.MajorCodeValidator;
import cn.edu.gpnu.platform.business.training.support.TrainingLinkValidator;
import cn.edu.gpnu.platform.business.training.support.TrainingStatus;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
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
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.exchange.support.ExchangeExportType;
import cn.edu.gpnu.platform.exchange.support.ExchangeImportHook;
import cn.edu.gpnu.platform.exchange.support.ImportStrategy;
import cn.edu.gpnu.platform.exchange.vo.BatchVO;
import cn.edu.gpnu.platform.exchange.vo.ExchangeFile;
import cn.edu.gpnu.platform.exchange.vo.ImportErrorVO;
import cn.edu.gpnu.platform.exchange.vo.ImportPreviewRowVO;
import cn.edu.gpnu.platform.exchange.vo.ImportResultVO;
import cn.edu.gpnu.platform.exchange.vo.PrevalidateResultVO;
import cn.edu.gpnu.platform.exchange.vo.RollbackResultVO;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.system.entity.SysCollege;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.entity.SysMajor;
import cn.edu.gpnu.platform.system.entity.TeachingSubject;
import cn.edu.gpnu.platform.system.mapper.SysCollegeMapper;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.mapper.SysMajorMapper;
import cn.edu.gpnu.platform.system.mapper.TeachingSubjectMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.NotificationService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
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
public class ExchangeServiceImpl implements ExchangeService {

    private static final String DEFAULT_YEAR_VERSION = "GLOBAL";
    private static final String SCHOOL_NAME = "广东技术师范大学";
    private static final String DEFAULT_SCHOOL_CODE = "10588";
    private static final String DEFAULT_PROVINCE_CODE = "44";
    private static final Set<String> EDUCATION_GRADUATE_PREFIXES = Set.of("0401", "0451", "0453");
    private static final TypeReference<List<PreviewPayload>> PREVIEW_LIST_TYPE = new TypeReference<>() {
    };
    // Phase 44b（§7.3 证书导出内存）：selectCertificates 只喂 export/exportAttachments 两个导出入口
    // （无其他调用方），故直接在此设置单次导出上限，早于逐条 matchTrainingAndStudent 后过滤即拦截，
    // 避免筛选条件过宽（或未按学年/学院收窄）时把过大结果集整体驻留堆内存；未超限时行为、返回值不变。
    private static final int MAX_EXPORT_ROWS = 20000;

    private final ImportExportBatchMapper batchMapper;
    private final ImportErrorDetailMapper errorMapper;
    private final ImportRecordRefMapper recordRefMapper;
    private final StudentMapper studentMapper;
    private final TrainingProfileMapper trainingProfileMapper;
    private final CertificateMapper certificateMapper;
    private final CertificateService certificateService;
    private final ProcessMaterialMapper materialMapper;
    private final VideoReviewMapper videoReviewMapper;
    private final SysDictItemMapper dictItemMapper;
    private final SysCollegeMapper collegeMapper;
    private final SysMajorMapper majorMapper;
    private final TeachingSubjectMapper teachingSubjectMapper;
    private final DataScopeService dataScopeService;
    private final NotificationService notificationService;
    private final ParamService paramService;
    private final NameValidator nameValidator;
    private final IdCardValidator idCardValidator;
    private final BirthDateValidator birthDateValidator;
    private final MajorCodeValidator majorCodeValidator;
    private final TrainingLinkValidator trainingLinkValidator;
    private final FileService fileService;
    private final ExchangeExcelHelper excelHelper;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final AuditLogService auditLogService;
    private final ExchangeImportHook exchangeImportHook;

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
        List<ExchangeExcelHelper.ReadRow> rows;
        try {
            rows = excelHelper.readStandardRows(file.getBytes());
        } catch (IOException e) {
            throw new BizException("读取Excel失败");
        }
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
                .map(item -> trim(item.row().getIdCardNo()))
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> certNoCounts = rows.stream()
                .map(item -> trim(item.row().getCertNo()))
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
        for (ExchangeExcelHelper.ReadRow readRow : rows) {
            List<ValidationError> errors = validate(readRow.row(), readRow.rowNo(), idCardCounts, certNoCounts);
            if (errors.isEmpty()) {
                ImportPreviewRowVO preview = new ImportPreviewRowVO();
                preview.setRowNo(readRow.rowNo());
                preview.setRow(readRow.row());
                result.getPreviewRows().add(preview);
                previews.add(new PreviewPayload(readRow.rowNo(), readRow.row()));
            } else {
                for (ValidationError error : errors) {
                    ImportErrorDetail detail = toErrorDetail(batch, readRow, error);
                    errorMapper.insert(detail);
                    result.getErrors().add(toErrorVO(detail));
                }
            }
        }
        result.setSuccessCount(result.getPreviewRows().size());
        result.setFailCount(result.getErrors().size());
        batch.setSuccessCount(result.getSuccessCount());
        batch.setFailCount(result.getFailCount());
        batch.setPreviewJson(writeJson(previews));
        batchMapper.updateById(batch);
        return result;
    }

    @Override
    public ExchangeFile errorReport(Long batchId) {
        ImportExportBatch batch = requireBatch(batchId);
        ensureBatchAccessible(batch, "exchange:prevalidate");
        List<ImportErrorDetail> errors = errorMapper.selectList(new LambdaQueryWrapper<ImportErrorDetail>()
                .eq(ImportErrorDetail::getBatchId, batchId)
                .orderByAsc(ImportErrorDetail::getRowNo)
                .orderByAsc(ImportErrorDetail::getId));
        byte[] content = excelHelper.writeErrorWorkbook(errors.stream()
                .map(item -> new ExchangeExcelHelper.ErrorRow(batch.getBatchNo(), item.getRowNo(),
                        item.getStudentNo(), item.getStudentName(), item.getFieldName(), item.getErrorValue(),
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
                String message = importFailureMessage(e);
                fail++;
                vo.getMessages().add("第" + preview.rowNo() + "行: " + message);
                try {
                    addErrorInNewTransaction(batch, preview.rowNo(), preview.row(), message);
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
        int rolledBack = 0;
        int conflicts = 0;
        RollbackResultVO vo = new RollbackResultVO();
        vo.setBatchId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        for (ImportRecordRef ref : refs) {
            RollbackDecision decision = rollbackOne(ref);
            if (decision.success()) {
                rolledBack++;
            } else {
                conflicts++;
                vo.getConflicts().add(decision.message());
            }
        }
        batch.setStatus(conflicts > 0 ? ExchangeBatchStatus.PARTIAL_ROLLBACK.name() : ExchangeBatchStatus.ROLLED_BACK.name());
        batch.setRemark(conflicts > 0 ? "部分记录已被后续修改，跳过回滚" : "已回滚");
        batchMapper.updateById(batch);
        auditLogService.record("exchange", batch.getId(), batch.getBatchNo(), "rollback",
                oldStatus, batch.getStatus(), batch.getRemark());
        vo.setRolledBackCount(rolledBack);
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
            return exportErrors();
        }
        if (exportType == ExchangeExportType.ATTACHMENT_LIST) {
            return exportAttachments(query);
        }
        boolean sensitive = UserContext.hasPermission("exchange:export:sensitive");
        List<Certificate> certificates = selectCertificates(query);
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
            content = excelHelper.writeStandardWorkbook(rows, null);
            fileName = "标准上报表.xlsx";
        } else if (exportType == ExchangeExportType.CERT_SUMMARY) {
            content = excelHelper.writeTableWorkbook("证书获得者汇总表", certSummaryHeaders(), certSummaryRows(certificates, sensitive, students));
            fileName = "证书获得者汇总表.xlsx";
        } else {
            Map<Long, List<VideoReview>> videosByStudent = videosByStudentIds(studentIds);
            Map<Long, List<ProcessMaterial>> materialsByStudent = materialsByStudentIds(studentIds);
            content = excelHelper.writeTableWorkbook("完整审核表", fullReviewHeaders(),
                    fullReviewRows(certificates, rows, students, trainingByStudent, videosByStudent, materialsByStudent));
            fileName = "完整审核表.xlsx";
        }
        recordExportBatch("export", exportType.name(), query, certificates.size(), certificates.size(), 0, fileName);
        return new ExchangeFile(fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    @Override
    public ExchangeFile exportAttachments(ExchangeQuery query) {
        List<Certificate> certificates = selectCertificates(query);
        Set<Long> studentIds = certificates.stream().map(Certificate::getStudentId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<List<String>> rows = attachmentRows(studentIds, query);
        byte[] workbook = excelHelper.writeTableWorkbook("附件清单表", attachmentHeaders(), rows);
        byte[] zip = zipSingleFile("附件清单.xlsx", workbook);
        recordExportBatch("export", ExchangeExportType.ATTACHMENT_LIST.name(), query, rows.size(), rows.size(), 0, "附件视频打包.zip");
        return new ExchangeFile("附件视频打包.zip", "application/zip", zip);
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
            addError(batch, rowNo, row, "导入", "", message, "请修正后重新预校验");
        });
    }

    private ImportDecision importOne(ImportExportBatch batch, PreviewPayload preview, ImportStrategy strategy) {
        ExchangeStandardRow row = preview.row();
        Student existingStudent = studentByNo(row.getStudentNo());
        Student duplicateId = studentByIdCard(row.getIdCardNo());
        Certificate existingCertificate = certificateByNo(row.getCertNo());
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
        Long collegeId = resolveCollegeId(row);
        ensureCanImportCollege(collegeId);
        ensureCanUpdateExisting(existingStudent, collegeId, "现有学生");
        ensureCanUpdateExisting(existingCertificate, collegeId, "现有证书");
        Student student = existingStudent == null ? new Student() : existingStudent;
        boolean studentExisting = existingStudent != null;
        String beforeStudent = studentExisting ? snapshot(student) : null;
        applyStudent(student, row, collegeId, strategy, studentExisting);
        if (studentExisting) {
            studentMapper.updateById(student);
        } else {
            try {
                studentMapper.insert(student);
            } catch (DuplicateKeyException e) {
                throw new BizException("学生学号已存在");
            }
        }
        recordRef(batch, preview.rowNo(), "student", student.getId(), studentExisting ? "UPDATE" : "INSERT",
                beforeStudent, snapshot(student), studentExisting ? "导入更新学生" : "导入新增学生");

        TrainingProfile training = trainingByStudentYear(student.getId(), assessmentYear(row));
        boolean trainingExisting = training != null;
        ensureCanUpdateExisting(training, student.getCollegeId(), "现有培养信息");
        if (!trainingExisting) {
            training = new TrainingProfile();
            training.setStudentId(student.getId());
        }
        String beforeTraining = trainingExisting ? snapshot(training) : null;
        applyTraining(training, row, student, strategy, trainingExisting);
        if (trainingExisting) {
            trainingProfileMapper.updateById(training);
        } else {
            trainingProfileMapper.insert(training);
        }
        recordRef(batch, preview.rowNo(), "training_profile", training.getId(), trainingExisting ? "UPDATE" : "INSERT",
                beforeTraining, snapshot(training), trainingExisting ? "导入更新培养信息" : "导入新增培养信息");

        Certificate certificate = existingCertificate;
        if (certificate == null) {
            certificate = certificateByStudentYear(student.getId(), assessmentYear(row));
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
            certificateMapper.updateById(certificate);
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

    private List<ValidationError> validate(ExchangeStandardRow row, int rowNo,
                                           Map<String, Long> idCardCounts,
                                           Map<String, Long> certNoCounts) {
        List<ValidationError> errors = new ArrayList<>();
        validateCompleteness(row, errors);
        validateTextFormat(row, errors);
        validateSchool(row, errors);
        validateName(row, errors);
        validateId(row, errors);
        validateBirth(row, errors);
        validateIdentityType(row, errors);
        validateMajor(row, errors);
        validateTrainingLink(row, errors);
        validateCertificateNo(row, errors);
        validateValidity(row, errors);
        validateDuplicate(row, errors, idCardCounts, certNoCounts);
        return errors;
    }

    private void validateCompleteness(ExchangeStandardRow row, List<ValidationError> errors) {
        for (ExchangeColumn column : ExchangeColumn.ALL) {
            if (column.required() && !StringUtils.hasText(column.value(row))) {
                errors.add(new ValidationError(column.header(), column.value(row), "V-01字段不能为空", "请补齐" + column.header()));
            }
        }
        if (!"education_master".equals(trim(row.getIdentityType()))) {
            if (!StringUtils.hasText(row.getInternalMajorCode())) {
                errors.add(new ValidationError("校内专业代码", row.getInternalMajorCode(), "V-01普通师范生校内专业代码不能为空", "请填写试点专业代码"));
            }
            if (!StringUtils.hasText(row.getInternalMajorName())) {
                errors.add(new ValidationError("校内专业名称", row.getInternalMajorName(), "V-01普通师范生校内专业名称不能为空", "请填写试点专业名称"));
            }
        }
    }

    private void validateTextFormat(ExchangeStandardRow row, List<ValidationError> errors) {
        Map<String, String> values = Map.of(
                "学校代码", nvl(row.getSchoolCode()),
                "学号", nvl(row.getStudentNo()),
                "身份证件号码", nvl(row.getIdCardNo()),
                "出生日期", nvl(row.getBirthDate()),
                "证书编号", nvl(row.getCertNo()),
                "有效期限", nvl(row.getValidUntil())
        );
        values.forEach((field, value) -> {
            if (!StringUtils.hasText(value)) {
                return;
            }
            if (value.matches("(?i).*[0-9]+E\\+?[0-9]+.*") || value.matches("^\\d+\\.0+$")) {
                errors.add(new ValidationError(field, value, "V-02文本字段疑似被Excel转换", "请将单元格设为文本后重填"));
            }
        });
        if (StringUtils.hasText(row.getStudentNo()) && row.getStudentNo().length() > 1
                && row.getStudentNo().startsWith("0") && !row.getStudentNo().matches("^0+\\S+")) {
            errors.add(new ValidationError("学号", row.getStudentNo(), "V-02学号文本格式异常", "请以文本格式填写学号"));
        }
    }

    private void validateSchool(ExchangeStandardRow row, List<ValidationError> errors) {
        String code = paramService.getString("cert.school.code", DEFAULT_SCHOOL_CODE);
        SysDictItem school = dictItem("school", code);
        String name = school == null ? SCHOOL_NAME : school.getItemValue();
        if (!code.equals(trim(row.getSchoolCode())) || !name.equals(trim(row.getSchoolName()))) {
            errors.add(new ValidationError("学校代码/学校名称", row.getSchoolCode() + "/" + row.getSchoolName(),
                    "V-03学校代码与名称不匹配", "应为" + code + "/" + name));
        }
    }

    private void validateName(ExchangeStandardRow row, List<ValidationError> errors) {
        try {
            nameValidator.validate(row.getName());
        } catch (BizException e) {
            errors.add(new ValidationError("姓名", row.getName(), "V-04" + e.getMessage(), "请按姓名规则填写"));
        }
    }

    private void validateId(ExchangeStandardRow row, List<ValidationError> errors) {
        if (dictItem("id_card_type", trim(row.getIdCardType())) == null) {
            errors.add(new ValidationError("身份证件类型", row.getIdCardType(), "V-05证件类型不在字典范围", "请使用模板下拉值"));
            return;
        }
        try {
            row.setIdCardNo(idCardValidator.validate(row.getIdCardType(), row.getIdCardNo()));
        } catch (BizException e) {
            errors.add(new ValidationError("身份证件号码", row.getIdCardNo(), "V-05" + e.getMessage(), "请核对证件类型与号码"));
        }
    }

    private void validateBirth(ExchangeStandardRow row, List<ValidationError> errors) {
        try {
            birthDateValidator.validate(row.getIdCardType(), row.getIdCardNo(), row.getBirthDate());
        } catch (BizException e) {
            errors.add(new ValidationError("出生日期", row.getBirthDate(), "V-06" + e.getMessage(), "请核对出生日期与证件号"));
        }
    }

    private void validateIdentityType(ExchangeStandardRow row, List<ValidationError> errors) {
        if (dictItem("identity_type", trim(row.getIdentityType())) == null) {
            errors.add(new ValidationError("身份类型", row.getIdentityType(), "V-07身份类型不在字典范围", "请使用模板下拉值"));
        }
    }

    private void validateMajor(ExchangeStandardRow row, List<ValidationError> errors) {
        if ("education_master".equals(trim(row.getIdentityType()))) {
            String code = trim(row.getSecondDisciplineCode());
            if (code == null || code.length() < 4 || !EDUCATION_GRADUATE_PREFIXES.contains(code.substring(0, 4))) {
                errors.add(new ValidationError("二级学科（专业）代码", row.getSecondDisciplineCode(),
                        "V-08教育类研究生专业代码须为0401/0451/0453系列", "请填写教育类研究生标准专业代码"));
            }
            return;
        }
        try {
            Student student = minimalStudent(row);
            student.setCollegeId(resolveCollegeId(row));
            majorCodeValidator.validate(student, trainingRequest(row, student.getId()));
        } catch (BizException e) {
            errors.add(new ValidationError("二级学科（专业）代码", row.getSecondDisciplineCode(),
                    "V-08" + e.getMessage(), "请核对专业代码与试点专业范围"));
        }
    }

    private void validateTrainingLink(ExchangeStandardRow row, List<ValidationError> errors) {
        try {
            Student student = minimalStudent(row);
            student.setCollegeId(resolveCollegeId(row));
            SysMajor major = null;
            if (!"education_master".equals(trim(row.getIdentityType()))) {
                major = majorByCodeName(resolveCollegeId(row), row.getInternalMajorCode(), row.getInternalMajorName());
            }
            trainingLinkValidator.validate(trainingRequest(row, student.getId()), major);
        } catch (BizException e) {
            String field = e.getMessage().contains("任教学科") ? "任教学科" : "教育实习实践地点";
            String code = field.equals("任教学科") ? "V-10" : "V-09";
            String value = field.equals("任教学科") ? row.getTeachingSubject() : row.getInternshipLocation();
            errors.add(new ValidationError(field, value, code + e.getMessage(), "请使用模板联动下拉项"));
        }
    }

    private void validateCertificateNo(ExchangeStandardRow row, List<ValidationError> errors) {
        String certNo = trim(row.getCertNo());
        if (certNo == null || !certNo.matches("^\\d{18}$")) {
            errors.add(new ValidationError("证书编号", row.getCertNo(), "V-11证书编号必须为18位数字", "请使用系统生成的证书编号"));
            return;
        }
        try {
            String year = certNo.substring(0, 4);
            String school = fixedDigits(paramService.getString("cert.school.code", DEFAULT_SCHOOL_CODE), 5);
            String province = fixedDigits(paramService.getString("cert.province.code", DEFAULT_PROVINCE_CODE), 2);
            String levelCode = certCode("education_level", row.getEducationLevel(), "certLevelCode");
            String segmentCode = certCode("teaching_segment", row.getTeachingSegment(), "certSegmentCode");
            if (!certNo.substring(4, 9).equals(school)
                    || !certNo.substring(9, 10).equals(levelCode)
                    || !certNo.substring(10, 12).equals(province)
                    || !certNo.substring(12, 13).equals(segmentCode)
                    || !certNo.substring(13).matches("^\\d{5}$")
                    || !year.matches("^\\d{4}$")) {
                errors.add(new ValidationError("证书编号", row.getCertNo(), "V-11证书编号段码不合法", "请核对年份/学校/层次/省码/学段/序号"));
            }
        } catch (BizException e) {
            errors.add(new ValidationError("证书编号", row.getCertNo(), "V-11" + e.getMessage(), "请补齐字典段码配置"));
        }
    }

    private void validateValidity(ExchangeStandardRow row, List<ValidationError> errors) {
        String certNo = trim(row.getCertNo());
        if (certNo == null || certNo.length() < 4) {
            return;
        }
        try {
            String normalized = normalizeDate(row.getValidUntil());
            int certYear = Integer.parseInt(certNo.substring(0, 4));
            Set<String> allowed = Set.of((certYear + 3) + "/6/30", (certYear + 3) + "/12/31");
            if (!allowed.contains(normalized)) {
                errors.add(new ValidationError("有效期限", row.getValidUntil(),
                        "V-12有效期限不符合证书年份+3年的上/下半年规则", "应为" + String.join(" 或 ", allowed)));
            } else {
                row.setValidUntil(normalized);
            }
        } catch (Exception e) {
            errors.add(new ValidationError("有效期限", row.getValidUntil(), "V-12有效期限格式异常", "请填写YYYY/M/D文本"));
        }
    }

    private void validateDuplicate(ExchangeStandardRow row, List<ValidationError> errors,
                                   Map<String, Long> idCardCounts,
                                   Map<String, Long> certNoCounts) {
        if (StringUtils.hasText(row.getIdCardNo()) && idCardCounts.getOrDefault(row.getIdCardNo().trim(), 0L) > 1) {
            errors.add(new ValidationError("身份证件号码", row.getIdCardNo(), "V-13证件号码已存在", "请选择覆盖/跳过策略或核对数据"));
        }
        if (StringUtils.hasText(row.getCertNo()) && certNoCounts.getOrDefault(row.getCertNo().trim(), 0L) > 1) {
            errors.add(new ValidationError("证书编号", row.getCertNo(), "V-13证书编号已存在", "请选择覆盖/跳过策略或核对数据"));
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
            student.setIdCardNo(required(row.getIdCardNo(), "证件号码不能为空"));
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
        TrainingProfileSaveRequest request = trainingRequest(row, student.getId());
        SysMajor major = "education_master".equals(student.getIdentityType())
                ? null
                : majorByCodeName(student.getCollegeId(), row.getInternalMajorCode(), row.getInternalMajorName());
        TeachingSubject subject = trainingLinkValidator.validate(request, major);
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
        training.setTeachingSubjectId(subject.getId());
        training.setTeachingSubjectCode(subject.getSubjectCode());
        training.setTeachingSubjectName(subject.getSubjectName());
        setIfAllowed(existing, strategy, training::getInterviewOrgMode, training::setInterviewOrgMode, row.getInterviewOrgMode());
        if (!StringUtils.hasText(training.getStatus())) {
            training.setStatus(TrainingStatus.PASSED.name());
        }
        if (training.getLocked() == null) {
            training.setLocked(1);
        }
    }

    private void applyCertificate(Certificate certificate, ExchangeStandardRow row, Student student,
                                  TrainingProfile training, ImportStrategy strategy, boolean existing) {
        certificate.setStudentId(student.getId());
        certificate.setCollegeId(student.getCollegeId());
        certificate.setAssessmentYear(assessmentYear(row));
        setIfAllowed(existing, strategy, certificate::getCertNo, certificate::setCertNo, row.getCertNo());
        certificate.setStudentNo(student.getStudentNo());
        certificate.setStudentName(student.getName());
        certificate.setIdCardType(student.getIdCardType());
        certificate.setIdCardNo(student.getIdCardNo());
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

    private RollbackDecision rollbackOne(ImportRecordRef ref) {
        if ("student".equals(ref.getTableName())) {
            Student current = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                    .eq(Student::getId, ref.getRecordId())
                    .last("FOR UPDATE"));
            return rollbackEntity(ref, current, Student.class, studentMapper::updateById,
                    id -> studentMapper.deleteById(id), "学生");
        }
        if ("training_profile".equals(ref.getTableName())) {
            TrainingProfile current = trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                    .eq(TrainingProfile::getId, ref.getRecordId())
                    .last("FOR UPDATE"));
            return rollbackEntity(ref, current, TrainingProfile.class, trainingProfileMapper::updateById,
                    id -> trainingProfileMapper.deleteById(id), "培养信息");
        }
        if ("certificate".equals(ref.getTableName())) {
            Certificate current = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                    .eq(Certificate::getId, ref.getRecordId())
                    .last("FOR UPDATE"));
            return rollbackEntity(ref, current, Certificate.class, certificateMapper::updateById,
                    id -> certificateMapper.deleteById(id), "证书");
        }
        return new RollbackDecision(false, "未知回滚表: " + ref.getTableName());
    }

    private <T> RollbackDecision rollbackEntity(ImportRecordRef ref, T current, Class<T> type,
                                                Function<T, Integer> updater, Function<Long, Integer> deleter,
                                                String label) {
        if (current == null) {
            return new RollbackDecision(false, label + "#" + ref.getRecordId() + "不存在，跳过");
        }
        if (!jsonEquals(snapshot(current), ref.getAfterJson())) {
            return new RollbackDecision(false, label + "#" + ref.getRecordId() + "已被后续修改，跳过");
        }
        if ("INSERT".equals(ref.getAction())) {
            deleter.apply(ref.getRecordId());
            return new RollbackDecision(true, "已删除新增" + label);
        }
        if ("UPDATE".equals(ref.getAction())) {
            T before = readJson(ref.getBeforeJson(), type);
            updater.apply(before);
            return new RollbackDecision(true, "已还原" + label);
        }
        return new RollbackDecision(false, label + "#" + ref.getRecordId() + "动作不可回滚: " + ref.getAction());
    }

    private List<Certificate> selectCertificates(ExchangeQuery query) {
        ExchangeQuery q = query == null ? new ExchangeQuery() : query;
        LambdaQueryWrapper<Certificate> wrapper = new LambdaQueryWrapper<Certificate>()
                .orderByAsc(Certificate::getAssessmentYear)
                .orderByAsc(Certificate::getStudentNo);
        if (StringUtils.hasText(q.getAssessmentYear())) {
            wrapper.eq(Certificate::getAssessmentYear, q.getAssessmentYear().trim());
        }
        if (q.getCollegeId() != null) {
            wrapper.eq(Certificate::getCollegeId, q.getCollegeId());
        }
        if (StringUtils.hasText(q.getCertStatus())) {
            wrapper.eq(Certificate::getStatus, q.getCertStatus().trim());
        }
        if (StringUtils.hasText(q.getTrainingGoal())) {
            wrapper.eq(Certificate::getTrainingGoal, q.getTrainingGoal().trim());
        }
        if (StringUtils.hasText(q.getTeachingSegment())) {
            wrapper.eq(Certificate::getTeachingSegment, q.getTeachingSegment().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String keyword = q.getKeyword().trim();
            wrapper.and(w -> w.like(Certificate::getStudentNo, keyword)
                    .or()
                    .like(Certificate::getStudentName, keyword)
                    .or()
                    .like(Certificate::getCertNo, keyword)
                    .or()
                    .like(Certificate::getIdCardNo, keyword));
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
        ExchangeStandardRow row = new ExchangeStandardRow();
        row.setSequenceNo(String.valueOf(sequence));
        row.setSchoolCode(paramService.getString("cert.school.code", DEFAULT_SCHOOL_CODE));
        SysDictItem school = dictItem("school", row.getSchoolCode());
        row.setSchoolName(school == null ? SCHOOL_NAME : school.getItemValue());
        row.setStudentNo(cert.getStudentNo());
        row.setName(cert.getStudentName());
        row.setGender(student == null ? "" : student.getGender());
        row.setIdCardType(cert.getIdCardType());
        row.setIdCardNo(sensitive ? cert.getIdCardNo() : SensitiveMasker.idCard(cert.getIdCardNo()));
        row.setBirthDate(student == null ? "" : student.getBirthDate());
        row.setIdentityType(student == null ? "" : student.getIdentityType());
        row.setSourcePlace(student == null ? "" : student.getSourceFull());
        row.setSecondDisciplineCode(training == null ? "" : training.getSecondDisciplineCode());
        row.setSecondDisciplineName(training == null ? "" : training.getSecondDisciplineName());
        row.setInternalMajorCode(training == null ? "" : training.getInternalMajorCode());
        row.setInternalMajorName(training == null ? "" : training.getInternalMajorName());
        row.setEducationLevel(cert.getEducationLevel());
        row.setTrainingGoal(cert.getTrainingGoal());
        row.setInternshipOrgMode(training == null ? "" : training.getInternshipOrgMode());
        row.setInternshipLocation(training == null ? "" : training.getInternshipLocation());
        row.setTeachingSegment(cert.getTeachingSegment());
        row.setTeachingSubject(cert.getTeachingSubjectCode());
        row.setInterviewOrgMode(training == null ? "" : training.getInterviewOrgMode());
        row.setCertNo(cert.getCertNo());
        row.setValidUntil(cert.getValidUntil());
        row.setIssuer(cert.getIssuer());
        // Phase 43.2 §7.4：备注列承载「学院ID」，与导入端 resolveCollegeId 读备注解析学院ID 对齐，
        // 保证 导出→导入 学院标识无损往返。旧实现把证书状态写入备注（与导入语义冲突：
        // 重导出文件时该列被当作学院ID parseLong，破坏学院识别）；证书状态另有汇总表/完整审核表的
        // 「证书状态」专列承载，不再复用备注一列表达两种含义。
        row.setRemark(cert.getCollegeId() == null ? "" : String.valueOf(cert.getCollegeId()));
        return row;
    }

    private ExchangeFile exportErrors() {
        List<ImportErrorDetail> errors = errorMapper.selectList(new LambdaQueryWrapper<ImportErrorDetail>()
                .orderByDesc(ImportErrorDetail::getCreatedAt)
                .orderByAsc(ImportErrorDetail::getBatchNo)
                .orderByAsc(ImportErrorDetail::getRowNo));
        byte[] content = excelHelper.writeErrorWorkbook(errors.stream()
                .map(item -> new ExchangeExcelHelper.ErrorRow(item.getBatchNo(), item.getRowNo(), item.getStudentNo(),
                        item.getStudentName(), item.getFieldName(), item.getErrorValue(), item.getErrorReason(), item.getSuggestion()))
                .toList());
        recordExportBatch("export", ExchangeExportType.ERROR.name(), new ExchangeQuery(), errors.size(), errors.size(), 0, "异常数据表.xlsx");
        return new ExchangeFile("异常数据表.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content);
    }

    private List<String> fullReviewHeaders() {
        List<String> headers = new ArrayList<>(ExchangeColumn.ALL.stream().map(ExchangeColumn::header).toList());
        headers.addAll(List.of("基本信息状态", "培养信息状态", "材料状态", "视频终分", "视频结论", "证书状态"));
        return headers;
    }

    private List<List<String>> fullReviewRows(List<Certificate> certificates, List<ExchangeStandardRow> standardRows,
            Map<Long, Student> students, Map<Long, List<TrainingProfile>> trainingByStudent,
            Map<Long, List<VideoReview>> videosByStudent, Map<Long, List<ProcessMaterial>> materialsByStudent) {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < certificates.size(); i++) {
            Certificate cert = certificates.get(i);
            ExchangeStandardRow standard = standardRows.get(i);
            List<String> values = ExchangeColumn.ALL.stream().map(col -> nvl(col.value(standard))).collect(Collectors.toCollection(ArrayList::new));
            Student student = students.get(cert.getStudentId());
            TrainingProfile training = trainingFor(trainingByStudent, cert.getStudentId(), cert.getAssessmentYear());
            VideoReview video = videoFor(videosByStudent, cert.getStudentId(), cert.getAssessmentYear());
            values.add(student == null ? "" : student.getStatus());
            values.add(training == null ? "" : training.getStatus());
            values.add(materialSummaryFor(materialsByStudent, cert.getStudentId(), cert.getAssessmentYear()));
            values.add(video == null || video.getFinalScore() == null ? "" : String.valueOf(video.getFinalScore()));
            values.add(video == null ? "" : nvl(video.getFinalConclusion()));
            values.add(nvl(cert.getStatus()));
            rows.add(values);
        }
        return rows;
    }

    private List<String> certSummaryHeaders() {
        return List.of("学号", "姓名", "身份证件号码", "身份类型", "学历层次", "培养目标", "任教学段",
                "任教学科", "证书编号", "签发人", "签发日期", "有效期限", "证书状态");
    }

    private List<List<String>> certSummaryRows(List<Certificate> certificates, boolean sensitive, Map<Long, Student> students) {
        List<List<String>> rows = new ArrayList<>();
        for (Certificate cert : certificates) {
            Student student = students.get(cert.getStudentId());
            rows.add(List.of(
                    nvl(cert.getStudentNo()),
                    nvl(cert.getStudentName()),
                    sensitive ? nvl(cert.getIdCardNo()) : nvl(SensitiveMasker.idCard(cert.getIdCardNo())),
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

    private List<List<String>> attachmentRows(Set<Long> studentIds, ExchangeQuery query) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> students = studentsByIds(studentIds);
        LambdaQueryWrapper<ProcessMaterial> materialWrapper = new LambdaQueryWrapper<ProcessMaterial>()
                .in(ProcessMaterial::getStudentId, studentIds)
                .orderByAsc(ProcessMaterial::getStudentId)
                .orderByAsc(ProcessMaterial::getCategory);
        if (query != null && StringUtils.hasText(query.getAssessmentYear())) {
            materialWrapper.eq(ProcessMaterial::getAssessmentYear, query.getAssessmentYear().trim());
        }
        List<List<String>> rows = new ArrayList<>();
        for (ProcessMaterial material : materialMapper.selectList(materialWrapper)) {
            Student student = students.get(material.getStudentId());
            String link = "";
            if (material.getFileId() != null && material.getFileId() > 0) {
                try {
                    link = fileService.presignedGet(material.getFileId(), 600);
                } catch (BizException ignored) {
                    link = "";
                }
            }
            rows.add(List.of(
                    student == null ? "" : nvl(student.getStudentNo()),
                    student == null ? "" : nvl(student.getName()),
                    nvl(material.getCategory()),
                    nvl(material.getFileName()),
                    nvl(material.getStatus()),
                    material.getSecondReviewerId() == null ? "" : String.valueOf(material.getSecondReviewerId()),
                    material.getSecondReviewTime() == null ? "" : material.getSecondReviewTime().toString(),
                    link
            ));
        }
        for (VideoReview video : videoReviewRows(studentIds, query)) {
            Student student = students.get(video.getStudentId());
            rows.add(List.of(
                    student == null ? "" : nvl(student.getStudentNo()),
                    student == null ? "" : nvl(student.getName()),
                    "教学能力视频",
                    nvl(video.getVideoFileName()),
                    nvl(video.getStatus()),
                    video.getConfirmedBy() == null ? "" : String.valueOf(video.getConfirmedBy()),
                    video.getConfirmedAt() == null ? "" : video.getConfirmedAt().toString(),
                    ""
            ));
        }
        return rows;
    }

    private List<VideoReview> videoReviewRows(Set<Long> studentIds, ExchangeQuery query) {
        LambdaQueryWrapper<VideoReview> wrapper = new LambdaQueryWrapper<VideoReview>()
                .in(VideoReview::getStudentId, studentIds)
                .orderByAsc(VideoReview::getStudentId);
        if (query != null && StringUtils.hasText(query.getAssessmentYear())) {
            wrapper.eq(VideoReview::getAssessmentYear, query.getAssessmentYear().trim());
        }
        return videoReviewMapper.selectList(wrapper);
    }

    private byte[] zipSingleFile(String filename, byte[] content) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(filename));
            zip.write(content);
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException("生成压缩包失败");
        }
    }

    private Map<String, List<String>> dropdowns() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("gender", dictCodes("gender"));
        values.put("idCardType", dictCodes("id_card_type"));
        values.put("identityType", dictCodes("identity_type"));
        values.put("educationLevel", dictCodes("education_level"));
        values.put("trainingGoal", dictCodes("training_goal"));
        values.put("internshipOrgMode", dictCodes("internship_org_mode"));
        values.put("internshipLocation", dictCodes("internship_location"));
        values.put("teachingSegment", dictCodes("teaching_segment"));
        values.put("teachingSubject", teachingSubjectMapper.selectList(new LambdaQueryWrapper<TeachingSubject>()
                .eq(TeachingSubject::getStatus, 1)
                .eq(TeachingSubject::getYearVersion, DEFAULT_YEAR_VERSION)
                .orderByAsc(TeachingSubject::getSegmentCode)
                .orderByAsc(TeachingSubject::getSubjectCode))
                .stream()
                .filter(item -> item.getIsCategory() == null || item.getIsCategory() == 0)
                .map(TeachingSubject::getSubjectCode)
                .toList());
        values.put("interviewOrgMode", dictCodes("interview_org_mode"));
        return values;
    }

    private List<String> dictCodes(String typeCode) {
        return dictItems(typeCode).stream().map(SysDictItem::getItemCode).toList();
    }

    private List<SysDictItem> dictItems(String typeCode) {
        return dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getStatus, 1)
                .eq(SysDictItem::getYearVersion, DEFAULT_YEAR_VERSION)
                .orderByAsc(SysDictItem::getSort));
    }

    private SysDictItem dictItem(String typeCode, String itemCode) {
        if (!StringUtils.hasText(itemCode)) {
            return null;
        }
        return dictItemMapper.selectOne(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getItemCode, itemCode.trim())
                .eq(SysDictItem::getStatus, 1)
                .last("LIMIT 1"));
    }

    private Long resolveCollegeId(ExchangeStandardRow row) {
        Long direct = parseLong(trim(row.getRemark()));
        if (direct != null && collegeMapper.selectById(direct) != null) {
            return direct;
        }
        SysMajor major = majorMapper.selectOne(new LambdaQueryWrapper<SysMajor>()
                .eq(SysMajor::getInternalMajorCode, trim(row.getInternalMajorCode()))
                .eq(SysMajor::getInternalMajorName, trim(row.getInternalMajorName()))
                .eq(SysMajor::getYearVersion, DEFAULT_YEAR_VERSION)
                .eq(SysMajor::getStatus, 1)
                .last("LIMIT 1"));
        if (major != null) {
            return major.getCollegeId();
        }
        List<Long> allowed = allowedCollegeIds("exchange:import");
        if (allowed.size() == 1) {
            return allowed.get(0);
        }
        throw new BizException("无法识别导入行所属学院，请填写校内专业或在备注填学院ID");
    }

    private List<Long> allowedCollegeIds(String permission) {
        DataScopeContext.Scope scope = dataScopeService.resolve(permission);
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权导入该学院数据");
        }
        if (scope.allSchool()) {
            return collegeMapper.selectList(new LambdaQueryWrapper<SysCollege>().eq(SysCollege::getStatus, 1))
                    .stream().map(SysCollege::getId).toList();
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE) {
            return new ArrayList<>(scope.getCollegeIds());
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权导入该学院数据");
    }

    private void ensureCanImportCollege(Long collegeId) {
        DataScopeContext.Scope scope = dataScopeService.resolve("exchange:import");
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权导入该学院数据");
        }
        if (scope.allSchool()) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE && scope.getCollegeIds().contains(collegeId)) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权导入该学院数据");
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
        TrainingProfileSaveRequest request = new TrainingProfileSaveRequest();
        request.setStudentId(studentId == null ? 0L : studentId);
        request.setAssessmentYear(assessmentYear(row));
        request.setSecondDisciplineCode(trim(row.getSecondDisciplineCode()));
        request.setSecondDisciplineName(trim(row.getSecondDisciplineName()));
        request.setInternalMajorCode(trim(row.getInternalMajorCode()));
        request.setInternalMajorName(trim(row.getInternalMajorName()));
        request.setEducationLevel(trim(row.getEducationLevel()));
        request.setTrainingGoal(trim(row.getTrainingGoal()));
        request.setInternshipOrgMode(trim(row.getInternshipOrgMode()));
        request.setInternshipLocation(trim(row.getInternshipLocation()));
        request.setTeachingSegment(trim(row.getTeachingSegment()));
        request.setTeachingSubjectCode(trim(row.getTeachingSubject()));
        request.setInterviewOrgMode(trim(row.getInterviewOrgMode()));
        request.setAbilityTestConclusion("qualified");
        return request;
    }

    private Student minimalStudent(ExchangeStandardRow row) {
        Student student = new Student();
        student.setStudentNo(trim(row.getStudentNo()));
        student.setName(trim(row.getName()));
        student.setGender(trim(row.getGender()));
        student.setIdCardType(trim(row.getIdCardType()));
        student.setIdCardNo(trim(row.getIdCardNo()));
        student.setBirthDate(trim(row.getBirthDate()));
        student.setIdentityType(trim(row.getIdentityType()));
        return student;
    }

    private SysMajor majorByCodeName(Long collegeId, String code, String name) {
        if (collegeId == null || !StringUtils.hasText(code) || !StringUtils.hasText(name)) {
            return null;
        }
        return majorMapper.selectOne(new LambdaQueryWrapper<SysMajor>()
                .eq(SysMajor::getCollegeId, collegeId)
                .eq(SysMajor::getInternalMajorCode, code.trim())
                .eq(SysMajor::getInternalMajorName, name.trim())
                .eq(SysMajor::getYearVersion, DEFAULT_YEAR_VERSION)
                .eq(SysMajor::getStatus, 1)
                .last("LIMIT 1"));
    }

    private Student studentByNo(String studentNo) {
        if (!StringUtils.hasText(studentNo)) {
            return null;
        }
        return studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getStudentNo, studentNo.trim())
                .last("LIMIT 1"));
    }

    private Student studentByIdCard(String idCardNo) {
        if (!StringUtils.hasText(idCardNo)) {
            return null;
        }
        return studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                .eq(Student::getIdCardNo, idCardNo.trim())
                .last("LIMIT 1"));
    }

    private Certificate certificateByNo(String certNo) {
        if (!StringUtils.hasText(certNo)) {
            return null;
        }
        return certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getCertNo, certNo.trim())
                .last("LIMIT 1"));
    }

    private Certificate certificateByStudentYear(Long studentId, String year) {
        return certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                .eq(Certificate::getStudentId, studentId)
                .eq(Certificate::getAssessmentYear, year)
                .last("LIMIT 1"));
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
        detail.setErrorValue(dbText(value, 512));
        detail.setErrorReason(dbText(reason, 512));
        detail.setSuggestion(dbText(suggestion, 512));
        errorMapper.insert(detail);
    }

    private ImportErrorDetail toErrorDetail(ImportExportBatch batch, ExchangeExcelHelper.ReadRow readRow, ValidationError error) {
        ImportErrorDetail detail = new ImportErrorDetail();
        detail.setBatchId(batch.getId());
        detail.setBatchNo(batch.getBatchNo());
        detail.setRowNo(readRow.rowNo());
        detail.setStudentNo(dbText(readRow.row().getStudentNo(), 64));
        detail.setStudentName(dbText(readRow.row().getName(), 64));
        detail.setFieldName(dbText(error.fieldName(), 128));
        detail.setErrorValue(dbText(error.errorValue(), 512));
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
        vo.setErrorValue(detail.getErrorValue());
        vo.setErrorReason(detail.getErrorReason());
        vo.setSuggestion(detail.getSuggestion());
        return vo;
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

    private ImportExportBatch requireImportingBatchForUpdate(Long batchId) {
        ImportExportBatch batch = requireBatchForUpdate(batchId);
        if (ExchangeBatchStatus.of(batch.getStatus()) != ExchangeBatchStatus.IMPORTING) {
            throw new ImportExecutionStoppedException(batch.getStatus());
        }
        return batch;
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

    private void recordExportBatch(String type, String exportType, ExchangeQuery query,
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
        batch.setScopeJson(writeJson(query == null ? new ExchangeQuery() : query));
        batch.setStrategy(exportType);
        batch.setStatus(ExchangeBatchStatus.EXPORTED.name());
        batchMapper.insert(batch);
        if ("export".equals(type)) {
            notificationService.send(operatorId, "EXPORT_DONE", "导出完成",
                    StringUtils.hasText(fileName) ? "导出完成：" + fileName : "导出完成",
                    "import_export_batch", String.valueOf(batch.getId()));
        }
    }

    private String nextBatchNo(String prefix) {
        return prefix + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "-" + IdWorker.getIdStr().substring(12);
    }

    private String assessmentYear(ExchangeStandardRow row) {
        String certNo = required(row.getCertNo(), "证书编号不能为空");
        if (certNo.length() >= 4) {
            return certNo.substring(0, 4);
        }
        return required(row.getSequenceNo(), "考核年度不能为空");
    }

    private String certCode(String typeCode, String itemCode, String fieldName) {
        SysDictItem item = dictItem(typeCode, itemCode);
        if (item == null || !StringUtils.hasText(item.getExtJson())) {
            throw new BizException(typeCode + "证书段码未配置: " + itemCode);
        }
        try {
            JsonNode node = objectMapper.readTree(item.getExtJson());
            String value = node.path(fieldName).asText(null);
            if (!StringUtils.hasText(value)) {
                throw new BizException(typeCode + "证书段码未配置: " + itemCode);
            }
            return value;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(typeCode + "证书段码解析失败: " + itemCode);
        }
    }

    private String fixedDigits(String value, int length) {
        String text = required(value, "参数不能为空");
        if (!text.matches("^\\d{" + length + "}$")) {
            throw new BizException("参数必须为" + length + "位数字");
        }
        return text;
    }

    private String normalizeDate(String value) {
        String text = required(value, "日期不能为空");
        for (DateTimeFormatter formatter : List.of(
                DateTimeFormatter.ofPattern("yyyy/M/d"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ISO_LOCAL_DATE)) {
            try {
                LocalDate date = LocalDate.parse(text, formatter);
                return date.getYear() + "/" + date.getMonthValue() + "/" + date.getDayOfMonth();
            } catch (Exception ignored) {
            }
        }
        throw new BizException("日期格式不正确");
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

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BizException("JSON解析失败");
        }
    }

    private List<PreviewPayload> readPreviews(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, PREVIEW_LIST_TYPE);
        } catch (Exception e) {
            throw new BizException("预校验成功行解析失败");
        }
    }

    private boolean jsonEquals(String left, String right) {
        try {
            return Objects.equals(normalizedSnapshot(left), normalizedSnapshot(right));
        } catch (Exception e) {
            return Objects.equals(left, right);
        }
    }

    private String importFailureMessage(Exception e) {
        Throwable cursor = e;
        while (cursor != null) {
            if (cursor instanceof BizException bizException && StringUtils.hasText(bizException.getMessage())) {
                return bizException.getMessage();
            }
            cursor = cursor.getCause();
        }
        if (StringUtils.hasText(e.getMessage())) {
            return e.getMessage();
        }
        return "导入失败";
    }

    private JsonNode normalizedSnapshot(String json) throws IOException {
        JsonNode node = objectMapper.readTree(json);
        if (node instanceof ObjectNode objectNode) {
            objectNode.remove(List.of("createdAt", "updatedAt", "createdBy", "updatedBy", "deleted"));
        }
        return node;
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

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value) || !value.matches("^\\d+$")) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface SupplierString {
        String get();
    }

    @FunctionalInterface
    private interface ConsumerString {
        void accept(String value);
    }

    private record ValidationError(String fieldName, String errorValue, String errorReason, String suggestion) {
    }

    private record PreviewPayload(Integer rowNo, ExchangeStandardRow row) {
    }

    private record ImportDecision(boolean success, String message) {
    }

    private record RollbackDecision(boolean success, String message) {
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
