package com.earmo.onlyoffice.integration.data;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据模块测试启动器。
 *
 * <p>data 模块本身不负责对外启动，因此测试时使用一个最小 Boot 应用来承载
 * mapper 扫描、Flyway 初始化和 repository Bean 注册。
 */
@SpringBootApplication
@MapperScan("com.earmo.onlyoffice.integration.data.mapper")
public class DataModuleTestApplication {
}
