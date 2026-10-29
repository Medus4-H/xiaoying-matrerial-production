package com.xiaoying.studio.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
  private final ErrorKey key;
  private final InternalErrorDetail detail;

  public ApiException(ErrorKey key) {
    this(key, null);
  }

  public ApiException(ErrorKey key, InternalErrorDetail detail) {
    super(ErrorCatalog.instance().message(key, ErrorCatalog.DEFAULT_LOCALE));
    this.key = key;
    this.detail = detail;
  }

  public HttpStatus status() {
    return HttpStatus.valueOf(ErrorCatalog.instance().definition(key).status());
  }

  public String code() {
    return ErrorCatalog.instance().definition(key).code();
  }

  public InternalErrorDetail detail() {
    return detail;
  }

  public ErrorKey key() {
    return key;
  }
}
