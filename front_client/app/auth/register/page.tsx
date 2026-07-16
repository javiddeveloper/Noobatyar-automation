'use client';

import { useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { registerUser, loginUser } from '@/lib/api';

function RegisterForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect') || '/appointments';

  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      await registerUser(phone, password, name);
      // Auto-login after register
      const data = await loginUser(phone, password);
      const tokens = (data as { tokens: { access: string; refresh: string } }).tokens;
      localStorage.setItem('access_token', tokens.access);
      localStorage.setItem('refresh_token', tokens.refresh);
      router.push(redirect);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'خطا در ثبت‌نام');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page-content" style={{ background: '#f9fafb', minHeight: '100dvh' }}>
      
      {/* ── Header ── */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '16px 24px', background: 'white',
        position: 'sticky', top: 0, zIndex: 50, marginBottom: 16
      }}>
        <button
          type="button"
          onClick={() => router.back()}
          style={{
            background: 'none', border: 'none', cursor: 'pointer',
            fontSize: 22, color: '#111827', padding: '4px 8px',
          }}
        >
          ←
        </button>
        <h1 style={{ fontSize: 16, fontWeight: 700, color: '#111827' }}>
          ثبت‌نام
        </h1>
      </div>

      <div style={{ padding: '0 24px' }}>
        <form onSubmit={handleSubmit}>
          
          <div className="form-group" style={{ marginBottom: 20 }}>
            <label style={{ display: 'block', textAlign: 'right', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 8 }}>
              نام و نام خانوادگی
            </label>
            <input
              className="form-input"
              type="text"
              placeholder="مثلاً: زهرا احمدی"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              style={{ height: 56, borderRadius: 14, fontSize: 15 }}
            />
          </div>

          <div className="form-group" style={{ marginBottom: 20 }}>
            <label style={{ display: 'block', textAlign: 'right', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 8 }}>
              شماره موبایل
            </label>
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

          <div className="form-group" style={{ marginBottom: 32 }}>
            <label style={{ display: 'block', textAlign: 'right', fontSize: 13, fontWeight: 600, color: '#374151', marginBottom: 8 }}>
              رمز عبور
            </label>
            <input
              className="form-input"
              type="password"
              placeholder="رمز عبور"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={8}
              style={{ textAlign: 'center', height: 56, borderRadius: 14, fontSize: 15 }}
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

          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <span style={{ fontSize: 13, color: '#6b7280' }}>حساب دارید؟ </span>
            <button
              type="button"
              onClick={() => router.push(`/auth/login?redirect=${redirect}`)}
              style={{
                background: 'none', border: 'none', cursor: 'pointer',
                color: '#d735a9', fontWeight: 600, fontSize: 13, fontFamily: 'inherit',
              }}
            >
              ورود
            </button>
          </div>

          {/* ── Fixed Bottom Button ── */}
          <div className="btn-group">
            <button className="btn-primary" type="submit" disabled={loading}>
              {loading ? 'در حال ثبت‌نام...' : 'تایید و ادامه'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default function RegisterPage() {
  return (
    <Suspense>
      <RegisterForm />
    </Suspense>
  );
}
