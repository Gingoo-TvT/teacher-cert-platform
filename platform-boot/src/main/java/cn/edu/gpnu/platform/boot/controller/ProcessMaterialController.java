package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.business.material.dto.MaterialBatchDownloadRequest;
import cn.edu.gpnu.platform.business.material.dto.MaterialQuery;
import cn.edu.gpnu.platform.business.material.dto.MaterialReviewRequest;
import cn.edu.gpnu.platform.business.material.dto.MaterialDirectUploadInitRequest;
import cn.edu.gpnu.platform.business.material.dto.MaterialDirectUploadCompleteRequest;
import cn.edu.gpnu.platform.business.material.service.ProcessMaterialService;
import cn.edu.gpnu.platform.business.material.vo.BatchDownloadFile;
import cn.edu.gpnu.platform.business.material.vo.ProcessMaterialVO;
import cn.edu.gpnu.platform.business.material.vo.ProcessStatusVO;
import cn.edu.gpnu.platform.business.material.vo.MaterialDirectUploadVO;
import cn.edu.gpnu.platform.boot.support.FileStreamingSupport;
import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.security.service.MediaAccessCookieService;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "过程性材料")
@RestController
@RequestMapping("/api/material")
@RequiredArgsConstructor
public class ProcessMaterialController {

    private static final int PREVIEW_EXPIRY_SECONDS = 600;

    private final ProcessMaterialService processMaterialService;
    private final MediaAccessCookieService mediaAccessCookieService;
    private final FileStreamingSupport fileStreamingSupport;
    private final AuditLogService auditLogService;

    @Operation(summary = "材料列表")
    @PreAuthorize("@pms.has('student:view') or @pms.has('material:upload') or @pms.has('material:firstReview') or @pms.has('material:secondReview') or @pms.has('material:batchDownload')")
    @DataScope(alias = "process_material", permission = "student:view")
    @GetMapping
    public Result<PageResult<ProcessMaterialVO>> list(MaterialQuery query) {
        return Result.ok(processMaterialService.list(query));
    }

    @Operation(summary = "上传材料")
    @PreAuthorize("@pms.has('material:upload')")
    @AuditLog(bizType = "material", operation = "upload", before = true)
    @PostMapping("/upload")
    public Result<Long> upload(@RequestParam("studentId") Long studentId,
                               @RequestParam("assessmentYear") String assessmentYear,
                               @RequestParam("category") String category,
                               @RequestParam("file") MultipartFile file) throws IOException {
        return Result.ok(processMaterialService.upload(studentId, assessmentYear, category,
                file.getInputStream(), file.getOriginalFilename(), file.getContentType(), file.getSize()));
    }

    @Operation(summary = "初始化材料浏览器直传")
    @PreAuthorize("@pms.has('material:upload')")
    @AuditLog(bizType = "material", operation = "uploadInit", before = true)
    @PostMapping("/upload/init")
    public Result<MaterialDirectUploadVO> initDirectUpload(
            @Valid @RequestBody MaterialDirectUploadInitRequest request) {
        return Result.ok(processMaterialService.initDirectUpload(request));
    }

    @Operation(summary = "定稿浏览器直传材料")
    @PreAuthorize("@pms.has('material:upload')")
    @AuditLog(bizType = "material", operation = "uploadComplete", before = true)
    @PostMapping("/upload/complete")
    public Result<Long> completeDirectUpload(
            @Valid @RequestBody MaterialDirectUploadCompleteRequest request) {
        return Result.ok(processMaterialService.completeDirectUpload(request));
    }

    @Operation(summary = "取消材料浏览器直传")
    @PreAuthorize("@pms.has('material:upload')")
    @AuditLog(bizType = "material", operation = "uploadCancel", before = true)
    @DeleteMapping("/upload/{fileId}")
    public Result<Void> cancelDirectUpload(@PathVariable Long fileId) {
        processMaterialService.cancelDirectUpload(fileId);
        return Result.ok();
    }

