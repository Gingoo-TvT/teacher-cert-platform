package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.business.video.dto.VideoArbitrateRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoAssignRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoQuery;
import cn.edu.gpnu.platform.business.video.dto.VideoReturnRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoScoreRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoThirdReviewRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoUploadInitRequest;
import cn.edu.gpnu.platform.business.video.dto.VideoUploadMergeRequest;
import cn.edu.gpnu.platform.business.video.service.VideoReviewService;
import cn.edu.gpnu.platform.business.video.vo.ReviewerCandidateVO;
import cn.edu.gpnu.platform.business.video.vo.VideoPlaybackVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewTaskVO;
import cn.edu.gpnu.platform.business.video.vo.VideoReviewVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadInitVO;
import cn.edu.gpnu.platform.business.video.vo.VideoUploadProgressVO;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "教学能力视频评审")
@RestController
@RequestMapping("/api/video")
@RequiredArgsConstructor
public class VideoReviewController {

    private final VideoReviewService videoReviewService;

    @Operation(summary = "视频分片上传初始化")
    @PreAuthorize("@pms.has('video:upload')")
    @AuditLog(bizType = "video", operation = "uploadInit")
    @PostMapping("/upload/init")
    public Result<VideoUploadInitVO> initUpload(@Valid @RequestBody VideoUploadInitRequest request) {
        return Result.ok(videoReviewService.initUpload(request));
    }

    @Operation(summary = "上传视频分片")
    @PreAuthorize("@pms.has('video:upload')")
    @AuditLog(bizType = "video", operation = "uploadChunk")
    @PostMapping("/upload/chunk")
    public Result<Void> uploadChunk(@RequestParam("uploadId") String uploadId,
                                    @RequestParam("index") Integer index,
                                    @RequestParam("md5") String md5,
                                    @RequestParam("file") MultipartFile file) throws IOException {
        videoReviewService.uploadChunk(uploadId, index, md5, file.getInputStream(), file.getSize());
        return Result.ok();
    }

    @Operation(summary = "合并视频分片并校验")
    @PreAuthorize("@pms.has('video:upload')")
    @AuditLog(bizType = "video", operation = "uploadMerge")
    @PostMapping("/upload/merge")
    public Result<VideoReviewVO> merge(@Valid @RequestBody VideoUploadMergeRequest request) {
        return Result.ok(videoReviewService.merge(request));
    }

    @Operation(summary = "视频上传进度")
    @PreAuthorize("@pms.has('video:upload')")
    @DataScope(alias = "video_upload_session", permission = "video:upload")
    @GetMapping("/upload/progress")
    public Result<VideoUploadProgressVO> progress(@RequestParam("uploadId") String uploadId) {
        return Result.ok(videoReviewService.progress(uploadId));
    }

    @Operation(summary = "视频评审列表")
    @PreAuthorize("@pms.has('video:upload') or @pms.has('video:assign') or @pms.has('video:arbitrate') or @pms.has('video:confirm') or @pms.has('video:play')")
    @DataScope(alias = "video_review", permission = "video:play")
    @GetMapping("/reviews")
    public Result<PageResult<VideoReviewVO>> list(VideoQuery query) {
        return Result.ok(videoReviewService.list(query));
    }

    @Operation(summary = "视频评审教师候选人")
    @PreAuthorize("@pms.has('video:assign')")
    @GetMapping("/reviewer-candidates")
    public Result<List<ReviewerCandidateVO>> reviewerCandidates() {
        return Result.ok(videoReviewService.reviewerCandidates());
    }

    @Operation(summary = "视频评审详情")
    @PreAuthorize("@pms.has('video:upload') or @pms.has('video:assign') or @pms.has('video:arbitrate') or @pms.has('video:confirm') or @pms.has('video:play')")
    @GetMapping("/reviews/{id}")
    public Result<VideoReviewVO> detail(@PathVariable Long id) {
        return Result.ok(videoReviewService.detail(id));
    }

