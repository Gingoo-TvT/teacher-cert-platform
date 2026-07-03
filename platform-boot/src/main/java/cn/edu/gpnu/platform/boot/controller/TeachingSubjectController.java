package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.system.dto.SubjectImportResult;
import cn.edu.gpnu.platform.system.dto.SubjectSelectRequest;
import cn.edu.gpnu.platform.system.service.TeachingSubjectService;
import cn.edu.gpnu.platform.system.vo.TeachingSubjectVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "任教学科")
@RestController
@RequestMapping("/api/subject")
@RequiredArgsConstructor
public class TeachingSubjectController {

    private final TeachingSubjectService teachingSubjectService;

    @Operation(summary = "查询任教学科标准库")
    @PreAuthorize("@pms.has('dict:view') or @pms.has('subject:manage')")
    @DataScope(alias = "teaching_subject", permission = "dict:view")
    @GetMapping
    public Result<List<TeachingSubjectVO>> list(@RequestParam(value = "segment", required = false) String segment,
                                                @RequestParam(value = "keyword", required = false) String keyword,
                                                @RequestParam(value = "category", required = false) String category,
                                                @RequestParam(value = "yearVersion", required = false) String yearVersion) {
        return Result.ok(teachingSubjectService.list(segment, keyword, category, yearVersion));
    }

    @Operation(summary = "导入任教学科标准库")
    @PreAuthorize("@pms.has('subject:import')")
    @AuditLog(bizType = "subject", operation = "import")
    @PostMapping("/import")
    public Result<SubjectImportResult> importSubjects(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "yearVersion", required = false) String yearVersion)
            throws IOException {
        return Result.ok(teachingSubjectService.importSubjects(file.getInputStream(), yearVersion));
    }

    @Operation(summary = "校验任教学科可选")
    @PreAuthorize("@pms.has('dict:view') or @pms.has('subject:manage')")
    @DataScope(alias = "teaching_subject", permission = "dict:view")
    @PostMapping("/validate")
    public Result<Void> validateSelectable(@Valid @RequestBody SubjectSelectRequest request) {
        teachingSubjectService.validateSelectable(request.getSegmentCode(), request.getSubjectCode(), request.getYearVersion());
        return Result.ok();
    }

    @Operation(summary = "记录最近使用任教学科")
    @PreAuthorize("@pms.has('dict:view')")
    @PostMapping("/recent")
    public Result<Void> recordRecent(@Valid @RequestBody SubjectSelectRequest request) {
        teachingSubjectService.recordRecent(request.getSegmentCode(), request.getSubjectCode(), request.getYearVersion());
        return Result.ok();
    }

    @Operation(summary = "查询最近使用任教学科")
    @PreAuthorize("@pms.has('dict:view')")
    @DataScope(alias = "teaching_subject", permission = "dict:view")
    @GetMapping("/recent")
    public Result<List<TeachingSubjectVO>> recent(@RequestParam("segment") String segment,
                                                  @RequestParam(value = "yearVersion", required = false) String yearVersion) {
        return Result.ok(teachingSubjectService.recent(segment, yearVersion));
    }
}
