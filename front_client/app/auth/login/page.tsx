'use client';

import { useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { loginUser } from '@/lib/api';

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect') || '/appointments';

  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const data = await loginUser(phone, password);
      const tokens = (data as { tokens: { access: string; refresh: string } }).tokens;
      localStorage.setItem('access_token', tokens.access);
      localStorage.setItem('refresh_token', tokens.refresh);
      router.push(redirect);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'خطا در ورود');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div style={{ textAlign: 'center', marginBottom: 28 }}>
          <div style={{ fontSize: 48, marginBottom: 8 }}>🗓</div>
          <h1 style={{ fontSize: 20, fontWeight: 700 }}>نوبت‌یار</h1>
          <p style={{ marginTop: 6, fontSize: 13 }}>برای ثبت نوبت وارد شوید</p>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">شماره موبایل</label>
            <input
              className="form-input"
              type="tel"
              placeholder="09xxxxxxxxx"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              required
              dir="ltr"
            />
          </div>
          <div className="form-group">
            <label className="form-label">رمز عبور</label>
            <input
              className="form-input"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          {error && (
            <div style={{
              background: '#fef2f2', border: '1px solid #fecaca',
              borderRadius: 10, padding: '10px 14px',
              color: '#dc2626', fontSize: 13, marginBottom: 16, textAlign: 'right',
            }}>
              {error}
            </div>
          )}

          <button className="btn-primary" type="submit" disabled={loading}>
            {loading ? 'در حال ورود...' : 'ورود'}
          </button>
        </form>

        <div style={{ textAlign: 'center', marginTop: 20 }}>
          <span style={{ fontSize: 13, color: '#6b7280' }}>حساب ندارید؟ </span>
          <button
            onClick={() => router.push(`/auth/register?redirect=${redirect}`)}
            style={{
              background: 'none', border: 'none', cursor: 'pointer',
              color: '#d735a9', fontWeight: 600, fontSize: 13, fontFamily: 'inherit',
            }}
          >
            ثبت‌نام
          </button>
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
