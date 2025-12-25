"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getFavoriteArticles } from "@/lib/api/articles";
import { isAuthenticated } from "@/lib/auth";
import type { ArticleResponse, ArticlePageResponse } from "@/types";
import ArticleCard from "@/components/ArticleCard";

export default function FavoritesPage() {
  const router = useRouter();
  const [articles, setArticles] = useState<ArticleResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    if (!isAuthenticated()) {
      router.push("/login");
      return;
    }
    fetchArticles(0);
  }, [router]);

  const fetchArticles = async (pageNum: number = 0) => {
    try {
      setLoading(true);
      setError(null);
      const response: ArticlePageResponse = await getFavoriteArticles({
        page: pageNum,
        size: 10,
        sort: "publishedAt,desc",
      });
      setArticles(response.content || []);
      setTotalPages(response.totalPages || 0);
      setPage(response.number || 0);
    } catch (err: any) {
      if (err.isNetworkError) {
        setError(err.message);
      } else if (err.status === 404 || err.status === 500) {
        setArticles([]);
        setTotalPages(0);
        setPage(0);
      } else {
        setError(err.message || "관심기사를 불러오는데 실패했습니다.");
      }
    } finally {
      setLoading(false);
    }
  };

  const handlePageChange = (newPage: number) => {
    if (newPage >= 0 && newPage < totalPages) {
      fetchArticles(newPage);
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
  };

  // 페이지 번호 범위 계산 (현재 페이지 중심으로 10개 표시)
  const getPageNumbers = () => {
    const pages: (number | string)[] = [];
    const maxVisible = 10;
    let start = Math.max(0, page - Math.floor(maxVisible / 2));
    let end = Math.min(totalPages - 1, start + maxVisible - 1);
    
    if (end - start < maxVisible - 1) {
      start = Math.max(0, end - maxVisible + 1);
    }

    if (start > 0) {
      pages.push(0);
      if (start > 1) pages.push("...");
    }

    for (let i = start; i <= end; i++) {
      pages.push(i);
    }

    if (end < totalPages - 1) {
      if (end < totalPages - 2) pages.push("...");
      pages.push(totalPages - 1);
    }

    return pages;
  };

  if (loading && articles.length === 0) {
    return (
      <div className="min-h-screen bg-gray-50 py-8">
        <div className="container mx-auto px-4">
          <h1 className="text-3xl font-bold mb-6 text-gray-900">관심기사</h1>
          <div className="text-center py-12">
            <p className="text-gray-600">로딩 중...</p>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 py-8">
        <div className="container mx-auto px-4">
          <h1 className="text-3xl font-bold mb-6 text-gray-900">관심기사</h1>
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
            {error}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="container mx-auto px-4">
        <h1 className="text-3xl font-bold mb-6 text-gray-900">관심기사</h1>

        {articles.length === 0 ? (
          <div className="bg-white rounded-lg shadow p-8 text-center">
            <p className="text-gray-600 text-lg">
              좋아요한 기사가 없습니다.
            </p>
            <p className="text-gray-500 mt-2">
              기사에 좋아요를 눌러 관심기사로 저장하세요.
            </p>
          </div>
        ) : (
          <>
            <div className="space-y-4 mb-8">
              {articles.map((article) => (
                <ArticleCard key={article.articleId} article={article} />
              ))}
            </div>

            {/* 페이지네이션 */}
            {totalPages > 1 && (
              <div className="flex justify-center items-center gap-2 mt-8">
                <button
                  onClick={() => handlePageChange(page - 1)}
                  disabled={page === 0}
                  className="px-4 py-2 border border-gray-300 rounded-lg bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  이전
                </button>

                {getPageNumbers().map((pageNum, idx) => {
                  if (pageNum === "...") {
                    return (
                      <span key={`ellipsis-${idx}`} className="px-2 text-gray-500">
                        ...
                      </span>
                    );
                  }
                  return (
                    <button
                      key={pageNum}
                      onClick={() => handlePageChange(pageNum as number)}
                      className={`px-4 py-2 border rounded-lg transition-colors ${
                        page === pageNum
                          ? "bg-[#ff6b35] text-white border-[#ff6b35]"
                          : "bg-white text-gray-700 border-gray-300 hover:bg-gray-50"
                      }`}
                    >
                      {(pageNum as number) + 1}
                    </button>
                  );
                })}

                <button
                  onClick={() => handlePageChange(page + 1)}
                  disabled={page >= totalPages - 1}
                  className="px-4 py-2 border border-gray-300 rounded-lg bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                >
                  다음
                </button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}

