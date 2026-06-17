# ANN(Qdrant) 적용 측정 — 부서 Top-5 재계산 (멘토 #1)

> brute-force(MySQL `findAll` + 전 기사 임베딩 N+1 로드 + JSON 파싱 + Java cosine)를
> Qdrant VectorDB의 ANN(HNSW) KNN으로 교체. 메타=MySQL, 벡터=Qdrant.

## 대상
`GET /api/v1/articles/top5/{dept}` → `DepartmentArticleService.getTop5`의 **캐시 미스 재계산**.
(Redis 캐시 히트는 #9에서 ~20ms로 측정됨 — 여기선 미스 경로 비교)

## 결과

| 구분 | 미스 평균 | 비고 |
|---|---:|---|
| **옛 brute-force** | **3,950 ms** | #9 JMeter(1 thread×10). `findAll`+N+1 임베딩 로드+320+ JSON 파싱+Java cosine |
| **Qdrant ANN** | **1,616 ms** | curl 8회 미스(매번 redis flush). min 1,262 / max 2,016 |
| | **~2.4배** | |

- Qdrant 컬렉션: 613 벡터(1536d, Cosine). 기동 시 `VectorIndexInitializer`가 MySQL 임베딩을 백필.
- getTop5는 부서 키워드 임베딩 평균을 질의 벡터로 Qdrant KNN(top 30) → 인기점수 재랭킹 → top5.

## 정직한 해석
- **남은 1,616ms는 대부분 부서 키워드 OpenAI 임베딩 호출(4회 ~1s+)이 지배**한다. Qdrant 검색 자체는 ms 단위(curl smoke로 확인).
- 즉 ANN이 제거한 건 cosine 계산이 아니라 **전 기사 임베딩 N+1 로드 + 큰 JSON 파싱**이다(예측대로). 그래서 3,950→1,616ms.
- 비교 방법론은 다르다(옛=JMeter 1×10, 새=curl 8회). 둘 다 1-thread 순차 미스라 조건은 유사. 절대 수치보다 **구조적 효과(N+1·파싱 제거 + 인덱싱)** 가 핵심.
- 멘토 #1의 "임베딩을 JSON으로 MySQL에 저장 → 인덱싱 불가" 도 함께 해소: 벡터를 HNSW로 인덱싱.

## 한계 (정직)
- 320~600건 규모라 cosine 자체는 원래도 빠름 — 큰 이득은 규모가 커질수록(수만~수십만) 벌어진다(O(N)→O(log N)).
- 관련도 의미가 "키워드별 cosine 평균"에서 **"평균 질의벡터 cosine"** 으로 바뀜(둘 다 합리적, 동일하진 않음).
- Qdrant 미가동/미색인 시 getTop5가 빈 결과(폴백 없음) — 회로차단/폴백은 후속 과제.

## 재현
```bash
docker run -d --name insk-qdrant -p 6333:6333 qdrant/qdrant
# 앱 기동 시 VectorIndexInitializer가 백필. getTop5 미스 호출(redis flush 후) 시간 측정.
```