    @Operation(summary = "分配视频评审教师")
    @PreAuthorize("@pms.has('video:assign')")
    @DataScope(alias = "video_review", permission = "video:assign")
    @AuditLog(bizType = "video", operation = "assign")
    @PostMapping("/reviews/{id}/assign")
    public Result<Void> assign(@PathVariable Long id,
                               @Valid @RequestBody VideoAssignRequest request) {
        videoReviewService.assign(id, request);
        return Result.ok();
    }

    @Operation(summary = "我的视频评审任务")
    @PreAuthorize("@pms.has('video:score')")
    @GetMapping("/tasks/my")
    public Result<PageResult<VideoReviewTaskVO>> myTasks(@RequestParam(value = "status", required = false) String status) {
        return Result.ok(videoReviewService.myTasks(status));
    }

    @Operation(summary = "评审任务详情")
    @PreAuthorize("@pms.has('video:score')")
    @GetMapping("/tasks/{id}")
    public Result<VideoReviewTaskVO> taskDetail(@PathVariable Long id) {
        return Result.ok(videoReviewService.taskDetail(id));
    }

    @Operation(summary = "提交视频评分")
    @PreAuthorize("@pms.has('video:score')")
    @AuditLog(bizType = "video", operation = "submitScore")
    @PostMapping("/tasks/{id}/score")
    public Result<Void> submitScore(@PathVariable Long id,
                                    @Valid @RequestBody VideoScoreRequest request) {
        videoReviewService.submitScore(id, request);
        return Result.ok();
    }

    @Operation(summary = "视频评审任务明细")
    @PreAuthorize("@pms.has('video:assign') or @pms.has('video:arbitrate') or @pms.has('video:confirm')")
    @DataScope(alias = "video_review_task", permission = "video:assign")
    @GetMapping("/reviews/{id}/tasks")
    public Result<List<VideoReviewTaskVO>> tasks(@PathVariable Long id) {
        return Result.ok(videoReviewService.tasks(id));
    }

    @Operation(summary = "第三专家复评")
    @PreAuthorize("@pms.has('video:arbitrate')")
    @DataScope(alias = "video_review", permission = "video:arbitrate")
    @AuditLog(bizType = "video", operation = "thirdReview")
    @PostMapping("/reviews/{id}/third-review")
    public Result<Void> thirdReview(@PathVariable Long id,
                                    @Valid @RequestBody VideoThirdReviewRequest request) {
        videoReviewService.thirdReview(id, request);
        return Result.ok();
    }

    @Operation(summary = "学院仲裁")
    @PreAuthorize("@pms.has('video:arbitrate')")
    @DataScope(alias = "video_review", permission = "video:arbitrate")
    @AuditLog(bizType = "video", operation = "arbitrate")
    @PostMapping("/reviews/{id}/arbitrate")
    public Result<Void> arbitrate(@PathVariable Long id,
                                  @Valid @RequestBody VideoArbitrateRequest request) {
        videoReviewService.arbitrate(id, request);
        return Result.ok();
    }

    @Operation(summary = "学院负责人确认视频结果")
    @PreAuthorize("@pms.has('video:confirm')")
    @DataScope(alias = "video_review", permission = "video:confirm")
    @AuditLog(bizType = "video", operation = "confirm")
    @PostMapping("/reviews/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        videoReviewService.confirm(id);
        return Result.ok();
    }

    @Operation(summary = "退回视频重新上传")
    @PreAuthorize("@pms.has('video:confirm') or @pms.has('video:arbitrate')")
    @PostMapping("/reviews/{id}/return")
    public Result<Void> returnReview(@PathVariable Long id,
                                     @Valid @RequestBody VideoReturnRequest request) {
        videoReviewService.returnReview(id, request);
        return Result.ok();
    }

    @Operation(summary = "获取鉴权播放地址与水印")
    @PreAuthorize("@pms.has('video:play')")
    @DataScope(alias = "video_review_task", permission = "video:play")
    @AuditLog(bizType = "video", operation = "play")
    @GetMapping("/reviews/{id}/play")
    public Result<VideoPlaybackVO> playback(@PathVariable Long id) {
        return Result.ok(videoReviewService.playback(id));
    }
}
