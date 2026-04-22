package com.earmo.onlyoffice.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
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
  public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
    configurer.setDefaultTimeout(180000L);
    configurer.setTaskExecutor(runtimeSseTaskExecutor());
  }

  @Bean("runtimeSseTaskExecutor")
  public ThreadPoolTaskExecutor runtimeSseTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("runtime-sse-");
    executor.initialize();
    return executor;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // 插件源码统一放在 packages/web/public 下。
    // 开发时优先直接读取前端目录，打包后再回退到 classpath 里的构建产物。
    registry.addResourceHandler("/onlyoffice-plugins/**")
        .addResourceLocations(
            "file:../web/public/onlyoffice-plugins/",
            "classpath:/static/onlyoffice-plugins/");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    // 示例环境优先追求可运行，直接放开 API 跨域；生产环境应收敛到明确白名单。
    registry.addMapping("/api/**")
        .allowedOriginPatterns("*")
        .allowedMethods("*")
        .allowedHeaders("*");

    // ONLYOFFICE Docs 会从自己的 iframe 域名加载桥接插件资源。
    // 本地调试下即使 pluginsData 指向 8080，也允许它跨域拉取 config.json / index.html / code.js。
    registry.addMapping("/onlyoffice-plugins/**")
        .allowedOriginPatterns("*")
        .allowedMethods("GET", "OPTIONS")
        .allowedHeaders("*");
  }
}


