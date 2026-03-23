package com.earmo.onlyoffice.integration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 相关的最小配置。
 *
 * <p>前端开发时通常跑在 Vite 的 5173 端口，后端跑在 8080，
 * 因此这里放开 /api/** 的跨域访问，降低本地联调门槛。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    // 示例环境优先追求可运行，直接放开 API 跨域；生产环境应收敛到明确白名单。
    registry.addMapping("/api/**")
        .allowedOriginPatterns("*")
        .allowedMethods("*")
        .allowedHeaders("*");
  }
}


