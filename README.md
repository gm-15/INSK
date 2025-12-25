# INSK v3.0 - 뉴스 트렌드 센싱 자동화 플랫폼

## 📋 프로젝트 개요

INSK는 AI 기반 뉴스 수집, 분석, 추천 시스템을 제공하는 엔터프라이즈급 플랫폼입니다. 다중 뉴스 소스에서 실시간으로 기사를 수집하고, OpenAI GPT 모델을 활용하여 기사를 분석·분류하며, 사용자 맞춤형 키워드 기반 필터링과 부서별 추천 시스템을 제공합니다.

### 핵심 가치 제안
- **자동화된 뉴스 수집**: Naver News, AI Times, The Guru 등 다중 소스에서 자동 수집
- **AI 기반 분석**: OpenAI GPT-4를 활용한 기사 요약, 인사이트 추출, 카테고리 분류, 태그 생성
- **맞춤형 필터링**: 사용자별 키워드 기반 기사 필터링 및 점수 계산
- **부서별 추천**: 10개 부서별 Top 5 기사 추천 시스템
- **실시간 피드백**: 좋아요/싫어요 및 텍스트 피드백을 통한 점수 개선

---

## 🏗️ 기술 아키텍처

### 백엔드 스택
- **Framework**: Spring Boot 3.x
- **Language**: Java 17+
- **ORM**: JPA/Hibernate
- **Database**: MySQL 8.0
- **Security**: Spring Security + JWT
- **External APIs**: 
  - OpenAI API (GPT-4) - 기사 분석, 키워드 추천
  - Naver News API - 뉴스 수집
  - AI Times, The Guru - 뉴스 수집
- **Web Scraping**: Jsoup
- **Async Processing**: Spring @Async

### 프론트엔드 스택
- **Framework**: Next.js 15.5.4 (App Router)
- **Language**: TypeScript
- **Styling**: Tailwind CSS 4
- **HTTP Client**: Axios
- **State Management**: React Hooks + Local Storage

### 인프라
- **Build Tool**: Gradle
- **Package Manager**: npm
- **Deployment**: Docker 지원 (백엔드), Vercel/Netlify 지원 (프론트엔드)

---

## 🎯 주요 기능 명세

### 1. 사용자 인증 및 권한 관리

#### 1.1 회원가입 및 로그인
- **회원가입**: 이메일, 비밀번호(8자 이상), 부서 선택
- **로그인**: JWT 기반 인증, 토큰 자동 저장
- **비밀번호 찾기**: 이메일 기반 재설정 토큰 생성
- **비밀번호 재설정**: 토큰 검증 후 새 비밀번호 설정
- **부서 변경**: 사용자 부서 정보 업데이트

#### 1.2 보안 기능
- JWT 토큰 기반 인증 (1시간 유효기간)
- BCrypt 비밀번호 암호화
- 토큰 기반 비밀번호 재설정 (1시간 유효)
- Spring Security를 통한 엔드포인트 보호

**API 엔드포인트**:
- `POST /api/v1/auth/signup` - 회원가입
- `POST /api/v1/auth/login` - 로그인
- `POST /api/v1/auth/forgot-password` - 비밀번호 찾기
- `POST /api/v1/auth/reset-password` - 비밀번호 재설정
- `PUT /api/v1/auth/me/department` - 부서 변경

---

### 2. 뉴스 수집 및 분석 파이프라인

#### 2.1 다중 소스 뉴스 수집
- **Naver News**: Naver News API를 통한 뉴스 검색 및 본문 스크래핑
- **AI Times**: AI Times 사이트에서 기사 수집
- **The Guru**: The Guru 사이트에서 기사 수집
- **중복 제거**: URL 기반 중복 기사 자동 필터링
- **에러 핸들링**: 403 에러 방지를 위한 User-Agent, Referrer 헤더 설정

#### 2.2 AI 기반 기사 분석
- **요약 생성**: OpenAI GPT-4를 활용한 기사 요약 (한국어)
- **인사이트 추출**: 기사의 핵심 인사이트 추출
- **카테고리 분류**: Telco, LLM, INFRA, AI Ecosystem 중 자동 분류
- **태그 생성**: 기사 관련 태그 자동 생성 (JSON 배열)
- **HTML 태그 제거**: 제목 및 본문에서 HTML 태그 자동 제거

#### 2.3 임베딩 및 유사도 계산
- **텍스트 임베딩**: OpenAI Embedding API를 통한 벡터 변환
- **키워드 유사도**: 사용자 키워드와 기사 임베딩 간 코사인 유사도 계산
- **점수 계산**: 유사도 기반 기사 점수 산출 (0-10점)

