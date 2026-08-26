package cn.edu.gpnu.platform.exchange.service.impl;

import cn.edu.gpnu.platform.business.certificate.entity.Certificate;
import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.exchange.dto.ExchangeQuery;
import cn.edu.gpnu.platform.exchange.mapper.ImportExportBatchMapper;
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.system.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ExchangeAttachmentLinkTest {

    @Mock
    private CertificateMapper certificateMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private ProcessMaterialMapper materialMapper;
    @Mock
    private VideoReviewMapper videoReviewMapper;
    @Mock
    private ExchangeExcelHelper excelHelper;
    @Mock
    private ImportExportBatchMapper batchMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ExchangeServiceImpl service;

    @Test
    void attachmentExportUsesAuthenticatedApplicationContentRoute() throws Exception {
        Certificate certificate = new Certificate();
        certificate.setStudentId(101L);
        certificate.setAssessmentYear("2026");
        Student student = new Student();
        student.setId(101L);
        student.setStudentNo("20260001");
        student.setName("测试学生");
        ProcessMaterial material = new ProcessMaterial();
        material.setId(301L);
        material.setStudentId(101L);
        material.setFileId(401L);
        material.setCategory("ETHICS");
        material.setFileName("ethics.pdf");
        material.setStatus("SECOND_PASSED");

        when(certificateMapper.selectList(any())).thenReturn(List.of(certificate));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(student));
        when(materialMapper.selectList(any())).thenReturn(List.of(material));
        when(videoReviewMapper.selectList(any())).thenReturn(List.of());
        when(excelHelper.writeTableWorkbook(anyString(), anyList(), anyList())).thenReturn(new byte[]{1});
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ExchangeQuery query = new ExchangeQuery();
        query.setContentBaseUrl("https://cert.example.edu.cn");
        service.exportAttachments(query);

        ArgumentCaptor<List<List<String>>> rows = ArgumentCaptor.forClass(List.class);
        verify(excelHelper).writeTableWorkbook(anyString(), anyList(), rows.capture());
        String link = rows.getValue().get(0).get(7);
        assertThat(link)
                .isEqualTo("https://cert.example.edu.cn/api/material/preview/301/content")
                .doesNotContain("X-Amz-", "minio");
    }
}
