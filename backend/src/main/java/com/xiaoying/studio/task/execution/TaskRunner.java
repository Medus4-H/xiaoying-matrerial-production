package com.xiaoying.studio.task.execution;

import com.xiaoying.studio.common.ErrorCatalog;
import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.common.SafeLog;
import com.xiaoying.studio.task.TaskStage;
import com.xiaoying.studio.task.TaskStateService;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class TaskRunner {
  private final Logger log;
  private final TaskStateService state;
  private final TaskWork work;
  private final TaskMetrics metrics;

  public TaskRunner(TaskStateService state, TaskWork work) {
    this(state, work, LoggerFactory.getLogger(TaskRunner.class), null);
  }

  public TaskRunner(TaskStateService state, TaskWork work, Logger log) {
    this(state, work, log, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public TaskRunner(TaskStateService state, TaskWork work, TaskMetrics metrics) {
    this(state, work, LoggerFactory.getLogger(TaskRunner.class), metrics);
  }

  public TaskRunner(TaskStateService state, TaskWork work, Logger log, TaskMetrics metrics) {
    this.state = state;
    this.work = work;
    this.log = log;
    this.metrics = metrics;
  }

  public void run(String id) {
    run(id, System.nanoTime());
  }

  public void run(String id, long queuedAt) {
    long queueWait = elapsed(queuedAt);
    Map<String, String> previous = MDC.getCopyOfContextMap();
    MDC.clear();
    MDC.put("taskId", id);
    TaskStage current = null;
    boolean claimed = false;
    long taskStart = System.nanoTime();
    long stageStart = taskStart;
    try {
      if (!state.claim(id)) {
        SafeLog.write(
            () ->
                log.atWarn()
                    .addKeyValue("event", "task.claim.lost")
                    .log("Task could not be claimed"));
        return;
      }
      claimed = true;
      SafeLog.write(
          () -> {
            var event =
                log.atInfo()
                    .addKeyValue("event", "task.claimed")
                    .addKeyValue("queueWaitMs", queueWait);
            addMetrics(event);
            event.log("Task claimed");
          });
      for (TaskStage stage : TaskStage.values()) {
        current = null;
        MDC.remove("stage");
        state.start(id, stage);
        current = stage;
        MDC.put("stage", stage.name());
        stageStart = System.nanoTime();
        SafeLog.write(
            () -> {
              var event = log.atInfo().addKeyValue("event", "task.stage.started");
              addMetrics(event);
              event.log("Task stage started");
            });
        String result = work.execute(id, stage);
        if (stage == TaskStage.FINALIZE) state.succeed(id, result);
        else state.complete(id, stage);
        long stageDuration = elapsed(stageStart);
        SafeLog.write(
            () -> {
              var event =
                  log.atInfo()
                      .addKeyValue("event", "task.stage.finished")
                      .addKeyValue("durationMs", stageDuration);
              addMetrics(event);
              event.log("Task stage finished");
            });
      }
      MDC.remove("stage");
      SafeLog.write(
          () ->
              log.atInfo()
                  .addKeyValue("event", "task.succeeded")
                  .addKeyValue("durationMs", elapsed(taskStart))
                  .log("Task succeeded"));
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      ErrorKey key =
          e instanceof TaskExecutionException failure ? failure.key() : ErrorKey.EXECUTION_ERROR;
      String detail =
          e instanceof TaskExecutionException failure
              ? failure.detail()
              : e instanceof InterruptedException ? "INTERRUPTED" : "UNEXPECTED";
      var catalog = ErrorCatalog.instance();
      String code = catalog.definition(key).code();
      String message =
          e instanceof TaskExecutionException
              ? e.getMessage()
              : catalog.message(key, ErrorCatalog.DEFAULT_LOCALE);
      long stageDuration = elapsed(stageStart);
      SafeLog.write(
          () -> {
            var event =
                log.atError()
                    .addKeyValue("event", "task.stage.failed")
                    .addKeyValue("code", code)
                    .addKeyValue("detail", detail)
                    .addKeyValue("durationMs", stageDuration)
                    .addKeyValue("exceptionClass", e.getClass().getName());
            addMetrics(event);
            event.log("Task stage failed");
          });
      try {
        state.fail(id, current, code, message, catalog.definition(key).key(), detail);
        SafeLog.write(
            () ->
                log.atError()
                    .addKeyValue("event", "task.failed")
                    .addKeyValue("code", code)
                    .addKeyValue("detail", detail)
                    .log("Task failed"));
      } catch (Exception updateError) {
        SafeLog.write(
            () ->
                log.atError()
                    .addKeyValue("event", "task.failure_persist_failed")
                    .addKeyValue("exceptionClass", updateError.getClass().getName())
                    .log("Could not persist task failure"));
      }
    } finally {
      if (claimed) {
        try {
          work.cleanup(id);
        } catch (Exception e) {
          SafeLog.write(
              () ->
                  log.atWarn()
                      .addKeyValue("event", "task.cleanup.failed")
                      .addKeyValue("exceptionClass", e.getClass().getName())
                      .log("Could not clean temporary task file"));
        }
      }
      MDC.clear();
      if (previous != null) MDC.setContextMap(previous);
    }
  }

  private long elapsed(long start) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  private void addMetrics(org.slf4j.spi.LoggingEventBuilder event) {
    if (metrics == null) return;
    try {
      metrics.snapshot().forEach(event::addKeyValue);
    } catch (RuntimeException ignored) {
      // Diagnostics must never change task execution.
    }
  }
}