#### 2.4 비동기 처리
- **@Async 어노테이션**: 파이프라인 비동기 실행으로 타임아웃 방지
- **사용자별 처리**: 파이프라인 실행 시 사용자 이메일 기반 필터링
- **진행 상황 알림**: 프론트엔드에서 폴링을 통한 완료 감지

**API 엔드포인트**:
- `POST /api/v1/articles/run-pipeline` - 뉴스 파이프라인 실행 (비동기)

**주요 처리 단계**:
1. 승인된 키워드 조회 (사용자별 또는 전체)
2. 각 뉴스 소스에서 키워드 기반 검색
3. 중복 제거 및 신규 기사 저장
4. OpenAI API를 통한 기사 분석 (요약, 인사이트, 카테고리, 태그)
5. 임베딩 생성 및 저장
6. 사용자별 기사 점수 계산

---

### 3. 키워드 관리 시스템

#### 3.1 키워드 CRUD
- **키워드 생성**: 사용자별 키워드 등록 (중복 체크)
- **키워드 조회**: 사용자별 승인된 키워드 목록 조회
- **키워드 삭제**: 키워드 삭제 기능
- **카테고리 설정**: Telco, LLM, INFRA, AI Ecosystem 중 선택

#### 3.2 AI 키워드 추천
- **부서 기반 추천**: 사용자 부서 정보를 기반으로 OpenAI가 키워드 추천
- **키워드 승인**: 추천된 키워드를 승인하여 사용자 키워드 목록에 추가
- **즉시 반영**: 승인 시 즉시 사용자 키워드 목록에 반영

#### 3.3 다른 사용자 키워드 조회
- **중복 제거**: 대소문자 무시 중복 제거
- **추가 횟수 표시**: 동일 키워드가 여러 사용자에 의해 추가된 횟수 표시
- **정렬**: 추가 횟수 기준 내림차순 정렬

**API 엔드포인트**:
- `GET /api/v1/keywords` - 승인된 키워드 조회 (사용자별)
- `POST /api/v1/keywords` - 키워드 생성
- `DELETE /api/v1/keywords/{keywordId}` - 키워드 삭제
- `GET /api/v1/keywords/others` - 다른 사용자 키워드 조회
- `POST /api/v1/keywords/recommend` - AI 키워드 추천
- `POST /api/v1/keywords/approve` - 키워드 승인

---

### 4. 기사 조회 및 필터링

#### 4.1 기사 목록 조회
- **페이지네이션**: Spring Data JPA Pageable 지원
- **정렬**: 발행일 기준 정렬 (기본: 최신순)
- **사용자별 필터링**: 로그인 사용자의 키워드 기반 기사만 표시
- **카테고리 필터**: Telco, LLM, INFRA, AI Ecosystem 필터링
- **출처 필터**: Naver, AI Times, The Guru 필터링
- **N+1 쿼리 최적화**: ArticleAnalysis 배치 조회로 성능 개선

#### 4.2 기사 상세 조회
- **전체 정보**: 제목, 본문, 요약, 인사이트, 카테고리, 태그, 출처, 발행일
- **태그 파싱**: JSON 배열 형식의 태그 안전하게 파싱 및 표시
- **원본 링크**: 원본 기사 URL 제공

#### 4.3 기사 점수 시스템
- **점수 계산**: 키워드 유사도 기반 점수 (0-10점)
- **좋아요/싫어요 반영**: 피드백 기반 점수 조정
- **조회수 추적**: 기사 조회 횟수 추적
- **실시간 업데이트**: 피드백 시 점수 자동 재계산

**API 엔드포인트**:
- `GET /api/v1/articles` - 기사 목록 조회 (필터링, 페이지네이션)
- `GET /api/v1/articles/{articleId}` - 기사 상세 조회
- `GET /api/v1/articles/{articleId}/score` - 기사 점수 조회
- `POST /api/v1/articles/{articleId}/score/update` - 기사 점수 업데이트

---

### 5. 피드백 시스템

#### 5.1 좋아요/싫어요
- **토글 기능**: 같은 버튼 재클릭 시 취소 (삭제)
- **전환 기능**: 좋아요 → 싫어요 또는 그 반대 시 업데이트
- **점수 반영**: 피드백 시 기사 점수 자동 재계산
- **사용자별 추적**: 로그인 사용자의 피드백 상태 표시

#### 5.2 텍스트 피드백
- **댓글 작성**: 기사에 대한 텍스트 피드백 작성
- **최근 댓글 표시**: 최근 5개 댓글 표시 (최신순)
- **익명 지원**: 비로그인 사용자도 피드백 가능

