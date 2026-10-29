package com.xiaoying.studio.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaoying.studio.common.ApiError;
import com.xiaoying.studio.identity.SessionProperties;
import com.xiaoying.studio.task.execution.TaskMetrics;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class DiagnosticsTest {
  private static final String SECRET = "test-only-123456789012345678901234567890";
  private static final String ORIGIN = "http://localhost:3000";
  private final ObjectMapper json = new ObjectMapper();

  private DiagnosticProperties props(long ttl) {
    return new DiagnosticProperties(true, SECRET, 10, 2048, ttl);
  }

  private MockEnvironment environment(String... profiles) {
    var env = new MockEnvironment();
    env.setActiveProfiles(profiles);
    return env;
  }

  private SessionProperties sessions() {
    return new SessionProperties("user", Duration.ofDays(1), false, List.of(ORIGIN));
  }

  private MockHttpServletRequest request(String origin, Cookie... cookies) {
    var request = new MockHttpServletRequest();
    if (origin != null) request.addHeader("Origin", origin);
    request.setCookies(cookies);
    return request;
  }

  @Test
  void validatesDedicatedProfilesAndStrongCredential() {
    assertThatThrownBy(() -> new DiagnosticEvents(props(60), json, environment("demo")).attach())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new DiagnosticEvents(props(60), json, environment("demo", "diagnostics", "prod"))
                    .attach())
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                new DiagnosticEvents(
                        new DiagnosticProperties(true, "short", 10, 2048, 60),
                        json,
                        environment("demo", "diagnostics"))
                    .attach())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void authenticationIsIndependentOriginCheckedAndRevocable() {
    var buffer = mock(DiagnosticEvents.class);
    var metrics = mock(TaskMetrics.class);
    var controller = new DiagnosticController(props(60), sessions(), buffer, metrics);
    var english = request(ORIGIN);
    english.addHeader("Accept-Language", "en");
    var failure = controller.login(new DiagnosticController.Login("wrong"), english);
    assertThat(failure.getBody()).isInstanceOf(ApiError.class);
    assertThat(((ApiError) failure.getBody()).message())
        .isEqualTo("Invalid diagnostic credentials.");
    assertThat(
            controller
                .events(request(ORIGIN, new Cookie("user", "account")), null, null, null, null)
                .getStatusCode()
                .value())
        .isEqualTo(401);
    assertThat(
            controller
                .login(new DiagnosticController.Login(SECRET), request("https://evil.test"))
                .getStatusCode()
                .value())
        .isEqualTo(403);
    assertThat(
            controller
                .login(new DiagnosticController.Login("wrong"), request(ORIGIN))
                .getStatusCode()
                .value())
        .isEqualTo(401);
    var login = controller.login(new DiagnosticController.Login(SECRET), request(ORIGIN));
    String header = login.getHeaders().getFirst("Set-Cookie");
    assertThat(header)
        .contains("HttpOnly", "SameSite=Strict", "Path=/api/diagnostics")
        .doesNotContain(SECRET);
    String token = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    Cookie cookie = new Cookie("studio_diagnostics", token);
    assertThat(controller.session(request(null, cookie)).getStatusCode().value()).isEqualTo(200);
    assertThat(controller.logout(request("https://evil.test", cookie)).getStatusCode().value())
        .isEqualTo(403);
    assertThat(controller.session(request(null, cookie)).getStatusCode().value()).isEqualTo(200);
    controller.logout(request(ORIGIN, cookie));
    assertThat(controller.session(request(null, cookie)).getStatusCode().value()).isEqualTo(401);
    var expiring = new DiagnosticController(props(0), sessions(), buffer, metrics);
    String expiredHeader =
        expiring
            .login(new DiagnosticController.Login(SECRET), request(ORIGIN))
            .getHeaders()
            .getFirst("Set-Cookie");
    Cookie expired =
        new Cookie(
            "studio_diagnostics",
            expiredHeader.substring(expiredHeader.indexOf('=') + 1, expiredHeader.indexOf(';')));
    assertThat(expiring.session(request(null, expired)).getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void bufferAllowsOnlyStructuredFieldsAndEvictsOldest() throws Exception {
    var buffer = new DiagnosticEvents(props(60), json, environment("demo", "diagnostics"));
    buffer.attach();
    try {
      var logger = LoggerFactory.getLogger("com.xiaoying.studio.diagnostics.test");
      logger
          .atInfo()
          .addKeyValue("event", "task.created")
          .addKeyValue("taskId", "11111111-1111-1111-1111-111111111111")
          .addKeyValue("password", SECRET)
          .log("private@example.test /private/file");
      var entries = buffer.find(null, null, null, null);
      assertThat(entries).hasSize(1);
      assertThat(json.writeValueAsString(entries))
          .doesNotContain(SECRET, "private@example.test", "/private/file", "password");
      assertThat(buffer.find(null, "11111111-1111-1111-1111-111111111111", null, null)).hasSize(1);
      for (int i = 0; i < 10; i++)
        logger.atInfo().addKeyValue("event", "task.claimed").log("event");
      assertThat(buffer.find(null, null, null, null))
          .hasSize(10)
          .noneMatch(item -> item.get("event").equals("task.created"));
    } finally {
      buffer.detach();
    }
  }

  @Test
  void jvmMetricsAreOptInAndPoolFailureIsIsolated() {
    var pool = mock(ThreadPoolTaskExecutor.class);
    when(pool.getQueueSize()).thenThrow(new IllegalStateException());
    assertThat(new TaskMetrics(pool, false).snapshot())
        .containsEntry("jvmSamplingEnabled", false)
        .doesNotContainKey("globalHeapUsedBytes");
    assertThat(new TaskMetrics(pool, true).snapshot())
        .containsEntry("jvmSamplingEnabled", true)
        .containsKey("globalHeapUsedBytes");
  }
}
