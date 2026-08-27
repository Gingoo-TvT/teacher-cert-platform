package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.system.cache.ReferenceCacheInvalidator;
import cn.edu.gpnu.platform.system.config.CacheConfig;
import cn.edu.gpnu.platform.system.dto.SysParamUpdateRequest;
import cn.edu.gpnu.platform.system.entity.SysParam;
import cn.edu.gpnu.platform.system.mapper.AuditQueryMapper;
import cn.edu.gpnu.platform.system.mapper.BackupRecordMapper;
import cn.edu.gpnu.platform.system.mapper.SysParamMapper;
import cn.edu.gpnu.platform.system.service.AuditLogService;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemManagementServiceImplTest {

    private static final long PARAM_ID = 340000000000000001L;

    @Mock
    private SysParamMapper paramMapper;
    @Mock
    private AuditQueryMapper auditQueryMapper;
    @Mock
    private BackupRecordMapper backupRecordMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DatabaseBackupService databaseBackupService;
    @Mock
    private ReferenceCacheInvalidator referenceCacheInvalidator;

    @InjectMocks
    private SystemManagementServiceImpl service;

    @Test
    void backupRetentionDaysAcceptsPositiveAdminUpdate() {
        SysParam param = backupRetentionParam();
        when(paramMapper.selectById(PARAM_ID)).thenReturn(param);

        assertThat(service.updateParam(PARAM_ID, request("45")).getParamValue()).isEqualTo("45");

        verify(paramMapper).updateById(param);
        verify(referenceCacheInvalidator).clearAfterCompletion(CacheConfig.SYS_PARAM);
    }

    @Test
    void backupRetentionDaysRejectsZero() {
        when(paramMapper.selectById(PARAM_ID)).thenReturn(backupRetentionParam());

        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("0")))
                .isInstanceOf(BizException.class)
                .hasMessage("备份保留天数必须大于0");

        verify(paramMapper, never()).updateById(any(SysParam.class));
    }

    @Test
    void backupRetentionDaysRejectsNegativeValue() {
        when(paramMapper.selectById(PARAM_ID)).thenReturn(backupRetentionParam());

        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("-1")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能为负数");

        verify(paramMapper, never()).updateById(any(SysParam.class));
    }

    @Test
    void reviewerCountRejectsFewerThanTwoAndUnreasonableUpperBound() {
        when(paramMapper.selectById(PARAM_ID)).thenReturn(param("video.reviewerCount", "int", "video"));

        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("0")))
                .isInstanceOf(BizException.class)
                .hasMessage("视频评审教师人数必须为2到10的整数");
        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("1")))
                .isInstanceOf(BizException.class)
                .hasMessage("视频评审教师人数必须为2到10的整数");
        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("11")))
                .isInstanceOf(BizException.class)
                .hasMessage("视频评审教师人数必须为2到10的整数");

        verify(paramMapper, never()).updateById(any(SysParam.class));
    }

    @Test
    void reviewerCountAcceptsBusinessRangeBoundaries() {
        SysParam param = param("video.reviewerCount", "int", "video");
        when(paramMapper.selectById(PARAM_ID)).thenReturn(param);

        assertThat(service.updateParam(PARAM_ID, request("2")).getParamValue()).isEqualTo("2");
        assertThat(service.updateParam(PARAM_ID, request("10")).getParamValue()).isEqualTo("10");

        verify(paramMapper, times(2)).updateById(param);
    }

    @Test
    void videoDurationTargetRequiresPositiveValueWithinOneDay() {
        SysParam param = param("video.durationTarget", "int", "video");
        when(paramMapper.selectById(PARAM_ID)).thenReturn(param);

        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("0")))
                .isInstanceOf(BizException.class)
                .hasMessage("视频目标时长必须为1到86400秒的整数");
        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("86401")))
                .isInstanceOf(BizException.class)
                .hasMessage("视频目标时长必须为1到86400秒的整数");
        assertThat(service.updateParam(PARAM_ID, request("1")).getParamValue()).isEqualTo("1");
        assertThat(service.updateParam(PARAM_ID, request("900")).getParamValue()).isEqualTo("900");
        assertThat(service.updateParam(PARAM_ID, request("86400")).getParamValue()).isEqualTo("86400");

        verify(paramMapper, times(3)).updateById(param);
    }

    @Test
    void videoDurationToleranceAllowsZeroButRejectsNegativeAndAboveOneHour() {
        SysParam param = param("video.durationTolerance", "int", "video");
        when(paramMapper.selectById(PARAM_ID)).thenReturn(param);

        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("-1")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能为负数");
        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("3601")))
                .isInstanceOf(BizException.class)
                .hasMessage("视频时长容差必须为0到3600秒的整数");
        assertThat(service.updateParam(PARAM_ID, request("0")).getParamValue()).isEqualTo("0");
        assertThat(service.updateParam(PARAM_ID, request("60")).getParamValue()).isEqualTo("60");
        assertThat(service.updateParam(PARAM_ID, request("3600")).getParamValue()).isEqualTo("3600");

        verify(paramMapper, times(3)).updateById(param);
    }

    @Test
    void certificateCodesRequireTheirConfiguredLengths() {
        when(paramMapper.selectById(PARAM_ID)).thenReturn(param("cert.school.code", "string", "cert"));
        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("1")))
                .isInstanceOf(BizException.class)
                .hasMessage("学校代码必须为5位数字");

        when(paramMapper.selectById(PARAM_ID)).thenReturn(param("cert.province.code", "string", "cert"));
        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("444")))
                .isInstanceOf(BizException.class)
                .hasMessage("省码必须为2位数字");

        verify(paramMapper, never()).updateById(any(SysParam.class));
    }

    @Test
    void scoreThresholdsAcceptZeroAndHundredButRejectAboveScoreScale() {
        SysParam passLine = param("video.passLine", "int", "video");
        when(paramMapper.selectById(PARAM_ID)).thenReturn(passLine);
        assertThat(service.updateParam(PARAM_ID, request("0")).getParamValue()).isEqualTo("0");
        assertThat(service.updateParam(PARAM_ID, request("100")).getParamValue()).isEqualTo("100");
        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("101")))
                .isInstanceOf(BizException.class)
                .hasMessage("视频合格线必须为0到100的整数");

        SysParam diffThreshold = param("video.diffThreshold", "int", "video");
        when(paramMapper.selectById(PARAM_ID)).thenReturn(diffThreshold);
        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("101")))
                .isInstanceOf(BizException.class)
                .hasMessage("视频分差阈值必须为0到100的整数");
    }

    @Test
    void fileSizeLimitRejectsZero() {
        when(paramMapper.selectById(PARAM_ID)).thenReturn(param("file.maxSize.material", "long", "file"));

        assertThatThrownBy(() -> service.updateParam(PARAM_ID, request("0")))
                .isInstanceOf(BizException.class)
                .hasMessage("文件大小上限必须大于0");

        verify(paramMapper, never()).updateById(any(SysParam.class));
    }

    private static SysParam backupRetentionParam() {
        SysParam param = param("cleanup.backup.retentionDays", "int", "global");
        param.setParamValue("30");
        param.setDescription("数据库备份对象与终态记录共享保留天数（必须大于0）");
        return param;
    }

    private static SysParam param(String key, String type, String group) {
        SysParam param = new SysParam();
        param.setId(PARAM_ID);
        param.setParamKey(key);
        param.setParamValue("2");
        param.setParamType(type);
        param.setParamGroup(group);
        param.setEditable(1);
        return param;
    }

    private static SysParamUpdateRequest request(String value) {
        SysParamUpdateRequest request = new SysParamUpdateRequest();
        request.setParamValue(value);
        return request;
    }
}
