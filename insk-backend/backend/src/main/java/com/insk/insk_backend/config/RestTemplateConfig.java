package com.insk.insk_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 멘토 피드백 #3/#5: 외부 API 호출 타임아웃.
 *
 * <p>OpenAI 호출에 타임아웃이 없으면 응답이 안 오는 호출이 스레드를 영원히 점유해
 * 재시도(@Retryable)조차 트리거되지 않고 병렬 풀(pipelineItemExecutor)까지 막힌다.
 * 연결/읽기 타임아웃을 외부화한다(읽기 기본 120초는 멘토 권고값). 외부 HTTP 클라이언트가 공유한다.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate externalApiRestTemplate(
            @Value("${external.http.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${external.http.read-timeout-ms:120000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(factory);
    }
}
