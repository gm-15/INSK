"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  getApprovedKeywords,
  getOtherUsersKeywords,
  createKeyword,
  deleteKeyword,
  recommendKeywords,
  approveKeyword,
  rejectKeyword,
  type OtherUsersKeywordResponse,
} from "@/lib/api/keywords";
import { runPipeline } from "@/lib/api/articles";
import type {
  KeywordResponse,
  KeywordRecommendResponse,
  DepartmentType,
} from "@/types";
import { isAuthenticated } from "@/lib/auth";

const departments: { value: DepartmentType; label: string }[] = [
  { value: "T_CLOUD", label: "T Cloud" },
  { value: "T_NETWORK_INFRA", label: "T Network Infra" },
  { value: "T_HR", label: "T HR" },
  { value: "T_AI_SERVICE", label: "T AI Service" },
  { value: "T_MARKETING", label: "T Marketing" },
  { value: "T_STRATEGY", label: "T Strategy" },
  { value: "T_ENTERPRISE_B2B", label: "T Enterprise B2B" },
  { value: "T_PLATFORM_DEV", label: "T Platform Dev" },
  { value: "T_TELCO_MNO", label: "T Telco MNO" },
  { value: "T_FINANCE", label: "T Finance" },
];

export default function KeywordsPage() {
  const router = useRouter();
  const [keywords, setKeywords] = useState<KeywordResponse[]>([]);
  const [otherUsersKeywords, setOtherUsersKeywords] = useState<OtherUsersKeywordResponse[]>([]);
  const [recommendations, setRecommendations] = useState<KeywordRecommendResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingOthers, setLoadingOthers] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [newKeyword, setNewKeyword] = useState("");
  const [selectedDepartment, setSelectedDepartment] = useState<DepartmentType>("T_CLOUD");
  const [authenticated, setAuthenticated] = useState(false);
  const [recommending, setRecommending] = useState(false);
  const [pipelineRunning, setPipelineRunning] = useState(false);

  useEffect(() => {
    const checkAuth = () => {
      if (!isAuthenticated()) {
        router.push("/login");
        return;
      }
      setAuthenticated(true);
    };
    checkAuth();
  }, [router]);

  useEffect(() => {
    if (authenticated) {
      fetchKeywords();
      fetchOtherUsersKeywords();
    }
  }, [authenticated]);

  const fetchKeywords = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await getApprovedKeywords();
      setKeywords(data);
    } catch (err: any) {
      setError(err.message || "키워드를 불러오는데 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const fetchOtherUsersKeywords = async () => {
    try {
      setLoadingOthers(true);
      const data = await getOtherUsersKeywords();
      setOtherUsersKeywords(data);
    } catch (err: any) {
      // 다른 사용자 키워드 로딩 실패는 조용히 처리
      console.error("다른 사용자 키워드 로딩 실패:", err);
      setOtherUsersKeywords([]);
    } finally {
      setLoadingOthers(false);
    }
  };

  const handleCreateKeyword = async () => {
    if (!newKeyword.trim()) {
      alert("키워드를 입력해주세요.");
      return;
    }

    try {
      await createKeyword({ keyword: newKeyword.trim() });
      setNewKeyword("");
      fetchKeywords();
      fetchOtherUsersKeywords(); // 다른 사용자 키워드도 새로고침
    } catch (err: any) {
      alert(err.message || "키워드 생성에 실패했습니다.");
    }
  };

  const handleDeleteKeyword = async (keywordId: number) => {
    if (!confirm("정말 삭제하시겠습니까?")) return;

    try {
      await deleteKeyword(keywordId);
      fetchKeywords();
      fetchOtherUsersKeywords(); // 다른 사용자 키워드도 새로고침
    } catch (err: any) {
      alert(err.message || "키워드 삭제에 실패했습니다.");
    }
  };

  const handleRecommend = async () => {
    try {
      setRecommending(true);
      setError(null);
      const data = await recommendKeywords({
        department: selectedDepartment,
        limit: 10,
      });
      setRecommendations(data);
      if (data.recommended.length === 0) {
        alert("추천할 키워드가 없습니다. 최근 뉴스가 부족할 수 있습니다.");
      }
    } catch (err: any) {
      setError(err.message || "키워드 추천에 실패했습니다.");
      alert(err.message || "키워드 추천에 실패했습니다.");
    } finally {
      setRecommending(false);
    }
  };

  const handleApprove = async (keyword: string, category: string) => {
    try {
      await approveKeyword({ keyword, category });
      alert("키워드가 승인되었습니다.");
      fetchKeywords(); // 승인된 키워드 목록 새로고침
      fetchOtherUsersKeywords(); // 다른 사용자 키워드도 새로고침 (중복 제거 반영)
      setRecommendations(null);
    } catch (err: any) {
      alert(err.message || "키워드 승인에 실패했습니다.");
    }
  };

  const handleReject = async (keyword: string) => {
    try {
      await rejectKeyword({ keyword });
      alert("키워드가 거부되었습니다.");
      if (recommendations) {
        setRecommendations({
          recommended: recommendations.recommended.filter(
            (r) => r.keyword !== keyword
          ),
        });
      }
    } catch (err: any) {
      alert(err.message || "키워드 거부에 실패했습니다.");
    }
  };

  const handleRunPipeline = async () => {
    if (!confirm("뉴스 파이프라인을 실행하시겠습니까? 약 2-3분 후 자동으로 반영됩니다.")) return;

    try {
      setPipelineRunning(true);
      setError(null);
      
      // 파이프라인 실행 전 최신 기사 ID 저장
      const { getArticles } = await import("@/lib/api/articles");
      const beforeResponse = await getArticles({ page: 0, size: 1 });
      const beforeArticleId = beforeResponse.content && beforeResponse.content.length > 0 
        ? beforeResponse.content[0].articleId 
        : null;
      const pipelineStartTime = Date.now();
      
      const message = await runPipeline();
      alert(message || "뉴스 파이프라인 실행이 시작되었습니다. 약 2-3분 후 자동으로 반영됩니다.");
      
      // 파이프라인 완료 감지를 위한 폴링 시작
      startPipelinePolling(beforeArticleId, pipelineStartTime);
    } catch (err: any) {
      setError(err.message || "파이프라인 실행에 실패했습니다.");
      alert(err.message || "파이프라인 실행에 실패했습니다.");
      setPipelineRunning(false);
    }
  };

  const startPipelinePolling = (beforeArticleId: number | null, startTime: number) => {
    // 파이프라인 완료 감지를 위해 주기적으로 기사 목록 확인
    let checkCount = 0;
    const maxChecks = 30; // 최대 5분간 확인 (10초마다)
    
    const checkInterval = setInterval(async () => {
      checkCount++;
      
      try {
        // 기사 목록을 확인하여 새 기사가 추가되었는지 확인
        const { getArticles } = await import("@/lib/api/articles");
        const response = await getArticles({ page: 0, size: 10 });
        
        // 새 기사가 추가되었는지 확인 (최신 기사 ID가 변경되었거나, 파이프라인 시작 후 생성된 기사가 있는지)
        const hasNewArticles = response.content && response.content.length > 0 && (
          beforeArticleId === null || 
          response.content[0].articleId !== beforeArticleId ||
          response.content.some(article => {
            // 파이프라인 시작 후 생성된 기사인지 확인 (대략적인 시간 비교)
            return article.publishedAt && new Date(article.publishedAt).getTime() > startTime - 60000;
          })
        );
        
        // 새 기사가 추가되었거나 최대 확인 횟수에 도달하면 알림
        if (hasNewArticles || checkCount >= maxChecks) {
          clearInterval(checkInterval);
          setPipelineRunning(false);
          
          if (hasNewArticles && checkCount < maxChecks) {
            // 새 기사가 추가된 경우
            if (Notification.permission === "granted") {
              new Notification("뉴스 파이프라인 완료", {
                body: "새로운 기사가 추가되었습니다. 기사 목록을 확인해주세요.",
                icon: "/favicon.ico",
              });
            } else if (Notification.permission !== "denied") {
              Notification.requestPermission().then((permission) => {
                if (permission === "granted") {
                  new Notification("뉴스 파이프라인 완료", {
                    body: "새로운 기사가 추가되었습니다. 기사 목록을 확인해주세요.",
                    icon: "/favicon.ico",
                  });
                }
              });
            }
            
            alert("뉴스 파이프라인이 완료되었습니다! 새로운 기사가 추가되었습니다.");
          } else if (checkCount >= maxChecks) {
            // 타임아웃
            alert("뉴스 파이프라인 처리가 완료되었습니다. 기사 목록을 확인해주세요.");
          }
        }
      } catch (err) {
        // 에러 발생 시에도 계속 확인
        console.error("파이프라인 상태 확인 중 오류:", err);
        if (checkCount >= maxChecks) {
          clearInterval(checkInterval);
          setPipelineRunning(false);
        }
      }
    }, 10000); // 10초마다 확인
  };

  if (!authenticated) {
    return null;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="container mx-auto px-4 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-4">
            키워드 관리
          </h1>
          <div className="flex gap-4 items-center">
            <button
              onClick={handleRunPipeline}
              disabled={pipelineRunning}
              className="px-4 py-2 bg-[#ff6b35] text-white rounded-lg hover:bg-[#e55a2b] disabled:opacity-50 disabled:cursor-not-allowed transition-colors font-medium flex items-center gap-2"
            >
              {pipelineRunning ? (
                <>
                  <span className="animate-spin">⏳</span>
                  <span>파이프라인 실행 중...</span>
                </>
              ) : (
                "뉴스 파이프라인 실행"
              )}
            </button>
          </div>
        </div>

        {/* 키워드 추가 */}
        <div className="bg-white rounded-lg shadow-lg p-6 mb-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">
            키워드 추가
          </h2>
          <div className="flex gap-2">
            <input
              type="text"
              value={newKeyword}
              onChange={(e) => setNewKeyword(e.target.value)}
              onKeyPress={(e) => e.key === "Enter" && handleCreateKeyword()}
              placeholder="새 키워드를 입력하세요"
              className="flex-1 px-4 py-2 border border-gray-300 rounded-lg bg-white text-gray-900 focus:outline-none focus:ring-2 focus:ring-[#ff6b35]"
            />
            <button
              onClick={handleCreateKeyword}
              className="px-6 py-2 bg-[#ff6b35] text-white rounded-lg hover:bg-[#e55a2b] transition-colors font-medium"
            >
              추가
            </button>
          </div>
        </div>

        {/* 키워드 추천 */}
        <div className="bg-white rounded-lg shadow-lg p-6 mb-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">
            AI 키워드 추천
          </h2>
          <div className="flex gap-2 mb-4">
            <select
              value={selectedDepartment}
              onChange={(e) => setSelectedDepartment(e.target.value as DepartmentType)}
              className="px-4 py-2 border border-gray-300 rounded-lg bg-white text-gray-900 focus:outline-none focus:ring-2 focus:ring-[#ff6b35]"
            >
              {departments.map((dept) => (
                <option key={dept.value} value={dept.value}>
                  {dept.label}
                </option>
              ))}
            </select>
            <button
              onClick={handleRecommend}
              disabled={recommending}
              className="px-6 py-2 bg-[#ff6b35] text-white rounded-lg hover:bg-[#e55a2b] disabled:opacity-50 disabled:cursor-not-allowed transition-colors font-medium flex items-center gap-2"
            >
              {recommending ? (
                <>
                  <span className="animate-spin">⏳</span>
                  <span>AI 추천 중...</span>
                </>
              ) : (
                "추천 받기"
              )}
            </button>
          </div>

          {recommendations && recommendations.recommended.length > 0 && (
            <div className="space-y-2">
              <h3 className="font-semibold text-gray-900">
                추천 키워드:
              </h3>
              {recommendations.recommended.map((rec, idx) => (
                <div
                  key={idx}
                  className="flex items-center justify-between p-3 bg-gray-50 rounded"
                >
                  <div>
                    <span className="font-medium text-gray-900">
                      {rec.keyword}
                    </span>
                    <span className="ml-2 text-sm text-gray-600">
                      ({rec.category})
                    </span>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => handleApprove(rec.keyword, rec.category)}
                      className="px-3 py-1 bg-[#ff6b35] text-white rounded-lg hover:bg-[#e55a2b] transition-colors text-sm font-medium"
                    >
                      승인
                    </button>
                    <button
                      onClick={() => handleReject(rec.keyword)}
                      className="px-3 py-1 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors text-sm font-medium"
                    >
                      거부
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 승인된 키워드 */}
        <div className="bg-white rounded-lg shadow-lg p-6 mb-6">
          <h2 className="text-xl font-semibold text-gray-900 mb-4">
            승인된 키워드
          </h2>

          {loading && <div className="text-center py-8">로딩 중...</div>}

          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-4">
              {error}
            </div>
          )}

          {!loading && !error && keywords.length === 0 && (
            <div className="text-center py-8 text-gray-600">
              승인된 키워드가 없습니다.
            </div>
          )}

          {!loading && !error && keywords.length > 0 && (
            <div className="space-y-2">
              {keywords.map((keyword) => (
                <div
                  key={keyword.keywordId}
                  className="flex items-center justify-between p-4 bg-gray-50 rounded"
                >
                  <div className="flex items-center gap-3">
                    <span className="font-medium text-gray-900">
                      {keyword.keyword}
                    </span>
                    <span className="px-2 py-1 text-xs bg-[#ff6b35] text-white rounded">
                      승인됨
                    </span>
                  </div>
                  <button
                    onClick={() => handleDeleteKeyword(keyword.keywordId)}
                    className="px-3 py-1 bg-red-500 text-white rounded-lg hover:bg-red-600 transition-colors text-sm font-medium"
                  >
                    삭제
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 다른 사용자가 추가한 키워드 */}
        {otherUsersKeywords.length > 0 && (
          <div className="bg-white rounded-lg shadow-lg p-6">
            <h2 className="text-xl font-semibold text-gray-900 mb-4">
              다른 사용자가 추가한 키워드는 이런게 있어요
            </h2>

            {loadingOthers && <div className="text-center py-4 text-sm text-gray-500">로딩 중...</div>}

            {!loadingOthers && (
              <div className="space-y-2">
                {otherUsersKeywords.map((keyword, index) => (
                  <div
                    key={`${keyword.keyword}-${index}`}
                    className="flex items-center justify-between p-4 bg-gray-50 rounded"
                  >
                    <div className="flex items-center gap-3">
                      <span className="font-medium text-gray-900">
                        {keyword.keyword}
                      </span>
                      {keyword.approved && (
                        <span className="px-2 py-1 text-xs bg-[#ff6b35] text-white rounded">
                          승인됨
                        </span>
                      )}
                      {keyword.count > 1 && (
                        <span className="px-2 py-1 text-xs bg-blue-100 text-blue-800 rounded">
                          {keyword.count}회 추가됨
                        </span>
                      )}
                    </div>
                    <span className="text-sm text-gray-500">참고용</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
