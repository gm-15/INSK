# INSK v3.0 - 기능 명세서 (Functional Specification)

## 📋 문서 목적

이 문서는 INSK v3.0 프로젝트의 상세 기능 명세를 담고 있으며, 개발팀 및 이해관계자들이 시스템의 동작 방식을 정확히 이해할 수 있도록 작성되었습니다.

---

## 1. 인증 및 사용자 관리 모듈

### 1.1 회원가입 기능

**기능 설명**: 새로운 사용자를 시스템에 등록합니다.

**입력 요구사항**:
- 이메일: 유효한 이메일 형식, 중복 불가
- 비밀번호: 최소 8자 이상
- 부서: 10개 부서 중 선택 (T_CLOUD, T_NETWORK_INFRA, T_HR, T_AI_SERVICE, T_MARKETING, T_STRATEGY, T_ENTERPRISE_B2B, T_PLATFORM_DEV, T_TELCO_MNO, T_FINANCE)

**처리 로직**:
1. 이메일 중복 체크
2. 비밀번호 BCrypt 해싱
3. User 엔티티 생성 및 저장
4. SignUpResponse 반환

**출력**:
- `userId`: 생성된 사용자 ID
- `email`: 등록된 이메일
- `department`: 선택한 부서

**예외 처리**:
- 이메일 중복 시: "이미 사용 중인 이메일입니다." 에러
- 유효성 검사 실패 시: 적절한 에러 메시지 반환

**API 명세**:
```
POST /api/v1/auth/signup
Content-Type: application/json

Request Body:
{
  "email": "user@example.com",
  "password": "password123",
  "department": "T_CLOUD"
}

Response 200:
{
  "userId": 1,
  "email": "user@example.com",
  "department": "T_CLOUD"
}
```

---

### 1.2 로그인 기능

**기능 설명**: 사용자 인증 및 JWT 토큰 발급

**입력 요구사항**:
- 이메일: 등록된 이메일
- 비밀번호: 등록 시 입력한 비밀번호

**처리 로직**:
1. 이메일로 사용자 조회
2. 비밀번호 일치 확인 (BCrypt 비교)
3. JWT 토큰 생성 (1시간 유효)
4. 토큰 반환 및 프론트엔드에 저장

**출력**:
- `accessToken`: JWT 토큰 문자열

**예외 처리**:
- 가입되지 않은 이메일: "가입되지 않은 이메일입니다."
- 잘못된 비밀번호: "잘못된 비밀번호입니다."

**API 명세**:
```
POST /api/v1/auth/login
Content-Type: application/json

Request Body:
{
  "email": "user@example.com",
  "password": "password123"
}

Response 200:
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

---

### 1.3 비밀번호 찾기 기능

**기능 설명**: 비밀번호를 잊은 사용자를 위한 재설정 토큰 생성

**입력 요구사항**:
- 이메일: 등록된 이메일 주소

**처리 로직**:
1. 이메일로 사용자 조회
2. 안전한 랜덤 토큰 생성 (Base64 URL-safe, 32바이트)
3. 토큰 만료 시간 설정 (현재 시간 + 1시간)
4. User 엔티티에 토큰 및 만료 시간 저장
5. 토큰 반환 (프론트엔드에서 사용자에게 표시)

**출력**:
- `resetToken`: 재설정 토큰 문자열
- `message`: 안내 메시지

**보안 고려사항**:
- 토큰은 일회성 사용 (재설정 후 삭제)
- 1시간 후 자동 만료
- 토큰은 URL-safe Base64 인코딩

**API 명세**:
```
POST /api/v1/auth/forgot-password
Content-Type: application/json

Request Body:
{
  "email": "user@example.com"
}

Response 200:
{
  "resetToken": "abc123...",
  "message": "비밀번호 재설정 토큰이 생성되었습니다..."
}
```

---

### 1.4 비밀번호 재설정 기능

**기능 설명**: 재설정 토큰을 사용하여 새 비밀번호 설정

**입력 요구사항**:
- `token`: 비밀번호 찾기에서 받은 재설정 토큰
- `newPassword`: 새 비밀번호 (최소 8자)

**처리 로직**:
1. 토큰으로 사용자 조회
2. 토큰 유효성 검사 (존재 여부, 만료 시간 확인)
3. 새 비밀번호 BCrypt 해싱
4. User 엔티티의 비밀번호 업데이트
5. 재설정 토큰 삭제 (clearResetToken)

**출력**:
- `message`: 성공 메시지

**예외 처리**:
- 유효하지 않은 토큰: "유효하지 않거나 만료된 토큰입니다."
- 만료된 토큰: "유효하지 않거나 만료된 토큰입니다."

**API 명세**:
```
POST /api/v1/auth/reset-password
Content-Type: application/json

