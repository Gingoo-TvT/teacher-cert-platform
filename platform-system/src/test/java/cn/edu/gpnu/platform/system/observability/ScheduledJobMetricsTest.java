package cn.edu.gpnu.platform.system.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledJobMetricsTest {

    private SimpleMeterRegistry registry;
    private ScheduledJobMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new ScheduledJobMetrics(registry);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void recordsRealOutcomesDurationAndLastSuccessWithFixedTags() {
        metrics.start(ScheduledJobMetrics.Job.DATABASE_BACKUP).success();
        metrics.start(ScheduledJobMetrics.Job.RETENTION_PRUNE).failure();
        metrics.skipped(ScheduledJobMetrics.Job.VIDEO_FINALIZATION_RECONCILIATION);

        assertCounter("database_backup", "success", 1.0);
        assertCounter("retention_prune", "failure", 1.0);
        assertCounter("video_finalization_reconciliation", "skipped", 1.0);
        assertThat(registry.find("platform.scheduled.job.duration")
                .tags("job", "database_backup", "outcome", "success").timer().count()).isEqualTo(1L);
        assertThat(registry.find("platform.scheduled.job.last.success.timestamp")
                .tag("job", "database_backup").gauge().value()).isPositive();
        assertThat(registry.find("platform.scheduled.job.last.success.timestamp")
                .tag("job", "retention_prune").gauge().value()).isZero();

        registry.getMeters().stream()
                .filter(meter -> meter.getId().getName().startsWith("platform.scheduled.job."))
                .forEach(meter -> {
                    assertThat(meter.getId().getTags().stream().map(tag -> tag.getKey()))
                            .allMatch(Set.of("job", "outcome")::contains);
                });
    }

    private void assertCounter(String job, String outcome, double expected) {
        assertThat(registry.find("platform.scheduled.job.executions")
                .tags("job", job, "outcome", outcome).counter().count()).isEqualTo(expected);
    }
}
