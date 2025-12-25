package com.insk.insk_backend.controller;

import com.insk.insk_backend.domain.ArticleScore;
import com.insk.insk_backend.dto.ArticleScoreDto;
import com.insk.insk_backend.service.ArticleScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/scores")   // ★ 변경됨
@RequiredArgsConstructor
public class ArticleScoreController {

    private final ArticleScoreService articleScoreService;

    @GetMapping("/{articleId}")
    public ArticleScoreDto.Response getScore(@PathVariable Long articleId) {
        ArticleScore s = articleScoreService.getScore(articleId);
        return ArticleScoreDto.Response.from(s);
    }

    @PostMapping("/{articleId}/update")
    public ArticleScoreDto.Response updateScore(@PathVariable Long articleId) {
        articleScoreService.updateScore(articleId);
        ArticleScore s = articleScoreService.getScore(articleId);
        return ArticleScoreDto.Response.from(s);
    }
}