Request Body:
{
  "token": "abc123...",
  "newPassword": "newpassword123"
}

Response 200:
{
  "message": "비밀번호가 성공적으로 재설정되었습니다."
}
```

---

### 1.5 부서 변경 기능

**기능 설명**: 로그인한 사용자의 부서 정보 변경

**입력 요구사항**:
- `department`: 새로운 부서 (10개 부서 중 선택)
- 인증: JWT 토큰 필요

**처리 로직**:
1. JWT 토큰에서 사용자 정보 추출
2. 사용자 조회
3. 부서 정보 업데이트

**API 명세**:
```
PUT /api/v1/auth/me/department
Authorization: Bearer {token}
Content-Type: application/json

Request Body:
{
  "department": "T_AI_SERVICE"
}

Response 204: No Content
```

---

## 2. 뉴스 수집 및 분석 파이프라인 모듈

### 2.1 뉴스 파이프라인 실행

**기능 설명**: 다중 뉴스 소스에서 기사를 수집하고 AI 분석을 수행하는 전체 파이프라인

**트리거**:
- 사용자가 키워드 관리 페이지에서 "파이프라인 실행" 버튼 클릭
- API: `POST /api/v1/articles/run-pipeline`

**처리 단계**:

#### Step 1: 키워드 조회
- 로그인 사용자: 해당 사용자의 승인된 키워드만 사용
- 비로그인 사용자: 전체 승인된 키워드 사용
- 키워드가 없으면 파이프라인 종료

#### Step 2: 뉴스 수집 (병렬 처리)
각 뉴스 소스별로 다음 작업 수행:

**2.1 Naver News**
- Naver News API 호출 (각 키워드별)
- 검색 결과에서 기사 URL 추출
- Jsoup을 사용한 본문 스크래핑
  - User-Agent, Referrer 헤더 설정 (403 에러 방지)
  - 타임아웃 설정
- 중복 체크 (original_url 기준)
- 신규 기사만 Article 테이블에 저장
- source 필드: "Naver"

**2.2 AI Times**
- AITimesClient를 통한 기사 수집
- 중복 체크 및 저장
- source 필드: "AI Times"

**2.3 The Guru**
- TheGuruClient를 통한 기사 수집
- 중복 체크 및 저장
- source 필드: "The Guru"

#### Step 3: AI 분석 (OpenAI API)
각 신규 기사에 대해:

**3.1 기사 분석 요청**
- OpenAI GPT-4 API 호출
- 프롬프트:
  ```
  다음 뉴스 기사를 분석해주세요:
  - 요약 (한국어, 200자 이내)
  - 핵심 인사이트 (한국어, 100자 이내)
  - 카테고리 분류 (Telco, LLM, INFRA, AI Ecosystem 중 하나)
  - 태그 (JSON 배열 형식, 3-5개)
  ```

**3.2 응답 파싱**
- JSON 응답에서 summary, insight, category, tags 추출
- ArticleAnalysis 엔티티 생성
- user_id 필드에 파이프라인 실행 사용자 저장
- HTML 태그 제거 (제목, 본문)

**3.3 임베딩 생성**
- OpenAI Embedding API 호출
- 기사 본문을 벡터로 변환 (1536차원)
- ArticleEmbedding 엔티티에 저장
- 토큰 길이 제한 처리 (최대 8000 토큰)

#### Step 4: 점수 계산
각 사용자별로:
- 사용자의 승인된 키워드 조회
- 각 키워드의 임베딩 생성
- 기사 임베딩과 키워드 임베딩 간 코사인 유사도 계산
- 유사도 평균 × 10 = 최종 점수 (0-10점)
- ArticleScore 엔티티에 저장

**비동기 처리**:
- `@Async` 어노테이션으로 비동기 실행
- 타임아웃 방지 (일반적으로 2-3분 소요)
- 프론트엔드에서 폴링으로 완료 감지

**에러 핸들링**:
- API 호출 실패 시 로그 기록 및 계속 진행
- 스크래핑 실패 시 해당 기사 건너뛰기
- OpenAI API 실패 시 재시도 로직 (선택적)

**API 명세**:
```
POST /api/v1/articles/run-pipeline
Authorization: Bearer {token}

