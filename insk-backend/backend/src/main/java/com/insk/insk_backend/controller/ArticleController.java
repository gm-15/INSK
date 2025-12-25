package com.insk.insk_backend.controller;

import com.insk.insk_backend.domain.ArticleScore;
import com.insk.insk_backend.dto.ArticleDto;
import com.insk.insk_backend.dto.ArticleScoreDto;
import com.insk.insk_backend.service.ArticleScoreService;
import com.insk.insk_backend.service.ArticleService;
import com.insk.insk_backend.service.NewsPipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final NewsPipelineService newsPipelineService;
    private final ArticleScoreService articleScoreService;

    @GetMapping
    public ResponseEntity<Page<ArticleDto.Response>> getArticles(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String source,
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "publishedAt") Pageable pageable
    ) {
        String userEmail = (userDetails != null ? userDetails.getUsername() : null);
        return ResponseEntity.ok(articleService.getArticles(category, source, userEmail, pageable));
    }

    @GetMapping("/{articleId}")
    public ResponseEntity<ArticleDto.DetailResponse> getArticleDetail(@PathVariable Long articleId) {
        return ResponseEntity.ok(articleService.getArticleById(articleId));
    }

    @PostMapping("/run-pipeline")
    public ResponseEntity<String> runPipeline(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        try {
            String userEmail = (userDetails != null ? userDetails.getUsername() : null);
            // 비동기로 파이프라인 실행 (타임아웃 방지)
            newsPipelineService.runPipelineAsync(userEmail);
            return ResponseEntity.ok("뉴스 파이프라인 실행이 시작되었습니다. 약 2-3분 후 자동으로 반영됩니다.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("뉴스 파이프라인 실행 실패: " + e.getMessage());
        }
    }

    @PostMapping("/{articleId}/score/update")
    public ArticleScoreDto.Response updateScore(@PathVariable Long articleId) {
        ArticleScore score = articleScoreService.updateScore(articleId);
        return ArticleScoreDto.Response.from(score);
    }

    @GetMapping("/{articleId}/score")
    public ResponseEntity<ArticleScoreDto.Response> getScore(@PathVariable Long articleId) {
        ArticleScore score = articleScoreService.getScore(articleId);
        return ResponseEntity.ok(ArticleScoreDto.Response.from(score));
    }

    /**
     * 관심기사 조회 (좋아요한 기사)
     */
    @GetMapping("/favorites")
    public ResponseEntity<Page<ArticleDto.Response>> getFavoriteArticles(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "publishedAt") Pageable pageable
    ) {
        String userEmail = (userDetails != null ? userDetails.getUsername() : null);
        if (userEmail == null || userEmail.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(articleService.getFavoriteArticles(userEmail, pageable));
    }
}

