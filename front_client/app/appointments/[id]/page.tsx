'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getAppointment, cancelAppointment, categoryLabel, type Appointment } from '@/lib/api';

// Anything still ahead of the appointment time can be cancelled by the client.
const CANCELLABLE = ['LOCKED', 'PENDING_APPROVAL', 'PENDING_VERIFICATION', 'WAITING', 'CONFIRMED'];

const CATEGORY_EMOJI: Record<string, string> = {
  BEAUTY_SALON: '💅',
  DOCTOR: '🏥',
  CONSULTANT: '💼',
  OTHER: '🏢',
};

const STATUS_LABELS: Record<string, { label: string; color: string; bg: string }> = {
  LOCKED:               { label: 'در انتظار پرداخت', color: '#b45309', bg: '#fef3c7' },
  PENDING_VERIFICATION: { label: 'در انتظار تایید پرداخت', color: '#d97706', bg: '#fef3c7' },
  PENDING_APPROVAL:     { label: 'در انتظار تایید', color: '#d97706', bg: '#fef3c7' },
  WAITING:              { label: 'در صف',          color: '#047857', bg: '#d1fae5' },
  CONFIRMED:            { label: 'تایید شده',      color: '#047857', bg: '#d1fae5' },
  IN_PROGRESS:          { label: 'در حال سرویس',   color: '#1d4ed8', bg: '#dbeafe' },
  COMPLETED:            { label: 'انجام شد',       color: '#374151', bg: '#f3f4f6' },
  CANCELLED:            { label: 'لغو شد',         color: '#b91c1c', bg: '#fee2e2' },
  NO_SHOW:              { label: 'غیبت',           color: '#b91c1c', bg: '#fee2e2' },
};

