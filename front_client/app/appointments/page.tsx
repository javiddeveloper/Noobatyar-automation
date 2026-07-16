'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getMyAppointments, type Appointment } from '@/lib/api';

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

export default function AppointmentsPage() {
  const router = useRouter();
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState<'UPCOMING' | 'PAST' | 'CANCELED'>('UPCOMING');

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

  const filteredAppointments = appointments.filter((apt) => {
    if (activeTab === 'UPCOMING') return !['COMPLETED', 'CANCELLED', 'NO_SHOW'].includes(apt.status);
    if (activeTab === 'PAST') return apt.status === 'COMPLETED';
    if (activeTab === 'CANCELED') return ['CANCELLED', 'NO_SHOW'].includes(apt.status);
    return true;
  });

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
          نوبت‌های من
        </h1>
      </div>

      <div style={{ padding: '24px 24px' }}>
        
        {/* ── Tabs ── */}
        <div style={{ display: 'flex', background: '#f3f4f6', borderRadius: 14, padding: 4, marginBottom: 24 }}>
          {(['CANCELED', 'PAST', 'UPCOMING'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              style={{
                flex: 1, padding: '10px 0', border: 'none',
                background: activeTab === tab ? 'white' : 'none',
                borderRadius: 10,
                color: activeTab === tab ? '#111827' : '#6b7280',
                fontSize: 12, fontWeight: 600, fontFamily: 'inherit',
                boxShadow: activeTab === tab ? '0 1px 3px rgba(0,0,0,0.05)' : 'none',
                cursor: 'pointer'
              }}
            >
              {tab === 'UPCOMING' ? 'پیش‌رو' : tab === 'PAST' ? 'قبلی' : 'لغو شده'}
            </button>
          ))}
        </div>

        {loading && (
          <>
            {[1, 2, 3].map((i) => (
              <div key={i} className="skeleton" style={{ height: 120, marginBottom: 16, borderRadius: 16 }} />
            ))}
          </>
        )}

        {error && (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <p style={{ color: '#6b7280' }}>{error}</p>
            <button className="btn-primary" style={{ marginTop: 16, width: 'auto', padding: '0 24px' }}
              onClick={() => location.reload()}>
              تلاش مجدد
            </button>
          </div>
        )}

        {!loading && !error && filteredAppointments.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <div style={{ fontSize: 52, marginBottom: 12 }}>🗓</div>
            <h2 style={{ fontSize: 16, color: '#111827', fontWeight: 600 }}>نوبتی یافت نشد</h2>
            <p style={{ fontSize: 13, color: '#6b7280', marginTop: 6 }}>
              شما در این بخش نوبتی ندارید
            </p>
          </div>
        )}

        {filteredAppointments.map((appt) => {
          const st = STATUS_LABELS[appt.status] || { label: appt.status, color: '#6b7280', bg: '#f3f4f6' };
          return (
            <div
              key={appt.id}
              onClick={() => router.push(`/appointments/${appt.id}`)}
              style={{
                background: 'white', borderRadius: 16, border: '1px solid #e5e7eb',
                padding: '20px 24px', marginBottom: 16, cursor: 'pointer',
                boxShadow: '0 1px 3px rgba(0,0,0,0.02)'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
                <span style={{
                  background: st.bg, color: st.color, padding: '4px 10px',
                  borderRadius: 8, fontSize: 11, fontWeight: 700
                }}>
                  {st.label}
                </span>
                <div style={{ textAlign: 'right' }}>
                  <h3 style={{ fontSize: 15, fontWeight: 700, color: '#111827' }}>{appt.business.title}</h3>
                  <div style={{ fontSize: 12, color: '#6b7280', marginTop: 4 }}>{appt.business.category}</div>
                </div>
              </div>

              <div style={{ background: '#f9fafb', borderRadius: 10, padding: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ fontSize: 13, fontWeight: 600, color: '#111827' }}>{formatDate(appt.appointment_date)}</div>
                <div style={{ fontSize: 11, color: '#6b7280' }}>تاریخ نوبت</div>
              </div>

              {appt.status === 'LOCKED' && (
                <div style={{ marginTop: 12, textAlign: 'center' }}>
                  <button
                    onClick={(e) => { e.stopPropagation(); router.push(`/b/${appt.business.unique_code}/checkout/${appt.id}`); }}
                    style={{ background: '#d735a9', color: 'white', border: 'none', borderRadius: 8, padding: '8px 16px', fontSize: 12, fontWeight: 600, cursor: 'pointer', width: '100%', fontFamily: 'inherit' }}
                  >
                    تکمیل پرداخت
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
