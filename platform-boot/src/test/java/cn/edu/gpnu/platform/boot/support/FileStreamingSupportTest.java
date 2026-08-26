package cn.edu.gpnu.platform.boot.support;

import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.service.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStreamingSupportTest {

    @Test
    void streamsRequestedRangeWithoutBufferingWholeFile() throws Exception {
        FileService fileService = mock(FileService.class);
        FileObject file = readyFile(10);
        when(fileService.readyFile(7L)).thenReturn(file);
        when(fileService.openRange(7L, 2, 4))
                .thenReturn(new ByteArrayInputStream("2345".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<StreamingResponseBody> response =
                new FileStreamingSupport(fileService).stream(7L, "bytes=2-5");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 2-5/10");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(4);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("2345");
        verify(fileService).openRange(7L, 2, 4);
    }

    @Test
    void supportsOpenEndedAndSuffixRanges() {
        assertThat(FileStreamingSupport.selectRange("bytes=7-", 10))
                .isEqualTo(new FileStreamingSupport.RangeSelection(7, 9, 3, true));
        assertThat(FileStreamingSupport.selectRange("bytes=-20", 10))
                .isEqualTo(new FileStreamingSupport.RangeSelection(0, 9, 10, true));
    }

    @Test
    void rejectsMultipleMalformedAndUnsatisfiedRanges() {
        assertThat(FileStreamingSupport.selectRange("bytes=0-1,4-5", 10)).isNull();
        assertThat(FileStreamingSupport.selectRange("bytes=ten-", 10)).isNull();
        assertThat(FileStreamingSupport.selectRange("bytes=10-", 10)).isNull();
        assertThat(FileStreamingSupport.selectRange("bytes=-0", 10)).isNull();
    }

    @Test
    void returnsRangeNotSatisfiableWithObjectSize() {
        FileService fileService = mock(FileService.class);
        when(fileService.readyFile(7L)).thenReturn(readyFile(10));

        ResponseEntity<StreamingResponseBody> response =
                new FileStreamingSupport(fileService).stream(7L, "bytes=20-30");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes */10");
        assertThat(response.getBody()).isNull();
    }

    private FileObject readyFile(long size) {
        FileObject file = new FileObject();
        file.setOriginalName("video.mp4");
        file.setContentType("video/mp4");
        file.setSize(size);
        file.setStatus("READY");
        return file;
    }
}
