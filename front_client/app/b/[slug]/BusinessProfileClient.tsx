'use client';

import { useRouter } from 'next/navigation';
import { categoryLabel, type Business } from '@/lib/api';

interface Props {
  business: Business;
  slug: string;
}

const CATEGORY_EMOJI: Record<string, string> = {
  BEAUTY_SALON: '💅',
  DOCTOR: '🏥',
  CONSULTANT: '💼',
  OTHER: '🏢',
};

export default function BusinessProfileClient({ business, slug }: Props) {
  const router = useRouter();

  const workHours = `${String(business.work_start_hour).padStart(2, '0')}:۰۰ الی ${String(business.work_end_hour).padStart(2, '0')}:۰۰`;
  const emoji = CATEGORY_EMOJI[business.category] || '🏢';
  const bookingEnabled = business.booking_enabled !== false; // default true if undefined

  return (
    <div className="page-content">

      {/* ── Header Card ── */}
      <div style={{
        background: 'linear-gradient(135deg, var(--color-primary-light), var(--color-primary))',
        borderRadius: '0 0 40px 40px',
        padding: '32px 24px 32px',
        color: 'white',
        position: 'relative',
        overflow: 'hidden'
      }}>
        {/* Decorative circles */}
        <div style={{
          position: 'absolute', top: -20, left: -20,
          width: 120, height: 120, borderRadius: '50%',
          background: 'rgba(255,255,255,0.08)'
        }} />
        <div style={{
          position: 'absolute', bottom: -30, right: -10,
          width: 80, height: 80, borderRadius: '50%',
          background: 'rgba(255,255,255,0.06)'
        }} />

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 12, marginBottom: 16 }}>
          <h1 style={{ fontSize: 20, fontWeight: 700, margin: 0, color: 'white', textAlign: 'right' }}>
            {business.title}
          </h1>
          <div style={{
            width: 52, height: 52, borderRadius: '50%',
            background: 'rgba(255,255,255,0.22)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 26, flexShrink: 0
          }}>
            {/* Show logo if available, else emoji */}
            {business.logo
              ? <img src={business.logo.startsWith('http') ? business.logo : `${process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000'}${business.logo}`} alt={business.title} style={{ width: '100%', height: '100%', borderRadius: '50%', objectFit: 'cover' }} />
              : emoji}
          </div>
        </div>

        <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.9)', textAlign: 'right', lineHeight: 1.8, marginBottom: 20, whiteSpace: 'pre-line' }}>
          {business.bio || `ارائه خدمات تخصصی در زمینه ${categoryLabel(business.category)} توسط کادر مجرب.`}
        </p>

        {(business.address || business.phone) && (
          <div style={{ textAlign: 'right', display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'flex-end' }}>
            {business.address && (
              <div style={{
                display: 'inline-flex', alignItems: 'center', gap: 6,
                background: 'var(--color-surface)', color: 'var(--color-text)',
                borderRadius: 20, padding: '8px 16px',
                fontSize: 12, fontWeight: 600
              }}>
                <span>📍</span>
                <span>{business.address}</span>
              </div>
            )}
            {business.phone && (
              <a href={`tel:${business.phone}`} style={{ textDecoration: 'none' }}>
                <div style={{
                  display: 'inline-flex', alignItems: 'center', gap: 8,
                  background: 'linear-gradient(to right, #10b981, #059669)', color: 'white',
                  borderRadius: 20, padding: '8px 16px',
                  fontSize: 13, fontWeight: 700,
                  boxShadow: '0 4px 12px rgba(5, 150, 105, 0.3)'
                }}>
                  <span style={{ fontSize: 16 }}>📞</span>
                  <span dir="ltr">{business.phone}</span>
                </div>
              </a>
            )}
          </div>
        )}
      </div>

      {/* ── Info Card ── */}
      <div className="section" style={{ paddingTop: 24 }}>
        <div className="info-card">
          <div className="info-row">
            <span className="label">ساعات کاری</span>
            <span className="value">{workHours}</span>
          </div>
          <div className="info-divider" />
          <div className="info-row">
            <span className="label">بیعانه</span>
            <span className="value">
              {business.deposit_mode === 'NONE' ? 'بدون بیعانه' : (business.deposit_mode === 'MANDATORY' ? 'اجباری' : 'اختیاری')}
              {business.deposit_amount ? ` (${business.deposit_amount.toLocaleString()} تومان)` : ''}
            </span>
          </div>
          {business.payment_method && business.payment_method !== 'NONE' && (
            <>
              <div className="info-divider" />
              <div className="info-row">
                <span className="label">روش پرداخت</span>
                <span className="value">
                  {business.payment_method === 'CARD' ? 'کارت به کارت' : 'درگاه آنلاین'}
                </span>
              </div>
            </>
          )}
        </div>
      </div>

      {/* ── Notice Banner (dynamic from backend) ── */}
      {business.notice_message && (
        <div className="section" style={{ paddingTop: 8 }}>
          <div className="notice-banner">
            <span>⏱ </span>
            {business.notice_message}
          </div>
        </div>
      )}

      {/* ── Booking Disabled Banner ── */}
      {!bookingEnabled && (
        <div className="section" style={{ paddingTop: 8 }}>
          <div style={{
            background: '#fee2e2',
            borderRadius: 12, padding: '14px 18px',
            fontSize: 13, color: '#b91c1c', fontWeight: 600,
            textAlign: 'right', lineHeight: 1.6,
            display: 'flex', alignItems: 'center', gap: 8
          }}>
            <span>🚫</span>
            ثبت نوبت در حال حاضر غیرفعال است
          </div>
        </div>
      )}

      {/* ── Noobatyar Promo ── */}
      <div className="section" style={{ paddingTop: 8, paddingBottom: 8 }}>
        <div className="promo-banner purple">
          <div style={{
            position: 'absolute', bottom: -20, right: -20,
            width: 100, height: 100, borderRadius: '50%',
            background: 'rgba(255,255,255,0.07)'
          }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <h2 style={{ color: 'white', fontSize: 13 }}>صاحب کسب‌وکار هستید؟</h2>
              <p style={{ fontSize: 11, color: 'rgba(237,229,255,0.9)', marginTop: 4 }}>
                نوبت‌دهی آنلاین با نوبت‌یار برای کسب‌وکار شما
              </p>
              <a href="https://noobatyar.ir" target="_blank" rel="noopener noreferrer" style={{ textDecoration: 'none', display: 'block' }}>
                <div className="promo-badge" style={{ color: 'var(--color-primary-dark)', marginTop: 10 }}>
                  <span>شروع رایگان در noobatyar.ir</span>
                </div>
              </a>
            </div>
            <div style={{
              width: 40, height: 40, borderRadius: '50%',
              background: 'rgba(255,255,255,0.18)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 20, flexShrink: 0
            }}>
              📣
            </div>
          </div>
        </div>
      </div>

      {/* ── Fixed Bottom Button ── */}
      <div className="btn-group">
        <button
          className="btn-primary"
          onClick={() => router.push(`/b/${slug}/book`)}
          disabled={!bookingEnabled}
          style={!bookingEnabled ? { background: 'var(--color-faint)', boxShadow: 'none', cursor: 'not-allowed' } : {}}
        >
          {bookingEnabled ? 'دریافت/مشاهده نوبت' : 'ثبت نوبت غیرفعال است'}
        </button>
      </div>

    </div>
  );
}
