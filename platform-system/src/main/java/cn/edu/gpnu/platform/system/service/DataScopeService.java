package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.common.context.DataScopeContext;

public interface DataScopeService {

    DataScopeContext.Scope resolve(String permissionCode);

    boolean hasAllSchoolScope(Long userId, String permissionCode);
}
