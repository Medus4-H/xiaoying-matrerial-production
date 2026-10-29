package com.xiaoying.studio.diagnostics;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "studio.diagnostics", name = "enabled", havingValue = "true")
public class DiagnosticEvents extends UnsynchronizedAppenderBase<ILoggingEvent> {
  private static final Set<String> EVENTS =
      Set.of(
          "http.request",
          "request.invalid_path",
          "task.created",
          "task.dispatched",
          "task.dispatch.rejected",
          "task.claimed",
          "task.claim.lost",
          "task.stage.started",
          "task.stage.finished",
          "task.stage.failed",
          "task.succeeded",
          "task.failed",
          "task.failure_persist_failed",
          "task.cleanup.failed",
          "task.interrupted_on_startup");
  private static final Set<String> NUMBERS =
      Set.of(
          "status",
          "durationMs",
          "queueWaitMs",
          "queueDepth",
          "activeThreads",
          "globalHeapUsedBytes",
          "globalHeapCommittedBytes",
          "globalHeapMaxBytes",
          "globalGcCount",
          "globalGcTimeMs");
  private final ArrayDeque<Map<String, Object>> events = new ArrayDeque<>();
  private final ReentrantLock lock = new ReentrantLock();
  private final DiagnosticProperties properties;
  private final ObjectMapper json;
  private final Environment environment;
  private Logger parent;

  public DiagnosticEvents(
      DiagnosticProperties properties, ObjectMapper json, Environment environment) {
    this.properties = properties;
    this.json = json;
    this.environment = environment;
  }

  @PostConstruct
  public void attach() {
    var profiles = Set.of(environment.getActiveProfiles());
    if (!profiles.contains("demo")
        || !profiles.contains("diagnostics")
        || profiles.contains("prod")
        || profiles.contains("production")
        || properties.secret() == null
        || properties.secret().length() < 32
        || properties.secret().length() > 256
        || properties.capacity() < 10
        || properties.capacity() > 5000
        || properties.maxEventBytes() < 256
        || properties.maxEventBytes() > 8192
        || properties.sessionTtlSeconds() < 60
        || properties.sessionTtlSeconds() > 3600) {
      throw new IllegalStateException("Invalid development diagnostics configuration");
    }
    parent = (Logger) LoggerFactory.getLogger("com.xiaoying.studio");
    setContext(parent.getLoggerContext());
    start();
    parent.addAppender(this);
  }

  @PreDestroy
  public void detach() {
    if (parent != null) parent.detachAppender(this);
    stop();
  }

  @Override
  protected void append(ILoggingEvent event) {
    try {
      Map<String, Object> pairs = new LinkedHashMap<>();
      if (event.getKeyValuePairs() != null)
        event.getKeyValuePairs().forEach(pair -> pairs.put(pair.key, pair.value));
      Object name = pairs.get("event");
      if (!(name instanceof String) || !EVENTS.contains(name)) return;
      Map<String, Object> safe = new LinkedHashMap<>();
      safe.put("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
      safe.put("level", event.getLevel().toString());
      safe.put("event", name);
      addId(safe, "requestId", event.getMDCPropertyMap().get("requestId"));
      Object taskId = pairs.getOrDefault("taskId", event.getMDCPropertyMap().get("taskId"));
      addId(safe, "taskId", taskId);
      String stage = event.getMDCPropertyMap().get("stage");
      if (Set.of("ASSET_CHECK", "GENERATE", "FINALIZE").contains(stage == null ? "" : stage))
        safe.put("stage", stage);
      for (String key : NUMBERS) if (pairs.get(key) instanceof Number value) safe.put(key, value);
      for (String key : List.of("code", "detail", "method")) {
        Object value = pairs.get(key);
        if (value instanceof String s && s.matches("[A-Z_]{1,64}")) safe.put(key, s);
      }
      Object route = pairs.get("route");
      if (route instanceof String s && s.matches("/api/[A-Za-z0-9_/{}/.-]{1,120}"))
        safe.put("route", s);
      if (pairs.get("jvmSamplingEnabled") instanceof Boolean b) safe.put("jvmSamplingEnabled", b);
      if (json.writeValueAsBytes(safe).length > properties.maxEventBytes() || !lock.tryLock())
        return;
      try {
        if (events.size() == properties.capacity()) events.removeFirst();
        events.addLast(Map.copyOf(safe));
      } finally {
        lock.unlock();
      }
    } catch (Exception ignored) {
      // Diagnostics are best effort; never affect logging or business operations.
    }
  }

  private void addId(Map<String, Object> safe, String key, Object value) {
    if (value instanceof String s
        && s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      safe.put(key, s);
  }

  public List<Map<String, Object>> find(
      String requestId, String taskId, String level, Instant since) {
    lock.lock();
    try {
      var result = new ArrayList<Map<String, Object>>();
      for (var event : events) {
        if (requestId != null && !requestId.equals(event.get("requestId"))) continue;
        if (taskId != null && !taskId.equals(event.get("taskId"))) continue;
        if (level != null && !level.equals(event.get("level"))) continue;
        if (since != null && Instant.parse((String) event.get("timestamp")).isBefore(since))
          continue;
        result.add(event);
      }
      return result;
    } finally {
      lock.unlock();
    }
  }
}
