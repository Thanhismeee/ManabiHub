import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { consumePostLoginRoute, storeAuthToken } from '../../shared/auth/authSession';

export function AuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const token = searchParams.get('token');
    if (!token) {
      setError('No authentication token found in the URL.');
      return;
    }

    const session = storeAuthToken('public', token);
    if (!session) {
      setError('Token đăng nhập không hợp lệ hoặc đã hết hạn.');
      return;
    }

    navigate(consumePostLoginRoute('public', session), { replace: true });
  }, [searchParams, navigate]);

  if (error) {
    return (
      <div className="flex h-screen items-center justify-center bg-gray-50">
        <div className="rounded-lg bg-white p-8 text-center shadow-lg">
          <h2 className="mb-4 text-2xl font-bold text-red-600">Authentication Failed</h2>
          <p className="text-gray-700">{error}</p>
          <button
            onClick={() => navigate('/login')}
            className="mt-6 rounded bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
          >
            Back to Login
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex h-screen items-center justify-center bg-gray-50">
      <div className="text-center">
        <div className="mb-4 inline-block h-8 w-8 animate-spin rounded-full border-4 border-solid border-blue-600 border-r-transparent"></div>
        <p className="text-lg font-medium text-gray-700">Authenticating...</p>
      </div>
    </div>
  );
}
