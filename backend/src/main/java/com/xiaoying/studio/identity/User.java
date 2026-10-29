package com.xiaoying.studio.identity;

import java.time.Instant;

public record User(String id, String sessionTokenHash, Instant createdAt, Instant expiresAt) {}
