package com.earmo.onlyoffice.integration.service;

import com.earmo.onlyoffice.integration.config.OnlyofficeIntegrationProperties;
import com.earmo.onlyoffice.integration.model.response.InsertImageResponse;
import com.earmo.onlyoffice.integration.service.impl.OnlyofficeImageServiceImpl;
import com.earmo.onlyoffice.integration.service.impl.OnlyofficeJwtServiceImpl;
import com.earmo.onlyoffice.integration.service.impl.RemoteResourceSecurityServiceImpl;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OnlyofficeImageServiceTest {

    @Test
    void shouldBuildSignedInsertImagePayload() {
        OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
        properties.setInternalBaseUrl("http://internal.example.test");
        properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

        OnlyofficeJwtService jwtService = new OnlyofficeJwtServiceImpl(properties);
        OnlyofficeImageService imageService = new OnlyofficeImageServiceImpl(
                properties,
                jwtService,
                new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
        );

        InsertImageResponse response = imageService.buildInsertImageResponse(
                "demo",
                "https://example.com/assets/logo.png"
        );

        Map<String, Object> payload = response.insertImage();
        assertEquals("add", payload.get("c"));
        assertEquals("png", payload.get("fileType"));
        assertTrue(payload.get("url").toString().contains("http://internal.example.test/api/document-runtime/demo/images/proxy"));
        assertTrue(payload.get("url").toString().contains("sourceUrl=https://example.com/assets/logo.png"));
        assertNotNull(payload.get("token"));
    }

    @Test
    @DisplayName("图片插入和代理都应拒绝回环地址")
    void shouldRejectLoopbackImageUrl() {
        OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
        properties.setInternalBaseUrl("http://internal.example.test");
        properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

        OnlyofficeImageService imageService = new OnlyofficeImageServiceImpl(
                properties,
                new OnlyofficeJwtServiceImpl(properties),
                new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> imageService.buildInsertImageResponse("demo", "http://127.0.0.1/logo.png")
        );

        assertTrue(exception.getMessage().contains("不支持访问内网、回环或保留地址"));
    }

    @Test
    @DisplayName("图片代理应校验媒体类型和响应大小")
    void shouldRejectNonImageProxyResponse() throws Exception {
        OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
        properties.getRemoteResource().setAllowPrivateAddressAccess(true);
        properties.getRemoteResource().setMaxImageBytes(1024);
        properties.setInternalBaseUrl("http://internal.example.test");
        properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

        OnlyofficeImageService imageService = new OnlyofficeImageServiceImpl(
                properties,
                new OnlyofficeJwtServiceImpl(properties),
                new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
        );

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/fake.png", exchange -> {
                byte[] body = "plain-text".getBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(body);
                }
            });
            server.start();

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> imageService.proxyRemoteImage("http://localhost:" + server.getAddress().getPort() + "/fake.png")
            );

            assertTrue(exception.getMessage().contains("不是合法图片类型"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("图片代理应拒绝超过大小上限的响应体")
    void shouldRejectImageProxyResponseWhenResponseExceedsLimit() throws Exception {
        OnlyofficeIntegrationProperties properties = new OnlyofficeIntegrationProperties();
        properties.getRemoteResource().setAllowPrivateAddressAccess(true);
        properties.getRemoteResource().setMaxImageBytes(8);
        properties.setInternalBaseUrl("http://internal.example.test");
        properties.setJwtSecret("onlyoffice-integration-secret-2026-03-09-123456");

        OnlyofficeImageService imageService = new OnlyofficeImageServiceImpl(
                properties,
                new OnlyofficeJwtServiceImpl(properties),
                new RemoteResourceSecurityServiceImpl(properties, RestClient.builder())
        );

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            server.createContext("/large.png", exchange -> {
                byte[] body = "0123456789".getBytes();
                exchange.getResponseHeaders().add("Content-Type", "image/png");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(body);
                }
            });
            server.start();

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> imageService.proxyRemoteImage("http://localhost:" + server.getAddress().getPort() + "/large.png")
            );

            assertTrue(exception.getMessage().contains("响应超过大小限制"));
        } finally {
            server.stop(0);
        }
    }
}


