package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件上传/预签名访问（Phase 5/7 业务复用）。
 */
@Tag(name = "文件")
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @Operation(summary = "上传文件")
    @PreAuthorize("isAuthenticated()")
    @AuditLog(bizType = "file", operation = "upload")
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "bizType", required = false) String bizType) throws IOException {
        FileObject fo = fileService.upload(file.getInputStream(), file.getOriginalFilename(),
                file.getContentType(), file.getSize(), bizType, null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", fo.getId());
        data.put("objectKey", fo.getObjectKey());
        return Result.ok(data);
    }

    @Operation(summary = "获取下载/预览预签名链接")
    @PreAuthorize("isAuthenticated()")
    @AuditLog(bizType = "file", operation = "presignedUrl")
    @GetMapping("/{id}/url")
    public Result<String> presignedUrl(@PathVariable Long id,
                                       @RequestParam(value = "expiry", defaultValue = "600") int expiry) {
        return Result.ok(fileService.presignedGet(id, expiry));
    }
}
