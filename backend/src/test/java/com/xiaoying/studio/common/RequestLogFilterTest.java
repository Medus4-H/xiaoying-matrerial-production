package com.xiaoying.studio.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLogFilterTest {
  @Test
  void requestCompletesAndRestoresContextWhenLoggingThrows() throws Exception {
    Logger logger = mock(Logger.class);
    when(logger.atInfo()).thenThrow(new IllegalStateException("logger unavailable"));
    var filter = new RequestLogFilter(500, logger);
    var request = new MockHttpServletRequest("POST", "/api/session");
    var response = new MockHttpServletResponse();
    MDC.put("requestId", "previous");
    try {
      filter.doFilter(request, response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getHeader("X-Request-Id")).isNotBlank().isNotEqualTo("previous");
      assertThat(MDC.get("requestId")).isEqualTo("previous");
    } finally {
      MDC.clear();
    }
  }
}
