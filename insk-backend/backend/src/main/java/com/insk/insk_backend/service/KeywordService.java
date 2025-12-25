package com.insk.insk_backend.service;

import com.insk.insk_backend.domain.Keyword;
import com.insk.insk_backend.domain.User;
import com.insk.insk_backend.dto.KeywordDto;
import com.insk.insk_backend.repository.KeywordRepository;
import com.insk.insk_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KeywordService {

    private final KeywordRepository keywordRepository;
    private final UserRepository userRepository;

    // 🔹 키워드 생성
    public Keyword createKeyword(KeywordDto.CreateRequest req, String userEmail) {
        // 사용자 정보 조회
        User user = null;
        if (userEmail != null && !userEmail.isBlank()) {
            user = userRepository.findByEmail(userEmail).orElse(null);
        }

        // 사용자별로 중복 체크 (같은 사용자가 같은 키워드를 중복으로 추가할 수 없음)
        if (user != null) {
            boolean existsByUser = keywordRepository.findByUser_EmailAndApprovedTrue(userEmail)
                    .stream()
                    .anyMatch(k -> k.getKeyword().equalsIgnoreCase(req.getKeyword().trim()));
            if (existsByUser) {
                throw new IllegalArgumentException("이미 등록한 키워드입니다.");
            }
        } else {
            // 사용자 정보가 없으면 전체 키워드에서 중복 체크 (하위 호환성)
            if (keywordRepository.existsByKeyword(req.getKeyword())) {
                throw new IllegalArgumentException("이미 존재하는 키워드입니다.");
            }
        }

        Keyword keyword = Keyword.builder()
                .keyword(req.getKeyword().trim())
                .approved(true)        // 자동 승인
                .user(user)            // 사용자 연결
                .build();

        return keywordRepository.save(keyword);
    }

    // 🔹 승인된 키워드 조회 (사용자별)
    public List<Keyword> getApprovedKeywords(String userEmail) {
        if (userEmail != null && !userEmail.isBlank()) {
            return keywordRepository.findByUser_EmailAndApprovedTrue(userEmail);
        }
        // 사용자 정보가 없으면 전체 승인된 키워드 반환 (하위 호환성)
        return keywordRepository.findByApprovedTrue();
    }
    
    // 🔹 전체 승인된 키워드 조회 (관리자용)
    public List<Keyword> getAllApprovedKeywords() {
        return keywordRepository.findByApprovedTrue();
    }

    // 🔹 전체 키워드 조회
    public List<Keyword> getAllKeywords() {
        return keywordRepository.findAll();
    }

    // 🔹 단일 키워드 조회
    public Keyword getKeyword(Long id) {
        return keywordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 키워드 ID입니다."));
    }

    // 🔹 중복체크
    public boolean existsByKeyword(String keyword) {
        return keywordRepository.existsByKeyword(keyword);
    }
    
    // 🔹 다른 사용자가 추가한 키워드 조회 (현재 사용자 제외, 중복 제거 및 카운트)
    public List<KeywordDto.OtherUsersKeywordResponse> getOtherUsersKeywords(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            // 사용자 정보가 없으면 빈 리스트 반환
            return List.of();
        }
        
        // 현재 사용자가 아닌 다른 사용자의 승인된 키워드만 조회
        List<Keyword> allApproved = keywordRepository.findByApprovedTrue();
        List<Keyword> otherUsersKeywords = allApproved.stream()
                .filter(k -> k.getUser() != null && !k.getUser().getEmail().equals(userEmail))
                .toList();
        
        // 키워드별로 그룹화하여 카운트 계산
        Map<String, Long> keywordCountMap = otherUsersKeywords.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        k -> k.getKeyword().toLowerCase(),
                        java.util.stream.Collectors.counting()
                ));
        
        // 중복 제거된 키워드 리스트 생성 (카운트 포함)
        return keywordCountMap.entrySet().stream()
                .map(entry -> {
                    // 원본 키워드 찾기 (대소문자 원본 유지)
                    Keyword firstKeyword = otherUsersKeywords.stream()
                            .filter(k -> k.getKeyword().equalsIgnoreCase(entry.getKey()))
                            .findFirst()
                            .orElse(null);
                    
                    if (firstKeyword == null) {
                        return null;
                    }
                    
                    return KeywordDto.OtherUsersKeywordResponse.builder()
                            .keyword(firstKeyword.getKeyword())
                            .approved(firstKeyword.isApproved())
                            .count(entry.getValue().intValue())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount())) // 카운트 내림차순 정렬
                .toList();
    }
}
