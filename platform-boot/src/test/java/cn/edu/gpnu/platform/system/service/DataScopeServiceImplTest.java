package cn.edu.gpnu.platform.system.service;

import cn.edu.gpnu.platform.system.mapper.SysRolePermissionMapper;
import cn.edu.gpnu.platform.system.mapper.SysUserDataScopeMapper;
import cn.edu.gpnu.platform.system.service.impl.DataScopeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataScopeServiceImplTest {

    private static final long USER_ID = 81L;
    private static final String PERMISSION = "system:role:manage";

    @Mock
    private SysRolePermissionMapper rolePermissionMapper;
    @Mock
    private SysUserDataScopeMapper userDataScopeMapper;

    @ParameterizedTest
    @MethodSource("systemScopeCases")
    void systemWriteCapabilityRequiresEffectiveSystemScope(List<String> scopes, boolean expected) {
        when(rolePermissionMapper.selectScopeTypes(USER_ID, PERMISSION)).thenReturn(scopes);
        DataScopeServiceImpl service = new DataScopeServiceImpl(rolePermissionMapper, userDataScopeMapper);

        assertThat(service.hasSystemScope(USER_ID, PERMISSION)).isEqualTo(expected);
    }

    @Test
    void systemWriteCapabilityRejectsMissingUser() {
        DataScopeServiceImpl service = new DataScopeServiceImpl(rolePermissionMapper, userDataScopeMapper);

        assertThat(service.hasSystemScope(null, PERMISSION)).isFalse();
        verifyNoInteractions(rolePermissionMapper, userDataScopeMapper);
    }

    @Test
    void systemWriteCapabilityRejectsBlankPermissionInsteadOfUsingTheMapperWildcard() {
        DataScopeServiceImpl service = new DataScopeServiceImpl(rolePermissionMapper, userDataScopeMapper);

        assertThat(service.hasSystemScope(USER_ID, null)).isFalse();
        assertThat(service.hasSystemScope(USER_ID, " ")).isFalse();
        verifyNoInteractions(rolePermissionMapper, userDataScopeMapper);
    }

    private static Stream<Arguments> systemScopeCases() {
        return Stream.of(
                Arguments.of(List.of("SYSTEM"), true),
                Arguments.of(List.of("SYSTEM", "SCHOOL"), true),
                Arguments.of(List.of("SCHOOL", "SYSTEM"), true),
                Arguments.of(List.of("SCHOOL"), false),
                Arguments.of(List.of("LOGIN_ALL"), false),
                Arguments.of(List.of("COLLEGE"), false),
                Arguments.of(List.of("NONE"), false),
                Arguments.of(List.of(), false));
    }
}
