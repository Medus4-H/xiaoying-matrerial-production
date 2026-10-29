package com.xiaoying.studio.task.execution;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.LongStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class TaskMetrics {
  private final ThreadPoolTaskExecutor executor;
  private final boolean jvmEnabled;

  public TaskMetrics(
      ThreadPoolTaskExecutor executor,
      @Value("${studio.metrics.jvm-enabled:false}") boolean jvmEnabled) {
    this.executor = executor;
    this.jvmEnabled = jvmEnabled;
  }

  public Map<String, Object> snapshot() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("jvmSamplingEnabled", jvmEnabled);
    try {
      result.put("queueDepth", executor.getQueueSize());
      result.put("activeThreads", executor.getActiveCount());
    } catch (RuntimeException ignored) {
      // A snapshot is best effort and never changes task execution.
    }
    if (jvmEnabled) {
      try {
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        result.put("globalHeapUsedBytes", heap.getUsed());
        result.put("globalHeapCommittedBytes", heap.getCommitted());
        result.put("globalHeapMaxBytes", heap.getMax());
        var beans = ManagementFactory.getGarbageCollectorMXBeans();
        aggregateGc(beans.stream().mapToLong(b -> b.getCollectionCount()))
            .ifPresent(value -> result.put("globalGcCount", value));
        aggregateGc(beans.stream().mapToLong(b -> b.getCollectionTime()))
            .ifPresent(value -> result.put("globalGcTimeMs", value));
      } catch (RuntimeException ignored) {
        // Some JVMs do not expose all metrics.
      }
    }
    return result;
  }

  static OptionalLong aggregateGc(LongStream stream) {
    long[] values = stream.toArray();
    if (values.length == 0 || java.util.Arrays.stream(values).anyMatch(value -> value < 0)) {
      return OptionalLong.empty();
    }
    return OptionalLong.of(java.util.Arrays.stream(values).sum());
  }
}
