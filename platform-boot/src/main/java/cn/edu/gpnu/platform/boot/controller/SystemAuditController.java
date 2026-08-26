package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.system.dto.AuditLogQuery;
import cn.edu.gpnu.platform.system.dto.BackupTriggerRequest;
import cn.edu.gpnu.platform.system.dto.SysParamUpdateRequest;
import cn.edu.gpnu.platform.system.service.SystemManagementService;
import cn.edu.gpnu.platform.system.vo.AuditLogVO;
import cn.edu.gpnu.platform.system.vo.BackupRecordVO;
import cn.edu.gpnu.platform.system.vo.SysParamVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统参数、审计与备份")
@RestController
@RequiredArgsConstructor
public class SystemAuditController {

    private final SystemManagementService systemManagementService;

    @Operation(summary = "系统参数列表")
    @PreAuthorize("@pms.has('system:param:manage')")
    @GetMapping("/api/system/param")
    public Result<PageResult<SysParamVO>> params(@RequestParam(value = "group", required = false) String group,
                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "page", required = false) Integer page,
                                                 @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(systemManagementService.params(group, keyword, page, size));
    }

    @Operation(summary = "更新系统参数")
    @PreAuthorize("@pms.has('system:param:manage')")
    @AuditLog(bizType = "systemParam", operation = "update")
    @PutMapping("/api/system/param/{id}")
    public Result<SysParamVO> updateParam(@PathVariable Long id,
                                          @Valid @RequestBody SysParamUpdateRequest request) {
        return Result.ok(systemManagementService.updateParam(id, request));
    }

    @Operation(summary = "审计日志查询")
    @PreAuthorize("@pms.has('audit:view')")
    @GetMapping("/api/audit/log")
    public Result<PageResult<AuditLogVO>> auditLogs(AuditLogQuery query) {
        return Result.ok(systemManagementService.auditLogs(query));
    }

    @Operation(summary = "拒绝删除审计日志")
    @PreAuthorize("@pms.has('audit:view')")
    @DeleteMapping("/api/audit/log/{id}")
    public Result<Void> deleteAuditLog(@PathVariable Long id) {
        systemManagementService.rejectAuditDelete(id);
        return Result.ok();
    }

    @Operation(summary = "备份记录列表")
    @PreAuthorize("@pms.has('system:backup')")
    @GetMapping("/api/system/backup")
    public Result<PageResult<BackupRecordVO>> backups(@RequestParam(value = "status", required = false) String status,
                                                       @RequestParam(value = "page", required = false) Integer page,
                                                       @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(systemManagementService.backups(status, page, size));
    }

    @Operation(summary = "触发数据库逻辑备份（JDBC 导出→gzip→MinIO）")
    @PreAuthorize("@pms.has('system:backup')")
    @AuditLog(bizType = "backup", operation = "trigger", before = true)
    @PostMapping("/api/system/backup/trigger")
    public Result<BackupRecordVO> triggerBackup(@Valid @RequestBody BackupTriggerRequest request) {
        return Result.ok(systemManagementService.triggerBackup(request));
    }
}