Response 200:
{
  "message": "뉴스 파이프라인 실행이 시작되었습니다. 약 2-3분 후 자동으로 반영됩니다."
}
```

---

### 2.2 중복 기사 처리

**기능 설명**: 동일한 기사의 중복 수집 방지

**처리 로직**:
1. 기사 URL (original_url)을 UNIQUE 제약조건으로 설정
2. 저장 전 `articleRepository.findByOriginalUrl(url)` 조회
3. 존재하면 건너뛰기, 없으면 저장

**데이터베이스 제약**:
```sql
ALTER TABLE articles ADD UNIQUE KEY uk_original_url (original_url);
```

---

### 2.3 HTML 태그 제거

**기능 설명**: 뉴스 제목 및 본문에서 HTML 태그 제거

**처리 로직**:
- Jsoup의 `Jsoup.parse(html).text()` 사용
- 또는 정규식으로 `<[^>]+>` 패턴 제거
- 제목 저장 전 자동 처리

**적용 위치**:
- NewsPipelineService의 `removeHtmlTags()` 메서드
- Naver News 제목 처리 시

---

## 3. 키워드 관리 모듈

### 3.1 키워드 생성

**기능 설명**: 사용자가 새로운 키워드를 등록

**입력 요구사항**:
- `keyword`: 키워드 문자열 (공백 제거, 대소문자 구분)
- `category`: Telco, LLM, INFRA, AI Ecosystem 중 선택
- 인증: JWT 토큰 필요

**처리 로직**:
1. 키워드 공백 제거 및 검증
2. 현재 사용자에게 동일 키워드가 있는지 확인 (대소문자 무시)
3. 중복이면 에러, 없으면 생성
4. Keyword 엔티티 생성:
   - `approved = true` (직접 생성 시 자동 승인)
   - `user_id` = 현재 사용자
5. 저장

**예외 처리**:
- 중복 키워드: "이미 등록한 키워드입니다."
- 빈 키워드: "keyword는 비어 있을 수 없습니다."

**API 명세**:
```
POST /api/v1/keywords
Authorization: Bearer {token}
Content-Type: application/json

Request Body:
{
  "keyword": "인공지능",
  "category": "LLM"
}

Response 200: Created
```

---

### 3.2 키워드 조회

**기능 설명**: 사용자별 승인된 키워드 목록 조회

**처리 로직**:
- 로그인 사용자: 해당 사용자의 `approved = true` 키워드만 조회
- 비로그인 사용자: 전체 승인된 키워드 조회 (공통 키워드)

**출력**:
- 키워드 ID
- 키워드 문자열
- 승인 여부
- 카테고리

**API 명세**:
```
GET /api/v1/keywords
Authorization: Bearer {token} (선택)

Response 200:
[
  {
    "keywordId": 1,
    "keyword": "인공지능",
    "approved": true,
    "category": "LLM"
  },
  ...
]
```

---

### 3.3 키워드 삭제

**기능 설명**: 사용자가 등록한 키워드 삭제

**처리 로직**:
1. 키워드 ID로 조회
2. 현재 사용자가 소유자인지 확인
3. 삭제

**API 명세**:
```
DELETE /api/v1/keywords/{keywordId}
Authorization: Bearer {token}

Response 204: No Content
```

---

### 3.4 AI 키워드 추천

**기능 설명**: OpenAI를 활용하여 사용자 부서에 맞는 키워드 추천

**입력 요구사항**:
- `department`: 사용자 부서 정보

**처리 로직**:
1. 사용자 부서 정보 조회
2. OpenAI GPT-4 API 호출
3. 프롬프트:
   ```
   {부서명} 부서에 관련된 뉴스 키워드를 5개 추천해주세요.
   JSON 배열 형식으로 반환해주세요.
   예: ["키워드1", "키워드2", ...]
   ```
4. JSON 응답 파싱
5. 키워드 배열 반환

**출력**:
- `keywords`: 추천 키워드 배열 (5개)

**API 명세**:
```
POST /api/v1/keywords/recommend
Content-Type: application/json

