package com.xiaoying.studio.diagnostics;

import com.xiaoying.studio.common.ApiError;
import com.xiaoying.studio.common.ErrorCatalog;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.common.RequestLogFilter;
import com.xiaoying.studio.identity.SessionProperties;
import com.xiaoying.studio.task.execution.TaskMetrics;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnostics")
@ConditionalOnProperty(prefix = "studio.diagnostics", name = "enabled", havingValue = "true")
public class DiagnosticController {
  private static final String COOKIE = "studio_diagnostics";
  private static final int MAX_SESSIONS = 20;
  private static final int MAX_ATTEMPTS = 10;
  private static final long ATTEMPT_WINDOW_SECONDS = 60;
  private final DiagnosticProperties properties;
  private final SessionProperties sessions;
  private final DiagnosticEvents events;
  private final TaskMetrics metrics;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Instant> tokens = new HashMap<>();
  private final ArrayDeque<Instant> attempts = new ArrayDeque<>();

  public DiagnosticController(
      DiagnosticProperties properties,
      SessionProperties sessions,
      DiagnosticEvents events,
      TaskMetrics metrics) {
    this.properties = properties;
    this.sessions = sessions;
    this.events = events;
    this.metrics = metrics;
  }

  public record Login(String secret) {}

  @PostMapping(value = "/session", consumes = MediaType.APPLICATION_JSON_VALUE)
  public synchronized ResponseEntity<?> login(
      @RequestBody Login input, HttpServletRequest request) {
    if (!allowedOrigin(request)) return error(ErrorKey.ORIGIN_DENIED, request);
    Instant now = Instant.now();
    while (!attempts.isEmpty()
        && attempts.peekFirst().isBefore(now.minusSeconds(ATTEMPT_WINDOW_SECONDS)))
      attempts.removeFirst();
    if (attempts.size() >= MAX_ATTEMPTS) return error(ErrorKey.DIAGNOSTIC_RATE_LIMITED, request);
    attempts.addLast(now);
    String supplied =
        input == null || input.secret() == null || input.secret().length() > 256
            ? ""
            : input.secret();
    if (!MessageDigest.isEqual(hash(supplied), hash(properties.secret())))
      return error(ErrorKey.DIAGNOSTIC_AUTH_FAILED, request);
    attempts.clear();
    tokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    if (tokens.size() >= MAX_SESSIONS) return error(ErrorKey.DIAGNOSTIC_RATE_LIMITED, request);
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    tokens.put(
        Base64.getEncoder().encodeToString(hash(token)),
        now.plusSeconds(properties.sessionTtlSeconds()));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.SET_COOKIE, cookie(token, properties.sessionTtlSeconds()))
        .body(Map.of("authenticated", true));
  }

  @PostMapping("/logout")
  public synchronized ResponseEntity<?> logout(HttpServletRequest request) {
    if (!allowedOrigin(request)) return error(ErrorKey.ORIGIN_DENIED, request);
    String token = token(request);
    if (token != null) tokens.remove(Base64.getEncoder().encodeToString(hash(token)));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.SET_COOKIE, cookie("", 0))
        .body(Map.of("authenticated", false));
  }

  @GetMapping("/session")
  public ResponseEntity<?> session(HttpServletRequest request) {
    return authorized(request)
        ? response(HttpStatus.OK, Map.of("authenticated", true))
        : error(ErrorKey.DIAGNOSTIC_SESSION_REQUIRED, request);
  }

  @GetMapping("/metrics")
  public ResponseEntity<?> metrics(HttpServletRequest request) {
    return authorized(request)
        ? response(HttpStatus.OK, metrics.snapshot())
        : error(ErrorKey.DIAGNOSTIC_SESSION_REQUIRED, request);
  }

  @GetMapping("/events")
  public ResponseEntity<?> events(
      HttpServletRequest request,
      @RequestParam(required = false) String requestId,
      @RequestParam(required = false) String taskId,
      @RequestParam(required = false) String level,
      @RequestParam(required = false) String since) {
    if (!authorized(request)) return error(ErrorKey.DIAGNOSTIC_SESSION_REQUIRED, request);
    if (!validId(requestId)
        || !validId(taskId)
        || (level != null && !Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR").contains(level)))
      return error(ErrorKey.INVALID_REQUEST, request);
    Instant timestamp;
    try {
      timestamp = since == null ? null : Instant.parse(since);
    } catch (RuntimeException error) {
      return error(ErrorKey.INVALID_REQUEST, request);
    }
    return response(
        HttpStatus.OK, Map.of("events", events.find(requestId, taskId, level, timestamp)));
  }

  private boolean validId(String id) {
    return id == null
        || id.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  }

  private boolean allowedOrigin(HttpServletRequest request) {
    String origin = request.getHeader("Origin");
    return origin != null && sessions.allowedOrigins().contains(origin);
  }

  private synchronized boolean authorized(HttpServletRequest request) {
    String value = token(request);
    if (value == null) return false;
    String key = Base64.getEncoder().encodeToString(hash(value));
    Instant expiry = tokens.get(key);
    if (expiry == null) return false;
    if (!expiry.isAfter(Instant.now())) {
      tokens.remove(key);
      return false;
    }
    return true;
  }

  private String token(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) return null;
    for (Cookie cookie : cookies)
      if (COOKIE.equals(cookie.getName()) && cookie.getValue().matches("[A-Za-z0-9_-]{43}"))
        return cookie.getValue();
    return null;
  }

  private String cookie(String value, long age) {
    return ResponseCookie.from(COOKIE, value)
        .httpOnly(true)
        .secure(sessions.secure())
        .sameSite("Strict")
        .path("/api/diagnostics")
        .maxAge(age)
        .build()
        .toString();
  }

  private byte[] hash(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private ResponseEntity<?> error(ErrorKey key, HttpServletRequest request) {
    var catalog = ErrorCatalog.instance();
    var definition = catalog.definition(key);
    request.setAttribute(RequestLogFilter.ERROR_CODE_ATTRIBUTE, definition.code());
    String id = org.slf4j.MDC.get("requestId");
    if (id == null) id = java.util.UUID.randomUUID().toString();
    return response(
        HttpStatus.valueOf(definition.status()),
        new ApiError(
            definition.code(),
            catalog.message(key, request.getHeader("Accept-Language")),
            null,
            id,
            definition.key()));
  }

  private ResponseEntity<?> response(HttpStatus status, Object body) {
    return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
  }
}
