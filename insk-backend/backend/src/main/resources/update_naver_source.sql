-- ============================================
-- 네이버 뉴스 출처 값 업데이트 스크립트
-- ============================================
-- 사용법: MySQL Workbench 또는 MySQL CLI에서 실행
-- USE insk_db; 후 이 스크립트 실행

-- "NaverNews"를 "Naver"로 변경
UPDATE articles 
SET source = 'Naver' 
WHERE source = 'NaverNews';

-- 확인 쿼리
SELECT source, COUNT(*) AS count 
FROM articles 
WHERE source IN ('Naver', 'NaverNews')
GROUP BY source;

