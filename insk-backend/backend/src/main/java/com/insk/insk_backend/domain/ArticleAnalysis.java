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
@Table(name = "article_analyses") // ERD의 테이블 이름
public class ArticleAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long analysisId;

    // [⭐️⭐️⭐️ 수정 ⭐️⭐️⭐️]
    @OneToOne(fetch = FetchType.LAZY) // ArticleService에서 getArticle()을 위해
    @JoinColumn(name = "article_id", nullable = false) // ERD의 컬럼 (article_id)
    private Article article;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String insight;

    @Column(length = 50)
    private String category; // 'category' 필드는 여기에만 존재

    @Column(length = 255)
    private String tags; // JSON 문자열

    // 사용자 FK (어떤 사용자의 키워드로 검색된 기사인지)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public ArticleAnalysis(Article article, String summary, String insight, String category, String tags, User user, LocalDateTime createdAt) {
        this.article = article;
        this.summary = summary;
        this.insight = insight;
        this.category = category;
        this.tags = tags;
        this.user = user;
        this.createdAt = createdAt;
    }
}