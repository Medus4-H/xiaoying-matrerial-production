package com.xiaoying.studio.identity;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studio.session")
public record SessionProperties(
    String cookieName, Duration ttl, boolean secure, List<String> allowedOrigins) {
  public SessionProperties {
    if (cookieName == null
        || !cookieName.matches("[A-Za-z0-9_]+")
        || ttl == null
        || ttl.isZero()
        || ttl.isNegative()) {
      throw new IllegalArgumentException("Invalid session configuration");
    }
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }
}
