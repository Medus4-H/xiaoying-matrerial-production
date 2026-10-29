package com.xiaoying.studio.template;

public record TemplateRow(
    String id,
    String title,
    String categoryId,
    String categoryName,
    String description,
    String coverKey,
    int requiredImageCount,
    String acceptedMimeTypes,
    long maxFileSizeBytes) {}
