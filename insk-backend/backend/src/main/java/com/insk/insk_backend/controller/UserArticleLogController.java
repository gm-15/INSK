package com.insk.insk_backend.controller;

import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.service.UserArticleLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class UserArticleLogController {

    private final UserArticleLogService logService;

    @PostMapping("/{id}/view")
    public void recordView(@PathVariable Long id,
                           @RequestParam(required = false) String userEmail) {
        logService.recordView(id, userEmail);
    }

    @GetMapping("/top5")
    public List<Object[]> getTop5(
            @RequestParam DepartmentType department,
            @RequestParam(defaultValue = "7") int days) {
        return logService.getTop5ByDepartment(department, days);
    }
}
