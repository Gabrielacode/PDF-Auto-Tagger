package com.sample.pdfautotagging.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@EnableScheduling
@EnableRetry
@Configuration
public class AsyncConfiguration {

    @Bean(value = "pdfJobQueueExecutor" )
    public Executor pdfJobQueueExecutor() {
        ThreadPoolTaskExecutor threadPoolExecutor = new ThreadPoolTaskExecutor();
        threadPoolExecutor.setCorePoolSize(2);
        //We would do 3
        threadPoolExecutor.setMaxPoolSize(2);
        //No queueing
        threadPoolExecutor.setQueueCapacity(0);
        threadPoolExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());

        threadPoolExecutor.initialize();

        return threadPoolExecutor;
    }
}
