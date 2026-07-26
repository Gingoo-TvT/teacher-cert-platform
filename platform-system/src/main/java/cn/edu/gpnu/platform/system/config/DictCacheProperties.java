package cn.edu.gpnu.platform.system.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 字典跨节点缓存写窗口配置。
 *
 * <p>租约只用于崩溃回收，活事务由后台续租维持，并在提交前再次校验 owner。续租周期必须显著短于租约，
 * 给短暂调度抖动和 Redis 往返留出余量。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "platform.cache.dictionary")
public class DictCacheProperties {

    private Duration writerLease = Duration.ofMinutes(2);
    private Duration writerRenewInterval = Duration.ofSeconds(20);

    @PostConstruct
    void validate() {
        if (writerLease == null || writerLease.isZero() || writerLease.isNegative()) {
            throw new IllegalStateException("platform.cache.dictionary.writer-lease 必须为正数");
        }
        if (writerRenewInterval == null || writerRenewInterval.isZero() || writerRenewInterval.isNegative()) {
            throw new IllegalStateException("platform.cache.dictionary.writer-renew-interval 必须为正数");
        }
        try {
            if (writerLease.toMillis() < 1L || writerRenewInterval.toMillis() < 1L) {
                throw new IllegalStateException("字典缓存 writer 租约与续租周期不得小于 1ms");
            }
            Math.multiplyExact(writerLease.toMillis(), 2L);
        } catch (ArithmeticException e) {
            throw new IllegalStateException("字典缓存 writer-lease 超出 Redis 毫秒租约范围", e);
        }
        if (writerRenewInterval.compareTo(writerLease.dividedBy(3L)) > 0) {
            throw new IllegalStateException("字典缓存 writer-renew-interval 必须不超过 writer-lease 的三分之一");
        }
    }
}
