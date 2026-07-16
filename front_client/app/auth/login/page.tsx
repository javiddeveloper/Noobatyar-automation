'use client';

import { useState, Suspense, useRef, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { sendOtp, verifyOtp, completeRegister } from '@/lib/api';

type Step = 'PHONE' | 'OTP' | 'NAME';

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect') || '/appointments';

  const [step, setStep] = useState<Step>('PHONE');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState(['', '', '', '', '']);
  const [name, setName] = useState('');
  const [registerToken, setRegisterToken] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [countdown, setCountdown] = useState(0);

  const codeRefs = useRef<(HTMLInputElement | null)[]>([]);

  // Countdown timer for resend
  useEffect(() => {
    if (countdown <= 0) return;
    const t = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [countdown]);

  const saveTokens = (tokens: { access: string; refresh: string }) => {
    localStorage.setItem('access_token', tokens.access);
    localStorage.setItem('refresh_token', tokens.refresh);
  };

  /* ── Step 1: Send OTP ── */
  const handleSendOtp = async () => {
    const cleaned = phone.replace(/\s/g, '');
    if (!/^09\d{9}$/.test(cleaned)) {
      setError('شماره موبایل معتبر نیست (مثال: ۰۹۱۲۳۴۵۶۷۸۹)');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await sendOtp(cleaned);
      setStep('OTP');
      setCountdown(120);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'خطا در ارسال کد');
    } finally {
      setLoading(false);
    }
  };

  /* ── Step 2: Verify OTP ── */
  const handleVerifyOtp = async () => {
    const fullCode = code.join('');
    if (fullCode.length !== 5) {
      setError('کد ۵ رقمی را کامل وارد کنید');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const cleaned = phone.replace(/\s/g, '');
      const result = await verifyOtp(cleaned, fullCode);
      if (result.is_registered && result.tokens) {
        saveTokens(result.tokens);
        router.push(redirect);
      } else if (!result.is_registered && result.register_token) {
        setRegisterToken(result.register_token);
        setStep('NAME');
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'کد وارد شده اشتباه است');
    } finally {
      setLoading(false);
    }
  };

  /* ── Step 3: Complete Registration ── */
  const handleRegister = async () => {
    if (name.trim().length < 2) {
      setError('نام باید حداقل ۲ حرف باشد');
      return;
    }
    setError('');
    setLoading(true);
    try {
      const cleaned = phone.replace(/\s/g, '');
      const result = await completeRegister(cleaned, registerToken, name.trim());
      saveTokens(result.tokens);
      router.push(redirect);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'خطا در ثبت‌نام');
    } finally {
      setLoading(false);
    }
  };

  /* ── OTP digit input handler ── */
  const handleCodeInput = (index: number, val: string) => {
    // Allow Persian/Arabic/Latin digits
    const digit = val.replace(/[^0-9۰-۹]/g, '').slice(-1);
    const latinDigit = digit.replace(/[۰-۹]/g, (d) => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(d)));
    const next = [...code];
    next[index] = latinDigit;
    setCode(next);
    if (latinDigit && index < 4) {
      codeRefs.current[index + 1]?.focus();
    }
    if (next.every(Boolean) && next.join('').length === 5) {
      // auto-submit when all 5 digits entered
      setTimeout(() => {
        const el = document.getElementById('otp-submit-btn');
        el?.click();
      }, 100);
    }
  };

  const handleCodeKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !code[index] && index > 0) {
      codeRefs.current[index - 1]?.focus();
    }
  };

  /* ── UI ── */
  return (
    <div style={{
      background: '#f9fafb', minHeight: '100dvh',
      display: 'flex', flexDirection: 'column',
    }}>

      {/* Header */}
      <div style={{
        padding: '20px 24px 16px',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        background: 'white', borderBottom: '1px solid #f3f4f6',
      }}>
        {step !== 'PHONE' ? (
          <button
            onClick={() => { setStep(step === 'NAME' ? 'OTP' : 'PHONE'); setError(''); }}
            style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 22, color: '#111827', padding: '4px 8px' }}
          >←</button>
        ) : <div style={{ width: 40 }} />}
        <h1 style={{ fontSize: 17, fontWeight: 700, color: '#111827', textAlign: 'center' }}>
          {step === 'PHONE' ? 'ورود / ثبت‌نام' : step === 'OTP' ? 'کد تأیید' : 'تکمیل پروفایل'}
        </h1>
        <div style={{ width: 40 }} />
      </div>

      <div style={{ flex: 1, padding: '32px 24px 120px' }}>

        {/* ── STEP 1: Phone ── */}
        {step === 'PHONE' && (
          <>
            <div style={{ textAlign: 'center', marginBottom: 32 }}>
              <div style={{ fontSize: 56, marginBottom: 12 }}>📱</div>
              <h2 style={{ fontSize: 16, fontWeight: 700, color: '#111827', marginBottom: 8 }}>
                شماره موبایل خود را وارد کنید
              </h2>
              <p style={{ fontSize: 13, color: '#6b7280', lineHeight: 1.7 }}>
                کد تأیید به این شماره پیامک می‌شود
              </p>
            </div>

            <div style={{ marginBottom: 16 }}>
              <input
                type="tel"
                placeholder="مثال: ۰۹۱۲۳۴۵۶۷۸۹"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSendOtp()}
                dir="ltr"
                autoComplete="tel"
                style={{
                  width: '100%', height: 56, borderRadius: 16,
                  border: '2px solid #e5e7eb', textAlign: 'center',
                  fontSize: 18, fontFamily: 'inherit', outline: 'none',
                  color: '#111827', letterSpacing: 2,
                  transition: 'border-color 0.2s',
                }}
                onFocus={(e) => (e.target.style.borderColor = '#d735a9')}
                onBlur={(e) => (e.target.style.borderColor = '#e5e7eb')}
              />
            </div>
          </>
        )}

        {/* ── STEP 2: OTP ── */}
        {step === 'OTP' && (
          <>
            <div style={{ textAlign: 'center', marginBottom: 32 }}>
              <div style={{ fontSize: 56, marginBottom: 12 }}>🔐</div>
              <h2 style={{ fontSize: 16, fontWeight: 700, color: '#111827', marginBottom: 8 }}>
                کد تأیید ارسال شد
              </h2>
              <p style={{ fontSize: 13, color: '#6b7280', lineHeight: 1.7 }}>
                کد ۵ رقمی ارسال‌شده به {phone} را وارد کنید
              </p>
            </div>

            {/* OTP boxes */}
            <div style={{
              display: 'flex', gap: 10, justifyContent: 'center',
              direction: 'ltr', marginBottom: 24
            }}>
              {code.map((digit, i) => (
                <input
                  key={i}
                  ref={(el) => { codeRefs.current[i] = el; }}
                  type="tel"
                  maxLength={1}
                  value={digit}
                  onChange={(e) => handleCodeInput(i, e.target.value)}
                  onKeyDown={(e) => handleCodeKeyDown(i, e)}
                  style={{
                    width: 52, height: 56, borderRadius: 12,
                    border: `2px solid ${digit ? '#d735a9' : '#e5e7eb'}`,
                    textAlign: 'center', fontSize: 22, fontWeight: 700,
                    fontFamily: 'inherit', outline: 'none',
                    background: digit ? '#fdf2fb' : 'white',
                    color: '#111827', transition: 'all 0.15s',
                  }}
                  onFocus={(e) => (e.target.style.borderColor = '#d735a9')}
                />
              ))}
            </div>

            {/* Resend */}
            <div style={{ textAlign: 'center', marginBottom: 16 }}>
              {countdown > 0 ? (
                <span style={{ fontSize: 13, color: '#6b7280' }}>
                  ارسال مجدد تا {countdown} ثانیه دیگر
                </span>
              ) : (
                <button
                  type="button"
                  onClick={() => { setCode(['','','','','']); handleSendOtp(); }}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#d735a9', fontWeight: 600, fontSize: 13, fontFamily: 'inherit' }}
                >
                  ارسال مجدد کد
                </button>
              )}
            </div>
          </>
        )}

        {/* ── STEP 3: Name ── */}
        {step === 'NAME' && (
          <>
            <div style={{ textAlign: 'center', marginBottom: 32 }}>
              <div style={{ fontSize: 56, marginBottom: 12 }}>👤</div>
              <h2 style={{ fontSize: 16, fontWeight: 700, color: '#111827', marginBottom: 8 }}>
                خوش آمدید!
              </h2>
              <p style={{ fontSize: 13, color: '#6b7280', lineHeight: 1.7 }}>
                برای تکمیل ثبت‌نام، نام خود را وارد کنید
              </p>
            </div>

            <input
              type="text"
              placeholder="نام و نام خانوادگی"
              value={name}
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleRegister()}
              autoFocus
              style={{
                width: '100%', height: 56, borderRadius: 16,
                border: '2px solid #e5e7eb', textAlign: 'right',
                fontSize: 15, fontFamily: 'inherit', outline: 'none',
                color: '#111827', padding: '0 16px',
                transition: 'border-color 0.2s',
              }}
              onFocus={(e) => (e.target.style.borderColor = '#d735a9')}
              onBlur={(e) => (e.target.style.borderColor = '#e5e7eb')}
            />
          </>
        )}

        {/* Error */}
        {error && (
          <div style={{
            background: '#fef2f2', border: '1px solid #fecaca',
            borderRadius: 12, padding: '12px 16px',
            color: '#dc2626', fontSize: 13, textAlign: 'right', marginTop: 12,
          }}>
            {error}
          </div>
        )}

        <p style={{ fontSize: 11, color: '#9ca3af', textAlign: 'center', marginTop: 24, lineHeight: 1.8 }}>
          با ادامه، قوانین و حریم خصوصی نوبت‌یار را می‌پذیرید.
        </p>
      </div>

      {/* Fixed bottom button */}
      <div style={{
        position: 'fixed', bottom: 0, width: '100%', maxWidth: 390,
        padding: '16px 24px', background: 'white',
        borderTop: '1px solid #f3f4f6',
      }}>
        <button
          id="otp-submit-btn"
          onClick={step === 'PHONE' ? handleSendOtp : step === 'OTP' ? handleVerifyOtp : handleRegister}
          disabled={loading}
          style={{
            width: '100%', height: 52, background: loading ? '#9ca3af' : '#d735a9',
            color: 'white', border: 'none', borderRadius: 14,
            fontSize: 16, fontWeight: 700, fontFamily: 'inherit',
            cursor: loading ? 'not-allowed' : 'pointer',
            boxShadow: loading ? 'none' : '0 4px 14px rgba(215,53,169,0.35)',
            transition: 'all 0.2s',
          }}
        >
          {loading
            ? 'لطفا صبر کنید...'
            : step === 'PHONE' ? 'دریافت کد تأیید'
            : step === 'OTP' ? 'تأیید و ورود'
            : 'ثبت‌نام و ورود'}
        </button>
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
