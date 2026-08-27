package cn.edu.gpnu.platform.exchange.vo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * 导出文件。
 *
 * <p>普通工作簿继续使用三参数构造保存字节内容；附件视频包使用流式写出器，逐个对象写入响应，
 * 避免把大视频或整个 ZIP 同时放入堆内存。
 */
public final class ExchangeFile {

    private final String fileName;
    private final String contentType;
    private final byte[] content;
    private final ContentWriter writer;

    public ExchangeFile(String fileName, String contentType, byte[] content) {
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.content = Objects.requireNonNull(content, "content");
        this.writer = out -> out.write(content);
    }

    private ExchangeFile(String fileName, String contentType, ContentWriter writer) {
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.content = null;
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    public static ExchangeFile streaming(String fileName, String contentType, ContentWriter writer) {
        return new ExchangeFile(fileName, contentType, writer);
    }

    public String fileName() {
        return fileName;
    }

    public String contentType() {
        return contentType;
    }

    /**
     * 保留既有小文件/测试调用兼容；生产下载入口统一调用 {@link #writeTo(OutputStream)}。
     */
    public byte[] content() {
        if (content != null) {
            return content;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("导出文件生成失败", e);
        }
    }

    public void writeTo(OutputStream out) throws IOException {
        writer.writeTo(out);
    }

    @FunctionalInterface
    public interface ContentWriter {
        void writeTo(OutputStream out) throws IOException;
    }
}