Request Body:
{
  "department": "T_AI_SERVICE"
}

Response 200:
{
  "keywords": ["머신러닝", "딥러닝", "자연어처리", "컴퓨터비전", "강화학습"]
}
```

---

### 3.5 키워드 승인

**기능 설명**: AI가 추천한 키워드를 사용자 키워드 목록에 추가

**입력 요구사항**:
- `keyword`: 승인할 키워드
- `category`: 카테고리 (선택)
- 인증: JWT 토큰 필요

**처리 로직**:
1. 키워드 공백 제거 및 검증
2. 현재 사용자에게 동일 키워드가 있는지 확인
3. 중복이면 무시 (로그만 기록)
4. Keyword 엔티티 생성:
   - `approved = true`
   - `user_id` = 현재 사용자
5. 저장
6. 프론트엔드에서 즉시 키워드 목록 새로고침

**API 명세**:
```
POST /api/v1/keywords/approve
Authorization: Bearer {token}
Content-Type: application/json

Request Body:
{
  "keyword": "머신러닝",
  "category": "LLM"
}

Response 200: No Content
```

---

### 3.6 다른 사용자 키워드 조회

**기능 설명**: 다른 사용자들이 등록한 키워드를 참고용으로 조회

**처리 로직**:
1. 전체 승인된 키워드 조회
2. 현재 사용자의 키워드 제외
3. 키워드별로 그룹화 (대소문자 무시)
4. 각 키워드의 추가 횟수 계산
5. 추가 횟수 기준 내림차순 정렬

**출력**:
- `keyword`: 키워드 문자열
- `approved`: 승인 여부 (항상 true)
- `count`: 추가된 횟수

**API 명세**:
```
GET /api/v1/keywords/others
Authorization: Bearer {token}

Response 200:
[
  {
    "keyword": "인공지능",
    "approved": true,
    "count": 5
  },
  ...
]
```

---

## 4. 기사 조회 및 필터링 모듈

### 4.1 기사 목록 조회

**기능 설명**: 페이지네이션 및 필터링을 지원하는 기사 목록 조회

**쿼리 파라미터**:
- `category`: Telco, LLM, INFRA, AI Ecosystem
- `source`: Naver, AI Times, The Guru
- `page`: 페이지 번호 (0부터 시작)
- `size`: 페이지 크기 (기본 10)
- `sort`: 정렬 기준 (기본: publishedAt,desc)

**처리 로직**:

#### 4.1.1 사용자별 필터링
- 로그인 사용자:
  - ArticleAnalysis의 `user_id`가 현재 사용자와 일치하는 기사만 조회
  - 또는 공통 기사 (user_id가 NULL인 기사)
- 비로그인 사용자:
  - 전체 기사 조회

#### 4.1.2 카테고리 필터링
- ArticleAnalysis와 JOIN
- `category` 필드로 필터링

#### 4.1.3 출처 필터링
- Article의 `source` 필드로 필터링

#### 4.1.4 N+1 쿼리 최적화
- Article 목록 조회 후
- ArticleAnalysis를 배치로 조회 (`findByArticle_ArticleIdIn`)
- Map으로 매핑하여 O(1) 조회

**출력**:
- 기사 ID
- 제목
- 원본 URL
- 요약
- 인사이트
- 카테고리
- 태그 (JSON 배열)
- 발행일
- 출처
- 점수

**API 명세**:
```
GET /api/v1/articles?category=LLM&source=Naver&page=0&size=20
Authorization: Bearer {token} (선택)

Response 200:
{
  "content": [
    {
      "articleId": 1,
      "title": "AI 기술 동향",
      "originalUrl": "https://...",
      "summary": "...",
      "insight": "...",
      "category": "LLM",
      "tags": "[\"AI\", \"머신러닝\"]",
      "publishedAt": "2025-01-01T00:00:00",
      "source": "Naver",
      "score": 8.5
    },
    ...
  ],
  "totalElements": 100,
  "totalPages": 5,
  "number": 0,
  "size": 20
}
```

---

### 4.2 기사 상세 조회

**기능 설명**: 특정 기사의 전체 정보 조회

**처리 로직**:
1. Article ID로 기사 조회
2. ArticleAnalysis 조회 (JOIN)
3. 태그 JSON 파싱 (안전한 파싱, 배열/객체/문자열 모두 처리)
4. DetailResponse 생성

**출력**:
- 기사 전체 정보
- 요약, 인사이트, 카테고리, 태그
- 원본 URL

**예외 처리**:
- 존재하지 않는 기사: 404 Not Found

**API 명세**:
```
GET /api/v1/articles/{articleId}

