"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { forgotPassword } from "@/lib/api/auth";

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);
  const [resetToken, setResetToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(false);
    setLoading(true);

    try {
      const response = await forgotPassword({ email });
      setResetToken(response.resetToken);
      setSuccess(true);
    } catch (err: any) {
      setError(err.message || "비밀번호 찾기에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleCopyToken = () => {
    if (resetToken) {
      navigator.clipboard.writeText(resetToken);
      alert("토큰이 클립보드에 복사되었습니다.");
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center py-12 px-4">
      <div className="max-w-md w-full bg-white rounded-lg shadow-lg p-8">
        <h1 className="text-2xl font-bold text-center mb-6 text-gray-900">
          비밀번호 찾기
        </h1>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-4">
            {error}
          </div>
        )}

        {success && resetToken && (
          <div className="bg-green-50 border border-green-200 text-green-700 px-4 py-3 rounded-lg mb-4">
            <p className="font-semibold mb-2">비밀번호 재설정 토큰이 생성되었습니다.</p>
            <div className="bg-white p-3 rounded border border-green-300 mb-3">
              <p className="text-xs text-gray-600 mb-1">재설정 토큰:</p>
              <p className="text-sm font-mono break-all">{resetToken}</p>
            </div>
            <button
              onClick={handleCopyToken}
              className="text-sm text-green-700 hover:underline mb-2"
            >
              토큰 복사하기
            </button>
            <p className="text-sm mt-2">
              이 토큰을 사용하여{" "}
              <Link href={`/reset-password?token=${encodeURIComponent(resetToken)}`} className="text-green-700 hover:underline font-semibold">
                비밀번호를 재설정
              </Link>
              하세요.
            </p>
          </div>
        )}

        {!success && (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-2">
                이메일
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                className="w-full px-4 py-2 border border-gray-300 rounded-lg bg-white text-gray-900 focus:outline-none focus:ring-2 focus:ring-[#ff6b35]"
                placeholder="user@example.com"
              />
              <p className="text-xs text-gray-500 mt-2">
                등록된 이메일 주소를 입력하시면 비밀번호 재설정 토큰을 받으실 수 있습니다.
              </p>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2 bg-[#ff6b35] text-white rounded-lg hover:bg-[#e55a2b] disabled:opacity-50 disabled:cursor-not-allowed font-medium transition-colors"
            >
              {loading ? "처리 중..." : "비밀번호 찾기"}
            </button>
          </form>
        )}

        <div className="mt-6 text-center">
          <Link href="/login" className="text-sm text-[#ff6b35] hover:underline">
            로그인으로 돌아가기
          </Link>
        </div>
      </div>
    </div>
  );
}

