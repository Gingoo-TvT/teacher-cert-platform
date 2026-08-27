package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.exchange.entity.ImportRecordRef;
import cn.edu.gpnu.platform.security.service.IdCardProtectionService;
import cn.edu.gpnu.platform.system.service.CollegeParentGuard;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExchangeRollbackServiceTest {

    private StudentMapper studentMapper;
    private CollegeParentGuard collegeParentGuard;
    private ObjectMapper objectMapper;
    private ExchangeRollbackService rollbackService;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(
                new MybatisConfiguration(), "exchange-rollback-test"), Student.class);
        studentMapper = mock(StudentMapper.class);
        collegeParentGuard = mock(CollegeParentGuard.class);
        objectMapper = new ObjectMapper();
        rollbackService = new ExchangeRollbackService(studentMapper,
                mock(TrainingProfileMapper.class), mock(CertificateMapper.class), collegeParentGuard,
                objectMapper, mock(IdCardProtectionService.class));
    }

    @Test
    void unknownTableIsReportedAsConflict() {
        ImportRecordRef ref = ref(1L, "unknown", 10L, "INSERT", null, null);

        ExchangeRollbackService.RollbackSummary result = rollbackService.compensateLocked(List.of(ref));

        assertThat(result.rolledBackCount()).isZero();
        assertThat(result.conflicts()).containsExactly("未知回滚表: unknown");
    }

    @Test
    void parentCollegesAreLockedOnceInAscendingOrder() {
        when(collegeParentGuard.lockStatusForUpdate(any(), any())).thenReturn(1);
        List<ImportRecordRef> refs = List.of(
                ref(1L, "student", 11L, "UPDATE", "{\"collegeId\":9}", "{}"),
                ref(2L, "student", 12L, "UPDATE", "{\"collegeId\":3}", "{}"),
                ref(3L, "student", 13L, "UPDATE", "{\"collegeId\":9}", "{}"));

        rollbackService.compensateLocked(refs);

        ArgumentCaptor<Long> collegeIds = ArgumentCaptor.forClass(Long.class);
        verify(collegeParentGuard, org.mockito.Mockito.times(2)).lockStatusForUpdate(
                collegeIds.capture(), org.mockito.ArgumentMatchers.eq(CollegeParentGuard.Operation.ROLLBACK_RESTORE));
        assertThat(collegeIds.getAllValues()).containsExactly(3L, 9L);
    }

    @Test
    void updateRestoresNullableSnapshotFieldsExplicitly() throws Exception {
        Student current = student(10L, 9L, "当前来源");
        Student before = student(10L, 9L, null);
        ImportRecordRef ref = ref(1L, "student", 10L, "UPDATE",
                objectMapper.writeValueAsString(before), objectMapper.writeValueAsString(current));
        when(collegeParentGuard.lockStatusForUpdate(9L, CollegeParentGuard.Operation.ROLLBACK_RESTORE))
                .thenReturn(1);
        when(studentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(current);
        when(studentMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        ExchangeRollbackService.RollbackSummary result = rollbackService.compensateLocked(List.of(ref));

        assertThat(result.rolledBackCount()).isEqualTo(1);
        assertThat(result.conflicts()).isEmpty();
        ArgumentCaptor<LambdaUpdateWrapper<Student>> wrapper = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(studentMapper).update(isNull(), wrapper.capture());
        assertThat(wrapper.getValue().getSqlSet()).contains("source_full");
    }

    @Test
    void updateWithUnexpectedAffectedRowsIsAConflict() throws Exception {
        Student current = student(10L, 9L, "当前来源");
        Student before = student(10L, 9L, null);
        ImportRecordRef ref = ref(1L, "student", 10L, "UPDATE",
                objectMapper.writeValueAsString(before), objectMapper.writeValueAsString(current));
        when(collegeParentGuard.lockStatusForUpdate(9L, CollegeParentGuard.Operation.ROLLBACK_RESTORE))
                .thenReturn(1);
        when(studentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(current);
        when(studentMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        ExchangeRollbackService.RollbackSummary result = rollbackService.compensateLocked(List.of(ref));

        assertThat(result.rolledBackCount()).isZero();
        assertThat(result.conflicts()).containsExactly("学生#10恢复影响行数异常，跳过");
    }

    private Student student(Long id, Long collegeId, String sourceFull) {
        Student student = new Student();
        student.setId(id);
        student.setCollegeId(collegeId);
        student.setSourceFull(sourceFull);
        return student;
    }

    private ImportRecordRef ref(Long id, String tableName, Long recordId, String action,
                                String beforeJson, String afterJson) {
        ImportRecordRef ref = new ImportRecordRef();
        ref.setId(id);
        ref.setTableName(tableName);
        ref.setRecordId(recordId);
        ref.setAction(action);
        ref.setBeforeJson(beforeJson);
        ref.setAfterJson(afterJson);
        return ref;
    }
}
