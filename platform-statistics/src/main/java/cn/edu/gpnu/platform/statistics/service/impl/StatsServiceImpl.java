package cn.edu.gpnu.platform.statistics.service.impl;

import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.certificate.support.CertificateStatus;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionRequest;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionRequestMapper;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.material.support.MaterialStatus;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.student.support.BirthDateValidator;
import cn.edu.gpnu.platform.business.student.support.IdCardValidator;
import cn.edu.gpnu.platform.business.student.support.StudentStatus;
import cn.edu.gpnu.platform.business.training.dto.TrainingProfileSaveRequest;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.business.training.support.MajorCodeValidator;
import cn.edu.gpnu.platform.business.training.support.TrainingLinkValidator;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.entity.VideoReviewTask;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.business.video.support.VideoReviewStatus;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.exchange.entity.ImportExportBatch;
import cn.edu.gpnu.platform.exchange.mapper.ImportExportBatchMapper;
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.statistics.dto.StatsQuery;
import cn.edu.gpnu.platform.statistics.service.StatsService;
import cn.edu.gpnu.platform.statistics.vo.StatsDetailVO;
import cn.edu.gpnu.platform.statistics.vo.StatsExportFile;
import cn.edu.gpnu.platform.statistics.vo.StatsMetricVO;
import cn.edu.gpnu.platform.statistics.vo.StatsReportVO;
import cn.edu.gpnu.platform.statistics.vo.StatsRowVO;
import cn.edu.gpnu.platform.system.entity.SysCollege;
import cn.edu.gpnu.platform.system.mapper.SysCollegeMapper;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.DictService;
import cn.edu.gpnu.platform.system.service.ParamService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final String DENOMINATOR_RULE = "分母按当前考核年度在册学生计；学生表无年度字段，默认使用 current_assessment_year 参数并按当前数据范围内学生集合计。";
    private static final String CONTENT_TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Pattern CERT_NO_PATTERN = Pattern.compile("^\\d{18}$");
    private static final Set<String> EDUCATION_GRADUATE_PREFIXES = Set.of("0401", "0451", "0453");

    private final StudentMapper studentMapper;
    private final TrainingProfileMapper trainingProfileMapper;
    private final ProcessMaterialMapper materialMapper;
    private final ExemptionRequestMapper exemptionMapper;
    private final VideoReviewMapper videoReviewMapper;
    private final VideoReviewTaskMapper videoReviewTaskMapper;
    private final CertificateMapper certificateMapper;
    private final ImportExportBatchMapper batchMapper;
    private final SysCollegeMapper collegeMapper;
    private final DictService dictService;
    private final DataScopeService dataScopeService;
    private final ParamService paramService;
    private final ExchangeExcelHelper excelHelper;
    private final IdCardValidator idCardValidator;
    private final BirthDateValidator birthDateValidator;
    private final MajorCodeValidator majorCodeValidator;
    private final TrainingLinkValidator trainingLinkValidator;

    @Override
    public StatsReportVO report(String type, StatsQuery query) {
        String normalized = normalizeType(type);
        StatsQuery q = query == null ? new StatsQuery() : query;
        String year = currentYear(q);
        StatsScope scope = resolveScope(q);
        return switch (normalized) {
            case "submission" -> submissionReport(scope, q, year);
            case "materials" -> materialReport(scope, q, year);
            case "exemptions" -> exemptionReport(scope, q, year);
            case "videos" -> videoReport(scope, q, year);
            case "certificates" -> certificateReport(scope, q, year);
            case "cross" -> crossReport(scope, q, year);
            case "anomalies" -> anomalyReport(scope, q, year);
            case "batches" -> batchReport(scope, q, year);
            default -> throw new BizException("未知统计类型: " + type);
        };
    }

    @Override
    public StatsExportFile export(String type, StatsQuery query) {
        StatsReportVO report = report(type, query);
        List<String> headers = new ArrayList<>(List.of("统计类型", "维度", "维度名称", "状态", "状态名称", "数量"));
        List<String> extraKeys = report.getRows().stream()
                .flatMap(row -> row.getValues().keySet().stream())
                .distinct()
                .toList();
        headers.addAll(extraKeys);
        List<List<String>> rows = report.getRows().stream().map(row -> {
            List<String> values = new ArrayList<>();
            values.add(report.getTitle());
            values.add(safe(row.getDimension()));
            values.add(safe(row.getDimensionLabel()));
            values.add(safe(row.getStatus()));
            values.add(safe(row.getStatusLabel()));
            values.add(String.valueOf(row.getCount() == null ? 0 : row.getCount()));
            for (String key : extraKeys) {
                values.add(safe(row.getValues().get(key)));
            }
            return values;
        }).toList();
        byte[] content = excelHelper.writeTableWorkbook(report.getTitle(), headers, rows);
        return new StatsExportFile(report.getType() + "-stats.xlsx", CONTENT_TYPE_XLSX, content);
    }

    private StatsReportVO submissionReport(StatsScope scope, StatsQuery query, String year) {
        List<Student> students = scopedStudents(scope, query, year);
        Map<Long, String> colleges = collegeNames();
        StatsReportVO report = baseReport("submission", "学院提交进度统计", year);
        addMetric(report, "学生数", students.size(), "人");
        addMetric(report, "复审通过", students.stream().filter(item -> "PASSED".equals(item.getStatus())).count(), "人");
        Map<String, Long> grouped = students.stream().collect(Collectors.groupingBy(
                item -> key(colleges.getOrDefault(item.getCollegeId(), String.valueOf(item.getCollegeId())), item.getStatus()),
                LinkedHashMap::new,
                Collectors.counting()));
        grouped.forEach((key, count) -> {
            String[] parts = splitKey(key);
            StatsRowVO row = row("college", parts[0], parts[1], studentStatusLabel(parts[1]), count);
            row.getValues().put("口径", "学生状态");
            report.getRows().add(row);
        });
        students.stream().limit(200).forEach(student -> report.getDetails().add(studentDetail(student, colleges)));
        return report;
    }

    private StatsReportVO materialReport(StatsScope scope, StatsQuery query, String year) {
        List<Student> students = scopedStudents(scope, query, year);
        Map<Long, Student> studentMap = byId(students);
        List<ProcessMaterial> materials = materialsFor(studentMap.keySet(), year);
        Map<String, String> categoryLabels = dictLabels("material_category");
        if (categoryLabels.isEmpty()) {
            materials.stream().map(ProcessMaterial::getCategory).filter(StringUtils::hasText)
                    .forEach(code -> categoryLabels.putIfAbsent(code, code));
        }
        Set<String> requiredCategories = new LinkedHashSet<>(categoryLabels.keySet());
        Map<Long, Set<String>> passedCategories = new LinkedHashMap<>();
        for (ProcessMaterial material : materials) {
            if (MaterialStatus.PASSED.name().equals(material.getStatus())) {
                passedCategories.computeIfAbsent(material.getStudentId(), ignored -> new LinkedHashSet<>()).add(material.getCategory());
            }
        }
        long qualified = students.stream()
                .filter(student -> !requiredCategories.isEmpty()
                        && passedCategories.getOrDefault(student.getId(), Set.of()).containsAll(requiredCategories))
                .count();
        StatsReportVO report = baseReport("materials", "材料完成率统计", year);
        addMetric(report, "应交人数", students.size(), "人");
        addMetric(report, "材料合格人数", qualified, "人");
        addMetric(report, "材料完成率", students.isEmpty() ? "0%" : percent(qualified, students.size()), "");
        report.getRows().add(row("overall", "整体合格", "PASSED", "合格", qualified));
        report.getRows().add(row("overall", "整体合格", "NOT_QUALIFIED", "未合格", Math.max(0, students.size() - qualified)));
        Map<String, Long> grouped = materials.stream().collect(Collectors.groupingBy(
                item -> key(item.getCategory(), item.getStatus()),
                LinkedHashMap::new,
                Collectors.counting()));
        grouped.forEach((key, count) -> {
            String[] parts = splitKey(key);
            StatsRowVO row = row("category", categoryLabels.getOrDefault(parts[0], parts[0]),
                    parts[1], materialStatusLabel(parts[1]), count);
            row.getValues().put("材料类别", parts[0]);
            report.getRows().add(row);
        });
        for (Student student : students) {
            Set<String> passed = passedCategories.getOrDefault(student.getId(), Set.of());
            for (String category : requiredCategories) {
                if (!passed.contains(category)) {
                    StatsDetailVO detail = studentDetail(student, collegeNames());
                    detail.setFieldName(categoryLabels.getOrDefault(category, category));
                    detail.setErrorReason("材料未复审通过或缺失");
                    report.getDetails().add(detail);
                }
            }
        }
        return report;
    }

    private StatsReportVO exemptionReport(StatsScope scope, StatsQuery query, String year) {
        List<Student> students = scopedStudents(scope, query, year);
        Map<Long, Student> studentMap = byId(students);
        List<ExemptionRequest> records = exemptionFor(studentMap.keySet(), year);
        StatsReportVO report = baseReport("exemptions", "免考统计", year);
        addMetric(report, "免考申请科目数", records.size(), "科");
        addMetric(report, "复审通过科目数", records.stream().filter(item -> "PASSED".equals(item.getFinalStatus())).count(), "科");
        Map<String, Long> grouped = records.stream().collect(Collectors.groupingBy(
                item -> key(labelOrCode(item.getSubjectLabel(), item.getSubject()), item.getFinalStatus()),
                LinkedHashMap::new,
                Collectors.counting()));
        grouped.forEach((key, count) -> {
            String[] parts = splitKey(key);
            report.getRows().add(row("subject", parts[0], parts[1], reviewStatusLabel(parts[1]), count));
        });
        records.stream().limit(200).forEach(item -> {
            Student student = studentMap.get(item.getStudentId());
            StatsDetailVO detail = studentDetail(student, collegeNames());
            detail.setFieldName(labelOrCode(item.getSubjectLabel(), item.getSubject()));
            detail.setErrorReason(reviewStatusLabel(item.getFinalStatus()));
            detail.getValues().put("依据", safe(item.getBasisLabel()));
            report.getDetails().add(detail);
        });
        return report;
    }

    private StatsReportVO videoReport(StatsScope scope, StatsQuery query, String year) {
        List<Student> students = scopedStudents(scope, query, year);
        Map<Long, Student> studentMap = byId(students);
        List<VideoReview> reviews = videoReviewsFor(studentMap.keySet(), year);
        StatsReportVO report = baseReport("videos", "视频评审进度统计", year);
        addMetric(report, "应传人数", students.size(), "人");
        addMetric(report, "已上传人数", reviews.stream().filter(item -> item.getVideoFileId() != null).count(), "人");
        addMetric(report, "需复评", reviews.stream().filter(item -> VideoReviewStatus.NEED_REVIEW.name().equals(item.getStatus())).count(), "人");
        Map<String, Long> grouped = reviews.stream().collect(Collectors.groupingBy(VideoReview::getStatus, LinkedHashMap::new, Collectors.counting()));
        long notUploaded = Math.max(0, students.size() - reviews.size());
        report.getRows().add(row("video", "上传评审", VideoReviewStatus.WAIT_UPLOAD.name(), "未上传", notUploaded));
        grouped.forEach((status, count) -> report.getRows().add(row("video", "上传评审", status, videoStatusLabel(status), count)));
        List<Long> reviewIds = reviews.stream().map(VideoReview::getId).toList();
        if (!reviewIds.isEmpty()) {
            List<VideoReviewTask> tasks = videoReviewTaskMapper.selectList(new LambdaQueryWrapper<VideoReviewTask>()
                    .in(VideoReviewTask::getVideoReviewId, reviewIds));
            Map<Long, VideoReview> reviewMap = reviews.stream().collect(Collectors.toMap(VideoReview::getId, Function.identity()));
            tasks.stream().limit(200).forEach(task -> {
                VideoReview review = reviewMap.get(task.getVideoReviewId());
                Student student = review == null ? null : studentMap.get(review.getStudentId());
                StatsDetailVO detail = studentDetail(student, collegeNames());
                detail.setFieldName("评审教师任务");
                detail.setErrorReason(task.getSubmitted() != null && task.getSubmitted() == 1 ? "已评审" : "待评审");
                detail.getValues().put("reviewerId", String.valueOf(task.getReviewerId()));
                detail.getValues().put("score", task.getScore() == null ? "" : String.valueOf(task.getScore()));
                report.getDetails().add(detail);
            });
        }
        return report;
    }

    private StatsReportVO certificateReport(StatsScope scope, StatsQuery query, String year) {
        List<Student> students = scopedStudents(scope, query, year);
        Map<Long, Student> studentMap = byId(students);
        List<Certificate> certificates = certificatesFor(studentMap.keySet(), year);
        StatsReportVO report = baseReport("certificates", "证书生成统计", year);
        addMetric(report, "应生成学生数", students.size(), "人");
        addMetric(report, "证书记录数", certificates.size(), "张");
        Map<String, Long> grouped = certificates.stream().collect(Collectors.groupingBy(
                Certificate::getStatus, LinkedHashMap::new, Collectors.counting()));
        long generatedStudents = certificates.stream()
                .filter(item -> !"VOIDED".equals(item.getStatus()))
                .map(Certificate::getStudentId)
                .distinct()
                .count();
        report.getRows().add(row("certificate", "证书状态", CertificateStatus.WAIT_GENERATE.name(),
                CertificateStatus.WAIT_GENERATE.label(), Math.max(0, students.size() - generatedStudents)));
        for (CertificateStatus status : CertificateStatus.values()) {
            report.getRows().add(row("certificate", "证书状态", status.name(), status.label(),
                    grouped.getOrDefault(status.name(), 0L)));
        }
        certificates.stream().limit(200).forEach(cert -> {
            Student student = studentMap.get(cert.getStudentId());
            StatsDetailVO detail = studentDetail(student, collegeNames());
            detail.setFieldName("证书状态");
            detail.setErrorReason(certStatusLabel(cert.getStatus()));
            detail.getValues().put("证书编号", safe(cert.getCertNo()));
            detail.getValues().put("签发日期", safe(cert.getIssueDate()));
            detail.getValues().put("有效期", safe(cert.getValidUntil()));
            report.getDetails().add(detail);
        });
        return report;
    }

    private StatsReportVO crossReport(StatsScope scope, StatsQuery query, String year) {
        List<Student> students = scopedStudents(scope, query, year);
        Map<Long, Student> studentMap = byId(students);
        List<TrainingProfile> trainings = trainingsFor(studentMap.keySet(), year);
        StatsReportVO report = baseReport("cross", "任教学段/学科交叉统计", year);
        addMetric(report, "培养信息记录数", trainings.size(), "条");
        Map<String, Long> grouped = trainings.stream().collect(Collectors.groupingBy(item -> {
            Student student = studentMap.get(item.getStudentId());
            return key(item.getTeachingSegment(), item.getTeachingSubjectName(), student == null ? "" : student.getIdentityType(), item.getEducationLevel());
        }, LinkedHashMap::new, Collectors.counting()));
        grouped.forEach((key, count) -> {
            String[] parts = splitKey(key);
            StatsRowVO row = row("segmentSubject", safe(parts, 0) + "/" + safe(parts, 1), safe(parts, 2), safe(parts, 3), count);
            row.getValues().put("任教学段", safe(parts, 0));
            row.getValues().put("任教学科", safe(parts, 1));
            row.getValues().put("身份类型", safe(parts, 2));
            row.getValues().put("学历层次", safe(parts, 3));
            report.getRows().add(row);
        });
        return report;
    }

    private StatsReportVO anomalyReport(StatsScope scope, StatsQuery query, String year) {
        List<Student> students = scopedStudents(scope, query, year);
        Map<Long, Student> studentMap = byId(students);
        Map<Long, List<TrainingProfile>> trainingMap = trainingsFor(studentMap.keySet(), year).stream()
                .collect(Collectors.groupingBy(TrainingProfile::getStudentId));
        List<Certificate> certificates = certificatesFor(studentMap.keySet(), year);
        StatsReportVO report = baseReport("anomalies", "异常数据统计", year);
        Map<Long, String> colleges = collegeNames();
        for (Student student : students) {
            validateStudentAnomalies(report, student, colleges);
            for (TrainingProfile training : trainingMap.getOrDefault(student.getId(), List.of())) {
                validateTrainingAnomalies(report, student, training, colleges);
            }
        }
        for (Certificate certificate : certificates) {
            Student student = studentMap.get(certificate.getStudentId());
            validateCertificateAnomalies(report, student, certificate, colleges);
        }
        Map<String, Long> grouped = report.getDetails().stream().collect(Collectors.groupingBy(
                detail -> key(detail.getFieldName(), detail.getErrorReason()), LinkedHashMap::new, Collectors.counting()));
        grouped.forEach((key, count) -> {
            String[] parts = splitKey(key);
            report.getRows().add(row("anomaly", parts[0], parts[1], parts[1], count));
        });
        addMetric(report, "异常数", report.getDetails().size(), "条");
        return report;
    }

    private StatsReportVO batchReport(StatsScope scope, StatsQuery query, String year) {
        List<ImportExportBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<ImportExportBatch>()
                .orderByDesc(ImportExportBatch::getOperateTime));
        List<ImportExportBatch> scoped = batches.stream()
                .filter(batch -> batchVisible(scope, batch))
                .filter(batch -> !StringUtils.hasText(query.getStatus()) || query.getStatus().trim().equals(batch.getStatus()))
                .filter(batch -> !StringUtils.hasText(query.getKeyword()) || contains(batch.getBatchNo(), query.getKeyword())
                        || contains(batch.getFileName(), query.getKeyword()))
                .toList();
        StatsReportVO report = baseReport("batches", "导入导出日志统计", year);
        addMetric(report, "批次数", scoped.size(), "批");
        addMetric(report, "成功数", scoped.stream().mapToLong(item -> item.getSuccessCount() == null ? 0 : item.getSuccessCount()).sum(), "条");
        addMetric(report, "失败数", scoped.stream().mapToLong(item -> item.getFailCount() == null ? 0 : item.getFailCount()).sum(), "条");
        Map<String, Long> grouped = scoped.stream().collect(Collectors.groupingBy(
                item -> key(item.getType(), item.getStatus()), LinkedHashMap::new, Collectors.counting()));
        grouped.forEach((key, count) -> {
            String[] parts = splitKey(key);
            report.getRows().add(row("batch", parts[0], parts[1], parts[1], count));
        });
        scoped.stream().limit(200).forEach(batch -> {
            StatsDetailVO detail = new StatsDetailVO();
            detail.setFieldName(batch.getType());
            detail.setErrorReason(batch.getStatus());
            detail.getValues().put("批次号", safe(batch.getBatchNo()));
            detail.getValues().put("文件名", safe(batch.getFileName()));
            detail.getValues().put("操作人ID", batch.getOperatorId() == null ? "" : String.valueOf(batch.getOperatorId()));
            detail.getValues().put("操作时间", batch.getOperateTime() == null ? "" : batch.getOperateTime().toString());
            detail.getValues().put("成功", String.valueOf(batch.getSuccessCount() == null ? 0 : batch.getSuccessCount()));
            detail.getValues().put("失败", String.valueOf(batch.getFailCount() == null ? 0 : batch.getFailCount()));
            report.getDetails().add(detail);
        });
        return report;
    }

    private void validateStudentAnomalies(StatsReportVO report, Student student, Map<Long, String> colleges) {
        if (!StringUtils.hasText(student.getStudentNo())) {
            addAnomaly(report, student, colleges, "字段缺失", "学号为空");
        }
        if (!StringUtils.hasText(student.getName())) {
            addAnomaly(report, student, colleges, "字段缺失", "姓名为空");
        }
        if (!StringUtils.hasText(student.getIdCardNo())) {
            addAnomaly(report, student, colleges, "字段缺失", "证件号码为空");
        }
        try {
            idCardValidator.validate(student.getIdCardType(), student.getIdCardNo());
        } catch (Exception e) {
            addAnomaly(report, student, colleges, "证件号码", e.getMessage());
        }
        try {
            birthDateValidator.validate(student.getIdCardType(), student.getIdCardNo(), student.getBirthDate());
        } catch (Exception e) {
            addAnomaly(report, student, colleges, "出生日期", e.getMessage());
        }
    }

    private void validateTrainingAnomalies(StatsReportVO report, Student student, TrainingProfile training, Map<Long, String> colleges) {
        TrainingProfileSaveRequest request = new TrainingProfileSaveRequest();
        request.setStudentId(student.getId());
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
        request.setTeachingSubjectCode(training.getTeachingSubjectCode());
        request.setInterviewOrgMode(training.getInterviewOrgMode());
        request.setAbilityTestConclusion(training.getAbilityTestConclusion());
        try {
            var major = majorCodeValidator.validate(student, request);
            trainingLinkValidator.validate(request, major);
        } catch (Exception e) {
            String field = messageContains(e, "专业") ? "专业代码" : "学段学科";
            addAnomaly(report, student, colleges, field, e.getMessage());
        }
    }

    private void validateCertificateAnomalies(StatsReportVO report, Student student, Certificate certificate, Map<Long, String> colleges) {
        if (student == null) {
            return;
        }
        if (!StringUtils.hasText(certificate.getCertNo()) || !CERT_NO_PATTERN.matcher(certificate.getCertNo()).matches()) {
            addAnomaly(report, student, colleges, "证书编号", "证书编号非18位数字");
        }
        if (!StringUtils.hasText(certificate.getValidUntil())) {
            addAnomaly(report, student, colleges, "有效期限", "有效期限为空");
        }
    }

    private List<Student> scopedStudents(StatsScope scope, StatsQuery query, String year) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<Student>().orderByAsc(Student::getStudentNo);
        if (scope.studentId() != null) {
            wrapper.eq(Student::getId, scope.studentId());
        }
        if (!scope.allSchool()) {
            if (scope.collegeIds().isEmpty()) {
                return List.of();
            }
            wrapper.in(Student::getCollegeId, scope.collegeIds());
        }
        if (StringUtils.hasText(query.getCollegeId())) {
            Long collegeId = parseLong(query.getCollegeId());
            if (collegeId == null || !scope.allowsCollege(collegeId)) {
                return List.of();
            }
            wrapper.eq(Student::getCollegeId, collegeId);
        }
        if (StringUtils.hasText(query.getClassName())) {
            wrapper.eq(Student::getClassName, query.getClassName().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(Student::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Student::getStudentNo, keyword).or().like(Student::getName, keyword));
        }
        List<Student> students = studentMapper.selectList(wrapper);
        if (!StringUtils.hasText(query.getInternalMajorCode())
                && !StringUtils.hasText(query.getTeachingSegment())
                && !StringUtils.hasText(query.getTeachingSubjectCode())) {
            return students;
        }
        Set<Long> ids = students.stream().map(Student::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> matched = trainingsFor(ids, year).stream()
                .filter(item -> !StringUtils.hasText(query.getInternalMajorCode())
                        || query.getInternalMajorCode().trim().equals(item.getInternalMajorCode()))
                .filter(item -> !StringUtils.hasText(query.getTeachingSegment())
                        || query.getTeachingSegment().trim().equals(item.getTeachingSegment()))
                .filter(item -> !StringUtils.hasText(query.getTeachingSubjectCode())
                        || query.getTeachingSubjectCode().trim().equals(item.getTeachingSubjectCode()))
                .map(TrainingProfile::getStudentId)
                .collect(Collectors.toSet());
        return students.stream().filter(student -> matched.contains(student.getId())).toList();
    }

    private List<TrainingProfile> trainingsFor(Set<Long> studentIds, String year) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return trainingProfileMapper.selectList(new LambdaQueryWrapper<TrainingProfile>()
                .in(TrainingProfile::getStudentId, studentIds)
                .eq(TrainingProfile::getAssessmentYear, year));
    }

    private List<ProcessMaterial> materialsFor(Set<Long> studentIds, String year) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return materialMapper.selectList(new LambdaQueryWrapper<ProcessMaterial>()
                .in(ProcessMaterial::getStudentId, studentIds)
                .eq(ProcessMaterial::getAssessmentYear, year));
    }

    private List<ExemptionRequest> exemptionFor(Set<Long> studentIds, String year) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return exemptionMapper.selectList(new LambdaQueryWrapper<ExemptionRequest>()
                .in(ExemptionRequest::getStudentId, studentIds)
                .eq(ExemptionRequest::getAssessmentYear, year));
    }

    private List<VideoReview> videoReviewsFor(Set<Long> studentIds, String year) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return videoReviewMapper.selectList(new LambdaQueryWrapper<VideoReview>()
                .in(VideoReview::getStudentId, studentIds)
                .eq(VideoReview::getAssessmentYear, year));
    }

    private List<Certificate> certificatesFor(Set<Long> studentIds, String year) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        return certificateMapper.selectList(new LambdaQueryWrapper<Certificate>()
                .in(Certificate::getStudentId, studentIds)
                .eq(Certificate::getAssessmentYear, year));
    }

    private StatsScope resolveScope(StatsQuery query) {
        DataScopeContext.Scope scope = dataScopeService.resolve("stats:view");
        if (scope == null || scope.getScopeType() == DataScopeContext.ScopeType.NONE) {
            throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权查看统计数据");
        }
        if (scope.allSchool()) {
            return new StatsScope(true, Set.of(), null, scope.getUserId());
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE) {
            return new StatsScope(false, new LinkedHashSet<>(scope.getCollegeIds()), null, scope.getUserId());
        }
        if (scope.getScopeType() == DataScopeContext.ScopeType.SELF && scope.getStudentId() != null) {
            return new StatsScope(false, Set.of(), scope.getStudentId(), scope.getUserId());
        }
        throw new BizException(ResultCode.FORBIDDEN.getCode(), "无权查看统计数据");
    }

    private boolean batchVisible(StatsScope scope, ImportExportBatch batch) {
        if (scope.allSchool()) {
            return true;
        }
        if (Objects.equals(batch.getOperatorId(), scope.userId())) {
            return true;
        }
        String scopeJson = batch.getScopeJson();
        return StringUtils.hasText(scopeJson)
                && scope.collegeIds().stream().map(String::valueOf).anyMatch(scopeJson::contains);
    }

    private StatsReportVO baseReport(String type, String title, String year) {
        StatsReportVO report = new StatsReportVO();
        report.setType(type);
        report.setTitle(title);
        report.setAssessmentYear(year);
        report.setDenominatorRule(DENOMINATOR_RULE);
        return report;
    }

    private StatsRowVO row(String dimension, String dimensionLabel, String status, String statusLabel, long count) {
        StatsRowVO row = new StatsRowVO();
        row.setDimension(dimension);
        row.setDimensionLabel(safe(dimensionLabel));
        row.setStatus(safe(status));
        row.setStatusLabel(safe(statusLabel));
        row.setCount(count);
        return row;
    }

    private StatsDetailVO studentDetail(Student student, Map<Long, String> colleges) {
        StatsDetailVO detail = new StatsDetailVO();
        if (student == null) {
            return detail;
        }
        detail.setStudentId(String.valueOf(student.getId()));
        detail.setStudentNo(student.getStudentNo());
        detail.setStudentName(student.getName());
        detail.setCollegeId(student.getCollegeId() == null ? "" : String.valueOf(student.getCollegeId()));
        detail.setCollegeName(colleges.getOrDefault(student.getCollegeId(), detail.getCollegeId()));
        detail.getValues().put("班级", safe(student.getClassName()));
        detail.getValues().put("学生状态", studentStatusLabel(student.getStatus()));
        return detail;
    }

    private void addAnomaly(StatsReportVO report, Student student, Map<Long, String> colleges, String field, String reason) {
        StatsDetailVO detail = studentDetail(student, colleges);
        detail.setFieldName(field);
        detail.setErrorReason(StringUtils.hasText(reason) ? reason : "数据异常");
        report.getDetails().add(detail);
    }

    private void addMetric(StatsReportVO report, String label, long value, String unit) {
        report.getMetrics().add(new StatsMetricVO(label, String.valueOf(value), unit));
    }

    private void addMetric(StatsReportVO report, String label, String value, String unit) {
        report.getMetrics().add(new StatsMetricVO(label, value, unit));
    }

    private Map<Long, Student> byId(List<Student> students) {
        return students.stream().collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, String> collegeNames() {
        return collegeMapper.selectList(new LambdaQueryWrapper<SysCollege>().orderByAsc(SysCollege::getSort))
                .stream()
                .collect(Collectors.toMap(SysCollege::getId, SysCollege::getName, (a, b) -> a, LinkedHashMap::new));
    }

    // Phase 44c（§7.3）：改走 DictService 缓存标签表（写时逐出）。返回可变副本——调用方（如 statMaterials 补齐缺失类别）
    // 会 putIfAbsent 原地改写，故必须复制，杜绝污染共享缓存对象。
    private Map<String, String> dictLabels(String typeCode) {
        return new LinkedHashMap<>(dictService.dictLabels(typeCode));
    }

    private String currentYear(StatsQuery query) {
        if (StringUtils.hasText(query.getAssessmentYear())) {
            return query.getAssessmentYear().trim();
        }
        return paramService.getString("current_assessment_year", "2026");
    }

    private String normalizeType(String type) {
        String value = StringUtils.hasText(type) ? type.trim().toLowerCase(Locale.ROOT) : "";
        return switch (value) {
            case "submission", "submission-progress", "progress" -> "submission";
            case "material", "materials", "material-completion" -> "materials";
            case "exemption", "exemptions" -> "exemptions";
            case "video", "videos", "video-review" -> "videos";
            case "certificate", "certificates", "cert" -> "certificates";
            case "cross", "teaching-cross", "segment-subject" -> "cross";
            case "anomaly", "anomalies", "exceptions" -> "anomalies";
            case "batch", "batches", "exchange-log" -> "batches";
            default -> value;
        };
    }

    private String studentStatusLabel(String status) {
        try {
            return StudentStatus.of(status).label();
        } catch (Exception ignored) {
            return safe(status);
        }
    }

    private String materialStatusLabel(String status) {
        try {
            return MaterialStatus.of(status).label();
        } catch (Exception ignored) {
            return safe(status);
        }
    }

    private String certStatusLabel(String status) {
        try {
            return CertificateStatus.of(status).label();
        } catch (Exception ignored) {
            return safe(status);
        }
    }

    private String videoStatusLabel(String status) {
        try {
            return VideoReviewStatus.of(status).label();
        } catch (Exception ignored) {
            return safe(status);
        }
    }

    private String reviewStatusLabel(String status) {
        return switch (safe(status)) {
            case "DRAFT" -> "草稿";
            case "FIRST_REVIEW" -> "待初审";
            case "FIRST_REJECTED" -> "初审退回";
            case "SECOND_REVIEW" -> "待复审";
            case "SECOND_REJECTED" -> "复审退回";
            case "PASSED" -> "复审通过";
            case "FAILED" -> "不合格";
            default -> safe(status);
        };
    }

    private String key(String... values) {
        return String.join("\u001F", java.util.Arrays.stream(values).map(this::safe).toList());
    }

    private String[] splitKey(String key) {
        return key.split("\u001F", -1);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safe(String[] values, int index) {
        return values.length > index ? safe(values[index]) : "";
    }

    private String labelOrCode(String label, String code) {
        return StringUtils.hasText(label) ? label : safe(code);
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && StringUtils.hasText(keyword) && value.contains(keyword.trim());
    }

    private boolean messageContains(Exception e, String text) {
        return e.getMessage() != null && e.getMessage().contains(text);
    }

    private String percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return "0%";
        }
        return String.format(Locale.ROOT, "%.2f%%", numerator * 100.0 / denominator);
    }

    private record StatsScope(boolean allSchool, Set<Long> collegeIds, Long studentId, Long userId) {
        boolean allowsCollege(Long collegeId) {
            return allSchool || (collegeId != null && collegeIds.contains(collegeId));
        }
    }
}
