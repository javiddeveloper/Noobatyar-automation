'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getAppointment, type Appointment } from '@/lib/api';

const STATUS_LABELS: Record<string, { label: string; color: string; bg: string }> = {
  LOCKED:               { label: 'در انتظار پرداخت', color: '#b45309', bg: '#fef3c7' },
  PENDING_VERIFICATION: { label: 'در انتظار تایید پرداخت', color: '#d97706', bg: '#fef3c7' },
  PENDING_APPROVAL:     { label: 'در انتظار تایید', color: '#d97706', bg: '#fef3c7' },
  WAITING:              { label: 'در صف',          color: '#047857', bg: '#d1fae5' },
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

  useEffect(() => {
    params.then((p) => {
      const aptId = parseInt(p.id, 10);
      const token = localStorage.getItem('access_token');
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

  const st = STATUS_LABELS[appointment.status] || { label: appointment.status, color: '#6b7280', bg: '#f3f4f6' };

  return (
    <div className="page-content" style={{ background: '#f9fafb', minHeight: '100dvh' }}>
      
      {/* ── Header ── */}
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '16px 24px', background: 'white', position: 'sticky', top: 0, zIndex: 50
      }}>
        <button
          onClick={() => router.back()}
          style={{
            background: 'none', border: 'none', cursor: 'pointer',
            fontSize: 22, color: '#111827', padding: '4px 8px',
          }}
        >
          ←
        </button>
        <h1 style={{ fontSize: 16, fontWeight: 700, color: '#111827' }}>
          جزئیات نوبت
        </h1>
      </div>

      <div style={{ padding: '24px 24px' }}>
        
        {/* Status Box */}
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <div style={{ display: 'inline-block', background: st.bg, color: st.color, padding: '6px 16px', borderRadius: 20, fontSize: 13, fontWeight: 700 }}>
            {st.label}
          </div>
          {appointment.status === 'WAITING' && (
            <div style={{ marginTop: 12, fontSize: 13, color: '#047857', fontWeight: 600 }}>
              {appointment.queue_position === 0 ? 'نوبت شما رسیده است' : `${appointment.queue_position} نفر جلوتر از شما هستند`}
            </div>
          )}
        </div>

        {/* Business Info */}
        <div style={{
          background: 'white', borderRadius: 16, border: '1px solid #e5e7eb',
          padding: 24, marginBottom: 16
        }}>
          <h2 style={{ fontSize: 13, color: '#6b7280', marginBottom: 12 }}>کسب‌وکار</h2>
          <div style={{ fontSize: 16, fontWeight: 700, color: '#111827' }}>{appointment.business.title}</div>
          <div style={{ fontSize: 13, color: '#6b7280', marginTop: 4 }}>{appointment.business.category}</div>
          
          <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px dashed #e5e7eb' }}>
            <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 4 }}>آدرس</div>
            <div style={{ fontSize: 13, color: '#111827', lineHeight: 1.6 }}>{appointment.business.address || 'ثبت نشده'}</div>
          </div>
          <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px dashed #e5e7eb' }}>
            <div style={{ fontSize: 12, color: '#6b7280', marginBottom: 4 }}>تلفن تماس</div>
            <div style={{ fontSize: 13, color: '#111827' }}>{appointment.business.phone || 'ثبت نشده'}</div>
          </div>
        </div>

        {/* Appointment Info */}
        <div style={{
          background: 'white', borderRadius: 16, border: '1px solid #e5e7eb',
          padding: 24, marginBottom: 24
        }}>
          <h2 style={{ fontSize: 13, color: '#6b7280', marginBottom: 12 }}>اطلاعات نوبت</h2>
          <div style={{ fontSize: 15, fontWeight: 700, color: '#111827', direction: 'rtl' }}>
            {formatDate(appointment.appointment_date)}
          </div>
          
          <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px dashed #e5e7eb', display: 'flex', justifyContent: 'space-between' }}>
            <div style={{ fontSize: 13, color: '#6b7280' }}>کد پیگیری نوبت</div>
            <div style={{ fontSize: 14, fontWeight: 700, color: '#111827', fontFamily: 'monospace' }}>#{appointment.id}</div>
          </div>
        </div>

        {/* Actions */}
        {appointment.status === 'LOCKED' && (
          <button
            className="btn-primary"
            onClick={() => router.push(`/b/${appointment.business.unique_code}/checkout/${appointment.id}`)}
            style={{ marginBottom: 12 }}
          >
            تکمیل پرداخت
          </button>
        )}
        
        <button
          onClick={() => router.push(`/b/Noobatyar-${appointment.business.unique_code}`)}
          style={{
            width: '100%', height: 52, borderRadius: 14, border: '1px solid #d1d5db',
            background: 'white', color: '#374151', fontSize: 14, fontWeight: 600,
            cursor: 'pointer', fontFamily: 'inherit'
          }}
        >
          مشاهده پروفایل کسب‌وکار
        </button>

      </div>
    </div>
  );
}
