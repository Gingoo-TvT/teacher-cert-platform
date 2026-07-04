package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.exchange.dto.ExchangeQuery;
import cn.edu.gpnu.platform.exchange.dto.ImportConfirmRequest;
import cn.edu.gpnu.platform.exchange.service.ExchangeService;
import cn.edu.gpnu.platform.exchange.vo.BatchVO;
import cn.edu.gpnu.platform.exchange.vo.ExchangeFile;
import cn.edu.gpnu.platform.exchange.vo.ImportResultVO;
import cn.edu.gpnu.platform.exchange.vo.PrevalidateResultVO;
import cn.edu.gpnu.platform.exchange.vo.RollbackResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "导入导出与预校验")
@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeService exchangeService;

    @Operation(summary = "下载标准导入模板")
    @PreAuthorize("@pms.has('exchange:template')")
    @GetMapping("/template")
    public void template(ExchangeQuery query, HttpServletResponse response) throws IOException {
        writeFile(exchangeService.template(query), response);
    }

    @Operation(summary = "导入预校验")
    @PreAuthorize("@pms.has('exchange:prevalidate')")
    @AuditLog(bizType = "exchange", operation = "prevalidate")
    @PostMapping("/prevalidate")
    public Result<PrevalidateResultVO> prevalidate(@RequestParam("file") MultipartFile file) {
        return Result.ok(exchangeService.prevalidate(file));
    }

    @Operation(summary = "下载预校验异常报告")
    @PreAuthorize("@pms.has('exchange:prevalidate')")
    @GetMapping("/prevalidate/{batch}/error-report")
    public void errorReport(@PathVariable("batch") Long batchId, HttpServletResponse response) throws IOException {
        writeFile(exchangeService.errorReport(batchId), response);
    }

    @Operation(summary = "确认导入")
    @PreAuthorize("@pms.has('exchange:import')")
    @AuditLog(bizType = "exchange", operation = "import")
    @PostMapping("/import/{batch}/confirm")
    public Result<ImportResultVO> confirm(@PathVariable("batch") Long batchId,
                                          @Valid @RequestBody ImportConfirmRequest request) {
        return Result.ok(exchangeService.confirmImport(batchId, request));
    }

    @Operation(summary = "确认导入（兼容文档 query 形式）")
    @PreAuthorize("@pms.has('exchange:import')")
    @AuditLog(bizType = "exchange", operation = "import")
    @PostMapping("/import")
    public Result<ImportResultVO> confirmByQuery(@RequestParam("batchId") Long batchId,
                                                 @RequestParam("strategy") String strategy) {
        ImportConfirmRequest request = new ImportConfirmRequest();
        request.setStrategy(strategy);
        return Result.ok(exchangeService.confirmImport(batchId, request));
    }

    @Operation(summary = "导入批次回滚")
    @PreAuthorize("@pms.has('exchange:import')")
    @AuditLog(bizType = "exchange", operation = "rollback")
    @PostMapping("/import/{batch}/rollback")
    public Result<RollbackResultVO> rollback(@PathVariable("batch") Long batchId) {
        return Result.ok(exchangeService.rollback(batchId));
    }

    @Operation(summary = "导入导出批次")
    @PreAuthorize("@pms.has('exchange:import') or @pms.has('exchange:export:standard') or @pms.has('exchange:export:full')")
    @GetMapping("/batches")
    public Result<PageResult<BatchVO>> batches(@RequestParam(value = "type", required = false) String type,
                                               @RequestParam(value = "status", required = false) String status,
                                               @RequestParam(value = "page", required = false) Integer page,
                                               @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(exchangeService.batches(type, status, page, size));
    }

    @Operation(summary = "导出数据")
    @PreAuthorize("@pms.has('exchange:export:standard') or @pms.has('exchange:export:full')")
    @DataScope(alias = "certificate", permission = "exchange:export:standard")
    @AuditLog(bizType = "exchange", operation = "export")
    @PostMapping("/export/{type}")
    public void export(@PathVariable String type,
                       @RequestBody(required = false) ExchangeQuery query,
                       HttpServletResponse response) throws IOException {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (("FULL_REVIEW".equals(normalized) || "CERT_SUMMARY".equals(normalized) || "ERROR".equals(normalized))
                && !cn.edu.gpnu.platform.common.context.UserContext.hasPermission("exchange:export:full")) {
            throw new cn.edu.gpnu.platform.common.exception.BizException(
                    cn.edu.gpnu.platform.common.api.ResultCode.FORBIDDEN.getCode(), "无权导出完整或敏感数据");
        }
        writeFile(exchangeService.export(type, query == null ? new ExchangeQuery() : query), response);
    }

    @Operation(summary = "附件与视频批量打包导出")
    @PreAuthorize("@pms.has('exchange:export:standard')")
    @DataScope(alias = "certificate", permission = "exchange:export:standard")
    @AuditLog(bizType = "exchange", operation = "exportAttachments")
    @PostMapping("/export/attachments")
    public void exportAttachments(@RequestBody(required = false) ExchangeQuery query,
                                  HttpServletResponse response) throws IOException {
        writeFile(exchangeService.exportAttachments(query == null ? new ExchangeQuery() : query), response);
    }

    private void writeFile(ExchangeFile file, HttpServletResponse response) throws IOException {
        response.setContentType(file.contentType());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8));
        response.getOutputStream().write(file.content());
    }
}
