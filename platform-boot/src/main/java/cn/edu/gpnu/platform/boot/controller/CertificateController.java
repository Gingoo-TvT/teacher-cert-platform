package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.business.certificate.dto.CertificateCorrectRequest;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateGenerateRequest;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateIssueRequest;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateQuery;
import cn.edu.gpnu.platform.business.certificate.dto.CertificateVoidRequest;
import cn.edu.gpnu.platform.business.certificate.service.CertificateService;
import cn.edu.gpnu.platform.business.certificate.vo.CertificatePrecheckVO;
import cn.edu.gpnu.platform.business.certificate.vo.CertificateVO;
import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "证书管理")
@RestController
@RequestMapping("/api/cert")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @Operation(summary = "证书清单")
    @PreAuthorize("@pms.has('cert:view')")
    @DataScope(alias = "certificate", permission = "cert:view")
    @GetMapping
    public Result<PageResult<CertificateVO>> list(CertificateQuery query) {
        return Result.ok(certificateService.list(query));
    }

    @Operation(summary = "证书详情")
    @PreAuthorize("@pms.has('cert:view')")
    @GetMapping("/{id}")
    public Result<CertificateVO> detail(@PathVariable Long id) {
        return Result.ok(certificateService.detail(id));
    }

    @Operation(summary = "证书生成前置校验")
    @PreAuthorize("@pms.has('cert:generate')")
    @GetMapping("/precheck/{studentId}")
    public Result<CertificatePrecheckVO> precheck(@PathVariable Long studentId,
                                                  @RequestParam("year") String assessmentYear) {
        return Result.ok(certificateService.precheck(studentId, assessmentYear));
    }

    @Operation(summary = "生成证书编号")
    @PreAuthorize("@pms.has('cert:generate')")
    @AuditLog(bizType = "cert", operation = "generate")
    @PostMapping("/generate")
    public Result<CertificateVO> generate(@Valid @RequestBody CertificateGenerateRequest request) {
        return Result.ok(certificateService.generate(request));
    }

    @Operation(summary = "签发证书")
    @PreAuthorize("@pms.has('cert:issue')")
    @AuditLog(bizType = "cert", operation = "issue")
    @PostMapping("/{id}/issue")
    public Result<CertificateVO> issue(@PathVariable Long id,
                                       @Valid @RequestBody CertificateIssueRequest request) {
        return Result.ok(certificateService.issue(id, request));
    }

    @Operation(summary = "标记证书已导出")
    @PreAuthorize("@pms.has('cert:view')")
    @AuditLog(bizType = "cert", operation = "export")
    @PostMapping("/{id}/export")
    public Result<CertificateVO> markExported(@PathVariable Long id) {
        return Result.ok(certificateService.markExported(id));
    }

    @Operation(summary = "证书归档")
    @PreAuthorize("@pms.has('cert:view')")
    @AuditLog(bizType = "cert", operation = "archive")
    @PostMapping("/{id}/archive")
    public Result<CertificateVO> archive(@PathVariable Long id) {
        return Result.ok(certificateService.archive(id));
    }

    @Operation(summary = "作废证书")
    @PreAuthorize("@pms.has('cert:void')")
    @AuditLog(bizType = "cert", operation = "void")
    @PostMapping("/{id}/void")
    public Result<CertificateVO> voidCertificate(@PathVariable Long id,
                                                 @Valid @RequestBody CertificateVoidRequest request) {
        return Result.ok(certificateService.voidCertificate(id, request));
    }

    @Operation(summary = "重开证书")
    @PreAuthorize("@pms.has('cert:reissue')")
    @AuditLog(bizType = "cert", operation = "reissue")
    @PostMapping("/{id}/reissue")
    public Result<CertificateVO> reissue(@PathVariable Long id) {
        return Result.ok(certificateService.reissue(id));
    }

    @Operation(summary = "更正证书")
    @PreAuthorize("@pms.has('cert:correct')")
    @AuditLog(bizType = "cert", operation = "correct")
    @PutMapping("/{id}/correct")
    public Result<CertificateVO> correct(@PathVariable Long id,
                                         @Valid @RequestBody CertificateCorrectRequest request) {
        return Result.ok(certificateService.correct(id, request));
    }
}
