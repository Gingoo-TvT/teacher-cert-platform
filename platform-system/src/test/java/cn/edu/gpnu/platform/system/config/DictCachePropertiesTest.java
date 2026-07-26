package cn.edu.gpnu.platform.system.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictCachePropertiesTest {

    @Test
    void defaultsAndExactOneThirdBoundaryAreValid() {
        DictCacheProperties defaults = new DictCacheProperties();
        assertThatCode(defaults::validate).doesNotThrowAnyException();

        DictCacheProperties boundary = properties(Duration.ofMillis(3), Duration.ofMillis(1));
        assertThatCode(boundary::validate).doesNotThrowAnyException();
    }

    @Test
    void nullZeroAndNegativeDurationsFailAtStartup() {
        DictCacheProperties nullLease = properties(null, Duration.ofMillis(1));
        DictCacheProperties zeroRenew = properties(Duration.ofSeconds(3), Duration.ZERO);
        DictCacheProperties negativeLease = properties(Duration.ofSeconds(-1), Duration.ofMillis(1));

        assertThatThrownBy(nullLease::validate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(zeroRenew::validate).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(negativeLease::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void positiveSubMillisecondDurationsAreRejectedBeforeSchedulingOrLua() {
        DictCacheProperties subMillisecondLease =
                properties(Duration.ofNanos(1), Duration.ofNanos(1));
        DictCacheProperties subMillisecondRenew =
                properties(Duration.ofSeconds(3), Duration.ofNanos(1));

        assertThatThrownBy(subMillisecondLease::validate)
                .hasMessageContaining("不得小于 1ms");
        assertThatThrownBy(subMillisecondRenew::validate)
                .hasMessageContaining("不得小于 1ms");
    }

    @Test
    void renewIntervalGreaterThanOneThirdLeaseIsRejected() {
        DictCacheProperties properties = properties(Duration.ofMillis(5), Duration.ofMillis(2));

        assertThatThrownBy(properties::validate)
                .hasMessageContaining("不超过 writer-lease 的三分之一");
    }

    @Test
    void doubledRecoveryTtlOverflowIsRejectedAtStartup() {
        DictCacheProperties properties =
                properties(Duration.ofMillis(Long.MAX_VALUE), Duration.ofMillis(1));

        assertThatThrownBy(properties::validate)
                .hasMessageContaining("超出 Redis 毫秒租约范围")
                .hasCauseInstanceOf(ArithmeticException.class);
    }

    private DictCacheProperties properties(Duration lease, Duration renewInterval) {
        DictCacheProperties properties = new DictCacheProperties();
        properties.setWriterLease(lease);
        properties.setWriterRenewInterval(renewInterval);
        return properties;
    }
}
