package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.*;
import com.insk.insk_backend.repository.ArticleRepository;
import com.insk.insk_backend.repository.UserArticleLogRepository;
import com.insk.insk_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserArticleLogService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final UserArticleLogRepository logRepository;

    @Transactional
    public void recordView(Long articleId, String userEmail) {

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("기사 없음"));

        User user = null;
        DepartmentType dept = null;

        if (userEmail != null) {
            user = userRepository.findByEmail(userEmail).orElse(null);

            if (user != null) {
                dept = user.getDepartment(); // User 엔티티의 필드 DepartmentType 사용
            }
        }

        UserArticleLog log = UserArticleLog.builder()
                .article(article)
                .user(user)
                .department(dept)
                .build();

        logRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<Object[]> getTop5ByDepartment(DepartmentType department, int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        return logRepository.findTopArticlesByDepartment(department, from);
    }
}
