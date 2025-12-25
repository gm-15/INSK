package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.Article;
import com.insk.insk_backend.domain.ArticleScore;
import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.dto.DepartmentTopDto;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.ArticleScoreRepository;
import com.insk.insk_backend.repository.UserArticleLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentNewsService {

    private final UserArticleLogRepository userArticleLogRepository;
    private final ArticleRepository articleRepository;
    private final ArticleScoreRepository articleScoreRepository;

    @Transactional(readOnly = true)
    public DepartmentTopDto.TopListResponse getTopArticles(DepartmentType department, int days, int limit) {

        if (days <= 0) days = 7;
        if (limit <= 0) limit = 5;

        LocalDateTime from = LocalDateTime.now().minusDays(days);

        List<Object[]> rows = userArticleLogRepository.findTopArticlesByDepartment(department, from);
        List<Object[]> limited = rows.stream().limit(limit).toList();

        List<DepartmentTopDto.TopArticle> list = new ArrayList<>();

        for (Object[] row : limited) {
            Long articleId = (Long) row[0];
            Long viewCount = (Long) row[1];

            Article article = articleRepository.findById(articleId).orElse(null);
            if (article == null) continue;

            ArticleScore score = articleScoreRepository.findByArticle_ArticleId(articleId).orElse(null);

            DepartmentTopDto.TopArticle dto = DepartmentTopDto.TopArticle.builder()
                    .articleId(article.getArticleId())
                    .title(article.getTitle())
                    .source(article.getSource())
                    .publishedAt(article.getPublishedAt())
                    .score(score != null ? score.getScore() : null)
                    .viewCount(viewCount)
                    .build();

            list.add(dto);
        }

        return DepartmentTopDto.TopListResponse.builder()
                .department(department != null ? department.name() : null)
                .days(days)
                .articles(list)
                .build();
    }
}