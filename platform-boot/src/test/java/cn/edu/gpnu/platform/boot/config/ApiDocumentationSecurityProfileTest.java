package cn.edu.gpnu.platform.boot.config;

import cn.edu.gpnu.platform.security.config.SecurityConfig;
import cn.edu.gpnu.platform.security.filter.JwtAuthenticationFilter;
import cn.edu.gpnu.platform.security.service.JwtService;
import cn.edu.gpnu.platform.security.service.TokenRevocationService;
import cn.edu.gpnu.platform.system.service.UserSecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 生产 profile 必须在安全链入口关闭 Knife4j/springdoc 的完整公开面；测试使用自然退出的
 * MockMvc WebApplicationContext，不启动端口或外部依赖。
 */
class ApiDocumentationSecurityProfileTest {

    private static final List<String> DOCUMENTATION_PATHS = List.of(
            "/doc.html",
            "/webjars",
            "/webjars/knife4j.css",
            "/swagger-ui",
            "/swagger-ui/index.html",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/v3/api-docs.yaml",
            "/v3/api-docs/system"
    );

    @Test
    void productionProfileReturnsNotFoundForEveryDocumentationSurface() throws Exception {
        try (AnnotationConfigWebApplicationContext context = applicationContext("prod")) {
            MockMvc mvc = mockMvc(context);

            for (String path : DOCUMENTATION_PATHS) {
                mvc.perform(get(path))
                        .andExpect(status().isNotFound());
            }
        }
    }

    @Test
    void developmentProfileKeepsDocumentationSurfacePublic() throws Exception {
        try (AnnotationConfigWebApplicationContext context = applicationContext("dev")) {
            MockMvc mvc = mockMvc(context);

            for (String path : DOCUMENTATION_PATHS) {
                mvc.perform(get(path))
                        .andExpect(status().isOk());
            }
        }
    }

    private AnnotationConfigWebApplicationContext applicationContext(String profile) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles(profile);
        context.register(SecurityConfig.class, DocumentationSurfaceFixture.class);
        context.refresh();
        return context;
    }

    private MockMvc mockMvc(AnnotationConfigWebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class))
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class DocumentationSurfaceFixture {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(ObjectMapper objectMapper) {
            return new JwtAuthenticationFilter(
                    mock(JwtService.class),
                    mock(UserSecurityService.class),
                    objectMapper,
                    mock(TokenRevocationService.class));
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(List.of("http://localhost"));
            configuration.setAllowedMethods(List.of("GET"));
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }

        @Bean
        DocumentationSurfaceController documentationSurfaceController() {
            return new DocumentationSurfaceController();
        }
    }

    @RestController
    static class DocumentationSurfaceController {

        @GetMapping({
                "/doc.html",
                "/webjars",
                "/webjars/knife4j.css",
                "/swagger-ui",
                "/swagger-ui/index.html",
                "/swagger-ui.html",
                "/v3/api-docs",
                "/v3/api-docs.yaml",
                "/v3/api-docs/system"
        })
        String documentation() {
            return "documentation";
        }
    }
}
