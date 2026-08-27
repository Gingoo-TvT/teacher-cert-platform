package cn.edu.gpnu.platform.security.filter;

import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.context.UserContext;
import cn.edu.gpnu.platform.common.exception.BizException;
import cn.edu.gpnu.platform.security.service.JwtService;
import cn.edu.gpnu.platform.security.service.MediaAccessCookieService;
import cn.edu.gpnu.platform.security.service.TokenRevocationService;
import cn.edu.gpnu.platform.system.service.UserSecurityService;
import cn.edu.gpnu.platform.system.vo.UserSecurityVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserSecurityService userSecurityService;
    private final ObjectMapper objectMapper;
    private final TokenRevocationService tokenRevocationService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return "/actuator/prometheus".equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (token != null) {
                Claims claims;
                try {
                    claims = jwtService.parse(token);
                } catch (BizException e) {
                    unauthorized(response, e.getMessage());
                    return;
                }
                if (!"access".equals(claims.get("typ", String.class))) {
                    unauthorized(response, "token类型不正确");
                    return;
                }
                Long uid = Long.valueOf(claims.getSubject());
                if (tokenRevocationService.isRevoked(
                        uid, claims.getIssuedAt(), jwtService.preciseIssuedAtMillis(claims))) {
                    unauthorized(response, "登录状态已失效，请重新登录");
                    return;
                }
                if (!tokenRevocationService.isCurrentSessionGeneration(
                        uid, jwtService.sessionGeneration(claims))) {
                    unauthorized(response, "登录状态已失效，请重新登录");
                    return;
                }
                UserSecurityVO user = userSecurityService.loadById(uid);
                if (user == null || !"ENABLED".equals(user.getStatus())) {
                    unauthorized(response, "用户不存在或已停用");
                    return;
                }
                if (!jwtService.hasCurrentCredentialVersion(claims, user)) {
                    unauthorized(response, "登录凭据已变更，请重新登录");
                    return;
                }
                bindContext(user);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user.getUsername(), null, user.getPermissions().stream().map(SimpleGrantedAuthority::new).toList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (user.getMustChangePwd() != null && user.getMustChangePwd() == 1
                        && mustBlockForPasswordChange(request)) {
                    forbidden(response, "请先修改初始密码");
                    return;
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            UserContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (!isMediaContentRequest(request) || request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (MediaAccessCookieService.COOKIE_NAME.equals(cookie.getName())
                    && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    static boolean isMediaContentRequest(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return matchesNumericRoute(uri, "/api/video/reviews/", "/content")
                || matchesNumericRoute(uri, "/api/material/preview/", "/content")
                || matchesNumericRoute(uri, "/api/exemption/materials/", "/content");
    }

    private static boolean matchesNumericRoute(String uri, String prefix, String suffix) {
        if (!uri.startsWith(prefix) || !uri.endsWith(suffix)) {
            return false;
        }
        String id = uri.substring(prefix.length(), uri.length() - suffix.length());
        if (id.isEmpty()) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            if (!Character.isDigit(id.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void bindContext(UserSecurityVO user) {
        UserContext.CurrentUser current = new UserContext.CurrentUser();
        current.setUserId(user.getId());
        current.setUsername(user.getUsername());
        current.setRealName(user.getRealName());
        current.setUserType(user.getUserType());
        current.setCollegeId(user.getCollegeId());
        current.setStudentId(user.getStudentId());
        current.setMustChangePwd(user.getMustChangePwd() != null && user.getMustChangePwd() == 1);
        current.setRoles(user.getRoles());
        current.setPermissions(user.getPermissions());
        UserContext.set(current);
    }

    private boolean mustBlockForPasswordChange(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !Set.of("/api/auth/change-pwd", "/api/auth/logout", "/api/auth/me").contains(uri);
    }

    private void unauthorized(HttpServletResponse response, String msg) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, Result.fail(ResultCode.UNAUTHORIZED.getCode(), msg));
    }

    private void forbidden(HttpServletResponse response, String msg) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, Result.fail(ResultCode.FORBIDDEN.getCode(), msg));
    }

    private void write(HttpServletResponse response, int status, Result<?> result) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
