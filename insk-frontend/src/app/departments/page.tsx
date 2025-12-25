"use client";

import { useState, useEffect } from "react";
import Link from "next/link";
import { getTop5ByDepartment } from "@/lib/api/articles";
import type { ArticleSimpleResponse, DepartmentType } from "@/types";

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

export default function DepartmentsPage() {
  const [selectedDepartment, setSelectedDepartment] = useState<DepartmentType>("T_CLOUD");
  const [articles, setArticles] = useState<ArticleSimpleResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleDepartmentChange = async (dept: DepartmentType) => {
    setSelectedDepartment(dept);
    setLoading(true);
    setError(null);

    try {
      const data = await getTop5ByDepartment(dept);
      setArticles(data);
    } catch (err: any) {
      setError(err.message || "기사를 불러오는데 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  // 초기 로드
  useEffect(() => {
    handleDepartmentChange("T_CLOUD");
  }, []);

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="container mx-auto px-4 py-8">
        <h1 className="text-3xl font-bold text-gray-900 mb-8">
          부서별 Top5 기사
        </h1>

        <div className="mb-6">
          <label className="block text-sm font-medium text-gray-700 mb-2">
            부서 선택
          </label>
          <select
            value={selectedDepartment}
            onChange={(e) => handleDepartmentChange(e.target.value as DepartmentType)}
            className="px-4 py-2 border border-gray-300 rounded-lg bg-white text-gray-900 focus:outline-none focus:ring-2 focus:ring-[#ff6b35]"
          >
            {departments.map((dept) => (
              <option key={dept.value} value={dept.value}>
                {dept.label}
              </option>
            ))}
          </select>
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
            해당 부서의 Top5 기사가 없습니다.
          </div>
        )}

        {!loading && !error && articles.length > 0 && (
          <div className="space-y-4">
            {articles.map((article, index) => (
              <div
                key={article.articleId}
                className="bg-white rounded-lg shadow-md p-6 hover:shadow-lg transition-shadow border border-gray-200 hover:border-[#ff6b35]"
              >
                <Link
                  href={`/articles/${article.articleId}`}
                  className="block"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <div className="flex items-center gap-3 mb-2">
                        <span className="px-3 py-1 text-sm font-bold bg-[#ff6b35] text-white rounded">
                          #{index + 1}
                        </span>
                        <span className="px-2 py-1 text-xs bg-gray-100 text-gray-700 rounded">
                          점수: {article.score.toFixed(2)}
                        </span>
                      </div>
                      <h3 className="text-xl font-semibold text-gray-900 mb-2">
                        {article.title}
                      </h3>
                    </div>
                  </div>
                </Link>
                <a
                  href={article.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-sm text-[#ff6b35] hover:underline mt-2 block"
                >
                  {article.url}
                </a>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
