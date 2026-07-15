'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getMyAppointments, type Appointment } from '@/lib/api';

const STATUS_LABELS: Record<string, { label: string; cls: string }> = {
  PENDING_APPROVAL: { label: 'در انتظار تایید', cls: 'status-pending' },
  WAITING:          { label: 'در صف',          cls: 'status-waiting' },
  IN_PROGRESS:      { label: 'در حال سرویس',  cls: 'status-waiting' },
  COMPLETED:        { label: 'انجام شد',        cls: 'status-done' },
  CANCELLED:        { label: 'لغو شد',          cls: 'status-cancelled' },
  NO_SHOW:          { label: 'غیبت',            cls: 'status-cancelled' },
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

export default function AppointmentsPage() {
  const router = useRouter();
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const token = localStorage.getItem('access_token');
    if (!token) {
      router.replace('/auth/login?redirect=/appointments');
      return;
    }
    getMyAppointments(token)
      .then(setAppointments)
      .catch(() => setError('خطا در بارگذاری نوبت‌ها'))
      .finally(() => setLoading(false));
  }, [router]);

  return (
    <div className="page-content">
      {/* ── Header ── */}
      <div style={{
        background: 'linear-gradient(135deg, #f65cca, #d735a9)',
        padding: '20px 24px 24px',
        color: 'white',
      }}>
        <h1 style={{ color: 'white', fontSize: 18 }}>نوبت‌های من</h1>
        <p style={{ color: 'rgba(255,255,255,0.85)', fontSize: 12, marginTop: 4 }}>
          تمام نوبت‌های ثبت‌شده شما
        </p>
      </div>

      <div style={{ padding: '20px 24px' }}>
        {loading && (
          <>
            {[1, 2, 3].map((i) => (
              <div key={i} className="skeleton" style={{ height: 100, marginBottom: 12, borderRadius: 14 }} />
            ))}
          </>
        )}

        {error && (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <p>{error}</p>
            <button className="btn-primary" style={{ marginTop: 16, width: 'auto', padding: '0 24px' }}
              onClick={() => location.reload()}>
              تلاش مجدد
            </button>
          </div>
        )}

        {!loading && !error && appointments.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <div style={{ fontSize: 52, marginBottom: 12 }}>🗓</div>
            <h2 style={{ fontSize: 16, color: '#111827' }}>نوبتی ندارید</h2>
            <p style={{ fontSize: 13, color: '#6b7280', marginTop: 6 }}>
              برای ثبت نوبت یک کسب‌وکار را جستجو کنید
            </p>
          </div>
        )}

        {appointments.map((appt) => {
          const st = STATUS_LABELS[appt.status] || { label: appt.status, cls: 'status-pending' };
          return (
            <div
              key={appt.id}
              className="info-card"
              style={{ marginBottom: 12, cursor: 'pointer' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span className={`status-badge ${st.cls}`}>{st.label}</span>
                <h3 style={{ fontSize: 14, color: '#111827' }}>{appt.business.title}</h3>
              </div>

              <div className="info-row" style={{ marginTop: 4 }}>
                <span className="value">{formatDate(appt.appointment_date)}</span>
                <span className="label">تاریخ نوبت</span>
              </div>

              {appt.status === 'WAITING' && (
                <div className="notice-banner" style={{ marginTop: 8, padding: '8px 12px' }}>
                  {appt.queue_position === 0
                    ? '🟢 نوبت شما رسیده!'
                    : `⏳ ${appt.queue_position} نفر جلوتر از شما`}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