function formatDate(ts: number): string {
  const d = new Date(ts);
  return d.toLocaleString('fa-IR', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function AppointmentDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const router = useRouter();
  const [appointment, setAppointment] = useState<Appointment | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [toast, setToast] = useState('');

  const handleCancel = async () => {
    if (!appointment) return;
    const token = localStorage.getItem('visitor_token');
    if (!token) return;

    setCancelling(true);
    try {
      await cancelAppointment(appointment.id, token);
      setAppointment({ ...appointment, status: 'CANCELLED' });
      setConfirmCancel(false);
      setToast('نوبت شما لغو شد');
      setTimeout(() => setToast(''), 3000);
    } catch (err: unknown) {
      setToast(err instanceof Error ? err.message : 'خطا در لغو نوبت');
      setTimeout(() => setToast(''), 3000);
    } finally {
      setCancelling(false);
    }
  };

  useEffect(() => {
    params.then((p) => {
      const aptId = parseInt(p.id, 10);
      const token = localStorage.getItem('visitor_token');
      if (!token) {
        router.replace(`/auth/login?redirect=/appointments/${aptId}`);
        return;
      }
      getAppointment(aptId, token)
        .then(setAppointment)
        .catch(() => setError('نوبت یافت نشد یا دسترسی ندارید'))
        .finally(() => setLoading(false));
    });
  }, [params, router]);

  if (loading) {
    return (
      <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
        <div className="toolbar">
          <div className="toolbar-placeholder" />
          <h1 className="toolbar-title">جزئیات نوبت</h1>
          <button className="toolbar-back" onClick={() => router.back()}>›</button>
        </div>
        <div style={{ padding: 24 }}>
          <div className="skeleton" style={{ width: 72, height: 72, borderRadius: '50%', margin: '8px auto 16px' }} />
          <div className="skeleton" style={{ height: 20, width: 160, borderRadius: 8, margin: '0 auto 24px' }} />
          <div className="skeleton" style={{ height: 150, borderRadius: 16, marginBottom: 16 }} />
          <div className="skeleton" style={{ height: 110, borderRadius: 16 }} />
        </div>
      </div>
    );
  }

  if (error || !appointment) {
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <div style={{ fontSize: 44, marginBottom: 12 }}>😕</div>
        <p style={{ color: 'var(--color-muted)' }}>{error}</p>
        <button className="btn-primary" style={{ marginTop: 20, width: 'auto', padding: '0 32px' }} onClick={() => router.back()}>بازگشت</button>
      </div>
    );
  }

  const st = STATUS_LABELS[appointment.status] || { label: appointment.status, color: 'var(--color-muted)', bg: '#f3f4f6' };
  const biz = appointment.business;
  const emoji = CATEGORY_EMOJI[biz.category] || '🏢';

  return (
    <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>

      <div className="toolbar">
        <div className="toolbar-placeholder" />
        <h1 className="toolbar-title">جزئیات نوبت</h1>
        <button className="toolbar-back" onClick={() => router.back()}>›</button>
      </div>

      <div style={{ padding: '24px 24px' }}>

        {/* ── Header ── */}
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{
            width: 76, height: 76, borderRadius: '50%', margin: '0 auto 12px',
            background: 'var(--color-primary-tint)', display: 'flex',
            alignItems: 'center', justifyContent: 'center', fontSize: 36,
          }}>
            {emoji}
          </div>
          <h2 style={{ fontSize: 18, fontWeight: 700, color: 'var(--color-text)' }}>{biz.title}</h2>
          <div style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 4, marginBottom: 14 }}>
            {categoryLabel(biz.category)}
          </div>
          <div style={{ display: 'inline-block', background: st.bg, color: st.color, padding: '6px 16px', borderRadius: 20, fontSize: 13, fontWeight: 700 }}>
            {st.label}
          </div>
          {appointment.status === 'WAITING' && (
            <div style={{ marginTop: 12, fontSize: 13, color: 'var(--color-success-text)', fontWeight: 600 }}>
              {appointment.queue_position === 0 ? 'نوبت شما رسیده است' : `${appointment.queue_position} نفر جلوتر از شما هستند`}
            </div>
          )}
        </div>

        {/* ── Appointment Info ── */}
        <div style={{
          background: 'var(--color-surface)', borderRadius: 16, border: '1px solid var(--color-border)',
          padding: 24, marginBottom: 16,
        }}>
          <h2 style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 12 }}>اطلاعات نوبت</h2>
          <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--color-text)', direction: 'rtl' }}>
            {formatDate(appointment.appointment_date)}
          </div>

          <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px dashed var(--color-border)', display: 'flex', justifyContent: 'space-between' }}>
            <div style={{ fontSize: 13, color: 'var(--color-muted)' }}>کد پیگیری نوبت</div>
            <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--color-text)', fontFamily: 'monospace' }}>#{appointment.id}</div>
          </div>
        </div>

        {/* ── Contact ── */}
        <div style={{
          background: 'var(--color-surface)', borderRadius: 16, border: '1px solid var(--color-border)',
          padding: 24, marginBottom: 24,
        }}>
          <h2 style={{ fontSize: 13, color: 'var(--color-muted)', marginBottom: 12 }}>راه‌های ارتباطی</h2>

          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
            <div style={{ fontSize: 12, color: 'var(--color-muted)', flexShrink: 0 }}>آدرس</div>
            {biz.address ? (
              <a
                href={`https://maps.google.com/?q=${encodeURIComponent(biz.address)}`}
                target="_blank" rel="noopener noreferrer"
                style={{ fontSize: 13, color: 'var(--color-primary)', lineHeight: 1.7, textAlign: 'left', textDecoration: 'none', fontWeight: 600 }}
              >
                {biz.address}
              </a>
            ) : (
              <div style={{ fontSize: 13, color: 'var(--color-text)' }}>ثبت نشده</div>
            )}
          </div>

          <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px dashed var(--color-border)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div style={{ fontSize: 12, color: 'var(--color-muted)' }}>تلفن تماس</div>
            {biz.phone ? (
              <a href={`tel:${biz.phone}`} dir="ltr" style={{ fontSize: 14, color: 'var(--color-primary)', textDecoration: 'none', fontWeight: 700 }}>
                {biz.phone}
              </a>
            ) : (
              <div style={{ fontSize: 13, color: 'var(--color-text)' }}>ثبت نشده</div>
            )}
          </div>
        </div>

        {/* ── Actions ── */}
        {appointment.status === 'LOCKED' && (
          <button
            className="btn-primary"
            onClick={() => router.push(`/b/Noobatyar-${biz.unique_code}/checkout/${appointment.id}`)}
            style={{ marginBottom: 12 }}
          >
            تکمیل پرداخت
          </button>
        )}

        <button
          onClick={() => router.push(`/b/Noobatyar-${biz.unique_code}`)}
          style={{
            width: '100%', height: 52, borderRadius: 14, border: '1px solid var(--color-border)',
            background: 'var(--color-surface)', color: 'var(--color-muted)', fontSize: 14, fontWeight: 600,
            cursor: 'pointer', fontFamily: 'inherit',
          }}
        >
          مشاهده پروفایل کسب‌وکار
        </button>

        {/* ── Cancel (only while the appointment is still ahead of us) ── */}
        {CANCELLABLE.includes(appointment.status) && appointment.appointment_date > Date.now() && (
          confirmCancel ? (
            <div style={{ marginTop: 12, padding: 16, borderRadius: 14, border: '1px solid var(--color-border)', background: 'var(--color-surface)' }}>
              <div style={{ fontSize: 14, color: 'var(--color-text)', textAlign: 'center', marginBottom: 14, lineHeight: 1.8 }}>
                از لغو این نوبت مطمئن هستید؟
              </div>
              <div style={{ display: 'flex', gap: 10 }}>
                <button
                  onClick={handleCancel}
                  disabled={cancelling}
                  style={{ flex: 1, height: 46, borderRadius: 12, border: 'none', background: '#b91c1c', color: '#fff', fontSize: 14, fontWeight: 700, cursor: 'pointer', fontFamily: 'inherit' }}
                >
                  {cancelling ? 'در حال لغو...' : 'بله، لغو کن'}
                </button>
                <button
                  onClick={() => setConfirmCancel(false)}
                  disabled={cancelling}
                  style={{ flex: 1, height: 46, borderRadius: 12, border: '1px solid var(--color-border)', background: 'var(--color-surface)', color: 'var(--color-muted)', fontSize: 14, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit' }}
                >
                  انصراف
                </button>
              </div>
            </div>
          ) : (
            <button
              onClick={() => setConfirmCancel(true)}
              style={{
                width: '100%', height: 52, marginTop: 12, borderRadius: 14,
                border: '1px solid #fecaca', background: 'var(--color-surface)', color: '#b91c1c',
                fontSize: 14, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit',
              }}
            >
              لغو نوبت
            </button>
          )
        )}

      </div>

      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}
