'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getAppointment, payAppointment, type Appointment } from '@/lib/api';

export default function CheckoutPage({ params }: { params: Promise<{ slug: string; id: string }> }) {
  const router = useRouter();
  const [slug, setSlug] = useState('');
  const [id, setId] = useState(0);
  const [appointment, setAppointment] = useState<Appointment | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [paymentRef, setPaymentRef] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    params.then((p) => {
      setSlug(p.slug);
      setId(parseInt(p.id, 10));
    });
  }, [params]);

  useEffect(() => {
    if (!id) return;
    const token = localStorage.getItem('access_token');
    if (!token) {
      router.push(`/auth/login?redirect=/b/${slug}/checkout/${id}`);
      return;
    }

    getAppointment(id, token)
      .then((apt) => {
        if (apt.status !== 'LOCKED') {
          // If already paid or something else, redirect to appointments
          router.replace('/appointments');
        } else {
          setAppointment(apt);
        }
      })
      .catch(() => setError('نوبت یافت نشد یا دسترسی ندارید'))
      .finally(() => setLoading(false));
  }, [id, slug, router]);

  const handleSubmit = async () => {
    if (!paymentRef.trim()) {
      alert('لطفا شماره پیگیری یا شماره کارت مبدا را وارد کنید');
      return;
    }
    
    const token = localStorage.getItem('access_token');
    if (!token) return;

    setSubmitting(true);
    try {
      await payAppointment(id, paymentRef, token);
      alert('پرداخت شما با موفقیت ثبت شد و در انتظار تایید است.');
      router.push('/appointments');
    } catch (err: any) {
      alert(err.message || 'خطا در ثبت پرداخت');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <div style={{ padding: 40, textAlign: 'center' }}>در حال بارگذاری...</div>;
  }

  if (error || !appointment) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <p style={{ color: '#6b7280' }}>{error}</p>
        <button className="btn-primary" style={{ marginTop: 20 }} onClick={() => router.back()}>بازگشت</button>
      </div>
    );
  }

  return (
    <div className="page-content" style={{ background: '#f9fafb', minHeight: '100dvh' }}>
      
      <div className="toolbar">
        <div className="toolbar-placeholder" />
        <h1 className="toolbar-title">پرداخت و نهایی‌سازی</h1>
        <button className="toolbar-back" onClick={() => router.back()}>›</button>
      </div>

      <div style={{ padding: '24px 24px' }}>
        
        {/* ── Tabs ── */}
        <div style={{ display: 'flex', background: '#f3f4f6', borderRadius: 14, padding: 4, marginBottom: 16 }}>
          <button style={{ flex: 1, padding: '12px 0', border: 'none', background: 'none', color: '#6b7280', fontSize: 13, fontWeight: 600, fontFamily: 'inherit' }}>
            درگاه آنلاین
          </button>
          <button style={{ flex: 1, padding: '12px 0', border: 'none', background: 'white', borderRadius: 10, color: '#111827', fontSize: 13, fontWeight: 600, fontFamily: 'inherit', boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
            کارت به کارت
          </button>
        </div>

        {/* ── Timer Banner ── */}
        <div style={{
          background: '#fef3c7', borderRadius: 12, padding: '12px 16px',
          display: 'flex', alignItems: 'center', gap: 8, marginBottom: 24,
          fontSize: 12, color: '#b45309', fontWeight: 600
        }}>
          <span>⏱</span>
          این نوبت تا ۱۴:۵۹ برای شما رزرو شده است
        </div>

        {/* ── Transfer Card ── */}
        <div style={{
          background: 'white', borderRadius: 16, border: '1px solid #e5e7eb',
          padding: 24, marginBottom: 24
        }}>
          <h2 style={{ fontSize: 14, fontWeight: 700, textAlign: 'right', marginBottom: 20 }}>انتقال به شماره کارت زیر</h2>
          
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
            <button style={{ color: '#d735a9', background: 'none', border: 'none', fontSize: 13, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit' }}>
              کپی
            </button>
            <div style={{ fontSize: 18, fontWeight: 700, color: '#d735a9', letterSpacing: 2, direction: 'ltr' }}>
              ۶۰۳۷-۹۹۱۲-۳۴۵۶-۷۸۹۰
            </div>
          </div>
          
          <div style={{ textAlign: 'right', fontSize: 13, color: '#6b7280', marginBottom: 16 }}>
            به نام: صاحب کسب‌وکار (تستی)
          </div>
          
          <div style={{ textAlign: 'right', fontSize: 15, fontWeight: 700, color: '#111827' }}>
            مبلغ: ۲۵۰,۰۰۰ تومان
          </div>
        </div>

        {/* ── Receipt Input ── */}
        <div style={{ textAlign: 'right', marginBottom: 8, fontSize: 13, fontWeight: 600 }}>
          شماره پیگیری / تصویر فیش واریزی
        </div>
        
        <input
          type="text"
          placeholder="شماره پیگیری را اینجا وارد کنید"
          value={paymentRef}
          onChange={(e) => setPaymentRef(e.target.value)}
          style={{
            width: '100%', height: 52, borderRadius: 12, border: '1px dashed #d1d5db',
            textAlign: 'center', fontSize: 14, fontFamily: 'inherit', marginBottom: 16,
            background: 'white', outline: 'none'
          }}
        />

        {/* ── Upload Mock Button ── */}
        <button style={{
          width: '100%', height: 52, borderRadius: 12, border: '1px dashed #d1d5db',
          background: 'white', color: '#6b7280', fontSize: 13, fontWeight: 600,
          fontFamily: 'inherit', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          cursor: 'pointer'
        }}>
          <span>📎</span> افزودن فیش پرداخت
        </button>

      </div>

      {/* ── Fixed Bottom Button ── */}
      <div className="btn-group">
        <button
          className="btn-primary"
          onClick={handleSubmit}
          disabled={submitting}
        >
          {submitting ? 'در حال ثبت...' : 'ثبت نهایی نوبت'}
        </button>
      </div>

    </div>
  );
}
