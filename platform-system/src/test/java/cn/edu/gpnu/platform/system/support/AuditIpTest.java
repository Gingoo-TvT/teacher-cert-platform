package cn.edu.gpnu.platform.system.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditIpTest {

    @Test
    void forgedForwardedHeadersFromUntrustedPeerAreIgnored() {
        AuditIp auditIp = new AuditIp("127.0.0.1");
        MockHttpServletRequest request = requestFrom("203.0.113.27");
        request.addHeader("X-Forwarded-For", "198.51.100.19");
        request.addHeader("X-Real-IP", "198.51.100.20");

        assertThat(auditIp.clientIp(request)).isEqualTo("203.0.113.27");
    }

    @Test
    void trustedProxyUsesCanonicalClientLiteral() {
        AuditIp auditIp = new AuditIp("127.0.0.1");
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.addHeader("X-Forwarded-For", "2001:0db8:0:0:0:0:0:1");

        assertThat(auditIp.clientIp(request)).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void trustedProxyChainSkipsKnownHopsFromRightToLeft() {
        AuditIp auditIp = new AuditIp("10.0.0.2,10.0.0.3");
        MockHttpServletRequest request = requestFrom("10.0.0.3");
        request.addHeader("X-Forwarded-For", "198.51.100.19, 10.0.0.2");

        assertThat(auditIp.clientIp(request)).isEqualTo("198.51.100.19");
    }

    @Test
    void duplicateForwardedHeaderFallsBackToDirectTrustedProxy() {
        AuditIp auditIp = new AuditIp("127.0.0.1");
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.19");
        request.addHeader("X-Forwarded-For", "198.51.100.20");

        assertThat(auditIp.clientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void realIpIsUsedOnlyWhenForwardedHeaderIsAbsent() {
        AuditIp auditIp = new AuditIp("127.0.0.1");
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.addHeader("X-Real-IP", "198.51.100.20");

        assertThat(auditIp.clientIp(request)).isEqualTo("198.51.100.20");
    }

    @Test
    void overlongForwardedHeaderFallsBackToTrustedProxyAddress() {
        AuditIp auditIp = new AuditIp("127.0.0.1");
        MockHttpServletRequest request = requestFrom("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.19," + "9".repeat(600));
        request.addHeader("X-Real-IP", "198.51.100.20");

        assertThat(auditIp.clientIp(request)).isEqualTo("127.0.0.1");
        assertThat(auditIp.clientIp(request)).hasSizeLessThanOrEqualTo(45);
    }

    @Test
    void malformedTrustedProxyConfigurationFailsClosedAtStartup() {
        assertThatThrownBy(() -> new AuditIp("proxy.internal"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IPv4/IPv6");
    }

    private MockHttpServletRequest requestFrom(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
