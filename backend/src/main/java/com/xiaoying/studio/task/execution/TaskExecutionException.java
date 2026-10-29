package com.xiaoying.studio.task.execution;

import com.xiaoying.studio.common.ErrorCatalog;
import com.xiaoying.studio.common.ErrorKey;

public class TaskExecutionException extends RuntimeException {
  private final ErrorKey key;
  private final String detail;

  public TaskExecutionException(ErrorKey key) {
    super(ErrorCatalog.instance().message(key, ErrorCatalog.DEFAULT_LOCALE));
    this.key = key;
    this.detail = key.name();
  }

  public TaskExecutionException(ErrorKey key, com.xiaoying.studio.common.ApiException cause) {
    super(ErrorCatalog.instance().message(key, ErrorCatalog.DEFAULT_LOCALE));
    this.key = key;
    this.detail = cause.detail() == null ? cause.key().name() : cause.detail().name();
  }

  public String code() {
    return ErrorCatalog.instance().definition(key).code();
  }

  public ErrorKey key() {
    return key;
  }

  public String detail() {
    return detail;
  }
}
