package cn.edu.gpnu.platform.system.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** 固定作业名与固定结果标签的调度结果指标。 */
@Component
public class ScheduledJobMetrics {

    public enum Job {
        DATABASE_BACKUP("database_backup"),
        RETENTION_PRUNE("retention_prune"),
        MINIO_INCOMPLETE_ABORT("minio_incomplete_abort"),
        ORPHAN_FILE_SCAN("orphan_file_scan"),
        VIDEO_FINALIZATION_RECONCILIATION("video_finalization_reconciliation");

        private final String tag;

        Job(String tag) {
            this.tag = tag;
        }
    }

    private enum Outcome {
        SUCCESS("success"), FAILURE("failure"), SKIPPED("skipped");

        private final String tag;

        Outcome(String tag) {
            this.tag = tag;
        }
    }

    private final Clock clock;
    private final Map<Job, Map<Outcome, Counter>> counters = new EnumMap<>(Job.class);
    private final Map<Job, Map<Outcome, Timer>> timers = new EnumMap<>(Job.class);
    private final Map<Job, AtomicLong> lastSuccess = new EnumMap<>(Job.class);

    public ScheduledJobMetrics(MeterRegistry registry) {
        this.clock = Clock.systemUTC();
        for (Job job : Job.values()) {
            Map<Outcome, Counter> jobCounters = new EnumMap<>(Outcome.class);
            Map<Outcome, Timer> jobTimers = new EnumMap<>(Outcome.class);
            for (Outcome outcome : Outcome.values()) {
                jobCounters.put(outcome, Counter.builder("platform.scheduled.job.executions")
                        .description("调度作业真实完成结果计数")
                        .tags("job", job.tag, "outcome", outcome.tag)
                        .register(registry));
                jobTimers.put(outcome, Timer.builder("platform.scheduled.job.duration")
                        .description("调度作业真实执行耗时")
                        .tags("job", job.tag, "outcome", outcome.tag)
                        .register(registry));
            }
            counters.put(job, jobCounters);
            timers.put(job, jobTimers);
            AtomicLong timestamp = new AtomicLong();
            lastSuccess.put(job, timestamp);
            Gauge.builder("platform.scheduled.job.last.success.timestamp", timestamp, AtomicLong::get)
                    .description("调度作业最近成功完成的 Unix 时间戳")
                    .baseUnit("seconds")
                    .tag("job", job.tag)
                    .register(registry);
        }
    }

    public Run start(Job job) {
        return new Run(job, System.nanoTime());
    }

    public void skipped(Job job) {
        record(job, Outcome.SKIPPED, 0L);
    }

    public void failed(Job job) {
        record(job, Outcome.FAILURE, 0L);
    }

    private void record(Job job, Outcome outcome, long elapsedNanos) {
        counters.get(job).get(outcome).increment();
        timers.get(job).get(outcome).record(Math.max(0L, elapsedNanos), TimeUnit.NANOSECONDS);
        if (outcome == Outcome.SUCCESS) {
            lastSuccess.get(job).set(clock.instant().getEpochSecond());
        }
    }

    public final class Run {
        private final Job job;
        private final long startedAtNanos;
        private final AtomicBoolean completed = new AtomicBoolean();

        private Run(Job job, long startedAtNanos) {
            this.job = job;
            this.startedAtNanos = startedAtNanos;
        }

        public void success() {
            complete(Outcome.SUCCESS);
        }

        public void failure() {
            complete(Outcome.FAILURE);
        }

        private void complete(Outcome outcome) {
            if (completed.compareAndSet(false, true)) {
                record(job, outcome, System.nanoTime() - startedAtNanos);
            }
        }
    }
}
