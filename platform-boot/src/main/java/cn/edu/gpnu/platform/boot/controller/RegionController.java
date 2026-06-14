package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.system.service.RegionService;
import cn.edu.gpnu.platform.system.vo.RegionPathVO;
import cn.edu.gpnu.platform.system.vo.RegionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "行政区划")
@RestController
@RequestMapping("/api/region")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @Operation(summary = "查询下级行政区划")
    @PreAuthorize("@pms.has('dict:view')")
    @DataScope(alias = "sys_region", permission = "dict:view")
    @GetMapping("/children")
    public Result<List<RegionVO>> children(@RequestParam(value = "parent", required = false) String parent) {
        return Result.ok(regionService.children(parent));
    }

    @Operation(summary = "查询行政区划完整路径")
    @PreAuthorize("@pms.has('dict:view')")
    @DataScope(alias = "sys_region", permission = "dict:view")
    @GetMapping("/path")
    public Result<RegionPathVO> path(@RequestParam("code") String code) {
        return Result.ok(regionService.path(code));
    }
}
