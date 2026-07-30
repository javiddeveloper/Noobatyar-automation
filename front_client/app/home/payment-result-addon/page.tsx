'use client';

import { useEffect, useState, Suspense } from 'react';
import { useSearchParams } from 'next/navigation';

const BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000';

interface VerifyResult {
  success: boolean;
  message: string;
  data?: Record<string, unknown>;
}

function PaymentResultAddonContent() {
  const searchParams = useSearchParams();
  const trackId = searchParams.get('trackId');
  const success = searchParams.get('success');

  const [result, setResult] = useState<VerifyResult | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!trackId) {
      setResult({ success: false, message: 'شناسه پرداخت یافت نشد.' });
      setLoading(false);
      return;
    }

    if (success === '0') {
      setResult({ success: false, message: 'پرداخت توسط شما لغو شد یا ناموفق بود.' });
      setLoading(false);
      return;
    }

    const verify = async () => {
      try {
        const res = await fetch(
          `${BASE_URL}/api/accounting/addons/payment-result?trackId=${trackId}&success=${success}`,
          { method: 'GET' }
        );
        const text = await res.text();
        const isSuccess = text.includes('موفق') && !text.includes('ناموفق');

        // Extract pack name from response if possible
        const packMatch = text.match(/«([^»]+)»/);
        const packName = packMatch ? packMatch[1] : 'بسته افزودنی';

        setResult({
          success: isSuccess,
          message: isSuccess
            ? `بسته‌ی «${packName}» با موفقیت فعال شد!`
            : 'پردازش پرداخت ناموفق بود. لطفاً با پشتیبانی تماس بگیرید.',
        });
      } catch {
        setResult({ success: false, message: 'خطا در ارتباط با سرور.' });
      } finally {
        setLoading(false);
      }
    };

    verify();
  }, [trackId, success]);

  return (
    <div style={styles.wrapper}>
      <div style={styles.card}>
        {loading ? (
          <>
            <div style={styles.spinner} />
            <p style={styles.loadingText}>در حال بررسی پرداخت...</p>
          </>
        ) : (
          <>
            <div style={{ ...styles.icon, color: result?.success ? '#22c55e' : '#ef4444' }}>
              {result?.success ? '✓' : '✗'}
            </div>
            <h1 style={{ ...styles.title, color: result?.success ? '#22c55e' : '#ef4444' }}>
              {result?.success ? 'پرداخت موفق' : 'پرداخت ناموفق'}
            </h1>
            <p style={styles.message}>{result?.message}</p>
            {result?.success && (
              <p style={styles.sub}>بسته افزودنی فعال شد. می‌توانید اپلیکیشن را باز کنید.</p>
            )}
            <a href="https://noobatyar.ir" style={styles.button}>
              بازگشت به سایت
            </a>
          </>
        )}
      </div>
    </div>
  );
}

export default function PaymentResultAddonPage() {
  return (
    <Suspense fallback={<div style={styles.wrapper}><div style={styles.spinner} /></div>}>
      <PaymentResultAddonContent />
    </Suspense>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrapper: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
    fontFamily: 'Vazirmatn, Tahoma, Arial, sans-serif',
    direction: 'rtl',
  },
  card: {
    background: 'rgba(255,255,255,0.05)',
    backdropFilter: 'blur(20px)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: '24px',
    padding: '48px 40px',
    maxWidth: '420px',
    width: '90%',
    textAlign: 'center',
    boxShadow: '0 25px 50px rgba(0,0,0,0.4)',
  },
  icon: {
    fontSize: '64px',
    fontWeight: 'bold',
    marginBottom: '16px',
    display: 'block',
  },
  title: {
    fontSize: '24px',
    fontWeight: '700',
    marginBottom: '12px',
  },
  message: {
    color: '#94a3b8',
    fontSize: '16px',
    lineHeight: '1.6',
    marginBottom: '12px',
  },
  sub: {
    color: '#64748b',
    fontSize: '14px',
    marginBottom: '24px',
  },
  button: {
    display: 'inline-block',
    marginTop: '24px',
    padding: '12px 32px',
    background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
    color: '#fff',
    borderRadius: '12px',
    textDecoration: 'none',
    fontWeight: '600',
    fontSize: '15px',
  },
  spinner: {
    width: '48px',
    height: '48px',
    border: '4px solid rgba(255,255,255,0.1)',
    borderTop: '4px solid #6366f1',
    borderRadius: '50%',
    animation: 'spin 0.8s linear infinite',
    margin: '0 auto 16px',
  },
  loadingText: {
    color: '#94a3b8',
    fontSize: '16px',
  },
};
