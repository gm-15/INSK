SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS article_embeddings;
DROP TABLE IF EXISTS article_analyses;
DROP TABLE IF EXISTS articles;
DROP TABLE IF EXISTS department_keyword_rule;
DROP TABLE IF EXISTS keywords;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================
-- USERS
-- =============================
CREATE TABLE users (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================
-- KEYWORDS
-- =============================
CREATE TABLE keywords (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    keyword VARCHAR(255) NOT NULL,
    user_id BIGINT,
    approved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_keyword_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
);

-- =============================
-- DEPARTMENT KEYWORD RULE
-- =============================
CREATE TABLE department_keyword_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    department VARCHAR(50) NOT NULL,
    category VARCHAR(255),
    keyword VARCHAR(255),
    priority INT DEFAULT 0,
    active BOOLEAN DEFAULT TRUE
);

-- =============================
-- ARTICLES
-- =============================
CREATE TABLE articles (
    article_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(500),
    original_url TEXT,
    source VARCHAR(100),
    country VARCHAR(50),
    language VARCHAR(50),
    published_at DATETIME
);

-- =============================
-- ARTICLE ANALYSIS
-- =============================
CREATE TABLE article_analyses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    summary TEXT,
    insight TEXT,
    category VARCHAR(255),
    tags TEXT,
    CONSTRAINT fk_analysis_article
        FOREIGN KEY (article_id)
        REFERENCES articles(article_id)
        ON DELETE CASCADE
);

-- =============================
-- ARTICLE EMBEDDINGS
-- =============================
CREATE TABLE article_embeddings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    article_id BIGINT NOT NULL,
    vector LONGBLOB,
    CONSTRAINT fk_embedding_article
        FOREIGN KEY (article_id)
        REFERENCES articles(article_id)
        ON DELETE CASCADE
);
