'use client';

import { useState, Suspense, useRef, useEffect, useCallback } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { sendOtp, verifyOtp, completeRegister } from '@/lib/api';
import {
  digitsOnly,
  normalizePhone,
  toPersianDigits,
  validateName,
  validateOtp,
  validatePhone,
  NAME_MAX_LENGTH,
  OTP_LENGTH,
} from '@/lib/validation';
import Toolbar from '@/app/components/Toolbar';
import TextField from '@/app/components/TextField';
import Icon, { type IconName } from '@/app/components/Icon';

type Step = 'PHONE' | 'OTP' | 'NAME';

const STEP_META: Record<Step, { title: string; icon: IconName; heading: string; sub: string }> = {
  PHONE: {
    title: 'ورود / ثبت‌نام',
    icon: 'smartphone',
    heading: 'شماره موبایل خود را وارد کنید',
    sub: 'کد تأیید به این شماره پیامک می‌شود',
  },
  OTP: {
    title: 'کد تأیید',
    icon: 'lock',
    heading: 'کد تأیید ارسال شد',
    sub: '',
  },
  NAME: {
    title: 'تکمیل پروفایل',
    icon: 'person',
    heading: 'خوش آمدید!',
    sub: 'برای تکمیل ثبت‌نام، نام خود را وارد کنید',
  },
};

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const redirect = searchParams.get('redirect') || '/appointments';

  const [step, setStep] = useState<Step>('PHONE');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState<string[]>(Array(OTP_LENGTH).fill(''));
  const [name, setName] = useState('');
  const [registerToken, setRegisterToken] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const codeRefs = useRef<(HTMLInputElement | null)[]>([]);

  useEffect(() => {
    if (countdown <= 0) return;
    const t = setTimeout(() => setCountdown((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [countdown]);

  const saveToken = (token: string) => localStorage.setItem('visitor_token', token);

  /* ── Step 1: send the OTP ── */
  const handleSendOtp = useCallback(async () => {
    setSubmitAttempted(true);
    // The field renders its own message once submitAttempted is set; repeating
    // it in the banner below would show the same sentence twice.
    if (validatePhone(phone)) return;
    setError('');
    setLoading(true);
    try {
      await sendOtp(normalizePhone(phone));
      setStep('OTP');
      setSubmitAttempted(false);
      setCountdown(120);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'خطا در ارسال کد');
    } finally {
      setLoading(false);
    }
  }, [phone]);

  /* ── Step 2: verify it ──
     Takes the digits explicitly so the auto-submit path can hand over the
     array it just built, instead of racing the `code` state update. */
  const handleVerifyOtp = useCallback(async (digits: string[] = code) => {
    setSubmitAttempted(true);
    const fullCode = digits.join('');
    const problem = validateOtp(fullCode);
    if (problem) {
      setError(problem);
      return;
    }
    setError('');
    setLoading(true);
    try {
      const result = await verifyOtp(normalizePhone(phone), fullCode);
      if (result.is_registered && result.token) {
        saveToken(result.token);
        router.push(redirect);
      } else if (!result.is_registered && result.register_token) {
        setRegisterToken(result.register_token);
        setStep('NAME');
        setSubmitAttempted(false);
      }
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'کد وارد شده اشتباه است');
    } finally {
      setLoading(false);
    }
  }, [code, phone, redirect, router]);

  /* ── Step 3: finish registering ── */
  const handleRegister = async () => {
    setSubmitAttempted(true);
    // Same as the phone step: the field itself explains the problem.
    if (validateName(name)) return;
    setError('');
    setLoading(true);
    try {
      const result = await completeRegister(normalizePhone(phone), registerToken, name.trim());
      saveToken(result.token);
      router.push(redirect);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'خطا در ثبت‌نام');
    } finally {
      setLoading(false);
    }
  };

  /* ── OTP boxes ── */
  const handleCodeInput = (index: number, val: string) => {
    // A paste lands entirely in one box: spread it across the remaining ones
    // instead of keeping a single digit and dropping the rest.
    const typed = digitsOnly(val);
    const next = [...code];

    if (typed.length > 1) {
      for (let i = 0; i < typed.length && index + i < OTP_LENGTH; i++) {
        next[index + i] = typed[i];
      }
      setCode(next);
      codeRefs.current[Math.min(index + typed.length, OTP_LENGTH - 1)]?.focus();
    } else {
      const digit = typed.slice(-1);
      next[index] = digit;
      setCode(next);
      if (digit && index < OTP_LENGTH - 1) codeRefs.current[index + 1]?.focus();
    }

    // Submit as soon as the last box is filled. Done here rather than from an
    // effect watching `code`, and handed `next` directly so it never reads the
    // pre-update state.
    if (!loading && next.every((d) => d !== '')) handleVerifyOtp(next);
  };

  const handleCodeKeyDown = (index: number, e: React.KeyboardEvent) => {
    if (e.key === 'Backspace' && !code[index] && index > 0) {
      codeRefs.current[index - 1]?.focus();
    }
  };

  const meta = STEP_META[step];
  const submit =
    step === 'PHONE' ? handleSendOtp : step === 'OTP' ? () => handleVerifyOtp() : handleRegister;

  return (
    <div style={{ background: 'var(--color-bg)', minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <Toolbar
        title={meta.title}
        hideBack={step === 'PHONE'}
        onBack={() => {
          setStep(step === 'NAME' ? 'OTP' : 'PHONE');
          setError('');
          setSubmitAttempted(false);
        }}
      />

      <div style={{ flex: 1, padding: '32px 24px 130px' }}>
        <div style={{ textAlign: 'center', marginBottom: 30 }}>
          <span className="empty-state-icon" style={{ margin: '0 auto 14px', width: 60, height: 60 }}>
            <Icon name={meta.icon} size={28} />
          </span>
          <h2 style={{ fontSize: 16, fontWeight: 700, color: 'var(--color-text)', marginBottom: 8 }}>
            {meta.heading}
          </h2>
          <p style={{ fontSize: 13, color: 'var(--color-muted)', lineHeight: 1.7 }}>
            {step === 'OTP'
              ? `کد ${toPersianDigits(OTP_LENGTH)} رقمی ارسال‌شده به ${toPersianDigits(normalizePhone(phone))} را وارد کنید`
              : meta.sub}
          </p>
        </div>

        {/* ── STEP 1: phone ── */}
        {step === 'PHONE' && (
          <TextField
            value={phone}
            onChange={setPhone}
            transform={normalizePhone}
            validate={validatePhone}
            showError={submitAttempted}
            onKeyDown={(e) => e.key === 'Enter' && handleSendOtp()}
            type="tel"
            inputMode="numeric"
            icon="smartphone"
            dir="ltr"
            autoComplete="tel"
            placeholder="09123456789"
            hint="شمارهٔ ۱۱ رقمی، با ۰۹ شروع می‌شود"
            required
          />
        )}

        {/* ── STEP 2: OTP ── */}
        {step === 'OTP' && (
          <>
            <div style={{ display: 'flex', gap: 9, justifyContent: 'center', direction: 'ltr', marginBottom: 22 }}>
              {code.map((digit, i) => (
                <input
                  key={i}
                  ref={(el) => {
                    codeRefs.current[i] = el;
                  }}
                  type="tel"
                  inputMode="numeric"
                  autoComplete={i === 0 ? 'one-time-code' : 'off'}
                  aria-label={`رقم ${toPersianDigits(i + 1)} از کد تأیید`}
                  value={digit}
                  onChange={(e) => handleCodeInput(i, e.target.value)}
                  onKeyDown={(e) => handleCodeKeyDown(i, e)}
                  style={{
                    width: 50,
                    height: 56,
                    borderRadius: 12,
                    border: `2px solid ${
                      error ? 'var(--color-error)' : digit ? 'var(--color-primary)' : 'var(--color-border)'
                    }`,
                    textAlign: 'center',
                    fontSize: 22,
                    fontWeight: 700,
                    fontFamily: 'inherit',
                    outline: 'none',
                    background: digit ? 'var(--color-primary-tint)' : 'var(--color-surface)',
                    color: 'var(--color-text)',
                    transition: 'all 0.15s',
                  }}
                />
              ))}
            </div>

            <div style={{ textAlign: 'center', marginBottom: 16 }}>
              {countdown > 0 ? (
                <span style={{ fontSize: 13, color: 'var(--color-muted)' }}>
                  ارسال مجدد تا {toPersianDigits(countdown)} ثانیه دیگر
                </span>
              ) : (
                <button
                  type="button"
                  onClick={() => {
                    setCode(Array(OTP_LENGTH).fill(''));
                    setError('');
                    handleSendOtp();
                  }}
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 5,
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    color: 'var(--color-primary)',
                    fontWeight: 600,
                    fontSize: 13,
                    fontFamily: 'inherit',
                  }}
                >
                  <Icon name="refresh" size={15} />
                  ارسال مجدد کد
                </button>
              )}
            </div>
          </>
        )}

        {/* ── STEP 3: name ── */}
        {step === 'NAME' && (
          <TextField
            value={name}
            onChange={setName}
            validate={validateName}
            showError={submitAttempted}
            onKeyDown={(e) => e.key === 'Enter' && handleRegister()}
            icon="person"
            maxLength={NAME_MAX_LENGTH}
            placeholder="نام و نام خانوادگی"
            hint="همین نام برای کسب‌وکار نمایش داده می‌شود"
            autoFocus
            required
          />
        )}

        {error && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 8,
              background: 'var(--color-error-bg)',
              border: '1px solid var(--color-error)',
              borderRadius: 12,
              padding: '12px 14px',
              color: 'var(--color-error)',
              fontSize: 13,
              textAlign: 'right',
              marginTop: 12,
            }}
            role="alert"
          >
            <Icon name="error" size={18} />
            <span>{error}</span>
          </div>
        )}

        <p style={{ fontSize: 11, color: 'var(--color-faint)', textAlign: 'center', marginTop: 24, lineHeight: 1.8 }}>
          با ادامه، قوانین و حریم خصوصی نوبت‌یار را می‌پذیرید.
        </p>
      </div>

      <div className="btn-group">
        <button className="btn-primary" onClick={submit} disabled={loading}>
          {loading ? (
            <>
              <span className="btn-spinner" /> لطفاً صبر کنید…
            </>
          ) : step === 'PHONE' ? (
            'دریافت کد تأیید'
          ) : step === 'OTP' ? (
            'تأیید و ورود'
          ) : (
            'ثبت‌نام و ورود'
          )}
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
