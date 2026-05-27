"""
INSK MySQL -> INSK-trend-forecast/data/*.parquet

매주 일요일 21시에 박건우가 클릭 한 번으로 실행.
4개 parquet 파일을 학교 레포 data/ 폴더에 떨군다.

사용법:
    cd C:\\dev\\INSK
    set MYSQL_PASSWORD=43214321         # Windows CMD
    $env:MYSQL_PASSWORD = "43214321"    # PowerShell
    python scripts/export_to_parquet.py

선택 인자:
    --out  출력 경로 (기본: C:\\dev\\INSK-trend-forecast\\data)
    --db   DB 이름 (기본: insk_db)
    --user DB 유저 (기본: root)
    --host DB 호스트 (기본: localhost)
"""

import argparse
import os
import sys
from pathlib import Path

import pandas as pd
from sqlalchemy import create_engine, text

# Windows PowerShell 한글 깨짐 방지
if sys.stdout.encoding and sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=r"C:\dev\INSK-trend-forecast\data")
    parser.add_argument("--db", default="insk_db")
    parser.add_argument("--user", default="root")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", default=3306, type=int)
    args = parser.parse_args()

    pw = os.environ.get("MYSQL_PASSWORD")
    if not pw:
        print("ERROR: MYSQL_PASSWORD 환경변수가 비어있음.", file=sys.stderr)
        print('  PowerShell: $env:MYSQL_PASSWORD = "비번"', file=sys.stderr)
        sys.exit(1)

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    url = f"mysql+pymysql://{args.user}:{pw}@{args.host}:{args.port}/{args.db}?charset=utf8mb4"
    engine = create_engine(url)

    # ----------------------------------------------------------------------
    # 1. articles
    # ----------------------------------------------------------------------
    print("[1/4] articles ...", end=" ", flush=True)
    articles = pd.read_sql(
        text("""
            SELECT
                article_id,
                title,
                original_url,
                published_at,
                created_at,
                source,
                country,
                language
            FROM articles
            ORDER BY article_id
        """),
        engine,
    )
    articles.to_parquet(out_dir / "insk_corpus.parquet", index=False)
    print(f"{len(articles)} rows")

    # ----------------------------------------------------------------------
    # 2. article_analyses
    # ----------------------------------------------------------------------
    print("[2/4] article_analyses ...", end=" ", flush=True)
    analyses = pd.read_sql(
        text("""
            SELECT
                analysis_id,
                article_id,
                summary,
                insight,
                category,
                tags,
                user_id,
                created_at
            FROM article_analyses
            ORDER BY analysis_id
        """),
        engine,
    )
    analyses.to_parquet(out_dir / "article_analyses.parquet", index=False)
    print(f"{len(analyses)} rows")

    # ----------------------------------------------------------------------
    # 3. article_embeddings (1536d, JSON string column)
    # ----------------------------------------------------------------------
    print("[3/4] article_embeddings ...", end=" ", flush=True)
    embeddings = pd.read_sql(
        text("""
            SELECT
                id AS embedding_id,
                article_id,
                embedding_json
            FROM article_embeddings
            ORDER BY id
        """),
        engine,
    )
    embeddings.to_parquet(out_dir / "article_embeddings.parquet", index=False)
    print(f"{len(embeddings)} rows")

    # ----------------------------------------------------------------------
    # 4. keywords
    # ----------------------------------------------------------------------
    print("[4/4] keywords ...", end=" ", flush=True)
    keywords = pd.read_sql(
        text("""
            SELECT
                keyword_id,
                keyword,
                category,
                approved,
                user_id,
                created_at
            FROM keywords
            ORDER BY keyword_id
        """),
        engine,
    )
    keywords.to_parquet(out_dir / "keywords.parquet", index=False)
    print(f"{len(keywords)} rows")

    # ----------------------------------------------------------------------
    # 보고서 요약 (카톡 공지에 그대로 복붙 가능)
    # ----------------------------------------------------------------------
    print()
    print("=" * 60)
    print("EXPORT 완료 요약 (카톡 공지용)")
    print("=" * 60)
    print(f"출력 경로: {out_dir}")
    print(f"총 article: {len(articles)}건")
    print(f"총 analysis: {len(analyses)}건")
    print(f"총 embedding: {len(embeddings)}건")
    print(f"총 keyword: {len(keywords)}건")
    print()

    # 카테고리 분포
    if not analyses.empty:
        cat = analyses["category"].value_counts()
        print("카테고리 분포:")
        for k, v in cat.items():
            pct = v / len(analyses) * 100
            print(f"  {k:15s} {v:5d}  ({pct:5.1f}%)")
        print()

    # 매체 분포
    if not articles.empty:
        src = articles["source"].value_counts()
        print("매체 분포:")
        for k, v in src.items():
            pct = v / len(articles) * 100
            print(f"  {k:15s} {v:5d}  ({pct:5.1f}%)")
        print()

    # 시계열 (created_at 일별 최근 7일)
    if not articles.empty:
        articles["created_date"] = pd.to_datetime(articles["created_at"]).dt.date
        daily = articles.groupby("created_date").size().sort_index(ascending=False).head(7)
        print("최근 7일 누적 (created_at):")
        for d, n in daily.items():
            print(f"  {d}  {n:5d}건")

    print()
    print("다음 단계:")
    print("  1. cd C:\\dev\\INSK-trend-forecast")
    print("  2. git add data/*.parquet")
    print('  3. git commit -m "data: weekly export YYYY-MM-DD (N rows)"')
    print("  4. git push origin main")
    print("  5. 팀원 카톡 공지")


if __name__ == "__main__":
    main()
