package com.xiaoying.studio.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.xiaoying.studio.task.execution.TaskMetrics;
import com.xiaoying.studio.task.execution.TaskRunner;
import com.xiaoying.studio.task.execution.TaskWork;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class TaskLoggingTest {
  @Test
  void metricsFailureDoesNotInterruptTask() throws Exception {
    var state = mock(TaskStateService.class);
    var work = mock(TaskWork.class);
    var metrics = mock(TaskMetrics.class);
    when(state.claim("task")).thenReturn(true);
    when(metrics.snapshot()).thenThrow(new IllegalStateException("sampling failed"));
    when(work.execute("task", TaskStage.FINALIZE)).thenReturn("result.jpg");
    new TaskRunner(state, work, LoggerFactory.getLogger(TaskRunner.class), metrics).run("task");
    verify(state).succeed("task", "result.jpg");
    verify(state, never())
        .fail(anyString(), any(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void concurrentTasksKeepIndependentLogContexts() throws Exception {
    var state = mock(TaskStateService.class);
    var work = mock(TaskWork.class);
    var bothStarted = new java.util.concurrent.CountDownLatch(2);
    when(state.claim(anyString())).thenReturn(true);
    when(work.execute(anyString(), eq(TaskStage.ASSET_CHECK)))
        .thenAnswer(
            invocation -> {
              bothStarted.countDown();
              assertThat(bothStarted.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
              return null;
            });
    when(work.execute(anyString(), eq(TaskStage.FINALIZE))).thenReturn("result.jpg");
    var logger = (Logger) LoggerFactory.getLogger(TaskRunner.class);
    var logs =
        new ListAppender<ILoggingEvent>() {
          @Override
          protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
          }
        };
    logs.start();
    logger.addAppender(logs);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(() -> new TaskRunner(state, work).run("parallel-a"));
      var second = executor.submit(() -> new TaskRunner(state, work).run("parallel-b"));
      first.get(5, java.util.concurrent.TimeUnit.SECONDS);
      second.get(5, java.util.concurrent.TimeUnit.SECONDS);
      for (String id : java.util.List.of("parallel-a", "parallel-b")) {
        var starts =
            logs.list.stream()
                .filter(e -> id.equals(e.getMDCPropertyMap().get("taskId")))
                .filter(
                    e ->
                        e.getKeyValuePairs() != null
                            && e.getKeyValuePairs().stream()
                                .anyMatch(
                                    pair ->
                                        pair.key.equals("event")
                                            && pair.value.equals("task.stage.started")))
                .toList();
        assertThat(starts).hasSize(3);
        assertThat(starts.stream().map(e -> e.getMDCPropertyMap().get("stage")))
            .containsExactly("ASSET_CHECK", "GENERATE", "FINALIZE");
      }
    } finally {
      executor.shutdownNow();
      logger.detachAppender(logs);
      logs.stop();
    }
  }

  @Test
  void loggingFailureDoesNotFailAWorkingTask() throws Exception {
    var state = mock(TaskStateService.class);
    var work = mock(TaskWork.class);
    var logger = mock(org.slf4j.Logger.class);
    when(logger.atInfo()).thenThrow(new IllegalStateException("logger unavailable"));
    when(state.claim("task")).thenReturn(true);
    when(work.execute("task", TaskStage.FINALIZE)).thenReturn("result.jpg");
    new TaskRunner(state, work, logger).run("task");
    verify(state).succeed("task", "result.jpg");
    verify(state, never())
        .fail(anyString(), any(), anyString(), anyString(), anyString(), anyString());
    verify(work).cleanup("task");
  }

  @Test
  void reusedWorkerKeepsTaskContextsSeparateAndDoesNotLogExceptionText() throws Exception {
    var state = mock(TaskStateService.class);
    var work = mock(TaskWork.class);
    when(state.claim(anyString())).thenReturn(true);
    doThrow(new IllegalStateException("private@example.test /private/user-file"))
        .when(work)
        .execute("task-a", TaskStage.GENERATE);
    var logger = (Logger) LoggerFactory.getLogger(TaskRunner.class);
    var logs =
        new ListAppender<ILoggingEvent>() {
          @Override
          protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
          }
        };
    logs.start();
    logger.addAppender(logs);
    try {
      new TaskRunner(state, work).run("task-a");
      new TaskRunner(state, work).run("task-b");
      assertThat(logs.list).isNotEmpty();
      assertThat(logs.list)
          .allSatisfy(
              event -> {
                assertThat(event.getMDCPropertyMap().get("taskId")).isIn("task-a", "task-b");
                assertThat(event.getFormattedMessage())
                    .doesNotContain("private@example.test", "/private/");
                assertThat(event.getThrowableProxy()).isNull();
              });
      assertThat(
              logs.list.stream().filter(e -> "task-b".equals(e.getMDCPropertyMap().get("taskId"))))
          .isNotEmpty()
          .noneMatch(e -> e.getFormattedMessage().contains("failed"));
      assertThat(MDC.get("taskId")).isNull();
      assertThat(MDC.get("stage")).isNull();
    } finally {
      logger.detachAppender(logs);
      logs.stop();
    }
  }
}
