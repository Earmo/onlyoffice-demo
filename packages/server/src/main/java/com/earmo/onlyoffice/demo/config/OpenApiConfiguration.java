package com.earmo.onlyoffice.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一声明 OpenAPI 与 Knife4j 文档信息。
 *
 * <p>这里集中维护接口文档元信息，避免把标题、版本、分组说明散落到各个控制器里。
 * Knife4j 在 Spring Boot 3 场景下底层基于 springdoc-openapi，因此只要提供标准 OpenAPI Bean，
 * 再配合控制器和 DTO 上的 Swagger 注解，就可以在 {@code /doc.html} 中查看完整接口文档。
 */
@Configuration
public class OpenApiConfiguration {

  /**
   * 声明项目级 OpenAPI 文档信息。
   *
   * <p>这里描述的是“文档服务”这个后端系统本身，而不是某个具体接口，
   * 便于后续在多微服务接入时保持文档标题、版本和联系信息的一致性。
   */
  @Bean
  public OpenAPI onlyofficeDemoOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("ONLYOFFICE 文档服务接口")
            .version("v1")
            .description("面向分布式文档编辑场景的后端接口文档，使用 Knife4j 统一展示。")
            .contact(new Contact()
                .name("earmo")
                .url("https://github.com/earmo/onlyoffice-demo")));
  }

  /**
   * 把当前服务对外暴露的 `/api/**` 路由收敛到同一个分组中。
   *
   * <p>这样做的好处是后续即使继续增加管理接口、回调接口或内部运维接口，
   * 也可以通过路径或包分组把文档拆开，而不需要重做整个 Swagger 方案。
   */
  @Bean
  public GroupedOpenApi documentServiceApi() {
    return GroupedOpenApi.builder()
        .group("document-service")
        .pathsToMatch("/api/**")
        .build();
  }
}
