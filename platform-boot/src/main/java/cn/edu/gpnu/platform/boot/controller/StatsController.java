package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.statistics.dto.StatsQuery;
import cn.edu.gpnu.platform.statistics.service.StatsService;
import cn.edu.gpnu.platform.statistics.vo.StatsExportFile;
import cn.edu.gpnu.platform.statistics.vo.StatsReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "统计查询与报表")
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "统计报表")
    @PreAuthorize("@pms.has('stats:view')")
    @GetMapping("/{type}")
    public Result<StatsReportVO> report(@PathVariable String type, StatsQuery query) {
        return Result.ok(statsService.report(type, query));
    }

    @Operation(summary = "统计报表导出")
    @PreAuthorize("@pms.has('stats:view')")
    @AuditLog(bizType = "stats", operation = "export")
    @PostMapping("/{type}/export")
    public void export(@PathVariable String type,
                       @RequestBody(required = false) StatsQuery query,
                       HttpServletResponse response) throws IOException {
        StatsExportFile file = statsService.export(type, query == null ? new StatsQuery() : query);
        response.setContentType(file.contentType());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8));
        response.getOutputStream().write(file.content());
    }
}
