package com.insk.insk_backend.repository;

import com.insk.insk_backend.domain.DepartmentType;
import com.insk.insk_backend.domain.UserArticleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface UserArticleLogRepository extends JpaRepository<UserArticleLog, Long> {

    @Query("""
        SELECT l.article.articleId, COUNT(l)
        FROM UserArticleLog l
        WHERE l.department = :dept
        AND l.viewedAt >= :from
        GROUP BY l.article.articleId
        ORDER BY COUNT(l) DESC
        """)
    List<Object[]> findTopArticlesByDepartment(DepartmentType dept, LocalDateTime from);
}
