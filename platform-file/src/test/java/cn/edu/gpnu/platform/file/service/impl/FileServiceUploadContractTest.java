package cn.edu.gpnu.platform.file.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.file.service.MultipartObjectService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceUploadContractTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MultipartObjectService multipartObjectService;

    @Mock
    private FileObjectMapper fileObjectMapper;

    private FileServiceImpl service;

    @BeforeEach
    void setUp() {
        MinioProperties properties = new MinioProperties();
        properties.setBucket("phase00-test");
        service = new FileServiceImpl(
                minioClient,
                multipartObjectService,
                properties,
                fileObjectMapper);
    }

    @Test
    void publicFileServiceDoesNotExposeDigestLookup() {
        assertThat(Arrays.stream(FileService.class.getMethods()).map(Method::getName))
                .doesNotContain("getByMd5", "existsByMd5", "presignedGet");
    }

    @Test
    void sameContentUploadsUseDistinctObjectKeysAndRows() throws Exception {
        AtomicLong ids = new AtomicLong(100L);
        when(minioClient.putObject(any())).thenReturn(null);
        doAnswer(invocation -> {
            FileObject row = invocation.getArgument(0);
            row.setId(ids.incrementAndGet());
            return 1;
        }).when(fileObjectMapper).insert(any(FileObject.class));
        when(fileObjectMapper.transitionStatus(any(), any(), any(), any())).thenReturn(1);

        byte[] content = "same-content".getBytes(StandardCharsets.UTF_8);
        FileObject first = service.upload(
                new ByteArrayInputStream(content), "same.txt", "text/plain",
                content.length, "material");
        FileObject second = service.upload(
                new ByteArrayInputStream(content), "same.txt", "text/plain",
                content.length, "material");

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getObjectKey()).isNotEqualTo(first.getObjectKey());

        ArgumentCaptor<FileObject> rows = ArgumentCaptor.forClass(FileObject.class);
        verify(fileObjectMapper, times(2)).insert(rows.capture());
        assertThat(rows.getAllValues())
                .extracting(FileObject::getObjectKey)
                .doesNotHaveDuplicates();
        verify(minioClient, times(2)).putObject(any());
    }

    @Test
    void objectStoreFailureDoesNotExposeSdkMessageToTheClient() throws Exception {
        doAnswer(invocation -> {
            FileObject row = invocation.getArgument(0);
            row.setId(101L);
            return 1;
        }).when(fileObjectMapper).insert(any(FileObject.class));
        when(minioClient.putObject(any()))
                .thenThrow(new IllegalStateException("SENTINEL endpoint=bucket-internal"));

        assertThatThrownBy(() -> service.upload(
                new ByteArrayInputStream(new byte[]{1}), "safe.txt", "text/plain", 1L, "material"))
                .isInstanceOf(BizException.class)
                .hasMessage("文件上传失败，请稍后重试")
                .hasMessageNotContaining("SENTINEL")
                .hasMessageNotContaining("endpoint");
    }

    @Test
    void metadataInsertFailureCannotCreateAnUntrackedObject() throws Exception {
        doThrow(new IllegalStateException("database unavailable")).doReturn(0)
                .when(fileObjectMapper).insert(any(FileObject.class));

        assertThatThrownBy(() -> service.upload(
                new ByteArrayInputStream(new byte[]{1}), "safe.txt", "text/plain", 1L, "material"))
                .isInstanceOf(BizException.class)
                .hasMessage("文件上传失败，请稍后重试");
        assertThatThrownBy(() -> service.upload(
                new ByteArrayInputStream(new byte[]{2}), "safe.txt", "text/plain", 1L, "material"))
                .isInstanceOf(BizException.class)
                .hasMessage("文件上传失败，请稍后重试");

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void readyTransitionFailureAttemptsExactObjectCompensation() throws Exception {
        doAnswer(invocation -> {
            FileObject row = invocation.getArgument(0);
            row.setId(102L);
            return 1;
        }).when(fileObjectMapper).insert(any(FileObject.class));
        when(minioClient.putObject(any())).thenReturn(null);
        when(fileObjectMapper.transitionStatus(any(), any(), any(), any())).thenReturn(0, 1);
        doThrow(new IllegalStateException("object store unavailable"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> service.upload(
                new ByteArrayInputStream(new byte[]{1}), "safe.txt", "text/plain", 1L, "material"))
                .isInstanceOf(BizException.class)
                .hasMessage("文件登记失败，请稍后重试");

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        ArgumentCaptor<RemoveObjectArgs> remove = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        ArgumentCaptor<String> targetStatus = ArgumentCaptor.forClass(String.class);
        verify(minioClient).putObject(put.capture());
        verify(minioClient).removeObject(remove.capture());
        verify(fileObjectMapper, times(2)).transitionStatus(
                any(), any(), targetStatus.capture(), any(LocalDateTime.class));
        assertThat(remove.getValue().bucket()).isEqualTo(put.getValue().bucket());
        assertThat(remove.getValue().object()).isEqualTo(put.getValue().object());
        assertThat(targetStatus.getAllValues()).containsExactly("READY", "FAILED");
    }
}
