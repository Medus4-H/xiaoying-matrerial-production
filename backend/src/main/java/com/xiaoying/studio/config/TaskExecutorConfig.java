package com.xiaoying.studio.config;

import com.xiaoying.studio.task.TaskProperties;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskExecutorConfig {
  @Bean
  public ThreadPoolTaskExecutor taskExecutor(TaskProperties properties) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(properties.concurrency());
    executor.setMaxPoolSize(properties.concurrency());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setThreadNamePrefix("制作任务-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(properties.shutdownGraceSeconds());
    return executor;
  }
}
