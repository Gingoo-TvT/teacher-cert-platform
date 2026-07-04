package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.system.service.NotificationService;
import cn.edu.gpnu.platform.system.vo.NotificationVO;
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

@Tag(name = "通知中心")
@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NotificationService notificationService;

    @Operation(summary = "通知列表")
    @PreAuthorize("@pms.has('notice:view')")
    @GetMapping
    public Result<PageResult<NotificationVO>> list(@RequestParam(value = "read", required = false) Boolean read,
                                                    @RequestParam(value = "page", required = false) Integer page,
                                                    @RequestParam(value = "size", required = false) Integer size) {
        return Result.ok(notificationService.list(read, page, size));
    }

    @Operation(summary = "未读通知数")
    @PreAuthorize("@pms.has('notice:view')")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(notificationService.unreadCount());
    }

    @Operation(summary = "标记通知已读")
    @PreAuthorize("@pms.has('notice:view')")
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.ok();
    }

    @Operation(summary = "全部标记已读")
    @PreAuthorize("@pms.has('notice:view')")
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead();
        return Result.ok();
    }
}
