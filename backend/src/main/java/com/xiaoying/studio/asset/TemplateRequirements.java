package com.xiaoying.studio.asset;

import java.util.Set;

/** Snapshot of the template's upload constraints, supplied by the task service. */
public record TemplateRequirements(
    int requiredImageCount, Set<String> acceptedMimeTypes, long maxFileSizeBytes) {
  public TemplateRequirements {
    if (requiredImageCount <= 0
        || acceptedMimeTypes == null
        || acceptedMimeTypes.isEmpty()
        || maxFileSizeBytes <= 0) {
      throw new IllegalArgumentException("Invalid template asset requirements");
    }
    acceptedMimeTypes = Set.copyOf(acceptedMimeTypes);
  }
}
