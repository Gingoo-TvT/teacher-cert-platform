package cn.edu.gpnu.platform.file.service.impl;

import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.file.config.MinioProperties;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.mapper.FileObjectMapper;
import cn.edu.gpnu.platform.file.service.FileService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final MinioClient minioClient;
    private final MinioProperties props;
    private final FileObjectMapper fileObjectMapper;

    @Override
    public FileObject upload(InputStream in, String originalName, String contentType, long size, String bizType, String md5) {
        String ext = (originalName != null && originalName.contains(".")) ? originalName.substring(originalName.lastIndexOf('.')) : "";
        String prefix = (bizType == null || bizType.isEmpty()) ? "misc" : bizType;
        String objectKey = prefix + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .stream(in, size, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception e) {
            throw new BizException("文件上传失败: " + e.getMessage());
        }
        FileObject fo = new FileObject();
        fo.setOriginalName(originalName);
        fo.setStoredName(objectKey.substring(objectKey.indexOf('/') + 1));
        fo.setBucket(props.getBucket());
        fo.setObjectKey(objectKey);
        fo.setSize(size);
        fo.setContentType(contentType);
        fo.setMd5(md5);
        fo.setBizType(bizType);
        fo.setUploaderId(UserContext.getUserIdOrSystem());
        fo.setUploadTime(LocalDateTime.now());
        fileObjectMapper.insert(fo);
        return fo;
    }

    @Override
    public String presignedGet(Long fileId, int expirySeconds) {
        FileObject fo = fileObjectMapper.selectById(fileId);
        if (fo == null) {
            throw new BizException("文件不存在");
        }
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(fo.getBucket())
                    .object(fo.getObjectKey())
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new BizException("生成下载链接失败: " + e.getMessage());
        }
    }

    @Override
    public void delete(Long fileId) {
        FileObject fo = fileObjectMapper.selectById(fileId);
        if (fo == null) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(fo.getBucket())
                    .object(fo.getObjectKey())
                    .build());
        } catch (Exception e) {
            throw new BizException("文件删除失败: " + e.getMessage());
        }
        fileObjectMapper.deleteById(fileId);
    }

    @Override
    public FileObject getByMd5(String md5) {
        if (md5 == null || md5.isEmpty()) {
            return null;
        }
        return fileObjectMapper.selectOne(Wrappers.<FileObject>lambdaQuery()
                .eq(FileObject::getMd5, md5)
                .last("limit 1"));
    }
}
