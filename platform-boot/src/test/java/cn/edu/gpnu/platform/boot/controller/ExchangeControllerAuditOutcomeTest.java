package cn.edu.gpnu.platform.boot.controller;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.exchange.dto.ImportConfirmRequest;
import cn.edu.gpnu.platform.exchange.service.ExchangeService;
import cn.edu.gpnu.platform.exchange.vo.ImportResultVO;
import cn.edu.gpnu.platform.security.service.MediaAccessCookieService;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExchangeControllerAuditOutcomeTest {

    private static final long BATCH_ID = 9201L;

    @Test
    void successfulImportRecordsPendingThenImportedForTheTargetBatch() {
        Fixture fixture = fixture();
        ImportConfirmRequest request = request();
        ImportResultVO result = result("IMPORTED");
        when(fixture.exchangeService.confirmImport(BATCH_ID, request)).thenReturn(result);

        fixture.controller.confirm(BATCH_ID, request);

        var order = inOrder(fixture.auditLogService, fixture.exchangeService);
        order.verify(fixture.auditLogService).recordRequiresNew(
                "exchange", BATCH_ID, "importBatch:" + BATCH_ID, "import",
                null, "PENDING", "确认导入已发起");
        order.verify(fixture.exchangeService).confirmImport(BATCH_ID, request);
        order.verify(fixture.auditLogService).recordRequiresNew(
                "exchange", BATCH_ID, "importBatch:" + BATCH_ID, "import",
                "PENDING", "IMPORTED", "确认导入已结束");
    }

    @Test
    void rowFailuresRemainExplicitlyFailedRatherThanLookingSuccessful() {
        Fixture fixture = fixture();
        ImportConfirmRequest request = request();
        ImportResultVO result = result("FAILED");
        when(fixture.exchangeService.confirmImport(eq(BATCH_ID), any(ImportConfirmRequest.class)))
                .thenReturn(result);

        fixture.controller.confirmByQuery(BATCH_ID, request.getStrategy());

        var order = inOrder(fixture.auditLogService, fixture.exchangeService);
        order.verify(fixture.auditLogService).recordRequiresNew(
                "exchange", BATCH_ID, "importBatch:" + BATCH_ID, "import",
                null, "PENDING", "确认导入已发起");
        order.verify(fixture.exchangeService).confirmImport(
                eq(BATCH_ID), any(ImportConfirmRequest.class));
        order.verify(fixture.auditLogService).recordRequiresNew(
                "exchange", BATCH_ID, "importBatch:" + BATCH_ID, "import",
                "PENDING", "FAILED", "确认导入已结束");
    }

    @Test
    void rejectedImportRecordsErrorAndPreservesTheOriginalFailure() {
        Fixture fixture = fixture();
        ImportConfirmRequest request = request();
        BizException failure = new BizException("批次认领失败");
        doThrow(failure).when(fixture.exchangeService).confirmImport(BATCH_ID, request);

        assertThatThrownBy(() -> fixture.controller.confirm(BATCH_ID, request))
                .isSameAs(failure);

        var order = inOrder(fixture.auditLogService, fixture.exchangeService);
        order.verify(fixture.auditLogService).recordRequiresNew(
                "exchange", BATCH_ID, "importBatch:" + BATCH_ID, "import",
                null, "PENDING", "确认导入已发起");
        order.verify(fixture.exchangeService).confirmImport(BATCH_ID, request);
        order.verify(fixture.auditLogService).recordRequiresNew(
                "exchange", BATCH_ID, "importBatch:" + BATCH_ID, "import",
                "PENDING", "ERROR", "确认导入异常终止");
    }

    private Fixture fixture() {
        ExchangeService exchangeService = mock(ExchangeService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        ExchangeController controller = new ExchangeController(
                exchangeService,
                mock(MediaAccessCookieService.class),
                auditLogService);
        return new Fixture(controller, exchangeService, auditLogService);
    }

    private ImportConfirmRequest request() {
        ImportConfirmRequest request = new ImportConfirmRequest();
        request.setStrategy("OVERWRITE");
        return request;
    }

    private ImportResultVO result(String status) {
        ImportResultVO result = new ImportResultVO();
        result.setBatchId(BATCH_ID);
        result.setStatus(status);
        return result;
    }

    private record Fixture(
            ExchangeController controller,
            ExchangeService exchangeService,
            AuditLogService auditLogService) {
    }
}
