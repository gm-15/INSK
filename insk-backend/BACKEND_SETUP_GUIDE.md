# INSK v3.0 백엔드 실행 가이드

## 📋 사전 요구사항

### 1. Java 21 설치 확인
```bash
java -version
# 출력 예시: openjdk version "21.0.x"
```

**Java 21이 없으면:**
- [Oracle JDK 21](https://www.oracle.com/java/technologies/downloads/#java21) 또는
- [OpenJDK 21](https://adoptium.net/) 다운로드 및 설치

### 2. MySQL 설치 및 실행 확인
```bash
# MySQL 서비스 상태 확인 (Windows)
sc query MySQL80

# MySQL 실행 (Windows)
net start MySQL80

# 또는 MySQL Workbench에서 서버 연결 확인
```

**MySQL이 없으면:**
- [MySQL Community Server 8.0](https://dev.mysql.com/downloads/mysql/) 다운로드 및 설치
- 설치 시 root 비밀번호를 임의로 설정 (이후 `application.properties`의 `spring.datasource.password`에 동일하게 입력)

### 3. Redis 설치 (선택사항, 캐싱 기능 사용 시)
```bash
# Redis 실행 확인 (Windows)
redis-cli ping
# 응답: PONG

# Redis가 없으면:
# - Windows: [WSL2 + Redis](https://redis.io/docs/getting-started/installation/install-redis-on-windows/) 또는
# - Docker 사용: docker run -d -p 6379:6379 redis:latest
```

---

## 🗄️ 데이터베이스 설정

### 1. MySQL 데이터베이스 생성
```sql
-- MySQL Workbench 또는 MySQL CLI에서 실행
CREATE DATABASE IF NOT EXISTS insk_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 사용자 권한 확인 (필요시)
GRANT ALL PRIVILEGES ON insk_db.* TO 'root'@'localhost';
FLUSH PRIVILEGES;
```

### 2. 기존 데이터 확인
```sql
-- 데이터베이스 선택
USE insk_db;

-- 테이블 목록 확인
SHOW TABLES;

-- 기사 데이터 확인
SELECT COUNT(*) FROM articles;

-- 키워드 데이터 확인
SELECT * FROM keywords;

-- 사용자 데이터 확인
SELECT * FROM users;
```

**기존 데이터가 있다면:**
- 기존 데이터를 그대로 사용 가능
- `spring.jpa.hibernate.ddl-auto=update` 설정으로 테이블 자동 업데이트

---

## ⚙️ 백엔드 설정 확인

### 1. `application.properties` 확인
파일 위치: `insk-backend/backend/src/main/resources/application.properties`

> ⚠️ **보안 주의**: `application.properties`는 `.gitignore`에 등록되어 있어 git에 커밋되지 않습니다. 아래 값들은 **로컬에만 작성**하고, 실제 자격증명을 절대 이 가이드 파일이나 README, 커밋 메시지 등 git에 들어가는 위치에 적지 마세요. 운영 환경(`application-prod.properties`)은 이미 `${env_var}` 형태로 외부 주입을 사용합니다.

**주요 설정 (값은 본인 환경에 맞게 입력):**
```properties
# 데이터베이스 연결
spring.datasource.url=jdbc:mysql://localhost:3306/insk_db?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=<YOUR_MYSQL_PASSWORD>

# JPA 설정
spring.jpa.hibernate.ddl-auto=update  # 기존 테이블 유지하면서 스키마 업데이트

# Redis (선택사항)
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### 2. API 키 발급 및 설정

다음 키들을 발급받아 로컬 `application.properties`에 입력합니다.

| 항목 | 발급 위치 |
|------|---------|
| Naver News API | https://developers.naver.com/apps |
| OpenAI API | https://platform.openai.com/api-keys |

```properties
# Naver News API
naver.api.client-id=<YOUR_NAVER_CLIENT_ID>
naver.api.client-secret=<YOUR_NAVER_CLIENT_SECRET>

# OpenAI API
openai.api.key=<YOUR_OPENAI_API_KEY>
```

---

## 🚀 백엔드 실행

### 방법 1: Gradle로 직접 실행 (권장)
```bash
# 프로젝트 루트에서
cd insk-backend/backend

# Windows (PowerShell)
.\gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

### 방법 2: IDE에서 실행
1. IntelliJ IDEA / Eclipse에서 프로젝트 열기
2. `InskBackendApplication.java` 파일 찾기
3. `main` 메서드에서 실행 (Run 버튼)

### 방법 3: 빌드 후 JAR 실행
```bash
cd insk-backend/backend

# 빌드
.\gradlew.bat build

# 실행
java -jar build/libs/insk-backend-0.0.1-SNAPSHOT.jar
```

---

## ✅ 실행 확인

### 1. 서버 시작 확인
```
# 콘솔 출력 예시:
Started InskBackendApplication in 5.234 seconds
Tomcat started on port(s): 8080 (http)
```

### 2. API 테스트
```bash
# 브라우저에서 접속
http://localhost:8080/api/v1/articles

# 또는 curl로 테스트
curl http://localhost:8080/api/v1/articles
```

### 3. Swagger UI 확인 (설정되어 있다면)
```
http://localhost:8080/swagger-ui/index.html
```

---

## 🔧 문제 해결

### 문제 1: 포트 8080이 이미 사용 중
```bash
# Windows에서 포트 사용 확인
netstat -ano | findstr :8080

# 프로세스 종료
taskkill /PID [PID번호] /F

# 또는 application.properties에서 포트 변경
server.port=8081
```

### 문제 2: MySQL 연결 실패
```
Error: Access denied for user 'root'@'localhost'
```

**해결:**
1. MySQL 비밀번호 확인
2. `application.properties`의 `spring.datasource.password` 수정
3. MySQL 서비스 실행 확인

### 문제 3: 데이터베이스가 없음
```
Error: Unknown database 'insk_db'
```

**해결:**
```sql
CREATE DATABASE insk_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 문제 4: Redis 연결 실패 (선택사항)
```
Error: Unable to connect to Redis
```

**해결:**
- Redis가 필수가 아니므로 `application.properties`에서 주석 처리:
```properties
# spring.data.redis.host=localhost
# spring.data.redis.port=6379
```

---

## 📊 기존 데이터 활용

### Postman에서 테스트했던 데이터 확인

1. **기사 데이터 확인:**
```sql
SELECT article_id, title, source, published_at 
FROM articles 
ORDER BY published_at DESC 
LIMIT 10;
```

2. **키워드 데이터 확인:**
```sql
SELECT * FROM keywords WHERE approved = true;
```

3. **사용자 데이터 확인:**
```sql
SELECT user_id, email, department FROM users;
```

### 기존 데이터로 프론트엔드 테스트

1. 백엔드 서버 실행 (`http://localhost:8080`)
2. 프론트엔드 실행 (`http://localhost:3000`)
3. 브라우저에서 메인 페이지 접속
4. 기사 목록이 표시되는지 확인

---

## 🔄 데이터 초기화 (필요시)

### 모든 데이터 삭제 후 재시작
```sql
-- 주의: 모든 데이터가 삭제됩니다!
USE insk_db;

DROP TABLE IF EXISTS article_feedbacks;
DROP TABLE IF EXISTS article_scores;
DROP TABLE IF EXISTS article_embeddings;
DROP TABLE IF EXISTS article_analyses;
DROP TABLE IF EXISTS articles;
DROP TABLE IF EXISTS keywords;
DROP TABLE IF EXISTS users;
```

백엔드 재시작 시 `spring.jpa.hibernate.ddl-auto=update`로 테이블이 자동 생성됩니다.

---

## 📝 다음 단계

1. ✅ 백엔드 서버 실행 확인
2. ✅ 프론트엔드에서 API 연결 확인
3. ✅ 기존 데이터로 기능 테스트
4. ✅ 새로운 키워드 추가 및 뉴스 파이프라인 실행

---

## 💡 팁

- **로그 확인**: 콘솔에서 SQL 쿼리와 에러 메시지 확인
- **Postman 재사용**: 기존 Postman 컬렉션으로 API 테스트
- **DB 관리**: MySQL Workbench로 데이터 직접 확인/수정 가능
- **포트 변경**: 프론트엔드 `.env.local`에서 `NEXT_PUBLIC_API_BASE_URL` 수정

