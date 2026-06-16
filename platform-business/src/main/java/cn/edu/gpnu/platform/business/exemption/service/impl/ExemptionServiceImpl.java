package cn.edu.gpnu.platform.business.exemption.service.impl;

import cn.edu.gpnu.platform.business.exemption.dto.ExemptionApplyRequest;
import cn.edu.gpnu.platform.business.exemption.dto.ExemptionQuery;
import cn.edu.gpnu.platform.business.exemption.dto.ExemptionReviewRequest;
import cn.edu.gpnu.platform.business.exemption.dto.ExemptionUpdateRequest;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionMaterial;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionRequest;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionMaterialMapper;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionRequestMapper;
import cn.edu.gpnu.platform.business.exemption.service.ExemptionService;
import cn.edu.gpnu.platform.business.exemption.support.ExemptionStatus;
import cn.edu.gpnu.platform.business.exemption.vo.ExamSubjectVO;
import cn.edu.gpnu.platform.business.exemption.vo.ExemptionMaterialVO;
import cn.edu.gpnu.platform.business.exemption.vo.ExemptionRequestVO;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
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
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExemptionServiceImpl implements ExemptionService {

    private static final String EXEMPTION_BIZ_TYPE = "exemption-material";
    private static final long DEFAULT_EXEMPTION_MAX_SIZE = 52_428_800L;
    private static final int PREVIEW_EXPIRY_SECONDS = 600;

    private final ExemptionRequestMapper requestMapper;
    private final ExemptionMaterialMapper materialMapper;
    private final StudentMapper studentMapper;
    private final SysDictItemMapper dictItemMapper;
    private final FileObjectMapper fileObjectMapper;
    private final FileService fileService;
    private final DataScopeService dataScopeService;
    private final ParamService paramService;

    @Override
    public List<SysDictItem> subjects(String segment) {
        LambdaQueryWrapper<SysDictItem> wrapper = dictWrapper("exemption_subject")
                .orderByAsc(SysDictItem::getSort);
        if (StringUtils.hasText(segment)) {
            wrapper.eq(SysDictItem::getParentCode, segment.trim());
        }
        return dictItemMapper.selectList(wrapper);
    }

    @Override
    public PageResult<ExemptionRequestVO> list(ExemptionQuery query) {
        List<ExemptionRequest> records = selectRequests(query);
        return new PageResult<>(records.size(), toVO(records));
    }

    @Override
    public List<ExemptionRequestVO> studentRequests(Long studentId, String assessmentYear) {
        Student student = requireStudent(studentId);
        ensureCanReadStudent(student);
        ExemptionQuery query = new ExemptionQuery();
        query.setStudentId(studentId);
        query.setAssessmentYear(assessmentYear);
        return toVO(selectRequests(query));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> apply(ExemptionApplyRequest request) {
        Student student = requireStudent(request.getStudentId());
        ensureCanWriteStudent(student, "exemption:apply");
        String year = requiredTrim(request.getAssessmentYear(), "考核年度不能为空");
        String segment = validateSegment(request.getTeachingSegment());
        Map<String, String> subjectLabels = subjectLabels(segment);
        Map<String, String> basisLabels = dictLabels("exemption_basis");
        Set<String> seen = new LinkedHashSet<>();
        List<Long> ids = new ArrayList<>();
        for (ExemptionApplyRequest.Item item : request.getItems()) {
            String subject = requiredTrim(item.getSubject(), "免考科目不能为空");
            if (!seen.add(subject)) {
                throw new BizException("同一申请中免考科目不能重复");
            }
            String basis = requiredTrim(item.getBasis(), "免考依据不能为空");
            if (!subjectLabels.containsKey(subject)) {
                throw new BizException("免考科目不在该学段可选范围");
            }
            if (!basisLabels.containsKey(basis)) {
                throw new BizException("免考依据不在字典范围");
            }
            ExemptionRequest entity = existing(student.getId(), year, subject);
            if (entity == null) {
                entity = new ExemptionRequest();
                entity.setStudentId(student.getId());
                entity.setCollegeId(student.getCollegeId());
                entity.setAssessmentYear(year);
                entity.setTeachingSegment(segment);
                entity.setSubject(subject);
                entity.setFinalStatus(ExemptionStatus.DRAFT.name());
                entity.setIncludedInExam(1);
                entity.setLocked(0);
                fillApply(entity, subjectLabels, basisLabels, item);
                requestMapper.insert(entity);
            } else {
                ensureCanWriteRequest(entity, "exemption:apply");
                ensureEditable(entity);
                entity.setCollegeId(student.getCollegeId());
                entity.setTeachingSegment(segment);
                fillApply(entity, subjectLabels, basisLabels, item);
                requestMapper.updateById(entity);
            }
            ids.add(entity.getId());
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ExemptionUpdateRequest request) {
        ExemptionRequest entity = requireRequest(id);
        ensureCanWriteRequest(entity, "exemption:apply");
        ensureEditable(entity);
        Map<String, String> basisLabels = dictLabels("exemption_basis");
        String basis = requiredTrim(request.getBasis(), "免考依据不能为空");
        if (!basisLabels.containsKey(basis)) {
            throw new BizException("免考依据不在字典范围");
        }
        entity.setBasis(basis);
        entity.setBasisLabel(basisLabels.get(basis));
        entity.setRemark(trimToNull(request.getRemark()));
        requestMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadMaterial(Long id, InputStream input, String originalFilename, String contentType, long size) {
        ExemptionRequest entity = requireRequest(id);
        ensureCanWriteRequest(entity, "exemption:apply");
        ensureEditable(entity);
        validateFile(originalFilename, contentType, size);
        FileObject file = fileService.upload(input, requiredTrim(originalFilename, "文件名不能为空"),
                normalizeContentType(originalFilename, contentType), size, EXEMPTION_BIZ_TYPE, null);
        ExemptionMaterial material = new ExemptionMaterial();
        material.setExemptionRequestId(entity.getId());
        material.setStudentId(entity.getStudentId());
        material.setCollegeId(entity.getCollegeId());
        fillFile(material, file);
        materialMapper.insert(material);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceMaterial(Long materialId, InputStream input, String originalFilename, String contentType, long size) {
        ExemptionMaterial material = requireMaterial(materialId);
        ExemptionRequest request = requireRequest(material.getExemptionRequestId());
        ensureCanWriteRequest(request, "exemption:apply");
        ensureEditable(request);
        validateFile(originalFilename, contentType, size);
        FileObject file = fileService.upload(input, requiredTrim(originalFilename, "文件名不能为空"),
                normalizeContentType(originalFilename, contentType), size, EXEMPTION_BIZ_TYPE, null);
        fillFile(material, file);
        materialMapper.updateById(material);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterial(Long materialId) {
        ExemptionMaterial material = requireMaterial(materialId);
        ExemptionRequest request = requireRequest(material.getExemptionRequestId());
        ensureCanWriteRequest(request, "exemption:apply");
        ensureEditable(request);
        materialMapper.deleteById(materialId);
    }

    @Override
    public String previewMaterial(Long materialId) {
        ExemptionMaterial material = requireMaterial(materialId);
        ExemptionRequest request = requireRequest(material.getExemptionRequestId());
        ensureReadableRequest(request);
        return fileService.presignedGet(material.getFileId(), PREVIEW_EXPIRY_SECONDS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        ExemptionRequest entity = requireRequest(id);
        ensureCanWriteRequest(entity, "exemption:apply");
        ExemptionStatus status = ExemptionStatus.of(entity.getFinalStatus());
        if (!status.editable()) {
            throw new BizException("当前状态不可提交");
        }
        if (materialCount(entity.getId()) <= 0) {
            throw new BizException("免考佐证不能为空");
        }
        entity.setFinalStatus(returnTargetFromSecondRejected(status));
        entity.setIncludedInExam(1);
        requestMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void firstReview(Long id, ExemptionReviewRequest request) {
        ExemptionRequest entity = requireRequest(id);
        ensureCanWriteRequest(entity, "exemption:firstReview");
        if (ExemptionStatus.of(entity.getFinalStatus()) != ExemptionStatus.FIRST_REVIEW) {
            throw new BizException("当前状态不可初审");
        }
        String action = normalizeAction(request.getAction());
        if ("PASS".equals(action)) {
            entity.setFinalStatus(ExemptionStatus.SECOND_REVIEW.name());
        } else if ("REJECT".equals(action)) {
            requireComment(request.getComment());
            entity.setFinalStatus(ExemptionStatus.FIRST_REJECTED.name());
        } else if ("FAIL".equals(action)) {
            requireComment(request.getComment());
            entity.setFinalStatus(ExemptionStatus.FAILED.name());
            entity.setLocked(1);
        } else {
            throw new BizException("审核结论不支持");
        }
        entity.setIncludedInExam(1);
        entity.setFirstReviewStatus(action);
        entity.setFirstReviewerId(UserContext.getUserIdOrSystem());
        entity.setFirstReviewTime(LocalDateTime.now());
        entity.setFirstReviewComment(trimToNull(request.getComment()));
        requestMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void secondReview(Long id, ExemptionReviewRequest request) {
        ExemptionRequest entity = requireRequest(id);
        ensureCanWriteRequest(entity, "exemption:secondReview");
        if (ExemptionStatus.of(entity.getFinalStatus()) != ExemptionStatus.SECOND_REVIEW) {
            throw new BizException("当前状态不可复审");
        }
        String action = normalizeAction(request.getAction());
        if ("PASS".equals(action)) {
            entity.setFinalStatus(ExemptionStatus.PASSED.name());
            entity.setIncludedInExam(0);
            entity.setLocked(1);
        } else if ("REJECT".equals(action)) {
            requireComment(request.getComment());
            entity.setFinalStatus(ExemptionStatus.SECOND_REJECTED.name());
            entity.setIncludedInExam(1);
            entity.setLocked(0);
        } else if ("FAIL".equals(action)) {
            requireComment(request.getComment());
            entity.setFinalStatus(ExemptionStatus.FAILED.name());
            entity.setIncludedInExam(1);
            entity.setLocked(1);
        } else {
            throw new BizException("审核结论不支持");
        }
        entity.setSecondReviewStatus(action);
        entity.setSecondReviewerId(UserContext.getUserIdOrSystem());
        entity.setSecondReviewTime(LocalDateTime.now());
        entity.setSecondReviewComment(trimToNull(request.getComment()));
        requestMapper.updateById(entity);
    }

    @Override
    public List<ExamSubjectVO> examSubjects(Long studentId, String assessmentYear, String teachingSegment) {
        Student student = requireStudent(studentId);
        ensureCanReadStudent(student);
        String year = requiredTrim(assessmentYear, "考核年度不能为空");
        String segment = validateSegment(teachingSegment);
        Map<String, String> subjectLabels = subjectLabels(segment);
        Map<String, ExemptionRequest> requests = requestMapper.selectList(new LambdaQueryWrapper<ExemptionRequest>()
                        .eq(ExemptionRequest::getStudentId, studentId)
                        .eq(ExemptionRequest::getAssessmentYear, year)
                        .eq(ExemptionRequest::getTeachingSegment, segment))
                .stream()
                .collect(Collectors.toMap(ExemptionRequest::getSubject, item -> item, (left, right) -> left, LinkedHashMap::new));
        return subjectLabels.entrySet().stream().map(entry -> {
            ExemptionRequest request = requests.get(entry.getKey());
            boolean passed = request != null && ExemptionStatus.of(request.getFinalStatus()) == ExemptionStatus.PASSED;
            ExamSubjectVO vo = new ExamSubjectVO();
            vo.setSubject(entry.getKey());
            vo.setSubjectLabel(entry.getValue());
            vo.setExempted(passed);
            vo.setIncludedInExam(!passed);
            return vo;
        }).toList();
    }

    private void fillApply(ExemptionRequest entity, Map<String, String> subjectLabels, Map<String, String> basisLabels,
                           ExemptionApplyRequest.Item item) {
        String subject = requiredTrim(item.getSubject(), "免考科目不能为空");
        String basis = requiredTrim(item.getBasis(), "免考依据不能为空");
        entity.setSubject(subject);
        entity.setSubjectLabel(subjectLabels.get(subject));
        entity.setBasis(basis);
        entity.setBasisLabel(basisLabels.get(basis));
        entity.setRemark(trimToNull(item.getRemark()));
    }

    private List<ExemptionRequest> selectRequests(ExemptionQuery query) {
        ExemptionQuery q = query == null ? new ExemptionQuery() : query;
        LambdaQueryWrapper<ExemptionRequest> wrapper = new LambdaQueryWrapper<ExemptionRequest>()
                .orderByAsc(ExemptionRequest::getAssessmentYear)
                .orderByAsc(ExemptionRequest::getStudentId)
                .orderByAsc(ExemptionRequest::getSubject);
        if (q.getStudentId() != null) {
            wrapper.eq(ExemptionRequest::getStudentId, q.getStudentId());
        }
        if (q.getCollegeId() != null) {
            wrapper.eq(ExemptionRequest::getCollegeId, q.getCollegeId());
        }
        if (StringUtils.hasText(q.getAssessmentYear())) {
            wrapper.eq(ExemptionRequest::getAssessmentYear, q.getAssessmentYear().trim());
        }
        if (StringUtils.hasText(q.getTeachingSegment())) {
            wrapper.eq(ExemptionRequest::getTeachingSegment, q.getTeachingSegment().trim());
        }
        if (StringUtils.hasText(q.getSubject())) {
            wrapper.eq(ExemptionRequest::getSubject, q.getSubject().trim());
        }
        if (StringUtils.hasText(q.getStatus())) {
            wrapper.eq(ExemptionRequest::getFinalStatus, q.getStatus().trim());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String keyword = q.getKeyword().trim();
            wrapper.and(w -> w.like(ExemptionRequest::getSubjectLabel, keyword)
                    .or()
                    .like(ExemptionRequest::getBasisLabel, keyword)
                    .or()
                    .like(ExemptionRequest::getRemark, keyword));
        }
        return requestMapper.selectList(wrapper);
    }

    private List<ExemptionRequestVO> toVO(List<ExemptionRequest> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        Map<Long, Student> students = students(records);
        Map<String, String> segments = dictLabels("teaching_segment");
        Map<Long, List<ExemptionMaterial>> materials = materialMapper.selectList(new LambdaQueryWrapper<ExemptionMaterial>()
                        .in(ExemptionMaterial::getExemptionRequestId,
                                records.stream().map(ExemptionRequest::getId).collect(Collectors.toSet()))
                        .orderByDesc(ExemptionMaterial::getUploadTime))
                .stream()
                .collect(Collectors.groupingBy(ExemptionMaterial::getExemptionRequestId, LinkedHashMap::new, Collectors.toList()));
        return records.stream()
                .map(item -> toVO(item, students.get(item.getStudentId()), segments, materials.getOrDefault(item.getId(), List.of())))
                .toList();
    }

    private ExemptionRequestVO toVO(ExemptionRequest entity, Student student, Map<String, String> segments,
                                    List<ExemptionMaterial> materials) {
        ExemptionRequestVO vo = new ExemptionRequestVO();
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setStudentNo(student == null ? null : student.getStudentNo());
        vo.setStudentName(student == null ? null : student.getName());
        vo.setCollegeId(entity.getCollegeId());
        vo.setAssessmentYear(entity.getAssessmentYear());
        vo.setTeachingSegment(entity.getTeachingSegment());
        vo.setTeachingSegmentLabel(segments.getOrDefault(entity.getTeachingSegment(), entity.getTeachingSegment()));
        vo.setSubject(entity.getSubject());
        vo.setSubjectLabel(entity.getSubjectLabel());
        vo.setBasis(entity.getBasis());
        vo.setBasisLabel(entity.getBasisLabel());
        vo.setRemark(entity.getRemark());
        vo.setFinalStatus(entity.getFinalStatus());
        vo.setStatusLabel(ExemptionStatus.of(entity.getFinalStatus()).label());
        vo.setIncludedInExam(entity.getIncludedInExam());
        vo.setLocked(entity.getLocked());
        vo.setFirstReviewStatus(entity.getFirstReviewStatus());
        vo.setFirstReviewComment(entity.getFirstReviewComment());
        vo.setSecondReviewStatus(entity.getSecondReviewStatus());
        vo.setSecondReviewComment(entity.getSecondReviewComment());
        vo.setFirstReviewTime(entity.getFirstReviewTime());
        vo.setSecondReviewTime(entity.getSecondReviewTime());
        vo.setMaterials(materials.stream().map(this::materialVO).toList());
        return vo;
    }

    private ExemptionMaterialVO materialVO(ExemptionMaterial material) {
        ExemptionMaterialVO vo = new ExemptionMaterialVO();
        vo.setId(material.getId());
        vo.setExemptionRequestId(material.getExemptionRequestId());
        vo.setFileId(material.getFileId());
        vo.setFileName(material.getFileName());
        vo.setFileSize(material.getFileSize());
        vo.setContentType(material.getContentType());
        vo.setUploaderId(material.getUploaderId());
        vo.setUploadTime(material.getUploadTime());
        return vo;
    }

    private Map<Long, Student> students(List<ExemptionRequest> records) {
        Set<Long> ids = records.stream().map(ExemptionRequest::getStudentId).collect(Collectors.toSet());
        return studentMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(Student::getId, item -> item));
    }

    private void fillFile(ExemptionMaterial material, FileObject file) {
        material.setFileId(file.getId());
        material.setFileName(file.getOriginalName());
        material.setFilePath(file.getObjectKey());
        material.setFileSize(file.getSize());
        material.setContentType(file.getContentType());
        material.setUploaderId(UserContext.getUserIdOrSystem());
        material.setUploadTime(LocalDateTime.now());
    }

    private String validateSegment(String segment) {
        String value = requiredTrim(segment, "任教学段不能为空");
        if (!dictLabels("teaching_segment").containsKey(value)) {
            throw new BizException("任教学段不在字典范围");
        }
        return value;
    }

    private Map<String, String> subjectLabels(String segment) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (SysDictItem item : subjects(segment)) {
            labels.put(item.getItemCode(), item.getItemValue());
        }
        return labels;
    }

    private Map<String, String> dictLabels(String typeCode) {
        return dictItemMapper.selectList(dictWrapper(typeCode).orderByAsc(SysDictItem::getSort))
                .stream()
                .collect(Collectors.toMap(SysDictItem::getItemCode, SysDictItem::getItemValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    private LambdaQueryWrapper<SysDictItem> dictWrapper(String typeCode) {
        return new LambdaQueryWrapper<SysDictItem>()
                .eq(SysDictItem::getTypeCode, typeCode)
                .eq(SysDictItem::getStatus, 1);
    }

    private void validateFile(String originalFilename, String contentType, long size) {
        if (size <= 0) {
            throw new BizException("文件不能为空");
        }
        long maxSize = Long.parseLong(paramService.getString("file.maxSize.exemption", String.valueOf(DEFAULT_EXEMPTION_MAX_SIZE)));
        if (size > maxSize) {
            throw new BizException("附件大小超过限制");
        }
        String normalized = normalizeContentType(originalFilename, contentType);
        if (!allowedContentTypes().contains(normalized)) {
            throw new BizException("附件类型不支持");
        }
    }

    private Set<String> allowedContentTypes() {
        String value = paramService.getString("file.exemption.allowedTypes", "application/pdf,image/jpeg,image/png");
        return Arrays.stream(value.split(","))
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

    private void ensureEditable(ExemptionRequest entity) {
        ExemptionStatus status = ExemptionStatus.of(entity.getFinalStatus());
        if (!status.editable() || (entity.getLocked() != null && entity.getLocked() == 1)) {
            throw new BizException("当前状态不可编辑");
        }
    }

    private void ensureCanWriteRequest(ExemptionRequest request, String permissionCode) {
        Student student = requireStudent(request.getStudentId());
        ensureCanWriteStudent(student, permissionCode);
    }

    private void ensureCanWriteStudent(Student student, String permissionCode) {
        DataScopeContext.Scope scope = dataScopeService.resolve(permissionCode);
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该免考申请");
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
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权操作该免考申请");
    }

    private void ensureReadableRequest(ExemptionRequest request) {
        Student student = requireStudent(request.getStudentId());
        ensureCanReadStudent(student);
    }

    private void ensureCanReadStudent(Student student) {
        DataScopeContext.Scope scope = dataScopeService.resolve(readPermission());
        if (scope == null) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该免考申请");
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
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权访问该免考申请");
    }

    private String readPermission() {
        if (UserContext.hasPermission("exemption:firstReview")) {
            return "exemption:firstReview";
        }
        if (UserContext.hasPermission("exemption:secondReview")) {
            return "exemption:secondReview";
        }
        if (UserContext.hasPermission("student:view")) {
            return "student:view";
        }
        return "exemption:apply";
    }

    private String returnTargetFromSecondRejected(ExemptionStatus currentStatus) {
        if (currentStatus != ExemptionStatus.SECOND_REJECTED) {
            return ExemptionStatus.FIRST_REVIEW.name();
        }
        String target = paramService.getString("review.return.target", "FIRST_REVIEW");
        return "SECOND_REVIEW".equalsIgnoreCase(target) ? ExemptionStatus.SECOND_REVIEW.name() : ExemptionStatus.FIRST_REVIEW.name();
    }

    private ExemptionRequest existing(Long studentId, String year, String subject) {
        return requestMapper.selectOne(new LambdaQueryWrapper<ExemptionRequest>()
                .eq(ExemptionRequest::getStudentId, studentId)
                .eq(ExemptionRequest::getAssessmentYear, year)
                .eq(ExemptionRequest::getSubject, subject)
                .last("LIMIT 1"));
    }

    private long materialCount(Long requestId) {
        return materialMapper.selectCount(new LambdaQueryWrapper<ExemptionMaterial>()
                .eq(ExemptionMaterial::getExemptionRequestId, requestId));
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

    private ExemptionRequest requireRequest(Long id) {
        if (id == null) {
            throw new BizException("免考申请ID不能为空");
        }
        ExemptionRequest entity = requestMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "免考申请不存在");
        }
        return entity;
    }

    private ExemptionMaterial requireMaterial(Long id) {
        if (id == null) {
            throw new BizException("免考佐证ID不能为空");
        }
        ExemptionMaterial material = materialMapper.selectById(id);
        if (material == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "免考佐证不存在");
        }
        FileObject file = fileObjectMapper.selectById(material.getFileId());
        if (file == null) {
            throw new BizException(ResultCode.NOT_FOUND.getCode(), "附件不存在");
        }
        return material;
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
