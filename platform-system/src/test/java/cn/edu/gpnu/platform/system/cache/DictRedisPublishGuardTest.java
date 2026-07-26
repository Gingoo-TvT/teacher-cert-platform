package cn.edu.gpnu.platform.system.cache;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictRedisPublishGuardTest {

    @Test
    void nestedWritersBlockPublishUntilTheLastHandleCloses() {
        DictRedisPublishGuard guard = new DictRedisPublishGuard();
        List<String> published = new ArrayList<>();
        DictRedisPublishGuard.WriteHandle first = guard.begin("type_a");
        DictRedisPublishGuard.WriteHandle second = guard.begin("type_a");

        assertThat(guard.activeWriterCount("type_a")).isEqualTo(2);
        assertThat(guard.publishIfIdle("type_a", () -> published.add("V2"))).isFalse();
        first.close();
        assertThat(guard.activeWriterCount("type_a")).isEqualTo(1);
        assertThat(guard.publishIfIdle("type_a", () -> published.add("V2"))).isFalse();

        second.close();
        assertThat(guard.activeWriterCount("type_a")).isZero();
        assertThat(guard.publishIfIdle("type_a", () -> published.add("V2"))).isTrue();
        assertThat(published).containsExactly("V2");
    }

    @Test
    void writeHandleCloseIsIdempotent() {
        DictRedisPublishGuard guard = new DictRedisPublishGuard();
        DictRedisPublishGuard.WriteHandle handle = guard.begin("type_a");

        handle.close();
        handle.close();

        assertThat(guard.activeWriterCount("type_a")).isZero();
        assertThat(guard.publishIfIdle("type_a", () -> { })).isTrue();
    }

    @Test
    void publisherAndWriterBeginAreLinearized() throws Exception {
        DictRedisPublishGuard guard = new DictRedisPublishGuard();
        CountDownLatch insidePublish = new CountDownLatch(1);
        CountDownLatch releasePublish = new CountDownLatch(1);
        AtomicReference<Thread> contenderThread = new AtomicReference<>();
        ExecutorService publisher = Executors.newSingleThreadExecutor();
        ExecutorService writer = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> publishing = publisher.submit(() -> guard.publishIfIdle("type_a", () -> {
                insidePublish.countDown();
                awaitOrThrow(releasePublish, "发布动作未获放行");
            }));
            assertThat(insidePublish.await(5, TimeUnit.SECONDS)).isTrue();

            Future<DictRedisPublishGuard.WriteHandle> opening = writer.submit(() -> {
                contenderThread.set(Thread.currentThread());
                return guard.begin("type_a");
            });
            assertThat(awaitQueue(guard, "type_a", contenderThread, 5, TimeUnit.SECONDS))
                    .as("writer begin 必须真实排队，不能插入“检查 idle → Redis PUT”之间")
                    .isTrue();

            releasePublish.countDown();
            assertThat(publishing.get(5, TimeUnit.SECONDS)).isTrue();
            DictRedisPublishGuard.WriteHandle handle = opening.get(5, TimeUnit.SECONDS);
            assertThat(guard.activeWriterCount("type_a")).isEqualTo(1);
            handle.close();
        } finally {
            releasePublish.countDown();
            publisher.shutdownNow();
            writer.shutdownNow();
        }
    }

    @Test
    void actionFailureReleasesTheStripeLock() {
        DictRedisPublishGuard guard = new DictRedisPublishGuard();

        assertThatThrownBy(() -> guard.publishIfIdle("type_a", () -> {
            throw new IllegalStateException("redis put failed");
        })).isInstanceOf(IllegalStateException.class);

        DictRedisPublishGuard.WriteHandle handle = guard.begin("type_a");
        assertThat(guard.activeWriterCount("type_a")).isEqualTo(1);
        handle.close();
        assertThat(guard.publishIfIdle("type_a", () -> { })).isTrue();
    }

    private static boolean awaitQueue(DictRedisPublishGuard guard, String identity,
                                      AtomicReference<Thread> contender, long timeout,
                                      TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            Thread thread = contender.get();
            if (thread != null && guard.isOperationQueued(identity, thread)) {
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(5);
        }
        Thread thread = contender.get();
        return thread != null && guard.isOperationQueued(identity, thread);
    }

    private static void awaitOrThrow(CountDownLatch latch, String message) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
