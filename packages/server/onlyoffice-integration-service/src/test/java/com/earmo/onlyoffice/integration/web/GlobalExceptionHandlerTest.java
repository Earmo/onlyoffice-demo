package com.earmo.onlyoffice.integration.web;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void shouldTreatWindowsClientAbortAsDisconnectedClient() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    ResponseEntity<?> result = handler.handleIoException(
        new IOException("你的主机中的软件中止了一个已建立的连接。"),
        response
    );

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(result.getBody()).isNull();
  }

  @Test
  void shouldTreatAsyncRequestNotUsableAsDisconnectedClient() {
    MockHttpServletResponse response = new MockHttpServletResponse();

    ResponseEntity<?> result = handler.handleIoException(
        new AsyncRequestNotUsableException("Response not usable after async request completion."),
        response
    );

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(result.getBody()).isNull();
  }
}
