package com.insk.insk_backend.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "articles") // ERD 테이블명
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "article_id")
    private Long articleId;

    @Column(nullable = false)
    private String title;

    @Column(name = "original_url", nullable = false, unique = true, length = 512)
    private String originalUrl;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** ✅ [새로 추가된 필드들] */
    @Column(name = "source", nullable = true, length = 100)
    private String source; // 기사 출처 (예: Naver, GoogleNews)

    @Column(name = "country", nullable = true, length = 10)
    private String country; // 국가 코드 (예: KR, US, JP 등)

    @Column(name = "language", nullable = true, length = 10)
    private String language; // 언어 코드 (예: ko, en 등)

    // category 필드는 제거됨

    /**
     * LLM 분석 상태 (멘토 피드백 #5).
     * 재시도·폴백 모두 실패한 기사는 유실하지 않고 FAILED로 저장해 재처리(DLQ) 대상으로 둔다.
     * 기존 행은 null(레거시)로 두고, 신규 수집분부터 채워진다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", length = 20)
    private AnalysisStatus analysisStatus;

    /**
     * DLQ 재처리 누적 시도 횟수 (멘토 피드백 #5).
     * 한도 초과 시 DEAD로 격리해 영구 실패 기사에 비용이 새는 것을 막는다.
     * 기존 행은 default 0으로 채워진다.
     */
    @Column(name = "retry_count", nullable = false, columnDefinition = "int default 0")
    private int retryCount = 0;

    /** ✅ Builder 포함 */
    @Builder
    public Article(String title,
                   String originalUrl,
                   LocalDateTime publishedAt,
                   LocalDateTime createdAt,
                   String source,
                   String country,
                   String language) {
        this.title = title;
        this.originalUrl = originalUrl;
        this.publishedAt = publishedAt;
        this.createdAt = createdAt;
        this.source = source;
        this.country = country;
        this.language = language;
    }

    public void markAnalysisCompleted() {
        this.analysisStatus = AnalysisStatus.COMPLETED;
    }

    /** 재시도·폴백 모두 실패 → 재처리 대기(DLQ). */
    public void markAnalysisFailed() {
        this.analysisStatus = AnalysisStatus.FAILED;
    }

    /** DLQ 재처리 1회 시도 기록. */
    public void incrementRetryCount() {
        this.retryCount++;
    }

    /** 재처리 한도 초과 → 영구 격리(재처리 제외). */
    public void markDead() {
        this.analysisStatus = AnalysisStatus.DEAD;
    }
}