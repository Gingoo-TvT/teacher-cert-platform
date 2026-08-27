package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.training.entity.TrainingProfile;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.exchange.entity.ImportRecordRef;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import cn.edu.gpnu.platform.system.service.CollegeParentGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 已锁定导入引用的回滚补偿内核；事务与批次状态编排由调用方负责。
 */
@Component
@RequiredArgsConstructor
public class ExchangeRollbackService {

    private final StudentMapper studentMapper;
    private final TrainingProfileMapper trainingProfileMapper;
    private final CertificateMapper certificateMapper;
    private final CollegeParentGuard collegeParentGuard;
    private final ObjectMapper objectMapper;
    private final IdCardProtectionService idCardProtectionService;

    public RollbackSummary compensateLocked(List<ImportRecordRef> refs) {
        RollbackParentPlan parentPlan = prepareRollbackParentLocks(refs);
        int rolledBack = 0;
        List<String> conflicts = new ArrayList<>();
        for (ImportRecordRef ref : refs) {
            RollbackDecision decision = parentPlan.conflictFor(ref);
            if (decision == null) {
                decision = rollbackOne(ref);
            }
            if (decision.success()) {
                rolledBack++;
            } else {
                conflicts.add(decision.message());
            }
        }
        return new RollbackSummary(rolledBack, conflicts);
    }

    private RollbackParentPlan prepareRollbackParentLocks(List<ImportRecordRef> refs) {
        Map<Long, Long> targetCollegeByRefId = new LinkedHashMap<>();
        Map<Long, RollbackDecision> conflictByRefId = new LinkedHashMap<>();
        Map<Long, ImportRecordRef> refById = refs.stream()
                .collect(Collectors.toMap(ImportRecordRef::getId, Function.identity()));
        for (ImportRecordRef ref : refs) {
            if (!"UPDATE".equals(ref.getAction()) || !hasRollbackCollegeParent(ref.getTableName())) {
                continue;
            }
            Long targetCollegeId = rollbackTargetCollegeId(ref.getBeforeJson());
            if (targetCollegeId == null) {
                conflictByRefId.put(ref.getId(), new RollbackDecision(false,
                        rollbackLabel(ref.getTableName()) + "#" + ref.getRecordId()
                                + "回滚快照缺少有效目标学院，禁止还原"));
                continue;
            }
            targetCollegeByRefId.put(ref.getId(), targetCollegeId);
        }

        Set<Long> missingCollegeIds = new LinkedHashSet<>();
        targetCollegeByRefId.values().stream()
                .distinct()
                .sorted()
                .forEach(collegeId -> {
                    Integer status = collegeParentGuard.lockStatusForUpdate(
                            collegeId, CollegeParentGuard.Operation.ROLLBACK_RESTORE);
                    if (status == null) {
                        missingCollegeIds.add(collegeId);
                    }
                });
        targetCollegeByRefId.forEach((refId, collegeId) -> {
            if (!missingCollegeIds.contains(collegeId)) {
                return;
            }
            ImportRecordRef ref = refById.get(refId);
            conflictByRefId.put(refId, new RollbackDecision(false,
                    rollbackLabel(ref.getTableName()) + "#" + ref.getRecordId()
                            + "目标学院#" + collegeId + "不存在或已删除，禁止还原"));
        });
        return new RollbackParentPlan(conflictByRefId);
    }

