package cn.edu.gpnu.platform.security.config;

import cn.edu.gpnu.platform.security.filter.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties({SecurityProperties.class, IdCardProtectionProperties.class})
public class SecurityConfig {

    private static final String[] API_DOCUMENTATION_PATHS = {
            "/doc.html",
            "/swagger-ui",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/**",
            "/webjars",
            "/webjars/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    cn.edu.gpnu.platform.common.api.Result.fail(
                                            cn.edu.gpnu.platform.common.api.ResultCode.UNAUTHORIZED)));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(
                                    cn.edu.gpnu.platform.common.api.Result.fail(
                                            cn.edu.gpnu.platform.common.api.ResultCode.FORBIDDEN)));
                        }))
                .authorizeHttpRequests(auth -> {
                    // StreamingResponseBody 完成时会触发 ASYNC 再分派；权限已在初始 REQUEST 分派完成校验。
                    auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                            .requestMatchers("/api/auth/captcha", "/api/auth/login", "/api/auth/refresh").permitAll()
                            .requestMatchers("/api/health").permitAll();
                    if (production) {
                        auth.requestMatchers(API_DOCUMENTATION_PATHS).denyAll();
                    } else {
                        auth.requestMatchers(API_DOCUMENTATION_PATHS).permitAll();
                    }
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        if (production) {
            // Knife4j UI 来自静态 webjar，不受 springdoc/knife4j enabled 开关控制；在认证链最前端统一返回
            // 404，避免匿名请求因 denyAll 被转换为 401，也避免携带有效 token 时重新暴露技术指纹。
            http.addFilterBefore(new DisabledApiDocumentationFilter(), JwtAuthenticationFilter.class);
        }
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    private static final class DisabledApiDocumentationFilter extends OncePerRequestFilter {

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String path = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
                path = path.substring(contextPath.length());
            }
            return !isApiDocumentationPath(path);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }

        private boolean isApiDocumentationPath(String path) {
            return "/doc.html".equals(path)
                    || "/swagger-ui".equals(path)
                    || "/swagger-ui.html".equals(path)
                    || path.startsWith("/swagger-ui/")
                    || "/v3/api-docs".equals(path)
                    || "/v3/api-docs.yaml".equals(path)
                    || path.startsWith("/v3/api-docs/")
                    || "/webjars".equals(path)
                    || path.startsWith("/webjars/");
        }
    }
}
