package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.business.exemption.service.ExemptionService;
import cn.edu.gpnu.platform.business.material.service.ProcessMaterialService;
import cn.edu.gpnu.platform.business.video.service.VideoReviewService;
import cn.edu.gpnu.platform.boot.support.FileStreamingSupport;
import cn.edu.gpnu.platform.security.service.MediaAccessCookieService;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensitiveContentAuditTest {

    @Test
    void materialContentIsAuditedAfterAuthorizationAndBeforeStreaming() {
        ProcessMaterialService service = mock(ProcessMaterialService.class);
        MediaAccessCookieService cookieService = mock(MediaAccessCookieService.class);
        FileStreamingSupport streaming = mock(FileStreamingSupport.class);
        AuditLogService audit = mock(AuditLogService.class);
        ProcessMaterialController controller =
                new ProcessMaterialController(service, cookieService, streaming, audit);
        when(service.previewFileId(7L)).thenReturn(70L);
        when(streaming.stream(70L, null)).thenReturn(emptyStream());

        controller.previewContent(7L, null);

        var order = inOrder(service, audit, streaming);
        order.verify(service).previewFileId(7L);
        order.verify(audit).record("material", 7L, "material:7:content",
                "previewContent", null, null, "读取过程性材料内容");
        order.verify(streaming).stream(70L, null);
    }

    @Test
    void exemptionContentIsAuditedAfterAuthorizationAndBeforeStreaming() {
        ExemptionService service = mock(ExemptionService.class);
        MediaAccessCookieService cookieService = mock(MediaAccessCookieService.class);
        FileStreamingSupport streaming = mock(FileStreamingSupport.class);
        AuditLogService audit = mock(AuditLogService.class);
        ExemptionController controller =
                new ExemptionController(service, cookieService, streaming, audit);
        when(service.previewMaterialFileId(8L)).thenReturn(80L);
        when(streaming.stream(80L, null)).thenReturn(emptyStream());

        controller.previewMaterialContent(8L, null);

        var order = inOrder(service, audit, streaming);
        order.verify(service).previewMaterialFileId(8L);
        order.verify(audit).record("exemption", 8L, "exemption-material:8:content",
                "previewMaterialContent", null, null, "读取免考佐证内容");
        order.verify(streaming).stream(80L, null);
    }

    @Test
    void videoContentIsAuditedAfterAuthorizationAndBeforeStreaming() {
        VideoReviewService service = mock(VideoReviewService.class);
        MediaAccessCookieService cookieService = mock(MediaAccessCookieService.class);
        FileStreamingSupport streaming = mock(FileStreamingSupport.class);
        AuditLogService audit = mock(AuditLogService.class);
        VideoReviewController controller =
                new VideoReviewController(service, cookieService, streaming, audit);
        when(service.playbackFileId(9L)).thenReturn(90L);
        when(streaming.stream(90L, null)).thenReturn(emptyStream());

        controller.playbackContent(9L, null);

        var order = inOrder(service, audit, streaming);
        order.verify(service).playbackFileId(9L);
        order.verify(audit).record("video", 9L, "video-review:9:content",
                "playContent", null, null, "读取视频播放内容");
        order.verify(streaming).stream(90L, null);
    }

    private ResponseEntity<StreamingResponseBody> emptyStream() {
        return ResponseEntity.ok(outputStream -> {
            // 本单测只验证授权、审计和流式返回之间的顺序。
        });
    }
}
