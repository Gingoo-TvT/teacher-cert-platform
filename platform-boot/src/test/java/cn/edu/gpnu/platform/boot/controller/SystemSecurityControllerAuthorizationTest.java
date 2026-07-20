package cn.edu.gpnu.platform.boot.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class SystemSecurityControllerAuthorizationTest {

    @Test
    void permissionTreeIsReadableByPermissionOrRoleManagers() throws NoSuchMethodException {
        Method endpoint = SystemSecurityController.class.getDeclaredMethod("permissionTree");

        PreAuthorize authorization = endpoint.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo(
                "@pms.has('system:perm:manage') or @pms.has('system:role:manage')");
    }
}
