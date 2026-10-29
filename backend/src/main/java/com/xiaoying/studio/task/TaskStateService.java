package com.xiaoying.studio.task;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TaskStateService {
  private final TaskMapper mapper;
  private final TransactionTemplate tx;

  public TaskStateService(TaskMapper mapper, TransactionTemplate tx) {
    this.mapper = mapper;
    this.tx = tx;
  }

  public boolean claim(String id) {
    return Boolean.TRUE.equals(tx.execute(s -> mapper.claim(id) == 1));
  }

  public void start(String id, TaskStage stage) {
    tx.executeWithoutResult(
        s -> {
          require(mapper.updateStage(id, stage));
          require(mapper.startStep(id, stage));
        });
  }

  public void complete(String id, TaskStage stage) {
    tx.executeWithoutResult(s -> require(mapper.completeStep(id, stage)));
  }

  public void succeed(String id, String key) {
    tx.executeWithoutResult(
        s -> {
          require(mapper.completeStep(id, TaskStage.FINALIZE));
          require(mapper.succeed(id, key));
        });
  }

  public void fail(String id, TaskStage stage, String code, String message) {
    fail(id, stage, code, message, null, null);
  }

  public void fail(
      String id, TaskStage stage, String code, String message, String messageKey, String detail) {
    tx.executeWithoutResult(
        s -> {
          if (mapper.fail(id, stage, code, message, messageKey, detail) == 1) {
            mapper.failRunning(id, code, message, messageKey, detail);
            mapper.skipPending(id);
          }
        });
  }

  private void require(int count) {
    if (count != 1) throw new IllegalStateException("Task state transition rejected");
  }
}
