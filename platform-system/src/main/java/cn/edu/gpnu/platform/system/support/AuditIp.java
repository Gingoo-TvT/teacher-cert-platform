package cn.edu.gpnu.platform.system.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 审计客户端 IP 解析器。
 *
 * <p>转发头只在请求直接来自显式配置的可信代理 IP 时使用；默认信任集合为空，
 * 因而直连后端的调用者无法用 {@code X-Forwarded-For} 伪造审计来源。</p>
 */
@Component
public final class AuditIp {

    private static final int MAX_FORWARDED_HEADER_LENGTH = 512;
    private static final int MAX_IP_LITERAL_LENGTH = 45;

    private final Set<String> trustedProxyAddresses;

    public AuditIp(@Value("${platform.audit.trusted-proxies:}") String trustedProxies) {
        this.trustedProxyAddresses = parseTrustedProxies(trustedProxies);
    }

    public String clientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        return clientIp(servletAttributes.getRequest());
    }

    String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String remoteIp = normalizeIp(request.getRemoteAddr());
        if (remoteIp == null || !trustedProxyAddresses.contains(remoteIp)) {
            return remoteIp;
        }

        if (request.getHeader("X-Forwarded-For") != null) {
            String forwardedIp = forwardedClient(request);
            return forwardedIp == null ? remoteIp : forwardedIp;
        }
        String realIp = normalizeHeaderIp(singleHeader(request, "X-Real-IP"));
        return realIp == null ? remoteIp : realIp;
    }

    private String forwardedClient(HttpServletRequest request) {
        String forwarded = singleHeader(request, "X-Forwarded-For");
        if (!StringUtils.hasText(forwarded) || forwarded.length() > MAX_FORWARDED_HEADER_LENGTH) {
            return null;
        }
        String[] hops = forwarded.split(",", -1);
        for (int index = hops.length - 1; index >= 0; index--) {
            String hop = normalizeIp(hops[index]);
            if (hop == null) {
                return null;
            }
            if (!trustedProxyAddresses.contains(hop)) {
                return hop;
            }
        }
        return null;
    }

    private String normalizeHeaderIp(String value) {
        if (!StringUtils.hasText(value) || value.length() > MAX_IP_LITERAL_LENGTH) {
            return null;
        }
        return normalizeIp(value);
    }

    private static String singleHeader(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        if (values == null || !values.hasMoreElements()) {
            return null;
        }
        String value = values.nextElement();
        return values.hasMoreElements() ? null : value;
    }

    private static Set<String> parseTrustedProxies(String value) {
        if (!StringUtils.hasText(value)) {
            return Set.of();
        }
        Set<String> addresses = new LinkedHashSet<>();
        Arrays.stream(value.split(",", -1)).forEach(candidate -> {
            String normalized = normalizeIp(candidate);
            if (normalized == null) {
                throw new IllegalArgumentException("platform.audit.trusted-proxies 只能包含 IPv4/IPv6 字面量");
            }
            addresses.add(normalized);
        });
        return Set.copyOf(addresses);
    }

    static String normalizeIp(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        if (text.length() > MAX_IP_LITERAL_LENGTH) {
            return null;
        }
        if (text.indexOf(':') < 0) {
            return normalizeIpv4(text);
        }
        if (!text.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(text).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private static String normalizeIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        int[] octets = new int[4];
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                return null;
            }
            int octet;
            try {
                octet = Integer.parseInt(part);
            } catch (NumberFormatException ignored) {
                return null;
            }
            if (octet > 255) {
                return null;
            }
            octets[index] = octet;
        }
        return octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
    }
}
