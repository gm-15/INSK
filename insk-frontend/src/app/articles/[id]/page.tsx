"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import {
  getArticleDetail,
  getArticleScore,
  downloadArticlePdf,
} from "@/lib/api/articles";
import { getFeedbackSummary, createFeedback } from "@/lib/api/feedback";
import type {
  ArticleDetailResponse,
  ArticleScoreResponse,
  ArticleFeedbackSummaryResponse,
} from "@/types";
import { isAuthenticated } from "@/lib/auth";

export default function ArticleDetailPage() {
  const params = useParams();
  const router = useRouter();
  const articleId = Number(params.id);

  const [article, setArticle] = useState<ArticleDetailResponse | null>(null);
  const [score, setScore] = useState<ArticleScoreResponse | null>(null);
  const [feedback, setFeedback] = useState<ArticleFeedbackSummaryResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [feedbackText, setFeedbackText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    setAuthenticated(isAuthenticated());
  }, []);

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        setError(null);

        const [articleData, scoreData, feedbackData] = await Promise.all([
          getArticleDetail(articleId),
          getArticleScore(articleId).catch(() => null),
          getFeedbackSummary(articleId).catch(() => null),
        ]);

        setArticle(articleData);
        setScore(scoreData);
        setFeedback(feedbackData);
      } catch (err: any) {
        setError(err.message || "기사를 불러오는데 실패했습니다.");
      } finally {
        setLoading(false);
      }
    };

    if (articleId) {
      fetchData();
    }
  }, [articleId]);

  const handleLike = async (liked: boolean) => {
    if (!authenticated) {
      router.push("/login");
      return;
    }

    try {
      setSubmitting(true);
      // 백엔드에서 자동으로 중복 체크 및 토글 처리
      await createFeedback(articleId, { liked, feedbackText: null });
      // 피드백 다시 불러오기
      const feedbackData = await getFeedbackSummary(articleId);
      setFeedback(feedbackData);
    } catch (err: any) {
      alert(err.message || "피드백 등록에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleTextFeedback = async () => {
    if (!authenticated) {
      router.push("/login");
      return;
    }

    if (!feedbackText.trim()) {
      alert("피드백 내용을 입력해주세요.");
      return;
    }

    try {
      setSubmitting(true);
      await createFeedback(articleId, {
        liked: null,
        feedbackText: feedbackText.trim(),
      });
      setFeedbackText("");
      // 피드백 다시 불러오기
      const feedbackData = await getFeedbackSummary(articleId);
      setFeedback(feedbackData);
    } catch (err: any) {
      alert(err.message || "피드백 등록에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDownloadPdf = async () => {
    try {
      await downloadArticlePdf(articleId);
    } catch (err: any) {
      alert(err.message || "PDF 다운로드에 실패했습니다.");
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString("ko-KR", {
      year: "numeric",
      month: "long",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="container mx-auto px-4 py-8">
          <div className="text-center">로딩 중...</div>
        </div>
      </div>
    );
  }

  if (error || !article) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="container mx-auto px-4 py-8">
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
            {error || "기사를 찾을 수 없습니다."}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="container mx-auto px-4 py-8 max-w-4xl">
        <button
          onClick={() => router.back()}
          className="mb-4 text-[#ff6b35] hover:underline"
        >
          ← 목록으로
        </button>

        <article className="bg-white rounded-lg shadow-lg p-8 mb-8">
          <div className="mb-6">
            <div className="flex items-center justify-between mb-4">
              <h1 className="text-3xl font-bold text-gray-900">
                {article.title}
              </h1>
              <span className="px-3 py-1 text-sm bg-[#ff6b35] text-white rounded">
                {article.category || "기타"}
              </span>
            </div>

            <div className="flex items-center gap-4 text-sm text-gray-600 mb-4">
              <span>출처: {article.source || "알 수 없음"}</span>
              <span>•</span>
              <span>{formatDate(article.publishedAt)}</span>
            </div>

            {score && (
              <div className="flex items-center gap-6 p-4 bg-gray-50 rounded-lg mb-4">
                <div>
                  <span className="text-sm text-gray-600">점수: </span>
                  <span className="text-lg font-bold text-[#ff6b35]">
                    {score.score.toFixed(2)}
                  </span>
                </div>
                <div>
                  <span className="text-sm text-gray-600">좋아요: </span>
                  <span className="font-semibold">{score.likeCount}</span>
                </div>
                <div>
                  <span className="text-sm text-gray-600">싫어요: </span>
                  <span className="font-semibold">{score.dislikeCount}</span>
                </div>
                <div>
                  <span className="text-sm text-gray-600">조회수: </span>
                  <span className="font-semibold">{score.viewCount}</span>
                </div>
              </div>
            )}
          </div>

          {article.summary && (
            <section className="mb-6">
              <h2 className="text-xl font-semibold text-gray-900 mb-2">
                요약
              </h2>
              <p className="text-gray-700 leading-relaxed">
                {article.summary}
              </p>
            </section>
          )}

          {article.insight && (
            <section className="mb-6">
              <h2 className="text-xl font-semibold text-gray-900 mb-2">
                인사이트
              </h2>
              <p className="text-gray-700 leading-relaxed">
                {article.insight}
              </p>
            </section>
          )}

          {article.tags && article.tags !== "[]" && (
            <section className="mb-6">
              <h2 className="text-xl font-semibold text-gray-900 mb-2">
                태그
              </h2>
              <div className="flex flex-wrap gap-2">
                {(() => {
                  try {
                    const parsed = JSON.parse(article.tags);
                    // 배열인지 확인
                    if (Array.isArray(parsed)) {
                      return parsed.map((tag: string, idx: number) => (
                        <span
                          key={idx}
                          className="px-2 py-1 text-sm bg-[#ff6b35] text-white rounded"
                        >
                          {tag}
                        </span>
                      ));
                    }
                    // 객체인 경우 tags 배열 추출 시도
                    if (typeof parsed === 'object' && parsed !== null && 'tags' in parsed && Array.isArray(parsed.tags)) {
                      return parsed.tags.map((tag: string, idx: number) => (
                        <span
                          key={idx}
                          className="px-2 py-1 text-sm bg-[#ff6b35] text-white rounded"
                        >
                          {tag}
                        </span>
                      ));
                    }
                    // 문자열인 경우
                    if (typeof parsed === 'string') {
                      return (
                        <span className="px-2 py-1 text-sm bg-[#ff6b35] text-white rounded">
                          {parsed}
                        </span>
                      );
                    }
                    return null;
                  } catch (e) {
                    // JSON 파싱 실패 시 원본 문자열 표시
                    return (
                      <span className="px-2 py-1 text-sm bg-[#ff6b35] text-white rounded">
                        {article.tags}
                      </span>
                    );
                  }
                })()}
              </div>
            </section>
          )}

          <div className="mt-6 pt-6 border-t border-gray-200">
            <a
              href={article.originalUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="text-[#ff6b35] hover:underline"
            >
              원문 보기 →
            </a>
          </div>

          <div className="mt-6 flex gap-4">
            <button
              onClick={handleDownloadPdf}
              className="px-4 py-2 bg-gray-600 text-white rounded-lg hover:bg-gray-700 transition-colors"
            >
              PDF 다운로드
            </button>
          </div>
        </article>

        {/* 피드백 섹션 */}
        <div className="bg-white rounded-lg shadow-lg p-8">
          <h2 className="text-2xl font-semibold text-gray-900 mb-4">
            피드백
          </h2>

          {feedback && (
            <div className="mb-6">
              <div className="flex items-center gap-6 mb-4">
                <button
                  onClick={() => handleLike(true)}
                  disabled={submitting}
                  className={`px-6 py-2 rounded-lg font-medium transition-colors ${
                    feedback.myFeedback?.liked === true
                      ? "bg-[#ff6b35] text-white"
                      : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                  } disabled:opacity-50`}
                >
                  👍 좋아요 ({feedback.likes})
                </button>
                <button
                  onClick={() => handleLike(false)}
                  disabled={submitting}
                  className={`px-6 py-2 rounded-lg font-medium transition-colors ${
                    feedback.myFeedback?.liked === false
                      ? "bg-red-500 text-white"
                      : "bg-gray-100 text-gray-700 hover:bg-gray-200"
                  } disabled:opacity-50`}
                >
                  👎 싫어요 ({feedback.dislikes})
                </button>
              </div>

              {feedback.recentComments.length > 0 && (
                <div className="mb-4">
                  <h3 className="font-semibold text-gray-900 mb-2">
                    최근 댓글
                  </h3>
                  <div className="space-y-2">
                    {feedback.recentComments.map((comment, idx) => (
                      <div
                        key={idx}
                        className="p-3 bg-gray-50 rounded text-gray-700"
                      >
                        {comment}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}

          {authenticated ? (
            <div>
              <textarea
                value={feedbackText}
                onChange={(e) => setFeedbackText(e.target.value)}
                placeholder="피드백을 입력하세요..."
                className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-white text-gray-900 mb-2 focus:outline-none focus:ring-2 focus:ring-[#ff6b35]"
                rows={4}
              />
              <button
                onClick={handleTextFeedback}
                disabled={submitting || !feedbackText.trim()}
                className="px-4 py-2 bg-[#ff6b35] text-white rounded-lg hover:bg-[#e55a2b] disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {submitting ? "등록 중..." : "피드백 등록"}
              </button>
            </div>
          ) : (
            <div className="text-center py-4">
              <p className="text-gray-600 mb-2">
                피드백을 남기려면 로그인이 필요합니다.
              </p>
              <button
                onClick={() => router.push("/login")}
                className="px-4 py-2 bg-[#ff6b35] text-white rounded-lg hover:bg-[#e55a2b] transition-colors"
              >
                로그인
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
