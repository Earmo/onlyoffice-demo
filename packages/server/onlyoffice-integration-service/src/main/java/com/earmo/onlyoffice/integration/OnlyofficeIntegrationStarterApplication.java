package com.earmo.onlyoffice.integration;

import com.earmo.onlyoffice.integration.service.DocumentStorageService;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 启动入口。
 *
 * <p>Phase 7 之后，这个工程不再把“demo 单体”当作唯一形态，
 * 而是明确收敛为可独立运行、也可被其他系统引用的 starter 服务。
 * 启动时仍保留一份最小引导文档，目的是让独立部署后的服务开箱即可联调。
 */
@SpringBootApplication(excludeName = "com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration")
@ConfigurationPropertiesScan
@MapperScan("com.earmo.onlyoffice.integration.data.mapper")
public class OnlyofficeIntegrationStarterApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlyofficeIntegrationStarterApplication.class, args);
    }

    @Bean
    CommandLineRunner seedBootstrapDocument(DocumentStorageService documentStorageService) {
        return args -> documentStorageService.ensureBootstrapDocument("sample");
    }
}

