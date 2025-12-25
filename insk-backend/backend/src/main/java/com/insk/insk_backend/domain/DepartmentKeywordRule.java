package com.insk.insk_backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DepartmentKeywordRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private DepartmentType department;

    private String category;

    private String keyword;

    private int priority;

    private boolean active;

    // -----------------------------
    // 🔥 수정 가능하도록 Setter 제공
    // -----------------------------

    public void setCategory(String category) {
        this.category = category;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
