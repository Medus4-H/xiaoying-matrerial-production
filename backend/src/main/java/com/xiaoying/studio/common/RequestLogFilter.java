package com.xiaoying.studio.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class RequestLogFilter extends OncePerRequestFilter {
  public static final String ERROR_CODE_ATTRIBUTE = RequestLogFilter.class.getName() + ".errorCode";
  public static final String ERROR_DETAIL_ATTRIBUTE =
      RequestLogFilter.class.getName() + ".errorDetail";
  private final Logger log;
  private static final Set<String> METHODS =
      Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
  private final long slowMs;

  @org.springframework.beans.factory.annotation.Autowired
  public RequestLogFilter(@Value("${studio.logging.slow-request-ms:500}") long slowMs) {
    this(slowMs, LoggerFactory.getLogger(RequestLogFilter.class));
  }

  public RequestLogFilter(long slowMs, Logger log) {
    this.slowMs = Math.max(0, slowMs);
    this.log = log;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Map<String, String> previous = MDC.getCopyOfContextMap();
    String requestId = UUID.randomUUID().toString();
    long start = System.nanoTime();
    MDC.put("requestId", requestId);
    response.setHeader("X-Request-Id", requestId);
    try {
      String path = request.getRequestURI().toLowerCase(java.util.Locale.ROOT);
      if (path.contains("..") || path.contains("%2e")) {
        SafeLog.write(
            () ->
                log.atWarn()
                    .addKeyValue("event", "request.invalid_path")
                    .log("Suspicious request path"));
      }
      chain.doFilter(request, response);
    } finally {
      long elapsed = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      boolean read = "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
      if (response.getStatus() >= 400
          || (!read && response.getStatus() < 400)
          || elapsed >= slowMs) {
        Object matched = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = matched == null ? "unmatched" : matched.toString();
        String method = METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER";
        Object errorCode = request.getAttribute(ERROR_CODE_ATTRIBUTE);
        Object errorDetail = request.getAttribute(ERROR_DETAIL_ATTRIBUTE);
        SafeLog.write(
            () ->
                log.atInfo()
                    .addKeyValue("event", "http.request")
                    .addKeyValue("method", method)
                    .addKeyValue("route", route)
                    .addKeyValue("status", response.getStatus())
                    .addKeyValue("durationMs", elapsed)
                    .addKeyValue("code", errorCode == null ? "" : errorCode.toString())
                    .addKeyValue("detail", errorDetail == null ? "" : errorDetail.toString())
                    .log("HTTP request completed"));
      }
      MDC.clear();
      if (previous != null) MDC.setContextMap(previous);
    }
  }
}
