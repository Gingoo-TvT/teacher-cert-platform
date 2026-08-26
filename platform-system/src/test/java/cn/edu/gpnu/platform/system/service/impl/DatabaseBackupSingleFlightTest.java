package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.config.DatabaseBackupProperties;
import cn.edu.gpnu.platform.system.entity.BackupRecord;
import cn.edu.gpnu.platform.system.mapper.BackupRecordMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseBackupSingleFlightTest {

    @Test
    void contenderIsRejectedBeforeCreatingRunningRecord() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet result = mock(ResultSet.class);
        BackupRecordMapper recordMapper = mock(BackupRecordMapper.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement("SELECT GET_LOCK(?, 0)")).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(result);
        when(result.next()).thenReturn(true);
        when(result.getInt(1)).thenReturn(0);

        DatabaseBackupService service = new DatabaseBackupService(
                dataSource,
                mock(MinioClient.class),
                recordMapper,
                mock(TransactionTemplate.class),
                new DatabaseBackupProperties());

        assertThatThrownBy(() -> service.backup("full", null, null, 1L))
                .isInstanceOf(BizException.class)
                .hasMessage("已有备份任务正在运行");

        verify(statement).setString(1, "teacher-cert-platform:database-backup");
        verify(recordMapper, never()).insert(any(BackupRecord.class));
    }

    @Test
    void releaseFailureAbortsThePooledPhysicalSession() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(connection.prepareStatement("SELECT RELEASE_LOCK(?)"))
                .thenThrow(new SQLException("connection lost"));
        DatabaseBackupService service = new DatabaseBackupService(
                dataSource,
                mock(MinioClient.class),
                mock(BackupRecordMapper.class),
                mock(TransactionTemplate.class),
                new DatabaseBackupProperties());

        service.releaseBackupLock(connection);

        verify(connection).abort(any(Executor.class));
    }
}
