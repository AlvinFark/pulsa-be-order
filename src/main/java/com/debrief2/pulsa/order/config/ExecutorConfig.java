package com.debrief2.pulsa.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class ExecutorConfig {
  @Bean(name = "asyncExecutor")
  public Executor asyncExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(3);
    executor.setMaxPoolSize(3);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("Async-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "workerExecutor")
  public Executor workerExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(11);
    executor.setMaxPoolSize(11);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("Worker-");
    executor.initialize();
    return executor;
  }
}
