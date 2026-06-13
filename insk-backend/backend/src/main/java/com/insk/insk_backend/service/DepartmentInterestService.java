package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.DepartmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DepartmentInterestService {

    // 10개 부서 전체 매핑. (과거엔 6개만 있어 HR·마케팅·전략·B2B 4개 부서는 추천이 빈 채로 반환됐다.)
    private static final Map<DepartmentType, List<String>> INTEREST_MAP = Map.ofEntries(
            Map.entry(DepartmentType.T_CLOUD, List.of("cloud", "kubernetes", "serverless", "aws", "gcp")),
            Map.entry(DepartmentType.T_NETWORK_INFRA, List.of("network", "infrastructure", "5g", "latency")),
            Map.entry(DepartmentType.T_HR, List.of("HR", "recruiting", "talent", "organization culture")),
            Map.entry(DepartmentType.T_AI_SERVICE, List.of("ai", "llm", "rag", "vision")),
            Map.entry(DepartmentType.T_MARKETING, List.of("marketing", "branding", "campaign", "customer")),
            Map.entry(DepartmentType.T_STRATEGY, List.of("strategy", "M&A", "business model", "competition")),
            Map.entry(DepartmentType.T_ENTERPRISE_B2B, List.of("B2B", "enterprise", "SaaS", "partnership")),
            Map.entry(DepartmentType.T_PLATFORM_DEV, List.of("platform", "api", "backend", "system design")),
            Map.entry(DepartmentType.T_TELCO_MNO, List.of("telecom", "spectrum", "mobile", "5G core")),
            Map.entry(DepartmentType.T_FINANCE, List.of("finance", "fintech", "payment"))
    );

    public List<String> getInterestKeywords(DepartmentType dept) {
        return INTEREST_MAP.getOrDefault(dept, List.of());
    }
}
