package com.xiaoying.studio.task;

import java.util.List;

public final class TaskConfiguration {
  private TaskConfiguration() {}

  public record Requirements(
      int requiredImageCount,
      List<String> acceptedMimeTypes,
      long maxFileSizeBytes,
      String presetResultKey) {}

  public record Simulation(
      TaskStage failAt, long assetCheckDelayMs, long generateDelayMs, long finalizeDelayMs) {
    public long delay(TaskStage stage) {
      return switch (stage) {
        case ASSET_CHECK -> assetCheckDelayMs;
        case GENERATE -> generateDelayMs;
        case FINALIZE -> finalizeDelayMs;
      };
    }
  }

  public record SimulationInput(
      TaskStage failAt, Long assetCheckDelayMs, Long generateDelayMs, Long finalizeDelayMs) {}
}
