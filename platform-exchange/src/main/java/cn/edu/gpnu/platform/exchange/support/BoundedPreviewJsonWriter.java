package cn.edu.gpnu.platform.exchange.support;

import cn.edu.gpnu.platform.common.exception.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * 预览 JSON 的有界 UTF-8 序列化器。
 *
 * <p>在 Jackson 写入过程中执行预算，超过上限即中止，禁止先生成完整 String 再检查大小。</p>
 */
public final class BoundedPreviewJsonWriter {

    private static final int INITIAL_BUFFER_BYTES = 8 * 1024;

    private BoundedPreviewJsonWriter() {
    }

    public static String write(ObjectMapper objectMapper, Object value, long maxBytes) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        if (maxBytes < 1L || maxBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxBytes 必须在 1..Integer.MAX_VALUE 之间");
        }
        CappedOutputStream output = new CappedOutputStream((int) maxBytes);
        try {
            objectMapper.writeValue(output, value);
            return output.toUtf8String();
        } catch (IOException exception) {
            if (hasBudgetCause(exception)) {
                throw new BizException("导入预览数据超过备份兼容上限 " + maxBytes + " 字节");
            }
            throw new BizException("JSON序列化失败");
        }
    }

    private static boolean hasBudgetCause(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof PreviewBudgetExceededException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static final class CappedOutputStream extends OutputStream {

        private final int limit;
        private byte[] buffer;
        private int count;

        private CappedOutputStream(int limit) {
            this.limit = limit;
            this.buffer = new byte[Math.min(INITIAL_BUFFER_BYTES, limit)];
        }

        @Override
        public void write(int value) throws IOException {
            ensureWritable(1);
            ensureCapacity(count + 1);
            buffer[count++] = (byte) value;
        }

        @Override
        public void write(byte[] values, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, values.length);
            ensureWritable(length);
            ensureCapacity(count + length);
            System.arraycopy(values, offset, buffer, count, length);
            count += length;
        }

        private void ensureWritable(int incoming) throws PreviewBudgetExceededException {
            if (incoming < 0 || incoming > limit - count) {
                throw new PreviewBudgetExceededException();
            }
        }

        private void ensureCapacity(int required) {
            if (required <= buffer.length) {
                return;
            }
            int doubled = buffer.length > limit / 2 ? limit : buffer.length * 2;
            int target = Math.min(limit, Math.max(required, doubled));
            buffer = Arrays.copyOf(buffer, target);
        }

        private String toUtf8String() {
            return new String(buffer, 0, count, StandardCharsets.UTF_8);
        }
    }

    private static final class PreviewBudgetExceededException extends IOException {
    }
}
