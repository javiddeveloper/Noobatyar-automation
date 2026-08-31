'use client';

import { useState, useEffect, type CSSProperties } from 'react';
import { useRouter } from 'next/navigation';
import { getMyAppointments, categoryLabel, mediaUrl, type Appointment } from '@/lib/api';
import {
  CATEGORY_EMOJI,
  STATUS_LABELS,
  formatDate,
  toPersianNumerals,
} from '@/lib/format';

// Business logo with graceful fallback to the category emoji when the image
// is missing or fails to load.
function BusinessAvatar({ logo, category }: { logo: string | null; category: string }) {
  const [failed, setFailed] = useState(false);
  const src = mediaUrl(logo);

  const circle: CSSProperties = {
    width: 44, height: 44, borderRadius: '50%', flexShrink: 0,
    background: 'var(--color-primary-tint)', display: 'flex',
    alignItems: 'center', justifyContent: 'center', fontSize: 22, overflow: 'hidden',
  };

  if (src && !failed) {
    return (
      <div style={circle}>
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={src}
          alt=""
          onError={() => setFailed(true)}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      </div>
    );
  }

  return <div style={circle}>{CATEGORY_EMOJI[category] || '🏢'}</div>;
}

// Live "time remaining until the appointment" label. Updates once a minute.
function Countdown({ target }: { target: number }) {
  const compute = () => {
    const diffMs = target - Date.now();
    if (diffMs <= 0) return { hours: 0, minutes: 0, isPast: true };
    return {
      hours: Math.floor(diffMs / 3_600_000),
      minutes: Math.floor((diffMs % 3_600_000) / 60_000),
      isPast: false,
    };
  };

  const [left, setLeft] = useState(compute);

  useEffect(() => {
    const tick = () => setLeft(compute());
    tick();
    const interval = setInterval(tick, 60_000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target]);

  if (left.isPast) {
    return (
      <span style={{ fontSize: 12, color: 'var(--color-muted)', fontStyle: 'italic' }}>
        ⏰ زمان نوبت فرا رسیده است
      </span>
    );
  }

  const color = left.hours < 1 ? '#b91c1c' : left.hours <= 3 ? '#d97706' : '#047857';
  const days = Math.floor(left.hours / 24);
  const label =
    days >= 1
      ? `${toPersianNumerals(days)} روز و ${toPersianNumerals(left.hours % 24)} ساعت`
      : `${toPersianNumerals(left.hours)} ساعت و ${toPersianNumerals(left.minutes)} دقیقه`;

  return (
    <span style={{ fontSize: 12, fontWeight: 700, color }}>
      ⏳ {label} مانده
    </span>
  );
}

export default function AppointmentsPage() {
  const router = useRouter();
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState<'UPCOMING' | 'PAST' | 'CANCELED'>('UPCOMING');
  // Set when the deposit gateway sends the client back here. Read from the URL
  // client-side rather than via useSearchParams, which would force this whole
  // page behind a Suspense boundary for one optional banner.
  const [paymentResult, setPaymentResult] = useState<'success' | 'failed' | null>(null);

  useEffect(() => {
    const state = new URLSearchParams(window.location.search).get('payment');
    if (state === 'success' || state === 'failed') {
      setPaymentResult(state);
      // Drop the parameter so a refresh does not replay the banner.
      window.history.replaceState({}, '', '/appointments');
    }
  }, []);

  useEffect(() => {
    const token = localStorage.getItem('visitor_token');
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
    <div className="page-content" style={{ background: 'var(--color-bg)', minHeight: '100dvh' }}>
      
      <div className="toolbar">
        {/* Left slot stays empty: the floating .theme-toggle is fixed at
            top-left with z-index 200 and would swallow any click here. */}
        <div className="toolbar-placeholder" />
        <h1 className="toolbar-title">نوبت‌های من</h1>
        <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
          <button
            className="toolbar-back"
            aria-label="پروفایل من"
            title="پروفایل من"
            onClick={() => router.push('/profile')}
          >
            👤
          </button>
          <button className="toolbar-back" onClick={() => router.back()}>›</button>
        </div>
      </div>

      <div style={{ padding: '24px 24px' }}>

        {/* ── Deposit gateway result ── */}
        {paymentResult && (
          <div
            style={{
              marginBottom: 20, padding: '14px 16px', borderRadius: 14, fontSize: 14,
              lineHeight: 1.8, textAlign: 'center',
              background: paymentResult === 'success'
                ? 'rgba(34,197,94,0.12)' : 'rgba(239,68,68,0.12)',
              color: paymentResult === 'success' ? '#16a34a' : '#dc2626',
            }}
          >
            {paymentResult === 'success'
              ? '✅ پرداخت بیعانه با موفقیت انجام شد و نوبت شما قطعی است.'
              : '❌ پرداخت انجام نشد. نوبت شما ثبت نهایی نشده است.'}
          </div>
        )}

        {/* ── Tabs ── */}
        <div style={{ display: 'flex', background: 'var(--color-surface-variant)', borderRadius: 14, padding: 4, marginBottom: 24 }}>
          {(['CANCELED', 'PAST', 'UPCOMING'] as const).map((tab) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              style={{
                flex: 1, padding: '10px 0', border: 'none',
                background: activeTab === tab ? 'var(--color-surface)' : 'none',
                borderRadius: 10,
                color: activeTab === tab ? 'var(--color-text)' : 'var(--color-muted)',
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
            <p style={{ color: 'var(--color-muted)' }}>{error}</p>
            <button className="btn-primary" style={{ marginTop: 16, width: 'auto', padding: '0 24px' }}
              onClick={() => location.reload()}>
              تلاش مجدد
            </button>
          </div>
        )}

        {!loading && !error && filteredAppointments.length === 0 && (
          <div style={{ textAlign: 'center', padding: '60px 0' }}>
            <div style={{ fontSize: 52, marginBottom: 12 }}>🗓</div>
            <h2 style={{ fontSize: 16, color: 'var(--color-text)', fontWeight: 600 }}>نوبتی یافت نشد</h2>
            <p style={{ fontSize: 13, color: 'var(--color-muted)', marginTop: 6 }}>
              شما در این بخش نوبتی ندارید
            </p>
          </div>
        )}

        {filteredAppointments.map((appt) => {
          const st = STATUS_LABELS[appt.status] || { label: appt.status, color: 'var(--color-muted)', bg: '#f3f4f6' };
          return (
            <div
              key={appt.id}
              onClick={() => router.push(`/appointments/${appt.id}`)}
              style={{
                background: 'var(--color-surface)', borderRadius: 16, border: '1px solid var(--color-border)',
                padding: '20px 24px', marginBottom: 16, cursor: 'pointer',
                boxShadow: '0 1px 3px rgba(0,0,0,0.02)'
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
                {/* Business identity — sits on the right (reading start) in RTL */}
                <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                  <BusinessAvatar logo={appt.business.logo} category={appt.business.category} />
                  <div style={{ textAlign: 'right' }}>
                    <h3 style={{ fontSize: 15, fontWeight: 700, color: 'var(--color-text)' }}>{appt.business.title}</h3>
                    <div style={{ fontSize: 12, color: 'var(--color-muted)', marginTop: 4 }}>{categoryLabel(appt.business.category)}</div>
                  </div>
                </div>
                {/* Status badge — sits on the left in RTL */}
                <span style={{
                  background: st.bg, color: st.color, padding: '4px 10px',
                  borderRadius: 8, fontSize: 11, fontWeight: 700, whiteSpace: 'nowrap',
                }}>
                  {st.label}
                </span>
              </div>

              <div style={{ background: 'var(--color-bg)', borderRadius: 10, padding: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ fontSize: 11, color: 'var(--color-muted)' }}>تاریخ نوبت</div>
                <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text)' }}>{formatDate(appt.appointment_date)}</div>
              </div>

              {(appt.reminder_sms_sent || appt.reminder_push_sent) && (
                <div style={{ display: 'flex', gap: 6, marginTop: 10, flexWrap: 'wrap' }}>
                  {appt.reminder_push_sent && (
                    <span style={{
                      background: 'var(--color-primary-tint)', color: 'var(--color-primary-dark)',
                      padding: '3px 8px', borderRadius: 8, fontSize: 10, fontWeight: 600,
                    }}>
                      🔔 اعلان یادآوری ارسال شد
                    </span>
                  )}
                  {appt.reminder_sms_sent && (
                    <span style={{
                      background: 'var(--color-primary-tint)', color: 'var(--color-primary-dark)',
                      padding: '3px 8px', borderRadius: 8, fontSize: 10, fontWeight: 600,
                    }}>
                      💬 پیامک یادآوری ارسال شد
                    </span>
                  )}
                </div>
              )}

              {(appt.status === 'WAITING' || appt.status === 'CONFIRMED') && (
                <div style={{ marginTop: 12, textAlign: 'center' }}>
                  <Countdown target={appt.appointment_date} />
                </div>
              )}

              {appt.status === 'LOCKED' && (
                <div style={{ marginTop: 12, textAlign: 'center' }}>
                  <button
                    onClick={(e) => { e.stopPropagation(); router.push(`/b/Noobatyar-${appt.business.unique_code}/checkout/${appt.id}`); }}
                    style={{ background: 'var(--color-primary)', color: 'white', border: 'none', borderRadius: 8, padding: '8px 16px', fontSize: 12, fontWeight: 600, cursor: 'pointer', width: '100%', fontFamily: 'inherit' }}
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
