package com.xiaoying.studio.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studio.auth")
public record AccountProperties(boolean demoEnabled, String demoCode, int passwordIterations) {
  public AccountProperties {
    if (passwordIterations < 100000 || passwordIterations > 2000000) {
      throw new IllegalArgumentException("Invalid password iteration count");
    }
    if (demoEnabled && (demoCode == null || !demoCode.matches("[0-9]{6}"))) {
      throw new IllegalArgumentException("Invalid demo verification code");
    }
  }
}
