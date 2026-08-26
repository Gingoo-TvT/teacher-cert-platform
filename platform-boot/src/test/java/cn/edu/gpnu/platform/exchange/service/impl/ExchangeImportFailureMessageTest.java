package cn.edu.gpnu.platform.exchange.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeImportFailureMessageTest {

    @Test
    void nonBusinessFailureNeverLeaksItsMessageToImportResultsOrReports() {
        String sentinel = "SENTINEL_INTERNAL_SQL users_email_unique";

        String message = ExchangeServiceImpl.importFailureMessage(
                new IllegalStateException(sentinel));

        assertThat(message).isEqualTo("导入失败").doesNotContain(sentinel);
    }

    @Test
    void explicitBusinessMessageRemainsPartOfTheStableClientContract() {
        String message = ExchangeServiceImpl.importFailureMessage(
                new IllegalStateException("wrapper", new BizException("证书状态不允许导入")));

        assertThat(message).isEqualTo("证书状态不允许导入");
    }
}
