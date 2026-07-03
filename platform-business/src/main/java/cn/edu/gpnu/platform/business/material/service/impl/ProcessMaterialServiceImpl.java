package cn.edu.gpnu.platform.business.material.service.impl;

import cn.edu.gpnu.platform.business.material.dto.MaterialBatchDownloadRequest;
import cn.edu.gpnu.platform.business.material.dto.MaterialQuery;
import cn.edu.gpnu.platform.business.material.dto.MaterialReviewRequest;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.material.service.ProcessMaterialService;
import cn.edu.gpnu.platform.business.material.support.MaterialStatus;
import cn.edu.gpnu.platform.business.material.vo.BatchDownloadFile;
import cn.edu.gpnu.platform.business.material.vo.ProcessMaterialVO;
import cn.edu.gpnu.platform.business.material.vo.ProcessStatusVO;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.support.ReviewNotificationHelper;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.mapper.SysDictItemMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
public class ProcessMaterialServiceImpl implements ProcessMaterialService {

    private static final String MATERIAL_BIZ_TYPE = "process-material";
    private static final long DEFAULT_MATERIAL_MAX_SIZE = 52_428_800L;
    private static final int PREVIEW_EXPIRY_SECONDS = 600;

    private final ProcessMaterialMapper processMaterialMapper;
    private final StudentMapper studentMapper;
    private final SysDictItemMapper dictItemMapper;
    private final FileObjectMapper fileObjectMapper;
    private final FileService fileService;
    private final DataScopeService dataScopeService;
    private final ParamService paramService;
    private final MinioClient minioClient;
    private final ReviewNotificationHelper notificationHelper;
    private final AuditLogService auditLogService;

    @Override
    public PageResult<ProcessMaterialVO> list(MaterialQuery query) {
        List<ProcessMaterial> records = selectMaterials(query, null);
        Map<Long, Student> students = students(records);
        Map<String, String> categories = categoryLabels();
        return new PageResult<>(records.size(), records.stream()
                .map(item -> toVO(item, students.get(item.getStudentId()), categories))
                .toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upload(Long studentId, String assessmentYear, String category, InputStream input,
                       String originalFilename, String contentType, long size) {
        Student student = requireStudent(studentId);
        ensureCanWriteStudent(student, "material:upload");
        validateCategory(category);
        validateFile(originalFilename, contentType, size);
        FileObject file = fileService.upload(input, requiredTrim(originalFilename, "文件名不能为空"),
                normalizeContentType(originalFilename, contentType), size, MATERIAL_BIZ_TYPE, null);
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
    @Transactional(rollbackFor = Exception.class)
    public void replace(Long id, InputStream input, String originalFilename, String contentType, long size) {
        ProcessMaterial entity = requireMaterial(id);
        ensureCanWriteMaterial(entity, "material:upload");
        ensureEditable(entity);
        validateFile(originalFilename, contentType, size);
        FileObject file = fileService.upload(input, requiredTrim(originalFilename, "文件名不能为空"),
                normalizeContentType(originalFilename, contentType), size, MATERIAL_BIZ_TYPE, null);
        fillFile(entity, file);
        entity.setUploaderId(UserContext.getUserIdOrSystem());
        entity.setUploadTime(LocalDateTime.now());
        processMaterialMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ProcessMaterial entity = requireMaterial(id);
        ensureCanWriteMaterial(entity, "material:upload");
        ensureEditable(entity);
        processMaterialMapper.deleteById(id);
    }

    @Override
    public String previewUrl(Long id) {
        ProcessMaterial entity = requireMaterial(id);
        ensureReadableMaterial(entity);
        return fileService.presignedGet(entity.getFileId(), PREVIEW_EXPIRY_SECONDS);
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
        // 原子条件更新：仅当状态未被并发改变时才写入，防重复提交竞态（P0-10）
        if (processMaterialMapper.update(entity, new LambdaUpdateWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getId, id).eq(ProcessMaterial::getStatus, oldStatus)) == 0) {
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
        // 原子条件更新：仅当仍为初审态时才写入，防并发/重复初审竞态（P0-10）
        if (processMaterialMapper.update(entity, new LambdaUpdateWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getId, id).eq(ProcessMaterial::getStatus, oldStatus)) == 0) {
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
        // 原子条件更新：仅当仍为复审态时才写入，防并发/重复复审竞态（P0-10）
        if (processMaterialMapper.update(entity, new LambdaUpdateWrapper<ProcessMaterial>()
                .eq(ProcessMaterial::getId, id).eq(ProcessMaterial::getStatus, oldStatus)) == 0) {
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

    @Override
    public BatchDownloadFile batchDownload(MaterialBatchDownloadRequest request) {
        List<ProcessMaterial> records = selectMaterials(toQuery(request), request.getIds());
        Map<Long, Student> students = students(records);
        Map<String, String> categories = categoryLabels();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
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
                    }
                    zip.closeEntry();
                }
            }
            String name = "process-material-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + ".zip";
            return new BatchDownloadFile(name, out.toByteArray());
        } catch (Exception e) {
            throw new BizException("材料打包失败: " + e.getMessage());
        }
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

    private List<ProcessMaterial> selectMaterials(MaterialQuery query, List<Long> ids) {
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
        return processMaterialMapper.selectList(wrapper);
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
        item.setTotalCount(records.size());
        item.setPassedCount(records.stream().filter(record -> MaterialStatus.of(record.getStatus()) == MaterialStatus.PASSED).count());
        item.setFailedCount(records.stream().filter(record -> MaterialStatus.of(record.getStatus()) == MaterialStatus.FAILED).count());
        item.setPassed(item.getPassedCount() > 0 && item.getFailedCount() == 0);
        return item;
    }

    private Map<Long, Student> students(List<ProcessMaterial> records) {
        if (records == null || records.isEmpty()) {
            return Map.of();
        }
        Set<Long> studentIds = records.stream().map(ProcessMaterial::getStudentId).collect(Collectors.toSet());
        return studentMapper.selectBatchIds(studentIds).stream().collect(Collectors.toMap(Student::getId, item -> item));
    }

    private Map<String, String> categoryLabels() {
        List<SysDictItem> items = dictItemMapper.selectList(new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, "material_category")
                .eq(SysDictItem::getStatus, 1)
                .orderByAsc(SysDictItem::getSort));
        Map<String, String> result = new LinkedHashMap<>();
        for (SysDictItem item : items) {
            result.put(item.getItemCode(), item.getItemValue());
        }
        return result;
    }

    private void validateCategory(String category) {
        String value = requiredTrim(category, "材料类别不能为空");
        if (!categoryLabels().containsKey(value)) {
            throw new BizException("材料类别不在字典范围");
        }
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
}
