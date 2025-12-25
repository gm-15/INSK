package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KeywordRepository extends JpaRepository<Keyword, Long> {

    boolean existsByKeyword(String keyword);

    List<Keyword> findByApprovedTrue();
    
    // 사용자별 승인된 키워드 조회
    List<Keyword> findByUser_EmailAndApprovedTrue(String email);

    boolean existsByKeywordIgnoreCase(String keyword);
}