Response 200:
{
  "articleId": 1,
  "title": "...",
  "originalUrl": "...",
  "body": "...",
  "summary": "...",
  "insight": "...",
  "category": "LLM",
  "tags": "[\"AI\", \"머신러닝\"]",
  "publishedAt": "...",
  "source": "Naver"
}
```

---

### 4.3 기사 점수 조회

**기능 설명**: 기사의 현재 점수 및 통계 정보 조회

**출력**:
- `articleId`: 기사 ID
- `score`: 최종 점수 (0-10점)
- `likeCount`: 좋아요 수
- `dislikeCount`: 싫어요 수
- `textRelevanceScore`: 텍스트 관련성 점수
- `viewCount`: 조회수

**API 명세**:
```
GET /api/v1/articles/{articleId}/score

Response 200:
{
  "articleId": 1,
  "score": 8.5,
  "likeCount": 10,
  "dislikeCount": 2,
  "textRelevanceScore": 0.85,
  "viewCount": 150
}
```

---

### 4.4 기사 점수 업데이트

**기능 설명**: 기사 점수를 수동으로 재계산

**처리 로직**:
1. ArticleScoreService의 `updateScore()` 호출
2. 키워드 유사도 재계산
3. 피드백 반영
4. 점수 업데이트

**API 명세**:
```
POST /api/v1/articles/{articleId}/score/update

Response 200:
{
  "articleId": 1,
  "score": 8.5,
  ...
}
```

---

## 5. 피드백 시스템 모듈

### 5.1 피드백 생성/업데이트

**기능 설명**: 기사에 대한 좋아요/싫어요 또는 텍스트 피드백 작성

**입력 요구사항**:
- `articleId`: 기사 ID
- `liked`: 좋아요 (true) 또는 싫어요 (false), 또는 null (텍스트 피드백만)
- `feedbackText`: 텍스트 피드백 (선택)
- 인증: 선택 (익명 피드백 가능)

**처리 로직**:

#### 5.1.1 좋아요/싫어요 처리
1. 현재 사용자의 기존 좋아요/싫어요 피드백 조회
2. 같은 버튼 재클릭 시:
   - 기존 피드백 삭제 (취소)
   - 점수 재계산
3. 다른 버튼 클릭 시:
   - 기존 피드백 업데이트 (좋아요 ↔ 싫어요)
   - 점수 재계산
4. 첫 클릭 시:
   - 새 피드백 생성
   - 점수 재계산

#### 5.1.2 텍스트 피드백 처리
- 좋아요/싫어요와 독립적으로 저장
- 여러 개의 텍스트 피드백 가능

**출력**:
- `feedbackId`: 피드백 ID
- `articleId`: 기사 ID
- `liked`: 좋아요/싫어요 여부
- `feedbackText`: 텍스트 피드백
- `userEmail`: 사용자 이메일 (익명이면 null)
- `department`: 사용자 부서
- `createdAt`: 생성 시간

**API 명세**:
```
POST /api/v1/articles/{articleId}/feedback
Authorization: Bearer {token} (선택)
Content-Type: application/json

Request Body (좋아요):
{
  "liked": true
}

Request Body (텍스트 피드백):
{
  "feedbackText": "유용한 기사입니다."
}

Response 200:
{
  "feedbackId": 1,
  "articleId": 1,
  "liked": true,
  "feedbackText": null,
  "userEmail": "user@example.com",
  "department": "T_CLOUD",
  "createdAt": "2025-01-01T00:00:00"
}
```

---

### 5.2 피드백 요약 조회

**기능 설명**: 기사의 전체 피드백 통계 및 요약 정보 조회

**처리 로직**:
1. 좋아요/싫어요 수 집계
2. 최근 텍스트 피드백 5개 조회 (최신순)
3. 현재 사용자의 피드백 상태 조회

**출력**:
- `likes`: 좋아요 총 수
- `dislikes`: 싫어요 총 수
- `recentComments`: 최근 댓글 배열 (최대 5개)
- `myFeedback`: 현재 사용자의 피드백
  - `liked`: 좋아요/싫어요 여부
  - `text`: 텍스트 피드백

**API 명세**:
```
GET /api/v1/articles/{articleId}/feedback/summary
Authorization: Bearer {token} (선택)

