package com.xiaoying.studio.diagnostics;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studio.diagnostics")
public record DiagnosticProperties(
    boolean enabled, String secret, int capacity, int maxEventBytes, long sessionTtlSeconds) {}
