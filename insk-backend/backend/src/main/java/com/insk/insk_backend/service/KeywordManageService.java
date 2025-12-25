package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.Keyword;
import com.insk.insk_backend.repository.KeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class KeywordManageService {

    private final KeywordRepository keywordRepository;

    @Transactional(readOnly = true)
    public List<Keyword> getApprovedKeywords() {
        return keywordRepository.findByApprovedTrue();
    }

    @Transactional
    public void deleteKeyword(Long id) {
        if (id == null) return;
        if (keywordRepository.existsById(id)) {
            keywordRepository.deleteById(id);
        }
    }

    @Transactional
    public void rejectKeyword(String keyword) {
        if (keyword == null) return;
        String kw = keyword.trim();
        if (kw.isBlank()) return;

        List<Keyword> existing = keywordRepository.findAll().stream()
                .filter(k -> k.getKeyword().equalsIgnoreCase(kw))
                .toList();

        if (!existing.isEmpty()) {
            for (Keyword k : existing) {
                k.setApproved(false);
            }
            return;
        }

        Keyword entity = Keyword.builder()
                .keyword(kw)
                .approved(false)
                .user(null)
                .build();

        keywordRepository.save(entity);
    }
}
