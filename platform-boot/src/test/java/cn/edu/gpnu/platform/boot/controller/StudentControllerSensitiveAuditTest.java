package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.business.student.service.StudentService;
import cn.edu.gpnu.platform.business.student.vo.StudentPlainIdCardVO;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentControllerSensitiveAuditTest {

    @Test
    void plainIdentityResponseIsReleasedOnlyAfterTargetedAudit() {
        StudentService studentService = mock(StudentService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        StudentController controller = new StudentController(studentService, auditLogService);
        StudentPlainIdCardVO plain = new StudentPlainIdCardVO(
                42L, "11010119900628002X", "1990/6/28");
        when(studentService.plainIdCard(42L)).thenReturn(plain);

        var response = controller.plainIdCard(42L);

        assertThat(response.getData()).isSameAs(plain);
        verify(auditLogService).record("student", 42L, "student:42:id-card",
                "plainIdCard", null, null, "查看明文证件信息");
    }
}
