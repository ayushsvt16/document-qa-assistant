package com.ayush.documentqa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Bounded async executor for document ingestion.
 * Uses a fixed pool to prevent unbounded thread creation under load.
 * Virtual threads are used via Spring Boot 4's default thread model for HTTP handling;
 * this executor is specifically for the ingestion pipeline.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private final AppProperties appProperties;

    public AsyncConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(appProperties.ingestion().poolSize());
        executor.setMaxPoolSize(appProperties.ingestion().poolSize());
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ingestion-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
