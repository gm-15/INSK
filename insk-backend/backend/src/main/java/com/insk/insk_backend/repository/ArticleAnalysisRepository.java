package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArticleAnalysisRepository extends JpaRepository<ArticleAnalysis, Long> {

    /**
     * 🔍 특정 기사에 대한 분석 결과 조회
     */
    Optional<ArticleAnalysis> findByArticle(Article article);

    /**
     * 🔍 Article ID로 직접 조회 (편의 기능)
     */
    Optional<ArticleAnalysis> findByArticle_ArticleId(Long articleId);

    /**
     * 🔍 특정 기사 분석 존재 여부 체크
     */
    boolean existsByArticle(Article article);

    // ✅ 키워드 추천용: 최근 N일 기사 분석 가져오기
    List<ArticleAnalysis> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime from);

    List<ArticleAnalysis> findBySummaryContainingIgnoreCaseOrInsightContainingIgnoreCase(
            String summary, String insight
    );

    // 카테고리로 필터링
    List<ArticleAnalysis> findByCategory(String category);

    // 카테고리와 출처로 필터링 (Article 조인 필요)
    @org.springframework.data.jpa.repository.Query(
            "SELECT aa FROM ArticleAnalysis aa " +
            "JOIN aa.article a " +
            "WHERE (:category IS NULL OR aa.category = :category) " +
            "AND (:source IS NULL OR a.source = :source)"
    )
    List<ArticleAnalysis> findByCategoryAndSource(String category, String source);

    // Article ID 리스트로 일괄 조회 (N+1 쿼리 문제 해결)
    @org.springframework.data.jpa.repository.Query(
            "SELECT aa FROM ArticleAnalysis aa " +
            "WHERE aa.article.articleId IN :articleIds"
    )
    List<ArticleAnalysis> findByArticle_ArticleIdIn(java.util.List<Long> articleIds);
    
    // 사용자별 기사 분석 조회 (현재 사용자 또는 user가 null인 기사)
    @org.springframework.data.jpa.repository.Query(
            "SELECT aa FROM ArticleAnalysis aa " +
            "JOIN aa.article a " +
            "WHERE (aa.user.email = :userEmail OR aa.user IS NULL) " +
            "AND (:category IS NULL OR aa.category = :category) " +
            "AND (:source IS NULL OR a.source = :source)"
    )
    List<ArticleAnalysis> findByUser_EmailAndCategoryAndSource(String userEmail, String category, String source);
    
    // 사용자별 기사 분석 조회 (카테고리만)
    @org.springframework.data.jpa.repository.Query(
            "SELECT aa FROM ArticleAnalysis aa " +
            "WHERE (aa.user.email = :userEmail OR aa.user IS NULL) " +
            "AND (:category IS NULL OR aa.category = :category)"
    )
    List<ArticleAnalysis> findByUser_EmailAndCategory(String userEmail, String category);
    
    // 사용자별 기사 분석 조회 (출처만)
    @org.springframework.data.jpa.repository.Query(
            "SELECT aa FROM ArticleAnalysis aa " +
            "JOIN aa.article a " +
            "WHERE (aa.user.email = :userEmail OR aa.user IS NULL) " +
            "AND (:source IS NULL OR a.source = :source)"
    )
    List<ArticleAnalysis> findByUser_EmailAndSource(String userEmail, String source);
    
    // 사용자별 전체 기사 분석 조회
    @org.springframework.data.jpa.repository.Query(
            "SELECT aa FROM ArticleAnalysis aa " +
            "WHERE aa.user.email = :userEmail OR aa.user IS NULL"
    )
    List<ArticleAnalysis> findByUser_Email(String userEmail);

}
