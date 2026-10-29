package com.xiaoying.studio.common;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ErrorCatalogController {
  @GetMapping("/api/i18n/errors")
  public ResponseEntity<ErrorCatalog.Bundle> errors(
      @RequestParam(defaultValue = "zh-CN") String locale) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
        .body(ErrorCatalog.instance().bundle(locale));
  }
}
