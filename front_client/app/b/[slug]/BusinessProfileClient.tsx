'use client';

import { useRouter } from 'next/navigation';
import Image from 'next/image';
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

      {/* ── Cover ── */}
      <div className="cover" style={{ background: 'linear-gradient(135deg, #f65cca, #d735a9)' }}>
        {/* Avatar */}
        {business.logo ? (
          <Image
            src={`http://127.0.0.1:8000${business.logo}`}
            alt={business.title}
            width={72}
            height={72}
            className="biz-avatar"
          />
        ) : (
          <div className="biz-avatar-placeholder">
            <span style={{ fontSize: 30 }}>{emoji}</span>
          </div>
        )}
      </div>

      {/* ── Business Name & Category ── */}
      <div style={{ padding: '48px 24px 0' }}>
        <h1 style={{ fontSize: 18, fontWeight: 700, textAlign: 'right', color: '#111827' }}>
          {business.title}
        </h1>
        <p style={{ fontSize: 12, color: '#9e2983', fontWeight: 600, marginTop: 4, textAlign: 'right' }}>
          {categoryLabel(business.category)}
        </p>
      </div>

      {/* ── Promo Banner (Business Info) ── */}
      <div className="section" style={{ paddingTop: 16 }}>
        <div className="promo-banner pink">
          <div style={{
            position: 'absolute', top: -20, left: -20,
            width: 120, height: 120, borderRadius: '50%',
            background: 'rgba(255,255,255,0.08)'
          }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div style={{ flex: 1 }}>
              <h2 style={{ color: 'white', fontSize: 14, marginBottom: 4 }}>{business.title}</h2>
              {business.address && (
                <p style={{ color: 'rgba(255,255,255,0.9)', fontSize: 11, marginBottom: 10 }}>
                  {business.address}
                </p>
              )}
              {business.address && (
                <div className="promo-badge" style={{ color: '#6c1453' }}>
                  <span>📍</span>
                  <span style={{ fontSize: 10 }}>{business.address}</span>
                </div>
              )}
            </div>
            <div style={{
              width: 40, height: 40, borderRadius: '50%',
              background: 'rgba(255,255,255,0.2)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 20, flexShrink: 0, marginRight: 12
            }}>
              {emoji}
            </div>
          </div>
        </div>
      </div>

      {/* ── Info Card ── */}
      <div className="section" style={{ paddingTop: 8 }}>
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
          <div className="info-divider" />
          <div className="info-row">
            <span className="label">مدت سرویس</span>
            <span className="value">{business.default_service_duration} دقیقه</span>
          </div>
        </div>
      </div>

      {/* ── Notice Banner (placeholder) ── */}
      <div className="section" style={{ paddingTop: 8 }}>
        <div className="notice-banner">
          <span>⏱ </span>
          برای مشاهده ساعات خالی و ثبت نوبت روی دکمه زیر کلیک کنید
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
                <span>🚀</span>
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
          دریافت / مشاهده نوبت
        </button>
      </div>

    </div>
  );
}
