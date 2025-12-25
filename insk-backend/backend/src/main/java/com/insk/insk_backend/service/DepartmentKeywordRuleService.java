package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.DepartmentKeywordRule;
import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.dto.DepartmentKeywordRuleDto;
import com.insk.insk_backend.repository.DepartmentKeywordRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DepartmentKeywordRuleService {

    private final DepartmentKeywordRuleRepository ruleRepository;

    /**
     * 부서별 전체 규칙 조회
     */
    public List<DepartmentKeywordRuleDto.RuleResponse> getRulesByDepartment(DepartmentType dept) {
        return ruleRepository.findByDepartmentOrderByCategoryAscPriorityAsc(dept)
                .stream()
                .map(DepartmentKeywordRuleDto.RuleResponse::of)
                .toList();
    }

    /**
     * 추천 키워드 반환
     */
    public List<String> getRecommendedKeywords(DepartmentType dept, String category) {

        List<DepartmentKeywordRule> list =
                ruleRepository.findByDepartmentAndActiveIsTrueOrderByPriorityAsc(dept);

        return list.stream()
                .filter(r -> category == null || r.getCategory().equalsIgnoreCase(category))
                .map(DepartmentKeywordRule::getKeyword)
                .toList();
    }

    /**
     * 규칙 생성
     */
    public DepartmentKeywordRuleDto.RuleResponse createRule(DepartmentKeywordRuleDto.CreateRequest req) {

        DepartmentKeywordRule rule = DepartmentKeywordRule.builder()
                .department(req.getDepartment())
                .category(req.getCategory())
                .keyword(req.getKeyword())
                .priority(req.getPriority())
                .active(true)
                .build();

        ruleRepository.save(rule);
        return DepartmentKeywordRuleDto.RuleResponse.of(rule);
    }

    /**
     * 규칙 수정
     */
    public DepartmentKeywordRuleDto.RuleResponse updateRule(Long ruleId, DepartmentKeywordRuleDto.UpdateRequest req) {

        DepartmentKeywordRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("규칙을 찾을 수 없습니다."));

        rule.setCategory(req.getCategory());
        rule.setKeyword(req.getKeyword());
        rule.setPriority(req.getPriority());
        rule.setActive(req.isActive());

        return DepartmentKeywordRuleDto.RuleResponse.of(rule);
    }

    /**
     * 규칙 삭제
     */
    public void deleteRule(Long ruleId) {
        if (!ruleRepository.existsById(ruleId)) {
            throw new IllegalArgumentException("해당 규칙이 존재하지 않습니다.");
        }
        ruleRepository.deleteById(ruleId);
    }
}
