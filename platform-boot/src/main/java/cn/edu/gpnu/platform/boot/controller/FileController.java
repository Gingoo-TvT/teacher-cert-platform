package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.file.entity.FileObject;
import cn.edu.gpnu.platform.file.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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
                file.getContentType(), file.getSize(), bizType);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", fo.getId());
        data.put("objectKey", fo.getObjectKey());
        return Result.ok(data);
    }

    // 安全急修(Phase 37a, P0-1)：原 GET /file/{id}/url 预签名端点仅 isAuthenticated、无属主/数据范围校验，
    // 任一登录者可凭 fileId 下载他人材料/视频/证件(活体已证实)。合法文件访问均走带 @DataScope/服务层
    // 范围校验的业务端点(材料/免考预览、视频播放、导出，内部再调 presignedGet)；该通用端点无任何调用方，
    // 直接删除以消除 IDOR 面。若将来需通用预签名，必须走带属主校验的业务入口。
}
