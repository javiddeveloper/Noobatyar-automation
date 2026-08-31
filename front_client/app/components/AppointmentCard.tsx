'use client';

import { useEffect, useState, type CSSProperties } from 'react';
import { useRouter } from 'next/navigation';
import { categoryLabel, mediaUrl, type Appointment } from '@/lib/api';
import { STATUS_LABELS, formatDate, toPersianNumerals } from '@/lib/format';
import Icon, { CATEGORY_ICON } from './Icon';

/** Business logo, falling back to the category icon when there is none. */
export function BusinessAvatar({
  logo,
  category,
  size = 44,
}: {
  logo: string | null;
  category: string;
  size?: number;
}) {
  const [failed, setFailed] = useState(false);
  const src = mediaUrl(logo);

  const circle: CSSProperties = {
    width: size,
    height: size,
    borderRadius: '50%',
    flexShrink: 0,
    background: 'var(--color-primary-tint)',
    color: 'var(--color-primary)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  };

  if (src && !failed) {
    return (
      <div style={circle}>
        {/* eslint-disable-next-line @next/next/no-img-element -- remote media host, no loader configured */}
        <img
          src={src}
          alt=""
          onError={() => setFailed(true)}
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
        />
      </div>
    );
  }

  return (
    <div style={circle}>
      <Icon name={CATEGORY_ICON[category] ?? 'storefront'} size={size * 0.5} />
    </div>
  );
}

/** Live "time remaining until the appointment" label. Ticks once a minute. */
export function Countdown({ target }: { target: number }) {
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
      <span className="countdown" style={{ color: 'var(--color-muted)' }}>
        <Icon name="schedule" size={14} />
        زمان نوبت فرا رسیده است
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
    <span className="countdown" style={{ color, fontWeight: 700 }}>
      <Icon name="schedule" size={14} />
      {label} مانده
    </span>
  );
}

/**
 * One appointment, as shown on both the home screen and /appointments.
 * Extracted so the two lists cannot drift apart.
 */
export default function AppointmentCard({ appt }: { appt: Appointment }) {
  const router = useRouter();
  const st =
    STATUS_LABELS[appt.status] ?? { label: appt.status, color: 'var(--color-muted)', bg: '#f3f4f6' };

  return (
    <div
      role="button"
      tabIndex={0}
      onClick={() => router.push(`/appointments/${appt.id}`)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          router.push(`/appointments/${appt.id}`);
        }
      }}
      style={{
        background: 'var(--color-surface)',
        borderRadius: 16,
        border: '1px solid var(--color-border)',
        padding: '18px 20px',
        marginBottom: 14,
        cursor: 'pointer',
        boxShadow: 'var(--shadow-card)',
      }}
    >
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          marginBottom: 14,
          gap: 10,
        }}
      >
        {/* Business identity — right (reading start) in RTL */}
        <div style={{ display: 'flex', gap: 12, alignItems: 'center', minWidth: 0 }}>
          <BusinessAvatar logo={appt.business.logo} category={appt.business.category} />
          <div style={{ textAlign: 'right', minWidth: 0 }}>
            <h3
              style={{
                fontSize: 15,
                fontWeight: 700,
                color: 'var(--color-text)',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {appt.business.title}
            </h3>
            <div style={{ fontSize: 12, color: 'var(--color-muted)', marginTop: 3 }}>
              {categoryLabel(appt.business.category)}
            </div>
          </div>
        </div>

        <span
          style={{
            background: st.bg,
            color: st.color,
            padding: '4px 10px',
            borderRadius: 8,
            fontSize: 11,
            fontWeight: 700,
            whiteSpace: 'nowrap',
            flexShrink: 0,
          }}
        >
          {st.label}
        </span>
      </div>

      <div
        style={{
          background: 'var(--color-bg)',
          borderRadius: 10,
          padding: '10px 12px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <div
          style={{
            fontSize: 11,
            color: 'var(--color-muted)',
            display: 'flex',
            alignItems: 'center',
            gap: 5,
          }}
        >
          <Icon name="calendar" size={14} />
          تاریخ نوبت
        </div>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-text)' }}>
          {formatDate(appt.appointment_date)}
        </div>
      </div>

      {(appt.reminder_sms_sent || appt.reminder_push_sent) && (
        <div style={{ display: 'flex', gap: 6, marginTop: 10, flexWrap: 'wrap' }}>
          {appt.reminder_push_sent && (
            <span className="reminder-chip">
              <Icon name="notifications" size={12} />
              اعلان یادآوری ارسال شد
            </span>
          )}
          {appt.reminder_sms_sent && (
            <span className="reminder-chip">
              <Icon name="sms" size={12} />
              پیامک یادآوری ارسال شد
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
        <button
          onClick={(e) => {
            e.stopPropagation();
            router.push(`/b/Noobatyar-${appt.business.unique_code}/checkout/${appt.id}`);
          }}
          style={{
            marginTop: 12,
            width: '100%',
            background: 'var(--color-primary)',
            color: '#fff',
            border: 'none',
            borderRadius: 10,
            padding: '10px 16px',
            fontSize: 12.5,
            fontWeight: 700,
            fontFamily: 'inherit',
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 6,
          }}
        >
          <Icon name="creditCard" size={16} />
          تکمیل پرداخت
        </button>
      )}
    </div>
  );
}
