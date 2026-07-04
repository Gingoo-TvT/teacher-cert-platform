package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.common.api.PageResult;
import cn.edu.gpnu.platform.system.dto.AuditLogQuery;
import cn.edu.gpnu.platform.system.dto.BackupTriggerRequest;
import cn.edu.gpnu.platform.system.dto.SysParamUpdateRequest;
import cn.edu.gpnu.platform.system.vo.AuditLogVO;
import cn.edu.gpnu.platform.system.vo.BackupRecordVO;
import cn.edu.gpnu.platform.system.vo.SysParamVO;

public interface SystemManagementService {

    PageResult<SysParamVO> params(String group, String keyword, Integer page, Integer size);

    SysParamVO updateParam(Long id, SysParamUpdateRequest request);

    PageResult<AuditLogVO> auditLogs(AuditLogQuery query);

    void rejectAuditDelete(Long id);

    PageResult<BackupRecordVO> backups(String status, Integer page, Integer size);

    BackupRecordVO triggerBackup(BackupTriggerRequest request);
}
