-- ============================================
-- INSK DB 초기화 스크립트
-- ============================================
-- 사용법: MySQL Workbench 또는 MySQL CLI에서 실행
-- USE insk_db; 후 이 스크립트 실행

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================
-- 옵션 1: 기사 관련 데이터만 삭제 (권장)
-- 사용자 계정과 키워드는 보존
-- ============================================

-- 기사 관련 데이터 삭제 (외래키 때문에 순서 중요)
DELETE FROM article_feedbacks;
DELETE FROM article_scores;
DELETE FROM user_article_logs;
DELETE FROM article_embeddings;
DELETE FROM article_analyses;
DELETE FROM articles;

-- ============================================
-- 옵션 2: 전체 데이터 삭제 (완전 초기화)
-- 아래 주석을 해제하면 사용자와 키워드도 모두 삭제됩니다
-- ============================================

-- DELETE FROM article_feedbacks;
-- DELETE FROM article_scores;
-- DELETE FROM user_article_logs;
-- DELETE FROM article_embeddings;
-- DELETE FROM article_analyses;
-- DELETE FROM articles;
-- DELETE FROM department_keyword_rule;
-- DELETE FROM keywords;
-- DELETE FROM users;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 확인 쿼리
-- ============================================
SELECT 'Articles' AS table_name, COUNT(*) AS count FROM articles
UNION ALL
SELECT 'Article Analyses', COUNT(*) FROM article_analyses
UNION ALL
SELECT 'Article Embeddings', COUNT(*) FROM article_embeddings
UNION ALL
SELECT 'Article Scores', COUNT(*) FROM article_scores
UNION ALL
SELECT 'Article Feedbacks', COUNT(*) FROM article_feedbacks
UNION ALL
SELECT 'User Article Logs', COUNT(*) FROM user_article_logs
UNION ALL
SELECT 'Keywords', COUNT(*) FROM keywords
UNION ALL
SELECT 'Users', COUNT(*) FROM users;

