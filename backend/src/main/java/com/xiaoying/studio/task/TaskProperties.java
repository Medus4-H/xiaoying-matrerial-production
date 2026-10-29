package com.xiaoying.studio.task;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studio.task")
public record TaskProperties(
    long assetCheckDelayMs,
    long generateDelayMs,
    long finalizeDelayMs,
    long maxDelayMs,
    int concurrency,
    int queueCapacity,
    int shutdownGraceSeconds) {
  public TaskProperties {
    if (maxDelayMs < 0
        || assetCheckDelayMs < 0
        || generateDelayMs < 0
        || finalizeDelayMs < 0
        || assetCheckDelayMs > maxDelayMs
        || generateDelayMs > maxDelayMs
        || finalizeDelayMs > maxDelayMs
        || concurrency < 1
        || queueCapacity < 0
        || shutdownGraceSeconds < 0) {
      throw new IllegalArgumentException("Invalid task delay configuration");
    }
  }
}