Response 200:
{
  "likes": 10,
  "dislikes": 2,
  "recentComments": [
    "유용한 기사입니다.",
    "좋은 정보네요.",
    ...
  ],
  "myFeedback": {
    "liked": true,
    "text": null
  }
}
```

---

## 6. 부서별 기사 추천 모듈

### 6.1 부서별 Top 5 기사 조회

**기능 설명**: 특정 부서에 가장 관련성 높은 상위 5개 기사 추천

**입력 요구사항**:
- `department`: 부서 코드 (10개 부서 중 하나)

**처리 로직**:
1. 해당 부서의 모든 사용자 키워드 조회
2. 각 기사에 대해:
   - 부서 사용자들의 키워드와의 유사도 계산
   - 평균 유사도 계산
   - 점수 = 평균 유사도 × 10
3. 점수 기준 내림차순 정렬
4. 상위 5개만 반환

**출력**:
- 기사 목록 (최대 5개)
- 각 기사에 순위 표시 (#1, #2, ...)
- 점수 표시

**API 명세**:
```
GET /api/v1/departments/{department}/articles/top5

Response 200:
[
  {
    "articleId": 1,
    "title": "...",
    "url": "...",
    "score": 9.5
  },
  ...
]
```

---

## 7. 관심 기사 관리 모듈

### 7.1 좋아요 기사 목록 조회

**기능 설명**: 사용자가 좋아요를 누른 기사만 필터링하여 조회

**처리 로직**:
1. 현재 사용자의 좋아요 피드백 조회
2. 해당 기사 ID 목록 추출
3. 기사 목록 조회 (페이지네이션 지원)

**API 명세**:
```
GET /api/v1/articles?liked=true
Authorization: Bearer {token}

Response 200:
{
  "content": [...],
  "totalElements": 20,
  ...
}
```

---

## 8. PDF 리포트 생성 모듈

### 8.1 기사 PDF 다운로드

**기능 설명**: 기사 상세 정보를 PDF 파일로 변환하여 다운로드

**처리 로직**:
1. 기사 상세 정보 조회
2. PDF 생성 라이브러리 사용 (예: iText, Apache PDFBox)
3. 제목, 요약, 인사이트, 태그 등 포함
4. Content-Type: application/pdf
5. 브라우저에서 다운로드

**API 명세**:
```
GET /api/v1/articles/{articleId}/pdf

Response 200:
Content-Type: application/pdf
[PDF 바이너리 데이터]
```

---

## 9. 에러 처리 및 예외 상황

### 9.1 글로벌 예외 처리

**구현**: `GlobalExceptionHandler` 클래스

**처리 예외**:
- `IllegalArgumentException`: 400 Bad Request
- `EntityNotFoundException`: 404 Not Found
- `BadCredentialsException`: 401 Unauthorized
- `RuntimeException`: 500 Internal Server Error

**응답 형식**:
```json
{
  "error": "에러 메시지",
  "timestamp": "2025-01-01T00:00:00"
}
```

---

### 9.2 프론트엔드 에러 처리

**구현**: Axios 인터셉터

**처리**:
- 401 Unauthorized: 토큰 만료 시 로그인 페이지로 리다이렉트
- 403 Forbidden: 권한 없음 메시지
- 500 Internal Server Error: 서버 오류 메시지

---

## 10. 성능 최적화 전략

### 10.1 쿼리 최적화
- **인덱스**: `original_url`, `email`, `reset_token` 등에 인덱스
- **배치 조회**: N+1 문제 해결을 위한 배치 조회
- **페이지네이션**: 대용량 데이터 처리

### 10.2 비동기 처리
- **파이프라인**: `@Async`를 통한 비동기 실행
- **타임아웃 방지**: 장시간 작업의 타임아웃 방지

### 10.3 캐싱 (향후 개선)
- Redis를 통한 기사 목록 캐싱
- 키워드 목록 캐싱

---

이 명세서는 INSK v3.0의 모든 기능을 상세히 설명하며, 개발 및 유지보수 시 참고 자료로 활용할 수 있습니다.

