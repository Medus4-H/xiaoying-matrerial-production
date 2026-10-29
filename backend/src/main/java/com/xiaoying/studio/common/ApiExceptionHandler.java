package com.xiaoying.studio.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private final ErrorCatalog catalog = ErrorCatalog.instance();

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiError> known(ApiException e, HttpServletRequest request) {
    if (e.detail() != null)
      request.setAttribute(RequestLogFilter.ERROR_DETAIL_ATTRIBUTE, e.detail().name());
    return response(e.status(), e.code(), e.key(), request, requestId(request));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> invalidBody(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        catalog.definition(ErrorKey.INVALID_REQUEST).code(),
        ErrorKey.INVALID_REQUEST,
        request,
        requestId(request));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unknown(Exception e, HttpServletRequest request) {
    if (e instanceof ErrorResponse error) {
      int status = error.getStatusCode().value();
      if (status == 413)
        return response(
            HttpStatus.PAYLOAD_TOO_LARGE,
            catalog.definition(ErrorKey.ASSET_REQUEST_TOO_LARGE).code(),
            ErrorKey.ASSET_REQUEST_TOO_LARGE,
            request,
            requestId(request));
      ErrorKey key =
          status == 404
              ? ErrorKey.HTTP_NOT_FOUND
              : status == 405 ? ErrorKey.HTTP_METHOD_NOT_ALLOWED : ErrorKey.HTTP_ERROR;
      return response(
          HttpStatus.valueOf(status), "HTTP_" + status, key, request, requestId(request));
    }
    String id = requestId(request);
    SafeLog.write(
        () ->
            log.atError()
                .addKeyValue("event", "http.unexpected_error")
                .addKeyValue("exceptionClass", e.getClass().getName())
                .log("Unexpected request error"));
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        catalog.definition(ErrorKey.INTERNAL_ERROR).code(),
        ErrorKey.INTERNAL_ERROR,
        request,
        id);
  }

  private String requestId(HttpServletRequest request) {
    String value = org.slf4j.MDC.get("requestId");
    return value == null ? java.util.UUID.randomUUID().toString() : value;
  }

  private ResponseEntity<ApiError> response(
      HttpStatus status, String code, ErrorKey key, HttpServletRequest request, String id) {
    request.setAttribute(RequestLogFilter.ERROR_CODE_ATTRIBUTE, code);
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(
            new ApiError(
                code,
                catalog.message(key, request.getHeader("Accept-Language")),
                null,
                id,
                catalog.definition(key).key()));
  }
}
