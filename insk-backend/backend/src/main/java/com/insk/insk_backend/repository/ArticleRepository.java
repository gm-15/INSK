package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.Article;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    boolean existsByOriginalUrl(String originalUrl);
    List<Article> findByTitleContainingIgnoreCaseOrderByPublishedAtDesc(String title);
}
