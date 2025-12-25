"use client";

import { useEffect, useState } from "react";
import { getArticles } from "@/lib/api/articles";
import type { ArticleResponse, ArticlePageResponse } from "@/types";
import ArticleCard from "@/components/ArticleCard";

export default function Home() {
  const [articles, setArticles] = useState<ArticleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [category, setCategory] = useState<string>("");
  const [source, setSource] = useState<string>("");

  const fetchArticles = async (pageNum: number = 0) => {
    try {
      setLoading(true);
      setError(null);
      const response: ArticlePageResponse = await getArticles({
        page: pageNum,
        size: 10,
        category: category || undefined,
        source: source || undefined,
        sort: "publishedAt,desc",
      });
      setArticles(response.content || []);
      setTotalPages(response.totalPages || 0);
      setPage(response.number || 0);
    } catch (err: any) {
      // Network Error인 경우 더 명확한 메시지 표시
      if (err.isNetworkError) {
        setError(err.message);
      } else if (err.status === 404 || err.status === 500) {
        // 서버는 연결되었지만 데이터가 없는 경우
        setArticles([]);
        setTotalPages(0);
        setPage(0);
      } else {
        setError(err.message || "기사를 불러오는데 실패했습니다.");
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchArticles(0);
  }, [category, source]);

  const handleCategoryChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setCategory(e.target.value);
    setPage(0);
  };

  const handleSourceChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setSource(e.target.value);
    setPage(0);
  };

  // 페이지 번호 범위 계산 (1-10개씩 표시)
  const getPageNumbers = () => {
    const pages: (number | string)[] = [];
    const currentPage = page + 1;
    
    if (totalPages <= 10) {
      // 전체 페이지가 10개 이하면 모두 표시
      for (let i = 1; i <= totalPages; i++) {
        pages.push(i);
      }
    } else {
      // 첫 페이지와 마지막 페이지는 항상 표시
      pages.push(1);
      
      // 현재 페이지 기준으로 앞뒤 4개씩 표시
      let start = Math.max(2, currentPage - 4);
      let end = Math.min(totalPages - 1, currentPage + 4);
      
      // 시작과 끝 사이에 간격이 있으면 "..." 추가
      if (start > 2) {
        pages.push("...");
      }
      
      for (let i = start; i <= end; i++) {
        pages.push(i);
      }
      
      if (end < totalPages - 1) {
        pages.push("...");
      }
      
      pages.push(totalPages);
    }
    
    return pages;
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="container mx-auto px-4 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-6">뉴스 기사 목록</h1>
          
          <div className="flex gap-4 mb-6">
            <select
              value={category}
              onChange={handleCategoryChange}
              className="px-4 py-2 border border-gray-300 rounded-lg bg-white text-gray-900 hover:border-[#ff6b35] focus:outline-none focus:ring-2 focus:ring-[#ff6b35] font-medium"
            >
              <option value="">전체 카테고리</option>
              <option value="Telco">Telco</option>
              <option value="LLM">LLM</option>
              <option value="INFRA">INFRA</option>
              <option value="AI Ecosystem">AI Ecosystem</option>
            </select>
            
            <select
              value={source}
              onChange={handleSourceChange}
              className="px-4 py-2 border border-gray-300 rounded-lg bg-white text-gray-900 hover:border-[#ff6b35] focus:outline-none focus:ring-2 focus:ring-[#ff6b35] font-medium"
            >
              <option value="">전체 출처</option>
              <option value="Naver">Naver</option>
              <option value="AITimes">AITimes</option>
              <option value="TheGuru">TheGuru</option>
            </select>
          </div>
        </div>

        {loading && (
          <div className="text-center py-12">
            <div className="text-gray-600">로딩 중...</div>
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-4">
            {error}
          </div>
        )}

        {!loading && !error && articles.length === 0 && (
          <div className="text-center py-12 text-gray-600">
            기사가 없습니다.
          </div>
        )}

        {!loading && !error && articles.length > 0 && (
          <>
            <div className="grid gap-6 mb-8">
              {articles.map((article) => (
                <ArticleCard key={article.articleId} article={article} />
              ))}
            </div>

            <div className="flex justify-center items-center gap-2 flex-wrap">
              {/* 첫 페이지 버튼 (항상 표시) */}
              {page > 0 && (
                <button
                  onClick={() => fetchArticles(0)}
                  className="px-3 py-2 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 font-medium"
                >
                  처음
                </button>
              )}

              {/* 이전 페이지 버튼 */}
              <button
                onClick={() => fetchArticles(page - 1)}
                disabled={page === 0}
                className="px-3 py-2 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
              >
                이전
              </button>

              {/* 페이지 번호 버튼들 */}
              {getPageNumbers().map((pageNum, idx) => {
                if (pageNum === "...") {
                  return (
                    <span key={`ellipsis-${idx}`} className="px-2 text-gray-500">
                      ...
                    </span>
                  );
                }
                const pageIndex = (pageNum as number) - 1;
                const isActive = page === pageIndex;
                return (
                  <button
                    key={pageNum}
                    onClick={() => fetchArticles(pageIndex)}
                    className={`px-3 py-2 rounded-lg font-medium transition-colors ${
                      isActive
                        ? "bg-[#ff6b35] text-white"
                        : "bg-white border border-gray-300 text-gray-700 hover:bg-gray-50"
                    }`}
                  >
                    {pageNum}
                  </button>
                );
              })}

              {/* 다음 페이지 버튼 */}
              <button
                onClick={() => fetchArticles(page + 1)}
                disabled={page >= totalPages - 1}
                className="px-3 py-2 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed font-medium"
              >
                다음
              </button>

              {/* 마지막 페이지 버튼 (항상 표시) */}
              {page < totalPages - 1 && (
                <button
                  onClick={() => fetchArticles(totalPages - 1)}
                  className="px-3 py-2 bg-white border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50 font-medium"
                >
                  마지막
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
