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
}