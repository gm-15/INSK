package com.insk.insk_backend.domain;

/**
 * 기사의 LLM 분석 상태.
 * FAILED는 재시도·폴백이 모두 실패한 기사로, 유실하지 않고 재처리(DLQ) 대상으로 보존한다.
 */
public enum AnalysisStatus {
    COMPLETED,  // 분석 완료
    FAILED      // 재시도·폴백 모두 실패 → 재처리 대기 (DLQ)
}
