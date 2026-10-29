package com.xiaoying.studio.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.xiaoying.studio.task.TaskProperties;
import org.junit.jupiter.api.Test;

class TaskExecutorConfigTest {
  @Test
  void executorUsesConfiguredCapacityAndConcurrency() {
    var properties = new TaskProperties(1000, 6000, 1000, 30000, 3, 17, 5);
    var executor = new TaskExecutorConfig().taskExecutor(properties);
    assertThat(executor.getCorePoolSize()).isEqualTo(3);
    assertThat(executor.getMaxPoolSize()).isEqualTo(3);
    assertThat(executor.getQueueCapacity()).isEqualTo(17);
  }
}
