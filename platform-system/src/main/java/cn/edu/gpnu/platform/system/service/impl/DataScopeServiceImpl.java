package cn.edu.gpnu.platform.system.service.impl;

import cn.edu.gpnu.platform.common.context.DataScopeContext;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.system.mapper.SysRolePermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserDataScopeMapper;
import cn.edu.gpnu.platform.system.service.DataScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScopeService {

    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserDataScopeMapper userDataScopeMapper;

    @Override
    public DataScopeContext.Scope resolve(String permissionCode) {
        UserContext.CurrentUser current = UserContext.get();
        DataScopeContext.Scope scope = new DataScopeContext.Scope();
        if (current == null || current.getUserId() == null) {
            scope.setScopeType(DataScopeContext.ScopeType.NONE);
            return scope;
        }
        scope.setUserId(current.getUserId());
        scope.setCollegeId(current.getCollegeId());
        scope.setStudentId(current.getStudentId());
        List<String> scopeTypes = rolePermissionMapper.selectScopeTypes(current.getUserId(), permissionCode);
        scope.setScopeType(bestScope(scopeTypes));
        if (scope.getScopeType() == DataScopeContext.ScopeType.COLLEGE) {
            LinkedHashSet<Long> collegeIds = new LinkedHashSet<>(userDataScopeMapper.selectCollegeIds(current.getUserId()));
            if (current.getCollegeId() != null) {
                collegeIds.add(current.getCollegeId());
            }
            scope.setCollegeIds(collegeIds);
            scope.setMajorIds(new LinkedHashSet<>(userDataScopeMapper.selectMajorIds(current.getUserId())));
        }
        return scope;
    }

    @Override
    public boolean hasAllSchoolScope(Long userId, String permissionCode) {
        if (userId == null) {
            return false;
        }
        DataScopeContext.ScopeType scopeType = bestScope(
                rolePermissionMapper.selectScopeTypes(userId, permissionCode));
        return scopeType == DataScopeContext.ScopeType.SCHOOL
                || scopeType == DataScopeContext.ScopeType.SYSTEM
                || scopeType == DataScopeContext.ScopeType.LOGIN_ALL;
    }

    @Override
    public boolean hasSystemScope(Long userId, String permissionCode) {
        if (userId == null || !StringUtils.hasText(permissionCode)) {
            return false;
        }
        List<String> scopeTypes = rolePermissionMapper.selectScopeTypes(userId, permissionCode);
        return scopeTypes != null && scopeTypes.stream().anyMatch("SYSTEM"::equals);
    }

    private DataScopeContext.ScopeType bestScope(List<String> values) {
        if (values == null || values.isEmpty()) {
            return DataScopeContext.ScopeType.NONE;
        }
        for (String value : values) {
            if ("SYSTEM".equals(value)) {
                return DataScopeContext.ScopeType.SYSTEM;
            }
            if ("SCHOOL".equals(value)) {
                return DataScopeContext.ScopeType.SCHOOL;
            }
            if ("LOGIN_ALL".equals(value)) {
                return DataScopeContext.ScopeType.LOGIN_ALL;
            }
            if ("COLLEGE".equals(value)) {
                return DataScopeContext.ScopeType.COLLEGE;
            }
            if ("SELF".equals(value)) {
                return DataScopeContext.ScopeType.SELF;
            }
            if ("ASSIGNED".equals(value)) {
                return DataScopeContext.ScopeType.ASSIGNED;
            }
        }
        return DataScopeContext.ScopeType.NONE;
    }
}
