package cn.edu.gpnu.platform.statistics.service.impl;

import cn.edu.gpnu.platform.exchange.entity.ImportExportBatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatsBatchScopeGuardTest {

    @Test
    void nonSchoolScopeOnlySeesOwnBatchAndNeverMatchesScopeJsonSubstring() {
        ImportExportBatch batch = new ImportExportBatch();
        batch.setOperatorId(200L);
        batch.setScopeJson("{\"collegeId\":10,\"keyword\":\"1\"}");

        assertThat(StatsServiceImpl.batchVisible(false, 100L, batch)).isFalse();
        assertThat(StatsServiceImpl.batchVisible(false, 200L, batch)).isTrue();
        assertThat(StatsServiceImpl.batchVisible(true, 100L, batch)).isTrue();
    }
}