#### 5.3 피드백 요약
- **좋아요/싫어요 수**: 전체 좋아요 및 싫어요 수 표시
- **내 피드백**: 현재 사용자의 피드백 상태 표시
- **통계 정보**: 피드백 기반 기사 인기도 측정

**API 엔드포인트**:
- `POST /api/v1/articles/{articleId}/feedback` - 피드백 생성/업데이트
- `GET /api/v1/articles/{articleId}/feedback/summary` - 피드백 요약 조회

---

### 6. 부서별 기사 추천

#### 6.1 부서별 Top 5 기사
- **10개 부서 지원**: 
  - T Cloud, T Network Infra, T HR, T AI Service, T Marketing
  - T Strategy, T Enterprise B2B, T Platform Dev, T Telco MNO, T Finance
- **점수 기반 정렬**: 부서별 키워드와의 유사도 기반 점수 계산
- **Top 5 선정**: 각 부서별 상위 5개 기사 추천

#### 6.2 부서별 키워드 매칭
- **부서별 키워드 집계**: 부서별 사용자 키워드 평균 유사도 계산
- **가중치 적용**: 부서별 키워드 중요도 반영

**API 엔드포인트**:
- `GET /api/v1/departments/{department}/articles/top5` - 부서별 Top 5 기사

---

### 7. 관심 기사 관리

#### 7.1 좋아요 기사 모음
- **좋아요 기사 목록**: 사용자가 좋아요를 누른 기사만 필터링
- **페이지네이션**: 좋아요 기사 목록 페이지네이션 지원

**프론트엔드 페이지**:
- `/favorites` - 관심 기사 목록 페이지

---

### 8. PDF 리포트 생성

#### 8.1 기사 PDF 다운로드
- **PDF 생성**: 기사 상세 정보를 PDF로 변환
- **포맷팅**: 제목, 요약, 인사이트, 태그 등 포함
- **다운로드**: 브라우저에서 PDF 파일 다운로드

**API 엔드포인트**:
- `GET /api/v1/articles/{articleId}/pdf` - 기사 PDF 다운로드

---

## 📊 데이터베이스 스키마

### 주요 엔티티

#### User (사용자)
- `user_id` (PK)
- `email` (UNIQUE)
- `password` (BCrypt 암호화)
- `department` (ENUM)
- `reset_token` (비밀번호 재설정용)
- `reset_token_expiry` (토큰 만료 시간)
- `created_at`

#### Article (기사)
- `article_id` (PK)
- `title`
- `original_url` (UNIQUE)
- `body` (TEXT)
- `source` (Naver, AI Times, The Guru)
- `published_at`
- `country`, `language`

#### ArticleAnalysis (기사 분석)
- `analysis_id` (PK)
- `article_id` (FK)
- `user_id` (FK) - 파이프라인 실행 사용자
- `summary` (TEXT)
- `insight` (TEXT)
- `category` (Telco, LLM, INFRA, AI Ecosystem)
- `tags` (JSON)

#### ArticleEmbedding (기사 임베딩)
- `embedding_id` (PK)
- `article_id` (FK)
- `embedding` (JSON - 벡터 배열)

#### Keyword (키워드)
- `keyword_id` (PK)
- `keyword`
- `approved` (BOOLEAN)
- `category`
- `user_id` (FK) - 키워드 소유자

#### ArticleFeedback (기사 피드백)
- `id` (PK)
- `article_id` (FK)
- `user_id` (FK, NULLABLE)
- `liked` (BOOLEAN, NULLABLE)
- `feedback_text` (TEXT, NULLABLE)
- `created_at`, `updated_at`

#### ArticleScore (기사 점수)
- `score_id` (PK)
- `article_id` (FK, UNIQUE)
- `score` (DOUBLE)
- `like_count`, `dislike_count`
- `text_relevance_score`
- `view_count`

---

## 🔄 주요 비즈니스 로직

### 1. 뉴스 파이프라인 실행 플로우
```
1. 사용자 키워드 조회 (승인된 키워드만)
2. 각 뉴스 소스 API 호출 (Naver, AI Times, The Guru)
3. 중복 체크 (URL 기반)
4. 신규 기사 저장
5. OpenAI API 호출 (분석, 임베딩)
6. ArticleAnalysis 저장
7. ArticleEmbedding 저장
8. 사용자별 점수 계산 및 저장
```

### 2. 기사 점수 계산 로직
```
점수 = (키워드 유사도 평균) × 10
- 키워드 유사도: 코사인 유사도 (임베딩 벡터)
- 피드백 반영: 좋아요/싫어요 비율 고려
- 최종 점수: 0-10점 범위
```

