package cn.edu.gpnu.platform.business.material.vo;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 批量下载结果：文件名 + 流式写出器。
 * 内容通过 {@link ContentWriter} 直接写入响应输出流（zip 边读 MinIO 边写响应），
 * 不再整包进堆，避免"下载全院材料"级大批次 OOM（P0-2）。
 */
public record BatchDownloadFile(String fileName, ContentWriter content) {

    @FunctionalInterface
    public interface ContentWriter {
        void writeTo(OutputStream out) throws IOException;
    }
}
