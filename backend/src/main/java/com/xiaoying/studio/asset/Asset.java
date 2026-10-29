package com.xiaoying.studio.asset;

import java.time.Instant;

public record Asset(
    String id,
    String userId,
    String storageKey,
    String originalName,
    String mimeType,
    long sizeBytes,
    int width,
    int height,
    Instant createdAt) {}
