'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  getAppointment,
  payAppointment,
  payAppointmentWithReceipt,
  type Appointment,
  type PaymentMethod,
} from '@/lib/api';

export default function CheckoutPage({ params }: { params: Promise<{ slug: string; id: string }> }) {
  const router = useRouter();
  const [slug, setSlug] = useState('');
  const [id, setId] = useState(0);
  const [appointment, setAppointment] = useState<Appointment | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [paymentRef, setPaymentRef] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState('');
  // Null until the user picks one: the effective method falls back to the first
  // method this business actually offers, rather than assuming CARD.
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethod | null>(null);
  const [toast, setToast] = useState('');
  const [copied, setCopied] = useState(false);

  const biz = appointment?.business;
  const accepted = biz?.accepted_payment_methods;
  const depositMode = biz?.deposit_mode;
  // A mandatory deposit cannot be settled in person, so cash is not offered.
  // An optional deposit always allows paying at the venue instead.
  const canPayOnline = !!accepted?.includes('ONLINE') && !!biz?.payment_link;
  const canPayCard = !accepted || accepted.includes('CARD');
  const canPayCash =
    depositMode !== 'MANDATORY' && (!!accepted?.includes('CASH') || depositMode === 'OPTIONAL');

  const availableMethods: PaymentMethod[] = [
    ...(canPayOnline ? (['ONLINE'] as const) : []),
    ...(canPayCard ? (['CARD'] as const) : []),
    ...(canPayCash ? (['CASH'] as const) : []),
  ];
  const method: PaymentMethod =
    selectedMethod && availableMethods.includes(selectedMethod)
      ? selectedMethod
      : availableMethods[0] ?? 'CARD';

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(''), 3000);
  };

  const copyCard = async () => {
    const card = appointment?.business?.card_number;
    if (!card) return;
    try {
      await navigator.clipboard.writeText(card.replace(/\s/g, ''));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      showToast('امکان کپی وجود ندارد');
    }
  };

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
    // Card and online transfers must carry proof; paying in person carries none.
    if (method !== 'CASH' && !file && !paymentRef.trim()) {
      showToast('لطفا شماره پیگیری را وارد کنید یا تصویر فیش را آپلود نمایید');
      return;
    }

    const token = localStorage.getItem('access_token');
    if (!token) return;

    setSubmitting(true);
    try {
      if (method !== 'CASH' && file) {
        const formData = new FormData();
        formData.append('method', method);
        formData.append('payment_receipt', file);
        if (paymentRef.trim()) {
          formData.append('payment_reference', paymentRef);
        }
        await payAppointmentWithReceipt(id, formData, token);
      } else {
        await payAppointment(id, method === 'CASH' ? '' : paymentRef, token, method);
      }
      showToast(
        method === 'CASH'
          ? '✅ نوبت شما ثبت شد و در انتظار تایید است.'
          : '✅ پرداخت شما ثبت شد و در انتظار تایید است.',
      );
      setTimeout(() => router.push('/appointments'), 1500);
    } catch (err: unknown) {
      showToast(err instanceof Error ? err.message : 'خطا در ثبت پرداخت');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
        <div className="toolbar">
          <div className="toolbar-placeholder" />
          <h1 className="toolbar-title">پرداخت و نهایی‌سازی</h1>
          <button className="toolbar-back" onClick={() => router.back()}>›</button>
        </div>
        <div style={{ padding: 24 }}>
          <div className="skeleton" style={{ height: 52, borderRadius: 14, marginBottom: 16 }} />
          <div className="skeleton" style={{ height: 180, borderRadius: 16, marginBottom: 16 }} />
          <div className="skeleton" style={{ height: 52, borderRadius: 12 }} />
        </div>
      </div>
    );
  }

  if (error || !appointment) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <p style={{ color: 'var(--color-muted)' }}>{error}</p>
        <button className="btn-primary" style={{ marginTop: 20 }} onClick={() => router.back()}>بازگشت</button>
      </div>
    );
  }

  return (
    <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
      
      <div className="toolbar">
        <div className="toolbar-placeholder" />
        <h1 className="toolbar-title">پرداخت و نهایی‌سازی</h1>
        <button className="toolbar-back" onClick={() => router.back()}>›</button>
      </div>

      <div style={{ padding: '24px 24px' }}>
        
        {/* ── Tabs ── (only the methods this business actually offers) */}
        {availableMethods.length > 1 && (
          <div style={{ display: 'flex', background: 'var(--color-surface-variant)', borderRadius: 14, padding: 4, marginBottom: 16 }}>
            {availableMethods.map((m) => {
              const isActive = method === m;
              const label = m === 'ONLINE' ? 'درگاه آنلاین' : m === 'CARD' ? 'کارت به کارت' : 'پرداخت در محل';
              return (
                <button
                  key={m}
                  onClick={() => setSelectedMethod(m)}
                  style={{ flex: 1, padding: '12px 0', border: 'none', background: isActive ? 'var(--color-surface)' : 'none', borderRadius: isActive ? 10 : 0, color: isActive ? 'var(--color-text)' : 'var(--color-muted)', fontSize: 13, fontWeight: 600, fontFamily: 'inherit', boxShadow: isActive ? '0 1px 3px rgba(0,0,0,0.05)' : 'none' }}>
                  {label}
                </button>
              );
            })}
          </div>
        )}

        {depositMode === 'OPTIONAL' && (
          <div style={{ background: 'var(--color-primary-tint)', color: 'var(--color-text)', borderRadius: 12, padding: '12px 16px', marginBottom: 16, fontSize: 13, textAlign: 'right', lineHeight: 1.7 }}>
            پرداخت بیعانه برای این کسب‌وکار <b>اختیاری</b> است. می‌توانید بیعانه را پرداخت کنید یا گزینهٔ «پرداخت در محل» را انتخاب نمایید.
          </div>
        )}

        {/* ── Transfer Card ── */}
        {method === 'CARD' && (
          <>
            <div style={{
              background: 'var(--color-surface)', borderRadius: 16, border: '1px solid var(--color-border)',
              padding: 24, marginBottom: 24
            }}>
              <h2 style={{ fontSize: 14, fontWeight: 700, textAlign: 'right', marginBottom: 20 }}>انتقال به شماره کارت زیر</h2>
              
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
                <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--color-primary)', letterSpacing: 2, direction: 'ltr' }}>
                  {appointment?.business?.card_number || 'شماره کارت ثبت نشده'}
                </div>
                <button
                  onClick={copyCard}
                  disabled={!appointment?.business?.card_number}
                  style={{ color: copied ? 'var(--color-success-text)' : 'var(--color-primary)', background: 'none', border: 'none', fontSize: 13, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit' }}>
                  {copied ? '✓ کپی شد' : 'کپی'}
                </button>
              </div>
              
              <div style={{ textAlign: 'right', fontSize: 13, color: 'var(--color-muted)', marginBottom: 16 }}>
                به نام: {appointment?.business?.card_owner_name || 'صاحب کسب‌وکار'}
              </div>
              
              {biz?.deposit_amount ? (
                <div style={{ textAlign: 'right', fontSize: 15, fontWeight: 700, color: 'var(--color-text)' }}>
                  مبلغ بیعانه: {biz.deposit_amount.toLocaleString('fa-IR')} تومان
                  {depositMode === 'OPTIONAL' && (
                    <span style={{ fontSize: 12, fontWeight: 500, color: 'var(--color-muted)' }}> (اختیاری)</span>
                  )}
                </div>
              ) : (
                <div style={{ textAlign: 'right', fontSize: 13, color: 'var(--color-muted)' }}>
                  مبلغ را با کسب‌وکار هماهنگ کنید.
                </div>
              )}
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
                width: '100%', height: 52, borderRadius: 12, border: '1px dashed var(--color-border)',
                textAlign: 'center', fontSize: 14, fontFamily: 'inherit', marginBottom: 16,
                background: 'var(--color-surface)', outline: 'none'
              }}
            />

            {/* ── Upload Button ── */}
            <label style={{
              width: '100%', height: 52, borderRadius: 12, border: '1px dashed var(--color-border)',
              background: 'var(--color-surface)', color: 'var(--color-muted)', fontSize: 13, fontWeight: 600,
              fontFamily: 'inherit', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
              cursor: 'pointer'
            }}>
              <span>📎</span> {file ? file.name : 'افزودن فیش پرداخت'}
              <input 
                type="file" 
                accept="image/jpeg,image/png,image/jpg" 
                style={{ display: 'none' }}
                onChange={(e) => {
                  if (e.target.files && e.target.files.length > 0) {
                    setFile(e.target.files[0]);
                  }
                }}
              />
            </label>
          </>
        )}
        
        {method === 'ONLINE' && (
          <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--color-muted)', fontSize: 14 }}>
            {biz?.payment_link ? (
              <>
                <p style={{ marginBottom: 16 }}>
                  برای پرداخت آنلاین مبلغ بیعانه، لطفا از طریق لینک زیر اقدام کنید:
                </p>
                <a 
                  href={biz.payment_link.startsWith('http') ? biz.payment_link : `https://${biz.payment_link}`} 
                  target="_blank" 
                  rel="noopener noreferrer"
                  style={{ color: 'var(--color-primary)', fontWeight: 600, textDecoration: 'none', fontSize: 16 }}
                >
                  انتقال به درگاه پرداخت
                </a>
                <p style={{ marginTop: 24, fontSize: 12, color: 'var(--color-faint)' }}>
                  پس از پرداخت، لطفا شماره پیگیری را در همین صفحه وارد کنید.
                </p>
                
                <input
                  type="text"
                  placeholder="شماره پیگیری درگاه پرداخت"
                  value={paymentRef}
                  onChange={(e) => setPaymentRef(e.target.value)}
                  style={{
                    width: '100%', height: 52, borderRadius: 12, border: '1px solid var(--color-border)',
                    textAlign: 'center', fontSize: 14, fontFamily: 'inherit', marginTop: 16,
                    background: 'var(--color-surface)', outline: 'none'
                  }}
                />
              </>
            ) : (
              'اتصال به درگاه پرداخت در حال حاضر غیرفعال است.'
            )}
          </div>
        )}

        {method === 'CASH' && (
          <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--color-muted)', fontSize: 14, lineHeight: 1.9 }}>
            شما پرداخت در محل را انتخاب کرده‌اید.
            <br />
            نوبت شما برای تایید به کسب‌وکار ارسال می‌شود.
          </div>
        )}

      </div>

      {/* ── Toast ── */}
      {toast && <div className="toast">{toast}</div>}

      {/* ── Fixed Bottom Button ── */}
      <div className="btn-group">
        <button
          className="btn-primary"
          onClick={handleSubmit}
          disabled={submitting}
        >
          {submitting ? 'در حال ثبت...' : method === 'CASH' ? 'ثبت نوبت (پرداخت در محل)' : 'ثبت نهایی نوبت'}
        </button>
      </div>

    </div>
  );
}
