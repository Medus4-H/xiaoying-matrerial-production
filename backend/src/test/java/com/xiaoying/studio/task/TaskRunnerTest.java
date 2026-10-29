package com.xiaoying.studio.task;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.xiaoying.studio.common.ErrorKey;
import com.xiaoying.studio.task.execution.TaskExecutionException;
import com.xiaoying.studio.task.execution.TaskRunner;
import com.xiaoying.studio.task.execution.TaskWork;
import org.junit.jupiter.api.Test;

class TaskRunnerTest {
  @Test
  void executesStagesInOrderAndCompletes() throws Exception {
    var state = mock(TaskStateService.class);
    var work = mock(TaskWork.class);
    when(state.claim("task")).thenReturn(true);
    when(work.execute("task", TaskStage.FINALIZE)).thenReturn("results/task.jpg");
    new TaskRunner(state, work).run("task");
    var order = inOrder(state, work);
    order.verify(state).claim("task");
    for (TaskStage stage : TaskStage.values()) {
      order.verify(state).start("task", stage);
      order.verify(work).execute("task", stage);
      if (stage == TaskStage.FINALIZE) order.verify(state).succeed("task", "results/task.jpg");
      else order.verify(state).complete("task", stage);
    }
    verify(state, never())
        .fail(anyString(), any(), anyString(), anyString(), anyString(), anyString());
    verify(work).cleanup("task");
  }

  @Test
  void failureStopsFollowingStagesAndRecordsTheFailedStage() throws Exception {
    for (TaskStage failed : TaskStage.values()) {
      var state = mock(TaskStateService.class);
      var work = mock(TaskWork.class);
      when(state.claim("task")).thenReturn(true);
      doThrow(new TaskExecutionException(ErrorKey.SIMULATED_GENERATE))
          .when(work)
          .execute("task", failed);
      new TaskRunner(state, work).run("task");
      verify(state)
          .fail(
              "task",
              failed,
              "SIMULATED_FAILURE",
              "模拟失败：模拟生成",
              "task.simulated_generate",
              "SIMULATED_GENERATE");
      for (TaskStage stage : TaskStage.values()) {
        if (stage.ordinal() > failed.ordinal()) verify(work, never()).execute("task", stage);
      }
      verify(state, never()).succeed(anyString(), anyString());
      verify(work).cleanup("task");
    }
  }

  @Test
  void duplicateClaimDoesNotExecuteAnyStep() throws Exception {
    var state = mock(TaskStateService.class);
    var work = mock(TaskWork.class);
    when(state.claim("task")).thenReturn(false);
    new TaskRunner(state, work).run("task");
    verify(work, never()).execute(anyString(), any());
    verify(state, never()).start(anyString(), any());
  }
}
