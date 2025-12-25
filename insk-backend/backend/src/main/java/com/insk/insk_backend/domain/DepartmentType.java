package com.insk.insk_backend.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

@Getter
public enum DepartmentType {
    T_CLOUD,
    T_NETWORK_INFRA,
    T_HR,
    T_AI_SERVICE,
    T_MARKETING,
    T_STRATEGY,
    T_ENTERPRISE_B2B,
    T_PLATFORM_DEV,
    T_TELCO_MNO,
    T_FINANCE;

    @JsonCreator
    public static DepartmentType from(String value) {
        if (value == null) return null;

        String v = value.trim().toUpperCase();

        // CLOUD → T_CLOUD 자동 변환
        if (v.equals("CLOUD")) return T_CLOUD;
        if (v.equals("NETWORK_INFRA")) return T_NETWORK_INFRA;
        if (v.equals("HR")) return T_HR;
        if (v.equals("AI_SERVICE")) return T_AI_SERVICE;
        if (v.equals("MARKETING")) return T_MARKETING;
        if (v.equals("STRATEGY")) return T_STRATEGY;
        if (v.equals("ENTERPRISE_B2B")) return T_ENTERPRISE_B2B;
        if (v.equals("PLATFORM_DEV")) return T_PLATFORM_DEV;
        if (v.equals("TELCO_MNO")) return T_TELCO_MNO;
        if (v.equals("FINANCE")) return T_FINANCE;

        // 원래 enum 이름도 허용
        try {
            return DepartmentType.valueOf(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid department value: " + value);
        }
    }
}
