package com.insk.insk_backend.service;

import com.insk.insk_backend.client.OpenAIClient;
import com.insk.insk_backend.client.OpenAiAnalysisException;
import com.insk.insk_backend.dto.OpenAIDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/**
 * 외부 LLM(OpenAI) 분석 호출의 신뢰성 레이어 (멘토 피드백 #5).
 *
 * <p>일시적 실패(타임아웃·rate limit·5xx)는 지수 백오프로 재시도하고,
 * 재시도가 모두 실패하면 저비용 폴백 모델로 1회 더 시도한다.
 * 폴백까지 실패하면 예외가 호출부로 전파되어 DLQ(ANALYSIS_FAILED)로 처리된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmAnalysisService {

    private final OpenAIClient openAIClient;

    @Value("${openai.model.analysis:gpt-4o}")
    private String analysisModel;

    @Value("${openai.model.analysis-fallback:gpt-4o-mini}")
    private String fallbackModel;

    /**
     * 주 모델로 분석. 실패 시 지수 백오프(1s→2s→4s→8s→16s) + jitter로 최대 5회 재시도.
     */
    @Retryable(
            retryFor = OpenAiAnalysisException.class,
            maxAttemptsExpression = "${openai.retry.max-attempts:5}",
            backoff = @Backoff(
                    delayExpression = "${openai.retry.delay-ms:1000}",
                    multiplierExpression = "${openai.retry.multiplier:2.0}",
                    maxDelayExpression = "${openai.retry.max-delay-ms:30000}",
                    random = true)
    )
    public OpenAIDto.AnalysisResponse analyze(String body) {
        return openAIClient.analyzeArticle(body, analysisModel);
    }

    /**
     * 재시도 모두 실패 시 저비용 폴백 모델로 1회 시도. 폴백도 실패하면 예외 전파(→ DLQ).
     */
    @Recover
    public OpenAIDto.AnalysisResponse recover(OpenAiAnalysisException e, String body) {
        log.warn("주 모델({}) 재시도 모두 실패 → 폴백 모델({}) 시도: {}", analysisModel, fallbackModel, e.getMessage());
        return openAIClient.analyzeArticle(body, fallbackModel);
    }
}
