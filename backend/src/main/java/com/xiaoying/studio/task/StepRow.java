package com.xiaoying.studio.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public record StepRow(
    String stage,
    int sequence,
    String status,
    Instant startedAt,
    Instant finishedAt,
    String errorCode,
    String errorMessage,
    String errorMessageKey,
    @JsonIgnore String errorMessageArgs,
    @JsonIgnore String errorDetail) {}
