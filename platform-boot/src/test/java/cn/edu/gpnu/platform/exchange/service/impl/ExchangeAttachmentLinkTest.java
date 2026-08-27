package cn.edu.gpnu.platform.exchange.service.impl;

import cn.edu.gpnu.platform.business.certificate.mapper.CertificateMapper;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionMaterial;
import cn.edu.gpnu.platform.business.exemption.entity.ExemptionRequest;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionMaterialMapper;
import cn.edu.gpnu.platform.business.exemption.mapper.ExemptionRequestMapper;
import cn.edu.gpnu.platform.business.material.entity.ProcessMaterial;
import cn.edu.gpnu.platform.business.material.mapper.ProcessMaterialMapper;
import cn.edu.gpnu.platform.business.student.entity.Student;
import cn.edu.gpnu.platform.business.student.mapper.StudentMapper;
import cn.edu.gpnu.platform.business.testresult.mapper.AbilityTestResultMapper;
import cn.edu.gpnu.platform.business.training.mapper.TrainingProfileMapper;
import cn.edu.gpnu.platform.business.video.entity.VideoReview;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewMapper;
import cn.edu.gpnu.platform.business.video.mapper.VideoReviewTaskMapper;
import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.exchange.dto.ExchangeQuery;
import cn.edu.gpnu.platform.exchange.mapper.ImportExportBatchMapper;
import cn.edu.gpnu.platform.exchange.support.ExchangeDictionaryHelper;
import cn.edu.gpnu.platform.exchange.support.ExchangeExcelHelper;
import cn.edu.gpnu.platform.exchange.vo.ExchangeFile;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.system.entity.SysDictItem;
import cn.edu.gpnu.platform.system.mapper.SysUserMapper;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import cn.edu.gpnu.platform.system.service.NotificationService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ExchangeAttachmentLinkTest {

    static {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), "exchange-exemption-attachment-test");
        assistant.setCurrentNamespace(ExemptionMaterialMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ExemptionMaterial.class);
    }

    @Mock
    private CertificateMapper certificateMapper;
    @Mock
    private StudentMapper studentMapper;
    @Mock
    private TrainingProfileMapper trainingProfileMapper;
    @Mock
    private ProcessMaterialMapper materialMapper;
    @Mock
    private ExemptionRequestMapper exemptionRequestMapper;
    @Mock
    private ExemptionMaterialMapper exemptionMaterialMapper;
    @Mock
    private AbilityTestResultMapper abilityTestResultMapper;
    @Mock
    private VideoReviewMapper videoReviewMapper;
    @Mock
    private VideoReviewTaskMapper videoReviewTaskMapper;
    @Mock
    private ExchangeExcelHelper excelHelper;
    @Mock
    private ExchangeDictionaryHelper dictionaryHelper;
    @Mock
    private ImportExportBatchMapper batchMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private FileService fileService;
    @Mock
    private SysUserMapper sysUserMapper;

    @InjectMocks
    private ExchangeServiceImpl service;

    @Test
    void attachmentExportStreamsManifestProcessExemptionAndVideoFilesWithoutCertificate() throws Exception {
        Student student = new Student();
        student.setId(101L);
        student.setStudentNo("20260001");
        student.setName("测试学生");
        ProcessMaterial material = new ProcessMaterial();
        material.setId(301L);
        material.setStudentId(101L);
        material.setAssessmentYear("2026");
        material.setFileId(401L);
        material.setCategory("ETHICS");
        material.setFileName("ethics.pdf");
        material.setStatus("SECOND_PASSED");
        ExemptionRequest exemption = new ExemptionRequest();
        exemption.setId(303L);
        exemption.setStudentId(101L);
        exemption.setAssessmentYear("2026");
        exemption.setSubject("math");
        exemption.setSubjectLabel("数学");
        exemption.setFinalStatus("SECOND_PASSED");
        ExemptionMaterial exemptionMaterial = new ExemptionMaterial();
        exemptionMaterial.setId(304L);
        exemptionMaterial.setExemptionRequestId(303L);
        exemptionMaterial.setStudentId(101L);
        exemptionMaterial.setFileId(403L);
        exemptionMaterial.setFileName("exemption.pdf");
        ExemptionMaterial unrelatedMaterial = new ExemptionMaterial();
        unrelatedMaterial.setId(999L);
        unrelatedMaterial.setExemptionRequestId(999L);
        unrelatedMaterial.setStudentId(999L);
        unrelatedMaterial.setFileId(999L);
        unrelatedMaterial.setFileName("unrelated.pdf");
        VideoReview video = new VideoReview();
        video.setId(302L);
        video.setStudentId(101L);
        video.setAssessmentYear("2026");
        video.setVideoFileId(402L);
        video.setVideoFileName("lesson.mp4");
        video.setStatus("CONFIRMED");

        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        scope.setScopeType(DataScopeContext.ScopeType.SCHOOL);
        when(dataScopeService.resolve(anyString())).thenReturn(scope);
        when(studentMapper.selectList(any())).thenReturn(List.of(student));
        when(trainingProfileMapper.selectList(any())).thenReturn(List.of());
        when(certificateMapper.selectList(any())).thenReturn(List.of());
        when(materialMapper.selectList(any())).thenReturn(List.of(material));
        when(exemptionRequestMapper.selectList(any())).thenReturn(List.of(exemption));
        when(exemptionMaterialMapper.selectList(any())).thenReturn(List.of(exemptionMaterial, unrelatedMaterial));
        when(abilityTestResultMapper.selectList(any())).thenReturn(List.of());
        when(videoReviewMapper.selectList(any())).thenReturn(List.of(video));
        when(videoReviewTaskMapper.selectList(any())).thenReturn(List.of());

        when(excelHelper.writeTableWorkbook(anyString(), anyList(), anyList())).thenReturn(new byte[]{1});
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        SysDictItem category = new SysDictItem();
        category.setItemValue("师德材料");
        when(dictionaryHelper.item("material_category", "ETHICS")).thenReturn(category);
        FileObject materialFile = file(401L, "ethics.pdf", 3L);
        FileObject videoFile = file(402L, "lesson.mp4", 4L);
        FileObject exemptionFile = file(403L, "exemption.pdf", 2L);
        when(fileService.readyFile(401L)).thenReturn(materialFile);
        when(fileService.readyFile(402L)).thenReturn(videoFile);
        when(fileService.readyFile(403L)).thenReturn(exemptionFile);
        when(fileService.openRange(401L, 0L, 3L)).thenReturn(new ByteArrayInputStream(new byte[]{2, 3, 4}));
        when(fileService.openRange(402L, 0L, 4L)).thenReturn(new ByteArrayInputStream(new byte[]{5, 6, 7, 8}));
        when(fileService.openRange(403L, 0L, 2L)).thenReturn(new ByteArrayInputStream(new byte[]{9, 10}));

        ExchangeQuery query = new ExchangeQuery();
        query.setAssessmentYear("2026");
        query.setContentBaseUrl("https://cert.example.edu.cn");
        ExchangeFile exported = service.exportAttachments(query);
        Map<String, byte[]> entries = zipEntries(exported.content());

        ArgumentCaptor<List<List<String>>> rows = ArgumentCaptor.forClass(List.class);
        verify(excelHelper).writeTableWorkbook(anyString(), anyList(), rows.capture());
        assertThat(rows.getValue()).hasSize(3);
        assertThat(rows.getValue().get(0).get(7))
                .isEqualTo("https://cert.example.edu.cn/api/material/preview/301/content")
                .doesNotContain("X-Amz-", "minio");
        assertThat(rows.getValue().get(1).get(2)).isEqualTo("免考佐证-数学");
        assertThat(rows.getValue().get(1).get(7))
                .isEqualTo("https://cert.example.edu.cn/api/exemption/materials/304/content");
        assertThat(rows.getValue().get(2).get(7))
                .isEqualTo("https://cert.example.edu.cn/api/video/reviews/302/content");
        ArgumentCaptor<LambdaQueryWrapper<ExemptionMaterial>> materialQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(exemptionMaterialMapper).selectList(materialQuery.capture());
        assertThat(materialQuery.getValue().getSqlSegment())
                .contains("exemption_request_id", "student_id");
        assertThat(materialQuery.getValue().getParamNameValuePairs().values())
                .contains(303L, 101L)
                .doesNotContain(999L);
        assertThat(entries).containsKey("附件清单.xlsx");
        assertThat(entries.keySet()).anyMatch(name -> name.endsWith("301-ethics.pdf"));
        assertThat(entries.keySet()).anyMatch(name -> name.endsWith("303-304-exemption.pdf"));
        assertThat(entries.keySet()).anyMatch(name -> name.endsWith("302-lesson.mp4"));
        assertThat(entries.keySet()).noneMatch(name -> name.contains("unrelated.pdf"));
        assertThat(entries.values()).anyMatch(bytes -> java.util.Arrays.equals(bytes, new byte[]{2, 3, 4}));
        assertThat(entries.values()).anyMatch(bytes -> java.util.Arrays.equals(bytes, new byte[]{9, 10}));
        assertThat(entries.values()).anyMatch(bytes -> java.util.Arrays.equals(bytes, new byte[]{5, 6, 7, 8}));
    }

    private FileObject file(Long id, String name, Long size) {
        FileObject file = new FileObject();
        file.setId(id);
        file.setOriginalName(name);
        file.setSize(size);
        return file;
    }

    private Map<String, byte[]> zipEntries(byte[] content) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
