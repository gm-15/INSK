package com.insk.insk_backend.controller;

import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.dto.ArticleDto;
import com.insk.insk_backend.service.DepartmentArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class DepartmentArticleController {

    private final DepartmentArticleService departmentArticleService;

    @GetMapping("/top5/{department}")
    public ResponseEntity<List<ArticleDto.SimpleResponse>> getTop5(
            @PathVariable DepartmentType department
    ) {
        return ResponseEntity.ok(departmentArticleService.getTop5(department));
    }
}
