package com.insk.insk_backend.client;

/**
 * OpenAI 기사 분석 호출 실패를 나타내는 예외.
 * null 반환 대신 이 예외를 던져, 상위에서 재시도(@Retryable)·폴백(@Recover)으로 다룰 수 있게 한다.
 */
public class OpenAiAnalysisException extends RuntimeException {
    public OpenAiAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
