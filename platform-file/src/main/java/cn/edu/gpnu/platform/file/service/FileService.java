package cn.edu.gpnu.platform.file.service;

import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.model.DirectFileUploadPlan;
import cn.edu.gpnu.platform.file.model.MultipartUploadedPart;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 文件服务：服务端小文件上传、浏览器可达的预签名访问、删除、按摘要查询（秒传）。
 */
public interface FileService {

    FileObject upload(InputStream in, String originalName, String contentType, long size, String bizType, String md5);

    DirectFileUploadPlan initDirectUpload(String originalName, String contentType, long size,
                                          String bizType, String contentHash, long partSize,
                                          Map<String, String> businessMetadata);

    FileObject completeDirectUpload(Long fileId, List<MultipartUploadedPart> parts,
                                    Map<String, String> expectedBusinessMetadata);

    void cancelDirectUpload(Long fileId);

    String presignedGet(Long fileId, int expirySeconds);

    void delete(Long fileId);

    FileObject getByMd5(String md5);

    FileObject getByMd5(String md5, String bizType);
}
