package cn.edu.gpnu.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 师范生教育教学能力考核与教师职业能力证书管理平台 启动类。
 * 基础包 cn.edu.gpnu.platform，组件扫描覆盖各模块。
 */
@SpringBootApplication
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