    private Long rollbackTargetCollegeId(String beforeJson) {
        if (!StringUtils.hasText(beforeJson)) {
            return null;
        }
        try {
            JsonNode collegeId = objectMapper.readTree(beforeJson).get("collegeId");
            if (collegeId == null || collegeId.isNull()) {
                return null;
            }
            Long value = parseLong(collegeId.asText());
            return value != null && value > 0 ? value : null;
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private boolean hasRollbackCollegeParent(String tableName) {
        return "student".equals(tableName)
                || "training_profile".equals(tableName)
                || "certificate".equals(tableName);
    }

    private String rollbackLabel(String tableName) {
        return switch (tableName) {
            case "student" -> "学生";
            case "training_profile" -> "培养信息";
            case "certificate" -> "证书";
            default -> "记录";
        };
    }

    private RollbackDecision rollbackOne(ImportRecordRef ref) {
        if ("student".equals(ref.getTableName())) {
            Student current = studentMapper.selectOne(new LambdaQueryWrapper<Student>()
                    .eq(Student::getId, ref.getRecordId())
                    .last("FOR UPDATE"));
            return rollbackEntity(ref, current, Student.class, this::restoreStudentSnapshot,
                    this::softDeleteImportedStudent, "学生");
        }
        if ("training_profile".equals(ref.getTableName())) {
            TrainingProfile current = trainingProfileMapper.selectOne(new LambdaQueryWrapper<TrainingProfile>()
                    .eq(TrainingProfile::getId, ref.getRecordId())
                    .last("FOR UPDATE"));
            return rollbackEntity(ref, current, TrainingProfile.class, this::restoreTrainingSnapshot,
                    trainingProfileMapper::deleteById, "培养信息");
        }
        if ("certificate".equals(ref.getTableName())) {
            Certificate current = certificateMapper.selectOne(new LambdaQueryWrapper<Certificate>()
                    .eq(Certificate::getId, ref.getRecordId())
                    .last("FOR UPDATE"));
            return rollbackEntity(ref, current, Certificate.class, this::restoreCertificateSnapshot,
                    certificateMapper::deleteById, "证书");
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
            if (updater.apply(before) != 1) {
                return new RollbackDecision(false, label + "#" + ref.getRecordId() + "恢复影响行数异常，跳过");
            }
            return new RollbackDecision(true, "已还原" + label);
        }
        return new RollbackDecision(false,
                label + "#" + ref.getRecordId() + "动作不可回滚: " + ref.getAction());
    }

    private Integer softDeleteImportedStudent(Long id) {
        return studentMapper.update(new Student(), new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, id)
                .set(Student::getIdCardHmac, null)
                .set(Student::getDeleted, 1));
    }

    private Integer restoreStudentSnapshot(Student before) {
        return studentMapper.update(null, new LambdaUpdateWrapper<Student>()
                .eq(Student::getId, before.getId())
                .set(Student::getStudentNo, before.getStudentNo())
                .set(Student::getName, before.getName())
                .set(Student::getGender, before.getGender())
                .set(Student::getIdCardType, before.getIdCardType())
                .set(Student::getIdCardNo, before.getIdCardNo())
                .set(Student::getIdCardHmac, before.getIdCardHmac())
                .set(Student::getBirthDate, before.getBirthDate())
                .set(Student::getIdentityType, before.getIdentityType())
                .set(Student::getSourceProvince, before.getSourceProvince())
                .set(Student::getSourceCity, before.getSourceCity())
                .set(Student::getSourceCounty, before.getSourceCounty())
                .set(Student::getSourceFull, before.getSourceFull())
                .set(Student::getCollegeId, before.getCollegeId())
                .set(Student::getGrade, before.getGrade())
                .set(Student::getClassName, before.getClassName())
                .set(Student::getStatus, before.getStatus())
                .set(Student::getLocked, before.getLocked())
                .set(Student::getFirstReviewerId, before.getFirstReviewerId())
                .set(Student::getFirstReviewTime, before.getFirstReviewTime())
                .set(Student::getFirstReviewComment, before.getFirstReviewComment())
                .set(Student::getSecondReviewerId, before.getSecondReviewerId())
                .set(Student::getSecondReviewTime, before.getSecondReviewTime())
                .set(Student::getSecondReviewComment, before.getSecondReviewComment())
                .set(Student::getCreatedBy, before.getCreatedBy())
                .set(Student::getCreatedAt, before.getCreatedAt())
                .set(Student::getUpdatedBy, before.getUpdatedBy())
                .set(Student::getUpdatedAt, before.getUpdatedAt()));
    }

    private Integer restoreTrainingSnapshot(TrainingProfile before) {
        return trainingProfileMapper.update(null, new LambdaUpdateWrapper<TrainingProfile>()
                .eq(TrainingProfile::getId, before.getId())
                .set(TrainingProfile::getStudentId, before.getStudentId())
                .set(TrainingProfile::getCollegeId, before.getCollegeId())
                .set(TrainingProfile::getAssessmentYear, before.getAssessmentYear())
                .set(TrainingProfile::getSecondDisciplineCode, before.getSecondDisciplineCode())
                .set(TrainingProfile::getSecondDisciplineName, before.getSecondDisciplineName())
                .set(TrainingProfile::getInternalMajorCode, before.getInternalMajorCode())
                .set(TrainingProfile::getInternalMajorName, before.getInternalMajorName())
                .set(TrainingProfile::getEducationLevel, before.getEducationLevel())
                .set(TrainingProfile::getTrainingGoal, before.getTrainingGoal())
                .set(TrainingProfile::getInternshipOrgMode, before.getInternshipOrgMode())
                .set(TrainingProfile::getInternshipLocation, before.getInternshipLocation())
                .set(TrainingProfile::getTeachingSegment, before.getTeachingSegment())
                .set(TrainingProfile::getTeachingSubjectId, before.getTeachingSubjectId())
                .set(TrainingProfile::getTeachingSubjectCode, before.getTeachingSubjectCode())
                .set(TrainingProfile::getTeachingSubjectName, before.getTeachingSubjectName())
                .set(TrainingProfile::getInterviewOrgMode, before.getInterviewOrgMode())
                .set(TrainingProfile::getAbilityTestConclusion, before.getAbilityTestConclusion())
                .set(TrainingProfile::getStatus, before.getStatus())
                .set(TrainingProfile::getLocked, before.getLocked())
                .set(TrainingProfile::getFirstReviewerId, before.getFirstReviewerId())
                .set(TrainingProfile::getFirstReviewTime, before.getFirstReviewTime())
                .set(TrainingProfile::getFirstReviewComment, before.getFirstReviewComment())
                .set(TrainingProfile::getSecondReviewerId, before.getSecondReviewerId())
                .set(TrainingProfile::getSecondReviewTime, before.getSecondReviewTime())
                .set(TrainingProfile::getSecondReviewComment, before.getSecondReviewComment())
                .set(TrainingProfile::getCreatedBy, before.getCreatedBy())
                .set(TrainingProfile::getCreatedAt, before.getCreatedAt())
                .set(TrainingProfile::getUpdatedBy, before.getUpdatedBy())
                .set(TrainingProfile::getUpdatedAt, before.getUpdatedAt()));
    }

    private Integer restoreCertificateSnapshot(Certificate before) {
        return certificateMapper.update(null, new LambdaUpdateWrapper<Certificate>()
                .eq(Certificate::getId, before.getId())
                .set(Certificate::getStudentId, before.getStudentId())
                .set(Certificate::getCollegeId, before.getCollegeId())
                .set(Certificate::getAssessmentYear, before.getAssessmentYear())
                .set(Certificate::getCertNo, before.getCertNo())
                .set(Certificate::getStudentNo, before.getStudentNo())
                .set(Certificate::getStudentName, before.getStudentName())
                .set(Certificate::getIdCardType, before.getIdCardType())
                .set(Certificate::getIdCardNo, before.getIdCardNo())
                .set(Certificate::getIdCardHmac, before.getIdCardHmac())
                .set(Certificate::getEducationLevel, before.getEducationLevel())
                .set(Certificate::getTrainingGoal, before.getTrainingGoal())
                .set(Certificate::getTeachingSegment, before.getTeachingSegment())
                .set(Certificate::getTeachingSubjectCode, before.getTeachingSubjectCode())
                .set(Certificate::getTeachingSubjectName, before.getTeachingSubjectName())
                .set(Certificate::getIssuer, before.getIssuer())
                .set(Certificate::getIssueDate, before.getIssueDate())
                .set(Certificate::getValidUntil, before.getValidUntil())
                .set(Certificate::getStatus, before.getStatus())
                .set(Certificate::getVoidReason, before.getVoidReason())
                .set(Certificate::getVoidOperatorId, before.getVoidOperatorId())
                .set(Certificate::getVoidTime, before.getVoidTime())
                .set(Certificate::getReissueOriginCertNo, before.getReissueOriginCertNo())
                .set(Certificate::getCorrectionReason, before.getCorrectionReason())
                .set(Certificate::getLocked, before.getLocked())
                .set(Certificate::getCreatedBy, before.getCreatedBy())
                .set(Certificate::getCreatedAt, before.getCreatedAt())
                .set(Certificate::getUpdatedBy, before.getUpdatedBy())
                .set(Certificate::getUpdatedAt, before.getUpdatedAt()));
    }

    private String snapshot(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException("导入快照序列化失败");
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BizException("JSON解析失败");
        }
    }

