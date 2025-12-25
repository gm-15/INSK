package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.DepartmentKeywordRule;
import com.insk.insk_backend.domain.DepartmentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmentKeywordRuleRepository extends JpaRepository<DepartmentKeywordRule, Long> {

    List<DepartmentKeywordRule> findByDepartmentOrderByCategoryAscPriorityAsc(DepartmentType department);

    List<DepartmentKeywordRule> findByDepartmentAndActiveIsTrueOrderByPriorityAsc(DepartmentType department);
}
