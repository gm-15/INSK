package com.insk.insk_backend.controller;

import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.dto.DepartmentKeywordRuleDto;
import com.insk.insk_backend.service.DepartmentKeywordRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/keyword-rules")
@RequiredArgsConstructor
public class AdminKeywordRuleController {

    private final DepartmentKeywordRuleService ruleService;

    @GetMapping("/{department}")
    public ResponseEntity<List<DepartmentKeywordRuleDto.RuleResponse>> getAllByDepartment(
            @PathVariable DepartmentType department) {

        return ResponseEntity.ok(
                ruleService.getRulesByDepartment(department)
        );
    }

    @PostMapping
    public ResponseEntity<DepartmentKeywordRuleDto.RuleResponse> createRule(
            @RequestBody DepartmentKeywordRuleDto.CreateRequest req) {

        return ResponseEntity.ok(ruleService.createRule(req));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<DepartmentKeywordRuleDto.RuleResponse> updateRule(
            @PathVariable Long ruleId,
            @RequestBody DepartmentKeywordRuleDto.UpdateRequest req) {

        return ResponseEntity.ok(ruleService.updateRule(ruleId, req));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<String> deleteRule(@PathVariable Long ruleId) {
        ruleService.deleteRule(ruleId);
        return ResponseEntity.ok("규칙이 삭제되었습니다.");
    }
}