### 3. 사용자별 기사 필터링
```
- 로그인 사용자: 해당 사용자의 승인된 키워드로 필터링
- 비로그인 사용자: 전체 기사 표시 (공통 기사)
- 파이프라인 실행 시: 실행한 사용자의 user_id로 ArticleAnalysis 저장
```

---

## 🚀 성능 최적화

### 1. 쿼리 최적화
- **N+1 문제 해결**: ArticleAnalysis 배치 조회 (`findByArticle_ArticleIdIn`)
- **인덱스 활용**: `original_url`, `email`, `reset_token` 등에 인덱스
- **페이지네이션**: 대용량 데이터 처리 시 페이지네이션 적용

### 2. 비동기 처리
- **파이프라인 비동기 실행**: `@Async`를 통한 타임아웃 방지
- **폴링 메커니즘**: 프론트엔드에서 주기적으로 새 기사 확인

### 3. 에러 핸들링
- **글로벌 예외 처리**: `GlobalExceptionHandler`를 통한 통합 에러 처리
- **재시도 로직**: API 호출 실패 시 재시도
- **타임아웃 설정**: 외부 API 호출 타임아웃 설정

---

## 📱 프론트엔드 주요 페이지

### 1. 메인 페이지 (`/`)
- 기사 목록 (카드 형태)
- 카테고리/출처 필터
- 페이지네이션
- 좋아요/싫어요 버튼

### 2. 기사 상세 페이지 (`/articles/[id]`)
- 기사 전체 정보 표시
- 피드백 작성/수정
- 좋아요/싫어요 통계
- PDF 다운로드 버튼

### 3. 키워드 관리 페이지 (`/keywords`)
- 승인된 키워드 목록
- 키워드 추가/삭제
- AI 키워드 추천 및 승인
- 다른 사용자 키워드 조회
- 파이프라인 실행 버튼

### 4. 부서별 Top 5 페이지 (`/departments`)
- 부서 선택 드롭다운
- 부서별 Top 5 기사 표시
- 점수 및 순위 표시

### 5. 관심 기사 페이지 (`/favorites`)
- 좋아요를 누른 기사만 필터링
- 페이지네이션 지원

### 6. 인증 페이지
- `/login` - 로그인
- `/signup` - 회원가입
- `/forgot-password` - 비밀번호 찾기
- `/reset-password` - 비밀번호 재설정

---

## 🔐 보안 고려사항

1. **JWT 토큰**: 1시간 유효기간, Bearer 토큰 방식
2. **비밀번호 암호화**: BCrypt 해싱
3. **비밀번호 재설정**: 1시간 유효 토큰, 일회성 사용
4. **SQL Injection 방지**: JPA를 통한 파라미터 바인딩
5. **XSS 방지**: React의 자동 이스케이핑
6. **CORS 설정**: 프론트엔드 도메인만 허용

---

## 📈 확장 가능성

### 향후 개선 사항
1. **이메일 알림**: 새 기사 알림, 파이프라인 완료 알림
2. **실시간 업데이트**: WebSocket을 통한 실시간 기사 업데이트
3. **고급 필터링**: 날짜 범위, 복합 필터
4. **대시보드**: 통계 및 분석 대시보드
5. **API 문서화**: Swagger/OpenAPI 통합
6. **캐싱**: Redis를 통한 성능 개선
7. **로깅**: 구조화된 로깅 시스템

---

## 🛠️ 개발 환경 설정

### 백엔드
```bash
cd insk-backend/backend
./gradlew bootRun
```

### 프론트엔드
```bash
cd insk-frontend
npm install
npm run dev
```

### 환경 변수
- **백엔드**: `application.yml`에서 데이터베이스, JWT, OpenAI API 키 설정
- **프론트엔드**: `.env.local`에서 `NEXT_PUBLIC_API_BASE_URL` 설정

---

## 📝 주요 기술적 특징

1. **마이크로서비스 준비**: 모듈화된 구조로 확장 용이
2. **RESTful API**: 표준 REST API 설계
3. **타입 안정성**: TypeScript를 통한 타입 안정성 보장
4. **반응형 디자인**: Tailwind CSS를 통한 모바일 대응
5. **에러 처리**: 전역 예외 처리 및 사용자 친화적 에러 메시지
6. **로깅**: 구조화된 로깅을 통한 디버깅 용이

---

이 문서는 INSK v3.0 프로젝트의 전체 기능과 기술 스택을 포괄적으로 설명합니다. 실무 환경에서 프로젝트 이해 및 유지보수를 위한 참고 자료로 활용할 수 있습니다.

