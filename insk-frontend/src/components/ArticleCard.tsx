"use client";

import Link from "next/link";
import type { ArticleResponse } from "@/types";

interface ArticleCardProps {
  article: ArticleResponse;
}

export default function ArticleCard({ article }: ArticleCardProps) {
  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString("ko-KR", {
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  };

  return (
    <Link href={`/articles/${article.articleId}`}>
      <div className="bg-white rounded-lg shadow-md p-6 hover:shadow-lg transition-shadow cursor-pointer border border-gray-200 hover:border-[#ff6b35]">
        <div className="flex items-start justify-between mb-3">
          <h3 className="text-xl font-semibold text-gray-900 line-clamp-2 flex-1">
            {article.title}
          </h3>
          <span className="ml-4 px-2 py-1 text-xs bg-[#ff6b35] text-white rounded">
            {article.category || "기타"}
          </span>
        </div>
        
        {article.summary && (
          <p className="text-gray-600 mb-4 line-clamp-2">
            {article.summary}
          </p>
        )}
        
        <div className="flex items-center justify-between text-sm text-gray-500">
          <div className="flex items-center gap-4">
            <span>출처: {article.source || "알 수 없음"}</span>
            <span>•</span>
            <span>{formatDate(article.publishedAt)}</span>
          </div>
          <span className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded">
            {article.language || "ko"}
          </span>
        </div>
      </div>
    </Link>
  );
}