    @Operation(summary = "删除材料")
    @PreAuthorize("@pms.has('material:upload')")
    @DataScope(alias = "process_material", permission = "material:upload")
    @AuditLog(bizType = "material", operation = "delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        processMaterialService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "替换材料附件")
    @PreAuthorize("@pms.has('material:upload')")
    @DataScope(alias = "process_material", permission = "material:upload")
    @AuditLog(bizType = "material", operation = "replace", before = true)
    @PutMapping("/{id}/replace")
    public Result<Void> replace(@PathVariable Long id,
                                @RequestParam("file") MultipartFile file) throws IOException {
        processMaterialService.replace(id, file.getInputStream(), file.getOriginalFilename(),
                file.getContentType(), file.getSize());
        return Result.ok();
    }

    @Operation(summary = "材料预览链接")
    @PreAuthorize("@pms.has('student:view') or @pms.has('material:upload') or @pms.has('material:firstReview') or @pms.has('material:secondReview')")
    @DataScope(alias = "process_material", permission = "student:view")
    @AuditLog(bizType = "material", operation = "preview", before = true)
    @GetMapping("/preview/{id}")
    public Result<String> preview(@PathVariable Long id,
                                  HttpServletRequest request,
                                  HttpServletResponse response) {
        processMaterialService.previewFileId(id);
        mediaAccessCookieService.issue(request, response, PREVIEW_EXPIRY_SECONDS);
        return Result.ok("/api/material/preview/" + id + "/content");
    }

    @Operation(summary = "流式预览材料")
    @PreAuthorize("@pms.has('student:view') or @pms.has('material:upload') or @pms.has('material:firstReview') or @pms.has('material:secondReview')")
    @DataScope(alias = "process_material", permission = "student:view")
    @GetMapping("/preview/{id}/content")
    public ResponseEntity<StreamingResponseBody> previewContent(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = HttpHeaders.RANGE, required = false) String range) {
        Long fileId = processMaterialService.previewFileId(id);
        auditLogService.record("material", id, "material:" + id + ":content",
                "previewContent", null, null, "读取过程性材料内容");
        return fileStreamingSupport.stream(fileId, range);
    }

    @Operation(summary = "提交材料审核")
    @PreAuthorize("@pms.has('material:upload')")
    @DataScope(alias = "process_material", permission = "material:upload")
    @AuditLog(bizType = "material", operation = "submit")
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id) {
        processMaterialService.submit(id);
        return Result.ok();
    }

    @Operation(summary = "材料初审")
    @PreAuthorize("@pms.has('material:firstReview')")
    @DataScope(alias = "process_material", permission = "material:firstReview")
    @PostMapping("/{id}/first-review")
    public Result<Void> firstReview(@PathVariable Long id,
                                    @Valid @RequestBody MaterialReviewRequest request) {
        processMaterialService.firstReview(id, request);
        return Result.ok();
    }

    @Operation(summary = "材料复审")
    @PreAuthorize("@pms.has('material:secondReview')")
    @DataScope(alias = "process_material", permission = "material:secondReview")
    @PostMapping("/{id}/second-review")
    public Result<Void> secondReview(@PathVariable Long id,
                                     @Valid @RequestBody MaterialReviewRequest request) {
        processMaterialService.secondReview(id, request);
        return Result.ok();
    }

    @Operation(summary = "过程性考核聚合状态")
    @PreAuthorize("@pms.has('student:view') or @pms.has('material:upload')")
    @DataScope(alias = "process_material", permission = "student:view")
    @GetMapping("/process-status/{studentId}")
    public Result<ProcessStatusVO> processStatus(@PathVariable Long studentId,
                                                 @RequestParam("year") String assessmentYear) {
        return Result.ok(processMaterialService.processStatus(studentId, assessmentYear));
    }

    @Operation(summary = "批量下载材料")
    @PreAuthorize("@pms.has('material:batchDownload')")
    @DataScope(alias = "process_material", permission = "material:batchDownload")
    @AuditLog(bizType = "material", operation = "batchDownload", before = true)
    @PostMapping("/batch-download")
    public void batchDownload(@RequestBody(required = false) MaterialBatchDownloadRequest request,
                              HttpServletResponse response) throws IOException {
        BatchDownloadFile file = processMaterialService.batchDownload(request == null ? new MaterialBatchDownloadRequest() : request);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8));
        file.content().writeTo(response.getOutputStream());
    }
}