    private boolean jsonEquals(String left, String right) {
        try {
            return Objects.equals(normalizedSnapshot(left), normalizedSnapshot(right));
        } catch (Exception e) {
            return Objects.equals(left, right);
        }
    }

    private JsonNode normalizedSnapshot(String json) throws IOException {
        JsonNode node = objectMapper.readTree(json);
        if (node instanceof ObjectNode objectNode) {
            objectNode.remove(List.of("createdAt", "updatedAt", "createdBy", "updatedBy", "deleted", "idCardHmac"));
            JsonNode storedIdCardNo = objectNode.get("idCardNo");
            if (storedIdCardNo != null && storedIdCardNo.isTextual()
                    && StringUtils.hasText(storedIdCardNo.asText())) {
                String stored = storedIdCardNo.asText();
                String plain = idCardProtectionService.isEncrypted(stored)
                        ? idCardProtectionService.decrypt(stored) : stored;
                objectNode.put("idCardNo", idCardProtectionService.hmac(plain));
            }
        }
        return node;
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

    public record RollbackSummary(int rolledBackCount, List<String> conflicts) {
    }

    private record RollbackDecision(boolean success, String message) {
    }

    private record RollbackParentPlan(Map<Long, RollbackDecision> conflictByRefId) {

        private RollbackDecision conflictFor(ImportRecordRef ref) {
            return conflictByRefId.get(ref.getId());
        }
    }
}
