package com.insk.insk_backend.controller;

import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.dto.DepartmentTopDto;
import com.insk.insk_backend.service.DepartmentNewsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/departments")
public class DepartmentNewsController {

    private final DepartmentNewsService departmentNewsService;

    @GetMapping("/{department}/top5")
    public ResponseEntity<DepartmentTopDto.TopListResponse> getTop5(
            @PathVariable("department") DepartmentType department,
            @RequestParam(name = "days", defaultValue = "7") int days,
            @RequestParam(name = "limit", defaultValue = "5") int limit
    ) {
        DepartmentTopDto.TopListResponse resp =
                departmentNewsService.getTopArticles(department, days, limit);
        return ResponseEntity.ok(resp);
    }
}
