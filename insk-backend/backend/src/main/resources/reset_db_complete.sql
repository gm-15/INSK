-- ============================================
-- INSK DB 완전 초기화 스크립트
-- ============================================
-- ⚠️ 주의: 이 스크립트는 모든 데이터를 삭제합니다!
-- 사용자 계정, 키워드, 기사 등 모든 데이터가 삭제됩니다.
-- 
-- 사용법: MySQL Workbench 또는 MySQL CLI에서 실행
-- USE insk_db; 후 이 스크립트 실행

SET FOREIGN_KEY_CHECKS = 0;

-- 모든 테이블 데이터 삭제
DELETE FROM article_feedbacks;
DELETE FROM article_scores;
DELETE FROM user_article_logs;
DELETE FROM article_embeddings;
DELETE FROM article_analyses;
DELETE FROM articles;
DELETE FROM department_keyword_rule;
DELETE FROM keywords;
DELETE FROM users;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 확인 쿼리
-- ============================================
SELECT 'Articles' AS table_name, COUNT(*) AS count FROM articles
UNION ALL
SELECT 'Article Analyses', COUNT(*) FROM article_analyses
UNION ALL
SELECT 'Keywords', COUNT(*) FROM keywords
UNION ALL
SELECT 'Users', COUNT(*) FROM users;

