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
    <div className="page-content" style={{ background: '#f9fafb', minHeight: '100dvh' }}>
      
      {/* ── Header ── */}
      <div style={{ padding: '24px 24px 16px', display: 'flex', justifyContent: 'flex-end' }}>
        <h1 style={{ fontSize: 18, fontWeight: 700, color: '#111827' }}>ورود مهمان</h1>
      </div>

      <div style={{ padding: '0 24px' }}>
        <p style={{ fontSize: 13, color: '#6b7280', textAlign: 'right', marginBottom: 24 }}>
          برای رزرو نوبت، شماره موبایل و رمز عبور خود را وارد کنید
        </p>

        <form onSubmit={handleSubmit}>
          <div className="form-group" style={{ marginBottom: 16 }}>
            <input
              className="form-input"
              type="tel"
              placeholder="۰۹۱۲ ۳۴۵ ۶۷۸۹"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              required
              dir="ltr"
              style={{ textAlign: 'center', height: 56, borderRadius: 14, fontSize: 15 }}
            />
          </div>
          
          <div className="form-group" style={{ marginBottom: 24 }}>
            <input
              className="form-input"
              type="password"
              placeholder="رمز عبور"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              style={{ textAlign: 'center', height: 56, borderRadius: 14, fontSize: 15 }}
            />
          </div>

          <p style={{ fontSize: 11, color: '#6b7280', textAlign: 'center', marginBottom: 24, lineHeight: 1.6 }}>
            با ورود، قوانین و حریم خصوصی نوبت‌یار را می‌پذیرید.
          </p>

          {error && (
            <div style={{
              background: '#fef2f2', border: '1px solid #fecaca',
              borderRadius: 10, padding: '10px 14px',
              color: '#dc2626', fontSize: 13, marginBottom: 16, textAlign: 'right',
            }}>
              {error}
            </div>
          )}

          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <span style={{ fontSize: 13, color: '#6b7280' }}>حساب ندارید؟ </span>
            <button
              type="button"
              onClick={() => router.push(`/auth/register?redirect=${redirect}`)}
              style={{
                background: 'none', border: 'none', cursor: 'pointer',
                color: '#d735a9', fontWeight: 600, fontSize: 13, fontFamily: 'inherit',
              }}
            >
              ثبت‌نام
            </button>
          </div>

          {/* ── Fixed Bottom Button ── */}
          <div className="btn-group">
            <button className="btn-primary" type="submit" disabled={loading}>
              {loading ? 'در حال ورود...' : 'ورود به حساب'}
            </button>
          </div>
        </form>
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
