package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.ArticleEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ArticleEmbeddingRepository extends JpaRepository<ArticleEmbedding, Long> {

    // Article 1:N Embedding 구조 → List 반환
    List<ArticleEmbedding> findByArticle_ArticleId(Long articleId);

    /**
     * Qdrant 백필용 — (articleId, embeddingJson)만 조회.
     * 엔티티를 로드하지 않아 LAZY 연관(article)을 건드리지 않는다. row[0]=articleId(Long), row[1]=json(String).
     */
    @Query("SELECT e.article.articleId, e.embeddingJson FROM ArticleEmbedding e")
    List<Object[]> findAllForIndexing();
}
