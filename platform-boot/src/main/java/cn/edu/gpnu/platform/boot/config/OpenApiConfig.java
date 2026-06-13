package cn.edu.gpnu.platform.boot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档（Knife4j / springdoc）。访问路径 /doc.html。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI platformOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("师范生教育教学能力考核与教师职业能力证书管理平台 API")
                .description("后端接口文档")
                .version("1.0.0"));
    }
}
