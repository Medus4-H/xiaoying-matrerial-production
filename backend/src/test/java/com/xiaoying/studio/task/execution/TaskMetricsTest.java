package com.xiaoying.studio.task.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class TaskMetricsTest {
  @Test
  void unsupportedGcCountersAreNotReportedAsZero() {
    assertThat(TaskMetrics.aggregateGc(LongStream.of(5, -1))).isEmpty();
    assertThat(TaskMetrics.aggregateGc(LongStream.of(5, 3))).hasValue(8);
    assertThat(TaskMetrics.aggregateGc(LongStream.empty())).isEmpty();
  }
}
