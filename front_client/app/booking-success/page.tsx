'use client';

import { Suspense, useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { getAppointment, type Appointment } from '@/lib/api';
import { STATUS_LABELS, formatDate } from '@/lib/format';
import { BusinessAvatar } from '@/app/components/AppointmentCard';
import Icon from '@/app/components/Icon';

/** Colours for the confetti burst. Brand purple + the success greens. */
const CONFETTI_COLORS = ['#8b5cf6', '#a78bfa', '#34d399', '#10b981', '#fbbf24'];

function Confetti() {
  // Deterministic spread rather than Math.random(): a server/client mismatch
  // is impossible here (client component, rendered after mount), but fixed
  // values also keep the burst from re-shuffling on every re-render.
  const pieces = Array.from({ length: 14 }, (_, i) => ({
    left: `${6 + i * 6.6}%`,
    delay: `${(i % 7) * 0.11}s`,
    color: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
    duration: `${2.2 + (i % 4) * 0.35}s`,
  }));

  return (
    <>
      {pieces.map((p, i) => (
        <span
          key={i}
          className="confetti-piece"
          aria-hidden="true"
          style={{
            left: p.left,
            background: p.color,
            animationDelay: p.delay,
            animationDuration: p.duration,
          }}
        />
      ))}
    </>
  );
}

function BookingSuccess() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const id = Number(searchParams.get('id'));
  // "pending" (default) = waiting on the business to approve.
  // "paid" = a deposit receipt was just submitted and needs verifying.
  const kind = searchParams.get('kind') === 'paid' ? 'paid' : 'pending';

  const [appt, setAppt] = useState<Appointment | null>(null);

  // The summary is a nicety — the confirmation must stand on its own even if
  // this fetch fails, so there is no error branch and no spinner gate.
  useEffect(() => {
    if (!id) return;
    const token = localStorage.getItem('visitor_token');
    if (!token) return;
    getAppointment(id, token)
      .then(setAppt)
      .catch(() => {});
  }, [id]);

  const status = appt ? STATUS_LABELS[appt.status] : null;

  return (
    <div className="success-screen">
      <Confetti />

      <div className="success-badge">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M4.5 12.5l5 5 10-10" />
        </svg>
      </div>

      <h1 className="success-title">نوبت شما ثبت شد</h1>
      <p className="success-sub">
        {kind === 'paid'
          ? 'پرداخت شما ثبت شد. پس از بررسی فیش توسط کسب‌وکار، نوبت قطعی می‌شود و به شما اطلاع می‌دهیم.'
          : 'درخواست نوبت شما برای کسب‌وکار ارسال شد. به‌محض تأیید، از طریق پیامک و اعلان به شما خبر می‌دهیم.'}
      </p>

      {appt && (
        <div className="success-card info-card">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <BusinessAvatar
              logo={appt.business.logo}
              category={appt.business.category}
              size={42}
            />
            <div style={{ textAlign: 'right', flex: 1, minWidth: 0 }}>
              <h3 style={{ fontSize: 14, fontWeight: 700 }}>{appt.business.title}</h3>
              {status && (
                <span
                  style={{
                    display: 'inline-block',
                    marginTop: 5,
                    background: status.bg,
                    color: status.color,
                    padding: '3px 9px',
                    borderRadius: 7,
                    fontSize: 10.5,
                    fontWeight: 700,
                  }}
                >
                  {status.label}
                </span>
              )}
            </div>
          </div>

          <div className="info-divider" />

          <div className="info-row">
            <span className="label" style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <Icon name="calendar" size={14} />
              تاریخ و ساعت
            </span>
            <span className="value">{formatDate(appt.appointment_date)}</span>
          </div>
        </div>
      )}

      <div className="success-actions">
        <button className="btn-primary" onClick={() => router.replace('/appointments')}>
          مشاهدهٔ نوبت‌های من
        </button>
        <button
          className="btn-primary"
          style={{
            background: 'var(--color-surface)',
            color: 'var(--color-primary)',
            border: '1.5px solid var(--color-primary)',
            boxShadow: 'none',
            height: 48,
          }}
          onClick={() => router.replace('/')}
        >
          صفحهٔ اصلی
        </button>
      </div>
    </div>
  );
}

export default function BookingSuccessPage() {
  return (
    <Suspense>
      <BookingSuccess />
    </Suspense>
  );
}
