package com.insk.insk_backend.domain;

/**
 * 기사의 LLM 분석 상태.
 * FAILED는 재시도·폴백이 모두 실패한 기사로, 유실하지 않고 재처리(DLQ) 대상으로 보존한다.
 * DEAD는 재처리도 한도(maxReprocessAttempts)만큼 실패한 기사로, 재처리 풀에서 영구 격리해
 * 절대 성공 못 할 호출에 비용이 새는 것을 막는다.
 */
public enum AnalysisStatus {
    COMPLETED,  // 분석 완료
    FAILED,     // 재시도·폴백 모두 실패 → 재처리 대기 (DLQ)
    DEAD        // 재처리 한도 초과 → 영구 격리 (재처리 제외, 비용 차단)
}
