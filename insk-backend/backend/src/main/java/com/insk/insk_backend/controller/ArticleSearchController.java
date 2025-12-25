package com.insk.insk_backend.controller;

import com.insk.insk_backend.dto.ArticleSearchDto;
import com.insk.insk_backend.service.ArticleSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/articles")
public class ArticleSearchController {

    private final ArticleSearchService articleSearchService;

    @GetMapping("/search")
    public ResponseEntity<ArticleSearchDto.SearchResponse> search(
            @RequestParam("q") String query,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(articleSearchService.search(query, limit));
    }
}
