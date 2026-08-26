package cn.edu.gpnu.platform.boot.support;

import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 对受保护文件提供单段 Range 流式响应，避免将大视频整体载入 JVM。
 */
@Component
@RequiredArgsConstructor
public class FileStreamingSupport {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final FileService fileService;

    public ResponseEntity<StreamingResponseBody> stream(Long fileId, String rangeHeader) {
        FileObject file = fileService.readyFile(fileId);
        long size = file.getSize();
        RangeSelection selection = selectRange(rangeHeader, size);
        if (selection == null) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + size)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .build();
        }

        StreamingResponseBody body = output -> {
            if (selection.length() == 0) {
                return;
            }
            try (InputStream input = fileService.openRange(fileId, selection.start(), selection.length())) {
                input.transferTo(output);
            }
        };
        ResponseEntity.BodyBuilder response = ResponseEntity.status(selection.partial()
                        ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .contentType(contentType(file.getContentType()))
                .contentLength(selection.length())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(fileName(file), StandardCharsets.UTF_8)
                        .build()
                        .toString());
        if (selection.partial()) {
            response.header(HttpHeaders.CONTENT_RANGE,
                    "bytes " + selection.start() + "-" + selection.end() + "/" + size);
        }
        return response.body(body);
    }

    static RangeSelection selectRange(String rangeHeader, long size) {
        if (size < 0) {
            return null;
        }
        if (!StringUtils.hasText(rangeHeader)) {
            return new RangeSelection(0, size == 0 ? -1 : size - 1, size, false);
        }
        if (size == 0 || !rangeHeader.startsWith("bytes=") || rangeHeader.indexOf(',') >= 0) {
            return null;
        }
        String value = rangeHeader.substring("bytes=".length()).trim();
        int dash = value.indexOf('-');
        if (dash < 0 || dash != value.lastIndexOf('-')) {
            return null;
        }
        String startText = value.substring(0, dash).trim();
        String endText = value.substring(dash + 1).trim();
        if (startText.isEmpty()) {
            Long suffixLength = parseUnsigned(endText);
            if (suffixLength == null || suffixLength == 0) {
                return null;
            }
            long length = Math.min(suffixLength, size);
            return new RangeSelection(size - length, size - 1, length, true);
        }
        Long start = parseUnsigned(startText);
        if (start == null || start >= size) {
            return null;
        }
        long end;
        if (endText.isEmpty()) {
            end = size - 1;
        } else {
            Long requestedEnd = parseUnsigned(endText);
            if (requestedEnd == null || requestedEnd < start) {
                return null;
            }
            end = Math.min(requestedEnd, size - 1);
        }
        return new RangeSelection(start, end, end - start + 1, true);
    }

    private static Long parseUnsigned(String value) {
        if (value.isEmpty()) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private MediaType contentType(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.parseMediaType(DEFAULT_CONTENT_TYPE);
        }
        try {
            return MediaType.parseMediaType(value);
        } catch (InvalidMediaTypeException ignored) {
            return MediaType.parseMediaType(DEFAULT_CONTENT_TYPE);
        }
    }

    private String fileName(FileObject file) {
        if (StringUtils.hasText(file.getOriginalName())) {
            return file.getOriginalName();
        }
        if (StringUtils.hasText(file.getStoredName())) {
            return file.getStoredName();
        }
        return "file";
    }

    record RangeSelection(long start, long end, long length, boolean partial) {
    }
}
