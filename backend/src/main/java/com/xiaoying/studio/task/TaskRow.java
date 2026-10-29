package com.xiaoying.studio.task;

import java.time.Instant;

public record TaskRow(
    String id,
    String userId,
    String templateId,
    String templateTitleSnapshot,
    String requirementsSnapshot,
    String status,
    String currentStage,
    String failedStage,
    String errorCode,
    String errorMessage,
    String resultKey,
    String simulationSnapshot,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt,
    Instant updatedAt,
    String errorMessageKey,
    String errorMessageArgs,
    String errorDetail) {}
