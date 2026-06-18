package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.business.video.dto.ReviewerGroupMemberRequest;
import cn.edu.gpnu.platform.business.video.dto.ReviewerGroupRequest;
import cn.edu.gpnu.platform.business.video.service.ReviewerGroupService;
import cn.edu.gpnu.platform.business.video.vo.ReviewerGroupVO;
import cn.edu.gpnu.platform.common.annotation.AuditLog;
import cn.edu.gpnu.platform.common.annotation.DataScope;
import cn.edu.gpnu.platform.common.api.Result;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "视频评审组")
@RestController
@RequestMapping("/api/video/reviewer-groups")
@RequiredArgsConstructor
public class ReviewerGroupController {

    private final ReviewerGroupService reviewerGroupService;

    @Operation(summary = "评审组列表")
    @PreAuthorize("@pms.has('video:assign')")
    @DataScope(alias = "reviewer_group", permission = "video:assign")
    @GetMapping
    public Result<List<ReviewerGroupVO>> list() {
        return Result.ok(reviewerGroupService.list());
    }

    @Operation(summary = "评审组详情")
    @PreAuthorize("@pms.has('video:assign')")
    @GetMapping("/{id}")
    public Result<ReviewerGroupVO> detail(@PathVariable Long id) {
        return Result.ok(reviewerGroupService.detail(id));
    }

    @Operation(summary = "创建评审组")
    @PreAuthorize("@pms.has('video:assign')")
    @AuditLog(bizType = "reviewerGroup", operation = "create")
    @PostMapping
    public Result<ReviewerGroupVO> create(@Valid @RequestBody ReviewerGroupRequest request) {
        return Result.ok(reviewerGroupService.create(request));
    }

    @Operation(summary = "更新评审组")
    @PreAuthorize("@pms.has('video:assign')")
    @AuditLog(bizType = "reviewerGroup", operation = "update")
    @PutMapping("/{id}")
    public Result<ReviewerGroupVO> update(@PathVariable Long id,
                                          @Valid @RequestBody ReviewerGroupRequest request) {
        return Result.ok(reviewerGroupService.update(id, request));
    }

    @Operation(summary = "删除评审组")
    @PreAuthorize("@pms.has('video:assign')")
    @AuditLog(bizType = "reviewerGroup", operation = "delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reviewerGroupService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "添加评审组成员")
    @PreAuthorize("@pms.has('video:assign')")
    @AuditLog(bizType = "reviewerGroup", operation = "addMember")
    @PostMapping("/{id}/members")
    public Result<ReviewerGroupVO> addMember(@PathVariable Long id,
                                             @Valid @RequestBody ReviewerGroupMemberRequest request) {
        return Result.ok(reviewerGroupService.addMember(id, request));
    }

    @Operation(summary = "移除评审组成员")
    @PreAuthorize("@pms.has('video:assign')")
    @AuditLog(bizType = "reviewerGroup", operation = "removeMember")
    @DeleteMapping("/{id}/members/{memberId}")
    public Result<Void> removeMember(@PathVariable Long id,
                                     @PathVariable Long memberId) {
        reviewerGroupService.removeMember(id, memberId);
        return Result.ok();
    }
}
