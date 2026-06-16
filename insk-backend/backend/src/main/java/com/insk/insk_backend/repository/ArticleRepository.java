package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.AnalysisStatus;
import com.insk.insk_backend.domain.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsByOriginalUrl(String originalUrl);
    List<Article> findByTitleContainingIgnoreCaseOrderByPublishedAtDesc(String title);

    /** DLQ 재처리용: 분석이 최종 실패한 기사 목록 (멘토 피드백 #5). */
    List<Article> findByAnalysisStatus(AnalysisStatus analysisStatus);

    /**
     * v4 비용 사다리 — 제목 Jaccard 중복 체크용 윈도우 쿼리.
     * findAll() 금지 (OOM 위험). 최근 N일치 제목만 조회.
     */
    @Query("SELECT a.title FROM Article a WHERE a.publishedAt >= :since AND a.title IS NOT NULL")
    List<String> findTitlesPublishedAfter(@Param("since") LocalDateTime since);
}
