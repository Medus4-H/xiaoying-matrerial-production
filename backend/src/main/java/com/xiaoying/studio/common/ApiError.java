package com.xiaoying.studio.common;

import java.util.Map;

public record ApiError(
    String code,
    String message,
    Map<String, String> fieldErrors,
    String requestId,
    String messageKey) {}
