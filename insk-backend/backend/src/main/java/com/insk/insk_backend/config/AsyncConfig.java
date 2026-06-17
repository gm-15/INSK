package com.insk.insk_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("pipeline-");
        executor.initialize();
        return executor;
    }

    /**
     * 멘토 #4: 파이프라인 기사별 처리 병렬화 전용 풀.
     * runPipelineAsync가 도는 taskExecutor와 분리해 nested 사용 시 풀 고갈(데드락)을 피한다.
     * 풀 크기가 동시 외부 호출(OpenAI) 상한이 되어 rate limit 폭주도 막는다.
     */
    @Bean(name = "pipelineItemExecutor")
    public Executor pipelineItemExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("pipeline-item-");
        executor.initialize();
        return executor;
    }
}

