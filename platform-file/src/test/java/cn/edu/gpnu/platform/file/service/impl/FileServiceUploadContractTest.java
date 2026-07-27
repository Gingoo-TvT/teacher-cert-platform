package cn.edu.gpnu.platform.file.service.impl;

import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.service.FileService;
import cn.edu.gpnu.platform.file.service.MultipartObjectService;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceUploadContractTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private S3Presigner s3Presigner;

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
                s3Presigner,
                multipartObjectService,
                properties,
                fileObjectMapper);
    }

    @Test
    void publicFileServiceDoesNotExposeDigestLookup() {
        assertThat(Arrays.stream(FileService.class.getMethods()).map(Method::getName))
                .doesNotContain("getByMd5", "existsByMd5");
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
}
