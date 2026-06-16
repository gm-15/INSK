package com.insk.insk_backend.service;

import com.insk.insk_backend.client.OpenAIClient;
import com.insk.insk_backend.client.OpenAiAnalysisException;
import com.insk.insk_backend.dto.OpenAIDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase B ④ 검증 — LlmAnalysisService 재시도/폴백 카오스 테스트.
 *
 * <p>실제 Spring AOP(@Retryable/@Recover)가 동작하는 컨텍스트에서, 주 모델 실패 주입 시
 * 지정 횟수만큼 재시도하고 폴백 모델로 회복하는지 검증한다. 백오프는 테스트 속도를 위해 1ms로 단축.
 */
@SpringJUnitConfig(LlmAnalysisServiceRetryTest.TestConfig.class)
@TestPropertySource(properties = {
        "openai.api.key=test-key",
        "openai.model.analysis=primary-model",
        "openai.model.analysis-fallback=fallback-model",
        "openai.retry.max-attempts=5",
        "openai.retry.delay-ms=1",
        "openai.retry.max-delay-ms=2",
        "openai.retry.multiplier=1.0"
})
class LlmAnalysisServiceRetryTest {

    @Configuration
    @EnableRetry
    static class TestConfig {
        @Bean
        static PropertySourcesPlaceholderConfigurer placeholders() {
            return new PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        OpenAIClient openAIClient() {
            return mock(OpenAIClient.class);
        }

        @Bean
        LlmAnalysisService llmAnalysisService(OpenAIClient openAIClient) {
            return new LlmAnalysisService(openAIClient);
        }
    }

    @Autowired
    LlmAnalysisService service;

    @Autowired
    OpenAIClient openAIClient;

    // Spring 컨텍스트가 두 테스트 간 캐시되어 mock 빈이 싱글톤으로 공유되므로,
    // 메서드마다 스텁·호출 횟수를 초기화한다.
    @BeforeEach
    void resetMock() {
        org.mockito.Mockito.reset(openAIClient);
    }

    @Test
    @DisplayName("주 모델이 계속 실패하면 5회 재시도 후 폴백 모델로 회복한다")
    void retriesThenFallsBackToCheaperModel() {
        OpenAIDto.AnalysisResponse fallbackResult = new OpenAIDto.AnalysisResponse();
        when(openAIClient.analyzeArticle(any(), eq("primary-model")))
                .thenThrow(new OpenAiAnalysisException("주 모델 실패", new RuntimeException("boom")));
        when(openAIClient.analyzeArticle(any(), eq("fallback-model")))
                .thenReturn(fallbackResult);

        OpenAIDto.AnalysisResponse result = service.analyze("본문");

        assertThat(result).isSameAs(fallbackResult);
        verify(openAIClient, times(5)).analyzeArticle(any(), eq("primary-model"));  // 5회 재시도
        verify(openAIClient, times(1)).analyzeArticle(any(), eq("fallback-model")); // 폴백 1회
    }

    @Test
    @DisplayName("일시적 실패 2회 후 재시도가 성공하면 폴백 없이 결과를 반환한다")
    void recoversOnRetry_withoutFallback() {
        OpenAIDto.AnalysisResponse ok = new OpenAIDto.AnalysisResponse();
        when(openAIClient.analyzeArticle(any(), eq("primary-model")))
                .thenThrow(new OpenAiAnalysisException("일시 실패", new RuntimeException()))
                .thenThrow(new OpenAiAnalysisException("일시 실패", new RuntimeException()))
                .thenReturn(ok); // 3번째 시도 성공

        OpenAIDto.AnalysisResponse result = service.analyze("본문");

        assertThat(result).isSameAs(ok);
        verify(openAIClient, times(3)).analyzeArticle(any(), eq("primary-model"));
        verify(openAIClient, never()).analyzeArticle(any(), eq("fallback-model")); // 폴백 안 함
    }
}
