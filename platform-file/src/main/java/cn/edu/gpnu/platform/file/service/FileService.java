package cn.edu.gpnu.platform.file.service;

import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.model.DirectFileUploadPlan;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 文件服务：服务端小文件上传、受控流读取与删除。
 *
 * <p>通用文件服务不公开摘要命中能力，避免调用方把摘要当成跨业务或跨主体复用对象的凭据。
 */
public interface FileService {

    FileObject upload(InputStream in, String originalName, String contentType, long size, String bizType);

    DirectFileUploadPlan initDirectUpload(String originalName, String contentType, long size,
                                          String bizType, String contentHash, long partSize,
                                          Map<String, String> businessMetadata);

    FileObject completeDirectUpload(Long fileId, List<MultipartUploadedPart> parts,
                                    Map<String, String> expectedBusinessMetadata);

    void cancelDirectUpload(Long fileId);

    FileObject readyFile(Long fileId);

    InputStream openRange(Long fileId, long offset, long length);

    void delete(Long fileId);
}
