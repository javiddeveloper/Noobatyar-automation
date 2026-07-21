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
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState('');
  const [selectedMethod, setSelectedMethod] = useState<string>('CARD');

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
    if (selectedMethod === 'CARD' && !file && !paymentRef.trim()) {
      alert('لطفا شماره پیگیری را وارد کنید یا تصویر فیش را آپلود نمایید');
      return;
    }
    
    const token = localStorage.getItem('access_token');
    if (!token) return;

    setSubmitting(true);
    try {
      if (file) {
        const formData = new FormData();
        formData.append('payment_receipt', file);
        if (paymentRef.trim()) {
          formData.append('payment_reference', paymentRef);
        }
        await import('@/lib/api').then(m => m.payAppointmentWithReceipt(id, formData, token));
      } else {
        await import('@/lib/api').then(m => m.payAppointment(id, paymentRef, token));
      }
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
          {appointment?.business?.accepted_payment_methods?.includes('ONLINE') && (
            <button 
              onClick={() => setSelectedMethod('ONLINE')}
              style={{ flex: 1, padding: '12px 0', border: 'none', background: selectedMethod === 'ONLINE' ? 'white' : 'none', borderRadius: selectedMethod === 'ONLINE' ? 10 : 0, color: selectedMethod === 'ONLINE' ? '#111827' : '#6b7280', fontSize: 13, fontWeight: 600, fontFamily: 'inherit', boxShadow: selectedMethod === 'ONLINE' ? '0 1px 3px rgba(0,0,0,0.05)' : 'none' }}>
              درگاه آنلاین
            </button>
          )}
          {(!appointment?.business?.accepted_payment_methods || appointment?.business?.accepted_payment_methods?.includes('CARD')) && (
            <button 
              onClick={() => setSelectedMethod('CARD')}
              style={{ flex: 1, padding: '12px 0', border: 'none', background: selectedMethod === 'CARD' ? 'white' : 'none', borderRadius: selectedMethod === 'CARD' ? 10 : 0, color: selectedMethod === 'CARD' ? '#111827' : '#6b7280', fontSize: 13, fontWeight: 600, fontFamily: 'inherit', boxShadow: selectedMethod === 'CARD' ? '0 1px 3px rgba(0,0,0,0.05)' : 'none' }}>
              کارت به کارت
            </button>
          )}
          {appointment?.business?.accepted_payment_methods?.includes('CASH') && (
            <button 
              onClick={() => setSelectedMethod('CASH')}
              style={{ flex: 1, padding: '12px 0', border: 'none', background: selectedMethod === 'CASH' ? 'white' : 'none', borderRadius: selectedMethod === 'CASH' ? 10 : 0, color: selectedMethod === 'CASH' ? '#111827' : '#6b7280', fontSize: 13, fontWeight: 600, fontFamily: 'inherit', boxShadow: selectedMethod === 'CASH' ? '0 1px 3px rgba(0,0,0,0.05)' : 'none' }}>
              پرداخت نقدی/محل
            </button>
          )}
        </div>

        {/* ── Transfer Card ── */}
        {selectedMethod === 'CARD' && (
          <>
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
                  {appointment?.business?.card_number || 'شماره کارت ثبت نشده'}
                </div>
              </div>
              
              <div style={{ textAlign: 'right', fontSize: 13, color: '#6b7280', marginBottom: 16 }}>
                به نام: {appointment?.business?.card_owner_name || 'صاحب کسب‌وکار'}
              </div>
              
              <div style={{ textAlign: 'right', fontSize: 15, fontWeight: 700, color: '#111827' }}>
                مبلغ بیعانه: {appointment?.business?.deposit_mode === 'NONE' ? 'رایگان' : (appointment?.business?.deposit_amount ? appointment.business.deposit_amount.toLocaleString() + ' تومان' : 'نامشخص')}
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

            {/* ── Upload Button ── */}
            <label style={{
              width: '100%', height: 52, borderRadius: 12, border: '1px dashed #d1d5db',
              background: 'white', color: '#6b7280', fontSize: 13, fontWeight: 600,
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
        
        {selectedMethod === 'ONLINE' && (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#6b7280', fontSize: 14 }}>
            {appointment?.business?.payment_link ? (
              <>
                <p style={{ marginBottom: 16 }}>
                  برای پرداخت آنلاین مبلغ بیعانه، لطفا از طریق لینک زیر اقدام کنید:
                </p>
                <a 
                  href={appointment.business.payment_link.startsWith('http') ? appointment.business.payment_link : `https://${appointment.business.payment_link}`} 
                  target="_blank" 
                  rel="noopener noreferrer"
                  style={{ color: '#007aff', fontWeight: 600, textDecoration: 'none', fontSize: 16 }}
                >
                  انتقال به درگاه پرداخت
                </a>
                <p style={{ marginTop: 24, fontSize: 12, color: '#9ca3af' }}>
                  پس از پرداخت، لطفا شماره پیگیری را در همین صفحه وارد کنید.
                </p>
                
                <input
                  type="text"
                  placeholder="شماره پیگیری درگاه پرداخت"
                  value={paymentRef}
                  onChange={(e) => setPaymentRef(e.target.value)}
                  style={{
                    width: '100%', height: 52, borderRadius: 12, border: '1px solid #d1d5db',
                    textAlign: 'center', fontSize: 14, fontFamily: 'inherit', marginTop: 16,
                    background: 'white', outline: 'none'
                  }}
                />
              </>
            ) : (
              'اتصال به درگاه پرداخت در حال حاضر غیرفعال است.'
            )}
          </div>
        )}

        {selectedMethod === 'CASH' && (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#6b7280', fontSize: 14 }}>
            شما پرداخت در محل را انتخاب کرده‌اید. در صورت تایید نوبت شما ثبت خواهد شد.
          </div>
        )}

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
