package com.earmo.onlyoffice.demo;

import com.earmo.onlyoffice.demo.service.DocumentStorageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.mybatis.spring.annotation.MapperScan;

/**
 * Spring Boot 启动入口。
 *
 * <p>这个示例项目没有数据库和复杂初始化逻辑，启动时只做一件事：
 * 预先创建一个 demo 文档，保证前端第一次打开页面时就能拿到可编辑文件。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.earmo.onlyoffice.demo.persistence")
public class OnlyofficeDemoApplication {

  public static void main(String[] args) {
    SpringApplication.run(OnlyofficeDemoApplication.class, args);
  }

  @Bean
  CommandLineRunner seedDemoDocument(DocumentStorageService documentStorageService) {
    return args -> documentStorageService.ensureBootstrapDocument("demo");
  }
}
