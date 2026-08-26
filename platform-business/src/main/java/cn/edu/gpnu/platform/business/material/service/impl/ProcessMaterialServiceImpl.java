package cn.edu.gpnu.platform.business.material.service.impl;

import cn.edu.gpnu.platform.business.material.dto.MaterialBatchDownloadRequest;
import cn.edu.gpnu.platform.business.material.dto.MaterialQuery;
import cn.edu.gpnu.platform.business.material.dto.MaterialReviewRequest;
import cn.edu.gpnu.platform.business.material.dto.MaterialDirectUploadInitRequest;
import cn.edu.gpnu.platform.business.material.dto.MaterialDirectUploadCompleteRequest;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.material.service.ProcessMaterialService;
import cn.edu.gpnu.platform.business.material.support.MaterialStatus;
import cn.edu.gpnu.platform.business.material.vo.BatchDownloadFile;
import cn.edu.gpnu.platform.business.material.vo.ProcessMaterialVO;
import cn.edu.gpnu.platform.business.material.vo.ProcessStatusVO;
import cn.edu.gpnu.platform.business.material.vo.MaterialDirectUploadVO;
import cn.edu.gpnu.platform.business.material.vo.MaterialPresignedPartVO;
import cn.edu.gpnu.platform.business.material.vo.MaterialUploadedPartVO;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.support.ReviewNotificationHelper;
import cn.edu.gpnu.platform.common.api.PageQuery;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.model.DirectFileUploadPlan;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.DictService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessMaterialServiceImpl implements ProcessMaterialService {

    private static final String MATERIAL_BIZ_TYPE = "process-material";
    private static final long DEFAULT_MATERIAL_MAX_SIZE = 52_428_800L;

    private final ProcessMaterialMapper processMaterialMapper;
    private final StudentMapper studentMapper;
    private final DictService dictService;
    private final FileObjectMapper fileObjectMapper;
    private final FileService fileService;
    private final MinioProperties minioProperties;
    private final DataScopeService dataScopeService;
    private final ParamService paramService;
    private final MinioClient minioClient;
    private final ReviewNotificationHelper notificationHelper;
    private final AuditLogService auditLogService;

    // Phase 44e（P1-1 真分页 rollout）：由「全表 selectList 后 new PageResult<>(size, records)」改为
    // MyBatis-Plus Page + selectPage 真分页。@DataScope（ProcessMaterialController.list，alias=process_material）
    // 设置的线程范围经数据权限拦截器在 selectPage 的 count 与数据两条 SQL 上均生效 → 该页与 total 同为
    // 「已按学院/本人范围过滤」的结果。批量下载（batchDownload）仍需全量，继续走 selectMaterials/selectList，不受影响。
    @Override
    public PageResult<ProcessMaterialVO> list(MaterialQuery query) {
        MaterialQuery q = query == null ? new MaterialQuery() : query;
        Page<ProcessMaterial> result = processMaterialMapper.selectPage(
                PageQuery.of(q.getPage(), q.getSize()), buildListWrapper(q, null));
        Map<Long, Student> students = students(result.getRecords());
        Map<String, String> categories = categoryLabels();
        List<ProcessMaterialVO> records = result.getRecords().stream()
                .map(item -> toVO(item, students.get(item.getStudentId()), categories))
                .toList();
        return new PageResult<>(result.getTotal(), records);
    }

    @Override
    // P0-11 收尾：去 @Transactional，使 fileService.upload 的 MinIO putObject 在无环绕事务下执行、不占用 DB 连接。
    // 本方法仅 1 次业务写（material insert），autocommit 即原子；插入失败仅遗留孤儿 file_object（罕见、无引用）。
    public Long upload(Long studentId, String assessmentYear, String category, InputStream input,
                       String originalFilename, String contentType, long size) {
        Student student = requireStudent(studentId);
        ensureCanWriteStudent(student, "material:upload");
        validateCategory(category);
        validateFile(originalFilename, contentType, size);
        FileObject file = fileService.upload(input, requiredTrim(originalFilename, "文件名不能为空"),
                normalizeContentType(originalFilename, contentType), size, MATERIAL_BIZ_TYPE);
        ProcessMaterial entity = new ProcessMaterial();
        entity.setStudentId(student.getId());
        entity.setCollegeId(student.getCollegeId());
        entity.setAssessmentYear(requiredTrim(assessmentYear, "考核年度不能为空"));
        entity.setCategory(requiredTrim(category, "材料类别不能为空"));
        entity.setStatus(MaterialStatus.DRAFT.name());
        entity.setLocked(0);
        fillFile(entity, file);
        processMaterialMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public MaterialDirectUploadVO initDirectUpload(MaterialDirectUploadInitRequest request) {
        DirectMaterialContext context = directMaterialContext(request.getMaterialId(), request.getStudentId(),
                request.getAssessmentYear(), request.getCategory());
        validateFile(request.getFileName(), request.getContentType(), request.getSize());
        if (!minioProperties.isDirectUploadEnabled()) {
            MaterialDirectUploadVO fallback = new MaterialDirectUploadVO();
            fallback.setUploadMode("SERVER_UPLOAD");
            fallback.setPartSize(request.getPartSize());
            fallback.setUploadedParts(List.of());
            fallback.setParts(List.of());
            return fallback;
        }
        DirectFileUploadPlan plan = fileService.initDirectUpload(
                requiredTrim(request.getFileName(), "文件名不能为空"),
                normalizeContentType(request.getFileName(), request.getContentType()),
                request.getSize(), MATERIAL_BIZ_TYPE,
                requiredTrim(request.getFileHash(), "文件摘要不能为空").toLowerCase(Locale.ROOT),
                request.getPartSize(), directMetadata(context));
        MaterialDirectUploadVO vo = new MaterialDirectUploadVO();
        vo.setFileId(plan.fileId());
        vo.setUploadMode(plan.ready() ? "READY" : "PRESIGNED_MULTIPART");
        vo.setPartSize(plan.partSize());
        if (plan.ready()) {
            vo.setUploadedParts(List.of());
            vo.setParts(List.of());
            return vo;
        }
        vo.setUploadedParts(plan.upload().uploadedParts().stream().map(part -> {
            MaterialUploadedPartVO item = new MaterialUploadedPartVO();
            item.setPartNumber(part.partNumber());
            item.setEtag(part.eTag());
            item.setSize(part.size());
            return item;
        }).toList());
        vo.setParts(plan.upload().parts().stream().map(part -> {
            MaterialPresignedPartVO item = new MaterialPresignedPartVO();
            item.setPartNumber(part.partNumber());
            item.setUrl(part.url());
            item.setExpiresAt(part.expiresAt());
            return item;
        }).toList());
        return vo;
    }

    @Override
    public Long completeDirectUpload(MaterialDirectUploadCompleteRequest request) {
        DirectMaterialContext context = directMaterialContext(request.getMaterialId(), request.getStudentId(),
                request.getAssessmentYear(), request.getCategory());
        List<MultipartUploadedPart> parts = request.getParts().stream()
                .map(part -> new MultipartUploadedPart(part.getPartNumber(), part.getEtag(), 0L))
                .toList();
        FileObject file = fileService.completeDirectUpload(request.getFileId(), parts, directMetadata(context));
        if (!MATERIAL_BIZ_TYPE.equals(file.getBizType()) || !"READY".equals(file.getStatus())) {
            throw new BizException("直传文件不是可绑定的材料对象");
        }
        ProcessMaterial bound = processMaterialMapper.selectOne(new LambdaQueryWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getFileId, file.getId())
                .last("LIMIT 1"));
        if (bound != null) {
            return bound.getId();
        }
        if (context.existing() != null) {
            Long expectedFileId = context.existing().getFileId();
            ProcessMaterial entity = requireMaterial(context.existing().getId());
            ensureCanWriteMaterial(entity, "material:upload");
            ensureEditable(entity);
            String expectedStatus = entity.getStatus();
            Integer expectedLocked = entity.getLocked();
            fillFile(entity, file);
            int changed = updateMaterialFile(entity, expectedFileId, expectedStatus, expectedLocked);
            if (changed != 1) {
                ProcessMaterial current = requireMaterial(entity.getId());
                if (file.getId().equals(current.getFileId())) {
                    return current.getId();
                }
                throw new BizException("材料已被其他上传替换，请刷新后重试");
            }
            return entity.getId();
        }
        ProcessMaterial entity = new ProcessMaterial();
        entity.setStudentId(context.student().getId());
        entity.setCollegeId(context.student().getCollegeId());
        entity.setAssessmentYear(context.assessmentYear());
        entity.setCategory(context.category());
        entity.setStatus(MaterialStatus.DRAFT.name());
        entity.setLocked(0);
        fillFile(entity, file);
        try {
            processMaterialMapper.insert(entity);
            return entity.getId();
        } catch (DuplicateKeyException duplicate) {
            ProcessMaterial concurrentlyBound = processMaterialMapper.selectOne(
                    new LambdaQueryWrapper<ProcessMaterial>()
                            .eq(ProcessMaterial::getFileId, file.getId())
                            .last("LIMIT 1"));
            if (concurrentlyBound != null) {
                return concurrentlyBound.getId();
            }
            throw duplicate;
        }
    }

    @Override
    public void cancelDirectUpload(Long fileId) {
        fileService.cancelDirectUpload(fileId);
    }

    @Override
    // P0-11 收尾：去 @Transactional，使 MinIO putObject 不占用 DB 连接（本方法仅 1 次业务写 updateById，autocommit 原子）。
    public void replace(Long id, InputStream input, String originalFilename, String contentType, long size) {
        ProcessMaterial entity = requireMaterial(id);
        ensureCanWriteMaterial(entity, "material:upload");
        ensureEditable(entity);
        Long expectedFileId = entity.getFileId();
        String expectedStatus = entity.getStatus();
        Integer expectedLocked = entity.getLocked();
        validateFile(originalFilename, contentType, size);
        FileObject file = fileService.upload(input, requiredTrim(originalFilename, "文件名不能为空"),
                normalizeContentType(originalFilename, contentType), size, MATERIAL_BIZ_TYPE);
        fillFile(entity, file);
        entity.setUploaderId(UserContext.getUserIdOrSystem());
        entity.setUploadTime(LocalDateTime.now());
        if (updateMaterialFile(entity, expectedFileId, expectedStatus, expectedLocked) == 0) {
            throw new BizException("操作冲突：材料已被提交、审核或其他上传替换，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProcessMaterial entity = requireMaterial(id);
        ensureCanWriteMaterial(entity, "material:upload");
        ensureEditable(entity);
        if (processMaterialMapper.delete(new LambdaQueryWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getId, id)
                .eq(ProcessMaterial::getFileId, entity.getFileId())
                .eq(ProcessMaterial::getStatus, entity.getStatus())
                .eq(ProcessMaterial::getLocked, entity.getLocked())) == 0) {
            throw new BizException("操作冲突：材料已被提交、审核或替换，请刷新后重试");
        }
    }

    @Override
    public Long previewFileId(Long id) {
        ProcessMaterial entity = requireMaterial(id);
        ensureReadableMaterial(entity);
        return entity.getFileId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        ProcessMaterial entity = requireMaterial(id);
        ensureCanWriteMaterial(entity, "material:upload");
        MaterialStatus status = MaterialStatus.of(entity.getStatus());
        if (!status.editable()) {
            throw new BizException("当前状态不可提交");
        }
        String oldStatus = entity.getStatus();
        String targetStatus = returnTargetFromSecondRejected(status);
        entity.setStatus(targetStatus);
        if (processMaterialMapper.update(new ProcessMaterial(), new LambdaUpdateWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getId, id)
                .eq(ProcessMaterial::getStatus, oldStatus)
                .set(ProcessMaterial::getStatus, targetStatus)) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        notificationHelper.notifySubmitted(entity.getCollegeId(), entity.getStudentId(), "过程性材料",
                targetStatus, "process_material", entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void firstReview(Long id, MaterialReviewRequest request) {
        ProcessMaterial entity = requireMaterial(id);
        ensureCanWriteMaterial(entity, "material:firstReview");
        if (MaterialStatus.of(entity.getStatus()) != MaterialStatus.FIRST_REVIEW) {
            throw new BizException("当前状态不可初审");
        }
        String oldStatus = entity.getStatus();
        String action = normalizeAction(request.getAction());
        if ("PASS".equals(action)) {
            entity.setStatus(MaterialStatus.SECOND_REVIEW.name());
        } else if ("REJECT".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(MaterialStatus.FIRST_REJECTED.name());
        } else if ("FAIL".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(MaterialStatus.FAILED.name());
            entity.setLocked(1);
        } else {
            throw new BizException("审核结论不支持");
        }
        entity.setFirstReviewStatus(action);
        entity.setFirstReviewerId(UserContext.getUserIdOrSystem());
        entity.setFirstReviewTime(LocalDateTime.now());
        entity.setFirstReviewComment(trimToNull(request.getComment()));
        LambdaUpdateWrapper<ProcessMaterial> update = new LambdaUpdateWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getId, id)
                .eq(ProcessMaterial::getStatus, oldStatus)
                .set(ProcessMaterial::getStatus, entity.getStatus())
                .set("FAIL".equals(action), ProcessMaterial::getLocked, entity.getLocked())
                .set(ProcessMaterial::getFirstReviewStatus, entity.getFirstReviewStatus())
                .set(ProcessMaterial::getFirstReviewerId, entity.getFirstReviewerId())
                .set(ProcessMaterial::getFirstReviewTime, entity.getFirstReviewTime())
                .set(ProcessMaterial::getFirstReviewComment, entity.getFirstReviewComment());
        if (processMaterialMapper.update(new ProcessMaterial(), update) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        auditLogService.record("material", entity.getId(), materialTarget(entity), "firstReview",
                oldStatus, entity.getStatus(), trimToNull(request.getComment()));
        if ("PASS".equals(action)) {
            notificationHelper.notifyFirstReviewPassed(entity.getCollegeId(), entity.getStudentId(), "过程性材料",
                    "process_material", entity.getId());
        } else {
            notificationHelper.notifyReturnedToStudent(entity.getStudentId(), "过程性材料", action,
                    "process_material", entity.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void secondReview(Long id, MaterialReviewRequest request) {
        ProcessMaterial entity = requireMaterial(id);
        ensureCanWriteMaterial(entity, "material:secondReview");
        if (MaterialStatus.of(entity.getStatus()) != MaterialStatus.SECOND_REVIEW) {
            throw new BizException("当前状态不可复审");
        }
        String oldStatus = entity.getStatus();
        String action = normalizeAction(request.getAction());
        if ("PASS".equals(action)) {
            entity.setStatus(MaterialStatus.PASSED.name());
            entity.setLocked(1);
        } else if ("REJECT".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(MaterialStatus.SECOND_REJECTED.name());
            entity.setLocked(0);
        } else if ("FAIL".equals(action)) {
            requireComment(request.getComment());
            entity.setStatus(MaterialStatus.FAILED.name());
            entity.setLocked(1);
        } else {
            throw new BizException("审核结论不支持");
        }
        entity.setSecondReviewStatus(action);
        entity.setSecondReviewerId(UserContext.getUserIdOrSystem());
        entity.setSecondReviewTime(LocalDateTime.now());
        entity.setSecondReviewComment(trimToNull(request.getComment()));
        if (processMaterialMapper.update(new ProcessMaterial(), new LambdaUpdateWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getId, id)
                .eq(ProcessMaterial::getStatus, oldStatus)
                .set(ProcessMaterial::getStatus, entity.getStatus())
                .set(ProcessMaterial::getLocked, entity.getLocked())
                .set(ProcessMaterial::getSecondReviewStatus, entity.getSecondReviewStatus())
                .set(ProcessMaterial::getSecondReviewerId, entity.getSecondReviewerId())
                .set(ProcessMaterial::getSecondReviewTime, entity.getSecondReviewTime())
                .set(ProcessMaterial::getSecondReviewComment, entity.getSecondReviewComment())) == 0) {
            throw new BizException("操作冲突：该记录已被其他操作更新，请刷新后重试");
        }
        auditLogService.record("material", entity.getId(), materialTarget(entity), "secondReview",
                oldStatus, entity.getStatus(), trimToNull(request.getComment()));
        if (!"PASS".equals(action)) {
            notificationHelper.notifySecondReviewReturned(entity.getCollegeId(), entity.getStudentId(), "过程性材料",
                    action, "process_material", entity.getId());
        }
    }

    @Override
    public ProcessStatusVO processStatus(Long studentId, String assessmentYear) {
        Student student = requireStudent(studentId);
        ensureCanReadStudent(student);
        String year = requiredTrim(assessmentYear, "考核年度不能为空");
        List<ProcessMaterial> records = processMaterialMapper.selectList(new LambdaQueryWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getStudentId, studentId)
                .eq(ProcessMaterial::getAssessmentYear, year));
        Map<String, List<ProcessMaterial>> byCategory = records.stream()
                .collect(Collectors.groupingBy(ProcessMaterial::getCategory));
        ProcessStatusVO vo = new ProcessStatusVO();
        vo.setStudentId(studentId);
        vo.setAssessmentYear(year);
        List<ProcessStatusVO.CategoryStatus> categories = categoryLabels().entrySet().stream()
                .map(entry -> categoryStatus(entry.getKey(), entry.getValue(), byCategory.getOrDefault(entry.getKey(), List.of())))
                .toList();
        vo.setCategories(categories);
        vo.setQualified(categories.stream().allMatch(ProcessStatusVO.CategoryStatus::isPassed));
        return vo;
    }

    private static final int MAX_BATCH_DOWNLOAD_FILES = 2000;

    @Override
    public BatchDownloadFile batchDownload(MaterialBatchDownloadRequest request) {
        // 查询 + 校验在返回前（当前请求线程，数据范围/权限生效）完成，异常在写响应头之前抛出，保证干净错误。
        List<ProcessMaterial> records = selectMaterials(toQuery(request), request.getIds());
        if (records.size() > MAX_BATCH_DOWNLOAD_FILES) {
            throw new BizException("单次批量下载不能超过 " + MAX_BATCH_DOWNLOAD_FILES + " 条，请按年度/学生缩小筛选范围后重试");
        }
        Map<Long, Student> students = students(records);
        Map<String, String> categories = categoryLabels();
        String name = "process-material-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + ".zip";
        // 流式打包：zip 直接写入响应输出流，逐个文件从 MinIO 读→写，全程不整包进堆（P0-2）
        return new BatchDownloadFile(name, out -> {
            try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("manifest.csv"));
                zip.write(manifest(records, students, categories).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                for (ProcessMaterial material : records) {
                    FileObject file = fileObjectMapper.selectById(material.getFileId());
                    if (file == null) {
                        continue;
                    }
                    String entryName = zipEntryName(material, students.get(material.getStudentId()), categories, file);
                    zip.putNextEntry(new ZipEntry(entryName));
                    try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(file.getBucket())
                            .object(file.getObjectKey())
                            .build())) {
                        input.transferTo(zip);
                    } catch (Exception e) {
                        log.error("批量下载材料读取失败 materialId={}", material.getId(), e);
                        throw new BizException("材料读取失败，请稍后重试");
                    }
                    zip.closeEntry();
                }
            }
        });
    }

    private void fillFile(ProcessMaterial entity, FileObject file) {
        entity.setFileId(file.getId());
        entity.setFileName(file.getOriginalName());
        entity.setFilePath(file.getObjectKey());
        entity.setFileSize(file.getSize());
        entity.setContentType(file.getContentType());
        entity.setUploaderId(UserContext.getUserIdOrSystem());
        entity.setUploadTime(LocalDateTime.now());
    }

    private int updateMaterialFile(ProcessMaterial entity, Long expectedFileId,
                                   String expectedStatus, Integer expectedLocked) {
        LambdaUpdateWrapper<ProcessMaterial> update = new LambdaUpdateWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getId, entity.getId())
                .eq(ProcessMaterial::getFileId, expectedFileId)
                .eq(ProcessMaterial::getStatus, expectedStatus)
                .eq(ProcessMaterial::getLocked, expectedLocked)
                .set(ProcessMaterial::getFileId, entity.getFileId())
                .set(ProcessMaterial::getFileName, entity.getFileName())
                .set(ProcessMaterial::getFilePath, entity.getFilePath())
                .set(ProcessMaterial::getFileSize, entity.getFileSize())
                .set(ProcessMaterial::getContentType, entity.getContentType())
                .set(ProcessMaterial::getUploaderId, entity.getUploaderId())
                .set(ProcessMaterial::getUploadTime, entity.getUploadTime());
        return processMaterialMapper.update(new ProcessMaterial(), update);
    }

    private List<ProcessMaterial> selectMaterials(MaterialQuery query, List<Long> ids) {
        return processMaterialMapper.selectList(buildListWrapper(query, ids));
    }

    // 抽出自原 selectMaterials：供 list()（selectPage 真分页）与 selectMaterials()（batchDownload 全量下载
    // selectList，不分页）共用同一套过滤条件，保证两者筛选口径一致（Phase 44e P1-1 真分页 rollout）。
    private LambdaQueryWrapper<ProcessMaterial> buildListWrapper(MaterialQuery query, List<Long> ids) {
        MaterialQuery q = query == null ? new MaterialQuery() : query;
        LambdaQueryWrapper<ProcessMaterial> wrapper = new LambdaQueryWrapper<ProcessMaterial>()
                .orderByAsc(ProcessMaterial::getAssessmentYear)
                .orderByAsc(ProcessMaterial::getStudentId)
                .orderByAsc(ProcessMaterial::getCategory)
                .orderByDesc(ProcessMaterial::getUploadTime);
        if (ids != null && !ids.isEmpty()) {
            wrapper.in(ProcessMaterial::getId, ids);
        }
        if (q.getStudentId() != null) {
            wrapper.eq(ProcessMaterial::getStudentId, q.getStudentId());
        }
        if (q.getCollegeId() != null) {
            wrapper.eq(ProcessMaterial::getCollegeId, q.getCollegeId());
        }
        if (StringUtils.hasText(q.getAssessmentYear())) {
            wrapper.eq(ProcessMaterial::getAssessmentYear, q.getAssessmentYear().trim());
        }
        if (StringUtils.hasText(q.getCategory())) {
            wrapper.eq(ProcessMaterial::getCategory, q.getCategory().trim());
        }
        if (StringUtils.hasText(q.getStatus())) {
            wrapper.eq(ProcessMaterial::getStatus, q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String keyword = q.getKeyword().trim();
            wrapper.and(w -> w.like(ProcessMaterial::getFileName, keyword)
                    .or()
                    .like(ProcessMaterial::getCategory, keyword));
        }
        return wrapper;
    }

    private MaterialQuery toQuery(MaterialBatchDownloadRequest request) {
        MaterialQuery query = new MaterialQuery();
        if (request == null) {
            return query;
        }
        query.setKeyword(request.getKeyword());
        query.setStatus(request.getStatus());
        query.setCollegeId(request.getCollegeId());
        query.setStudentId(request.getStudentId());
        query.setAssessmentYear(request.getAssessmentYear());
        query.setCategory(request.getCategory());
        return query;
    }

    private ProcessMaterialVO toVO(ProcessMaterial entity, Student student, Map<String, String> categories) {
        ProcessMaterialVO vo = new ProcessMaterialVO();
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setStudentNo(student == null ? null : student.getStudentNo());
        vo.setStudentName(student == null ? null : student.getName());
        vo.setCollegeId(entity.getCollegeId());
        vo.setAssessmentYear(entity.getAssessmentYear());
        vo.setCategory(entity.getCategory());
        vo.setCategoryLabel(categories.getOrDefault(entity.getCategory(), entity.getCategory()));
        vo.setFileId(entity.getFileId());
        vo.setFileName(entity.getFileName());
        vo.setFileSize(entity.getFileSize());
        vo.setContentType(entity.getContentType());
        vo.setUploaderId(entity.getUploaderId());
        vo.setUploadTime(entity.getUploadTime());
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(MaterialStatus.of(entity.getStatus()).label());
        vo.setLocked(entity.getLocked());
        vo.setFirstReviewStatus(entity.getFirstReviewStatus());
        vo.setFirstReviewComment(entity.getFirstReviewComment());
        vo.setSecondReviewStatus(entity.getSecondReviewStatus());
        vo.setSecondReviewComment(entity.getSecondReviewComment());
        return vo;
    }

    private ProcessStatusVO.CategoryStatus categoryStatus(String category, String label, List<ProcessMaterial> records) {
        ProcessStatusVO.CategoryStatus item = new ProcessStatusVO.CategoryStatus();
        item.setCategory(category);
        item.setCategoryLabel(label);
        // totalCount/passedCount/failedCount 保留"全历史"口径，仅作展示信息（前端「复审通过」「不通过」列），不参与合格判定。
        item.setTotalCount(records.size());
        item.setPassedCount(records.stream().filter(record -> MaterialStatus.of(record.getStatus()) == MaterialStatus.PASSED).count());
        item.setFailedCount(records.stream().filter(record -> MaterialStatus.of(record.getStatus()) == MaterialStatus.FAILED).count());
        // Rule 11（§7.5 唯一真 bug）合格判定修复：合格只看该类别"最新/有效"的一份材料。
        // 分组槽位 = 同一 (student_id, assessment_year, category)（本方法入参 records 即该槽位内的全部材料，
        // 上游 selectList 经 @TableLogic 已排除软删行）；"最新" = 该槽位内**创建顺序最靠后**的一条，取自主键
        // id（雪花 ASSIGN_ID 随创建时间单调递增，max(id) 即最后提交的那份材料；id 不可变、不受 replace 改动，
        // 比 uploadTime 更稳健地表达"最后一次提交的材料"）。该"有效材料"状态为 PASSED 才算此类别合格。
        // 效果：更晚提交并 PASSED 的材料会"取代"更早的 FAILED（恢复合格）；而"最新一份为 FAILED"或"该类别无
        // 任何 PASSED 材料"仍判不合格（真失败不放行）。
        // 旧实现按全历史行 passedCount>0 && failedCount==0：一条终态 FAILED 永久钉住 failedCount≥1，使该类别再也
        // 无法 passed（即便后续重传通过）→ 证书合格永久卡死、无恢复路径。
        ProcessMaterial effective = records.stream()
                .max(Comparator.comparing(ProcessMaterial::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        item.setPassed(effective != null && MaterialStatus.of(effective.getStatus()) == MaterialStatus.PASSED);
        return item;
    }

    private Map<Long, Student> students(List<ProcessMaterial> records) {
        if (records == null || records.isEmpty()) {
            return Map.of();
        }
        Set<Long> studentIds = records.stream().map(ProcessMaterial::getStudentId).collect(Collectors.toSet());
        return studentMapper.selectBatchIds(studentIds).stream().collect(Collectors.toMap(Student::getId, item -> item));
    }

    // Phase 44c（§7.3）：改走 DictService 缓存标签表（写时逐出）；返回可变副本，保持原「每次新 map」语义。
    private Map<String, String> categoryLabels() {
        return new LinkedHashMap<>(dictService.dictLabels("material_category"));
    }

    private void validateCategory(String category) {
        String value = requiredTrim(category, "材料类别不能为空");
        if (!categoryLabels().containsKey(value)) {
            throw new BizException("材料类别不在字典范围");
        }
    }

    private DirectMaterialContext directMaterialContext(Long materialId, Long studentId,
                                                        String assessmentYear, String category) {
        String year = requiredTrim(assessmentYear, "考核年度不能为空");
        String categoryCode = requiredTrim(category, "材料类别不能为空");
        if (materialId == null) {
            Student student = requireStudent(studentId);
            ensureCanWriteStudent(student, "material:upload");
            validateCategory(categoryCode);
            return new DirectMaterialContext(null, student, year, categoryCode);
        }
        ProcessMaterial existing = requireMaterial(materialId);
        ensureCanWriteMaterial(existing, "material:upload");
        ensureEditable(existing);
        if (!existing.getStudentId().equals(studentId)
                || !existing.getAssessmentYear().equals(year)
                || !existing.getCategory().equals(categoryCode)) {
            throw new BizException("替换材料的业务上下文不可变更");
        }
        return new DirectMaterialContext(existing, requireStudent(existing.getStudentId()),
                existing.getAssessmentYear(), existing.getCategory());
    }

    private Map<String, String> directMetadata(DirectMaterialContext context) {
        return Map.of(
                "student-id", String.valueOf(context.student().getId()),
                "assessment-year", context.assessmentYear(),
                "category", context.category(),
                "material-id", context.existing() == null ? "NEW" : String.valueOf(context.existing().getId()),
                "binding-version", context.existing() == null
                        ? "NONE" : String.valueOf(context.existing().getFileId()));
    }

    private void validateFile(String originalFilename, String contentType, long size) {
        if (size <= 0) {
            throw new BizException("文件不能为空");
        }
        long maxSize = Long.parseLong(paramService.getString("file.maxSize.material", String.valueOf(DEFAULT_MATERIAL_MAX_SIZE)));
        if (size > maxSize) {
            throw new BizException("附件大小超过限制");
        }
        String normalized = normalizeContentType(originalFilename, contentType);
        Set<String> allowed = allowedContentTypes();
        if (!allowed.contains(normalized)) {
            throw new BizException("附件类型不支持");
        }
    }

    private Set<String> allowedContentTypes() {
        String value = paramService.getString("file.material.allowedTypes", "application/pdf,image/jpeg,image/png");
        return java.util.Arrays.stream(value.split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private String normalizeContentType(String originalFilename, String contentType) {
        String type = StringUtils.hasText(contentType) ? contentType.trim().toLowerCase(Locale.ROOT) : "";
        if ("image/jpg".equals(type)) {
            type = "image/jpeg";
        }
        if (StringUtils.hasText(type) && !"application/octet-stream".equals(type)) {
            return type;
        }
        String name = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        return type;
    }

    private void ensureEditable(ProcessMaterial entity) {
        MaterialStatus status = MaterialStatus.of(entity.getStatus());
        if (!status.editable() || (entity.getLocked() != null && entity.getLocked() == 1)) {
            throw new BizException("当前状态不可编辑");
        }
    }

    private void ensureCanWriteMaterial(ProcessMaterial material, String permissionCode) {
        Student student = requireStudent(material.getStudentId());
        ensureCanWriteStudent(student, permissionCode);
    }

    private void ensureCanWriteStudent(Student student, String permissionCode) {
        DataScopeContext.Scope scope = dataScopeService.resolve(permissionCode);
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该材料");
        }
        if (scope.allSchool()) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && scope.getCollegeIds().contains(student.getCollegeId())) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.SELF
                && scope.getStudentId() != null
                && scope.getStudentId().equals(student.getId())) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该材料");
    }

    private void ensureReadableMaterial(ProcessMaterial material) {
        Student student = requireStudent(material.getStudentId());
        ensureCanReadStudent(student);
    }

    private void ensureCanReadStudent(Student student) {
        DataScopeContext.Scope scope = dataScopeService.resolve(readPermission());
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该材料");
        }
        if (scope.allSchool()) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE
                && scope.getCollegeIds().contains(student.getCollegeId())) {
            return;
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.SELF
                && scope.getStudentId() != null
                && scope.getStudentId().equals(student.getId())) {
            return;
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该材料");
    }

    private String readPermission() {
        if (UserContext.hasPermission("material:firstReview")) {
            return "material:firstReview";
        }
        if (UserContext.hasPermission("material:secondReview")) {
            return "material:secondReview";
        }
        if (UserContext.hasPermission("material:batchDownload")) {
            return "material:batchDownload";
        }
        return "material:upload";
    }

    private String returnTargetFromSecondRejected(MaterialStatus currentStatus) {
        if (currentStatus != MaterialStatus.SECOND_REJECTED) {
            return MaterialStatus.FIRST_REVIEW.name();
        }
        String target = paramService.getString("review.return.target", "FIRST_REVIEW");
        return "SECOND_REVIEW".equalsIgnoreCase(target) ? MaterialStatus.SECOND_REVIEW.name() : MaterialStatus.FIRST_REVIEW.name();
    }

    private String manifest(List<ProcessMaterial> records, Map<Long, Student> students, Map<String, String> categories) {
        StringBuilder csv = new StringBuilder();
        csv.append('\ufeff');
        csv.append("学号,姓名,材料类别,文件名,材料状态,初审人ID,初审时间,复审人ID,复审时间\n");
        for (ProcessMaterial item : records) {
            Student student = students.get(item.getStudentId());
            csv.append(csv(student == null ? "" : student.getStudentNo())).append(',')
                    .append(csv(student == null ? "" : student.getName())).append(',')
                    .append(csv(categories.getOrDefault(item.getCategory(), item.getCategory()))).append(',')
                    .append(csv(item.getFileName())).append(',')
                    .append(csv(MaterialStatus.of(item.getStatus()).label())).append(',')
                    .append(csv(String.valueOf(item.getFirstReviewerId() == null ? "" : item.getFirstReviewerId()))).append(',')
                    .append(csv(formatTime(item.getFirstReviewTime()))).append(',')
                    .append(csv(String.valueOf(item.getSecondReviewerId() == null ? "" : item.getSecondReviewerId()))).append(',')
                    .append(csv(formatTime(item.getSecondReviewTime()))).append('\n');
        }
        return csv.toString();
    }

    private String zipEntryName(ProcessMaterial material, Student student, Map<String, String> categories, FileObject file) {
        String studentNo = student == null ? String.valueOf(material.getStudentId()) : student.getStudentNo();
        String category = categories.getOrDefault(material.getCategory(), material.getCategory());
        return sanitize(studentNo) + "/" + sanitize(category) + "/" + sanitize(file.getOriginalName());
    }

    private String materialTarget(ProcessMaterial material) {
        String category = categoryLabels().getOrDefault(material.getCategory(), material.getCategory());
        return material.getId() + "/" + material.getAssessmentYear() + "/" + material.getStudentId() + "/" + category;
    }

    private String csv(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(time);
    }

    private String sanitize(String value) {
        String text = StringUtils.hasText(value) ? value.trim() : "unknown";
        return text.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private Student requireStudent(Long id) {
        if (id == null) {
            throw new BizException("学生ID不能为空");
        }
        Student student = studentMapper.selectById(id);
        if (student == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "学生不存在");
        }
        return student;
    }

    private ProcessMaterial requireMaterial(Long id) {
        if (id == null) {
            throw new BizException("材料ID不能为空");
        }
        ProcessMaterial entity = processMaterialMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "材料不存在");
        }
        return entity;
    }

    private String normalizeAction(String action) {
        return requiredTrim(action, "审核结论不能为空").toUpperCase(Locale.ROOT);
    }

    private void requireComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            throw new BizException("退回或不通过必须填写原因");
        }
    }

    private String requiredTrim(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BizException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record DirectMaterialContext(ProcessMaterial existing, Student student,
                                         String assessmentYear, String category) {
    }
}
