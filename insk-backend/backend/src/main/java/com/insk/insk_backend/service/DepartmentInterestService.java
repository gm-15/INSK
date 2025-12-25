package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.DepartmentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DepartmentInterestService {

    private static final Map<DepartmentType, List<String>> INTEREST_MAP = Map.of(
            DepartmentType.T_CLOUD, List.of("cloud", "kubernetes", "serverless", "aws", "gcp"),
            DepartmentType.T_NETWORK_INFRA, List.of("network", "infrastructure", "5g", "latency"),
            DepartmentType.T_AI_SERVICE, List.of("ai", "llm", "rag", "vision"),
            DepartmentType.T_PLATFORM_DEV, List.of("platform", "api", "backend", "system design"),
            DepartmentType.T_TELCO_MNO, List.of("telecom", "spectrum", "mobile", "5G core"),
            DepartmentType.T_FINANCE, List.of("finance", "fintech", "payment")
    );

    public List<String> getInterestKeywords(DepartmentType dept) {
        return INTEREST_MAP.getOrDefault(dept, List.of());
    }
}
