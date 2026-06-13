package cn.edu.gpnu.platform.file.service;

import cn.edu.gpnu.platform.file.entity.FileObject;

import java.io.InputStream;

/**
 * 文件服务：上传、预签名访问、删除、按 MD5 查询（秒传）。
 */
public interface FileService {

    FileObject upload(InputStream in, String originalName, String contentType, long size, String bizType, String md5);

    String presignedGet(Long fileId, int expirySeconds);

    void delete(Long fileId);

    FileObject getByMd5(String md5);
}
