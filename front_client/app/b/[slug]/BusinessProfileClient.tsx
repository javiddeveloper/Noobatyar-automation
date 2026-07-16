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

  return (
    <div className="page-content">

      {/* ── Header Card ── */}
      <div style={{
        background: 'linear-gradient(135deg, #f65cca, #d735a9)',
        borderRadius: '0 0 40px 40px',
        padding: '32px 24px 32px',
        color: 'white',
        position: 'relative',
        overflow: 'hidden'
      }}>
        {/* Background decorative circles */}
        <div style={{
          position: 'absolute', top: -20, left: -20,
          width: 120, height: 120, borderRadius: '50%',
          background: 'rgba(255,255,255,0.08)'
        }} />

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 12, marginBottom: 16 }}>
          <h1 style={{ fontSize: 18, fontWeight: 700, margin: 0, color: 'white', textAlign: 'right' }}>
            {business.title}
          </h1>
          <div style={{
            width: 48, height: 48, borderRadius: '50%',
            background: 'rgba(255,255,255,0.2)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 24, flexShrink: 0
          }}>
            {emoji}
          </div>
        </div>

        <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.9)', textAlign: 'right', lineHeight: 1.8, marginBottom: 20 }}>
          ارائه خدمات تخصصی در زمینه {categoryLabel(business.category)} توسط کادر مجرب.
        </p>

        {business.address && (
          <div style={{ textAlign: 'right' }}>
            <div style={{
              display: 'inline-flex', alignItems: 'center', gap: 6,
              background: 'white', color: '#111827',
              borderRadius: 20, padding: '8px 16px',
              fontSize: 12, fontWeight: 600
            }}>
              <span>📍</span>
              <span>{business.address}</span>
            </div>
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
            <span className="label">نحوه پرداخت (بیعانه)</span>
            <span className="value">کارت به کارت</span>
          </div>
        </div>
      </div>

      {/* ── Notice Banner ── */}
      <div className="section" style={{ paddingTop: 8 }}>
        <div className="notice-banner">
          <span>⏱ </span>
          این هفته رفتیم مسافرت نوبت ها از هفته دیگه بررسی میشه
        </div>
      </div>

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
              <div className="promo-badge" style={{ color: '#7c3bed', marginTop: 10 }}>
                <span>شروع رایگان در noobatyar.ir</span>
              </div>
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
        >
          دریافت/مشاهده نوبت
        </button>
      </div>

    </div>
  );
}
