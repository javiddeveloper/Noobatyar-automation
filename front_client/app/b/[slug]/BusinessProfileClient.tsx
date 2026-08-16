'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { categoryLabel, type Business } from '@/lib/api';
import BusinessNotice from '@/app/components/BusinessNotice';

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

const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const toFa = (n: string | number) =>
  String(n).replace(/[0-9]/g, (d) => FA_DIGITS[+d]);

export default function BusinessProfileClient({ business, slug }: Props) {
  const router = useRouter();
  const [isNavigating, setIsNavigating] = useState(false);

  const emoji = CATEGORY_EMOJI[business.category] || '🏢';
  const bookingEnabled = business.booking_enabled !== false; // default true if undefined

  const logoUrl = business.logo
    ? business.logo.startsWith('http')
      ? business.logo
      : `${process.env.NEXT_PUBLIC_API_URL || 'http://127.0.0.1:8000'}${business.logo}`
    : null;

  // ── Stat values (short, so they fit the compact cards) ──
  const workHours = `${toFa(String(business.work_start_hour).padStart(2, '0'))} تا ${toFa(String(business.work_end_hour).padStart(2, '0'))}`;

  const depositText =
    business.deposit_mode === 'NONE'
      ? 'رایگان'
      : business.deposit_amount
        ? `${toFa(business.deposit_amount.toLocaleString('en-US'))} ت`
        : business.deposit_mode === 'MANDATORY'
          ? 'اجباری'
          : 'اختیاری';

  // Payment comes from accepted_payment_methods. The older single-value
  // payment_method is only a fallback: the owner app never sends it, so it is
  // permanently "NONE" and reading it made every business look cash-free.
  const methods = business.accepted_payment_methods?.length
    ? business.accepted_payment_methods
    : business.payment_method && business.payment_method !== 'NONE'
      ? [business.payment_method]
      : [];

  const acceptsOnline = methods.includes('ONLINE') || methods.includes('GATEWAY');
  const acceptsCard = methods.includes('CARD');
  const acceptsCash = methods.includes('CASH');

  const paymentText =
    acceptsOnline && acceptsCard ? 'آنلاین / کارت'
      : acceptsOnline ? 'آنلاین'
        : acceptsCard && acceptsCash ? 'کارت / محل'
          : acceptsCard ? 'کارت به کارت'
            : acceptsCash ? 'در محل'
              : 'ندارد';

  const paymentIcon = acceptsOnline ? '🌐' : acceptsCard ? '🏦' : acceptsCash ? '💵' : '🏦';

  return (
    <div className="page-content">

      {/* ── Hero ── */}
      <div className="biz-hero">
        <div className="biz-hero-avatar">
          {logoUrl
            ? <img src={logoUrl} alt={business.title} />
            : emoji}
        </div>

        <h1 className="biz-hero-title">{business.title}</h1>

        <span className="biz-hero-chip">
          <span>{emoji}</span>
          <span>{categoryLabel(business.category)}</span>
        </span>

        <p className="biz-hero-bio">
          {business.bio || `ارائه خدمات تخصصی در زمینه ${categoryLabel(business.category)} توسط کادر مجرب.`}
        </p>

        {business.phone && (
          <div className="biz-hero-actions">
            <a href={`tel:${business.phone}`} className="biz-action call">
              <span style={{ fontSize: 15 }}>📞</span>
              <span dir="ltr">{toFa(business.phone)}</span>
            </a>
          </div>
        )}
      </div>

      {/* ── Owner notice — first thing under the hero, before any detail ── */}
      <BusinessNotice business={business} style={{ paddingTop: 16 }} />

      {/* ── Stats strip ── */}
      <div className="section" style={{ paddingTop: 20, paddingBottom: 8 }}>
        <div className="stat-grid">
          <div className="stat-card">
            <div className="ico">🕐</div>
            <div className="k">ساعات کاری</div>
            <div className="v">{workHours}</div>
          </div>
          <div className="stat-card">
            <div className="ico">💳</div>
            <div className="k">بیعانه</div>
            <div className="v">{depositText}</div>
          </div>
          <div className="stat-card">
            <div className="ico">{paymentIcon}</div>
            <div className="k">روش پرداخت</div>
            <div className="v">{paymentText}</div>
          </div>
        </div>
      </div>

      {/* ── Address ── */}
      {business.address && (
        <div className="section" style={{ paddingTop: 8, paddingBottom: 8 }}>
          <a
            className="biz-address"
            href={`https://maps.google.com/?q=${encodeURIComponent(business.address)}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            <div className="pin">📍</div>
            <div className="addr-text">
              <div className="addr-k">آدرس</div>
              <div className="addr-v">{business.address}</div>
            </div>
            <span className="chevron">‹</span>
          </a>
        </div>
      )}

      {/* ── Booking Disabled Banner ──
          Stays down here, next to the CTA it explains, rather than next to the
          notice at the top: the two say different things (one is the owner's
          message, one is the state of the button) and separating them keeps a
          business that is both closed *and* running late from stacking two
          near-identical boxes above the fold. */}
      {!bookingEnabled && (
        <div className="section" style={{ paddingTop: 8, paddingBottom: 8 }}>
          <div style={{
            background: 'var(--color-error-bg)',
            border: '1px solid var(--color-error)',
            borderRadius: 12, padding: '14px 18px',
            fontSize: 13, color: 'var(--color-error)', fontWeight: 600,
            textAlign: 'right', lineHeight: 1.6,
            display: 'flex', alignItems: 'center', gap: 8,
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
            position: 'absolute', bottom: -20, insetInlineEnd: -20,
            width: 100, height: 100, borderRadius: '50%',
            background: 'rgba(255,255,255,0.07)',
          }} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
            <div style={{ textAlign: 'right' }}>
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
              width: 44, height: 44, borderRadius: '50%',
              background: 'rgba(255,255,255,0.18)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: 22, flexShrink: 0,
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
          onClick={() => {
            setIsNavigating(true);
            router.push(`/b/${slug}/book`);
          }}
          disabled={!bookingEnabled || isNavigating}
          style={!bookingEnabled ? { background: 'var(--color-faint)', boxShadow: 'none', cursor: 'not-allowed' } : {}}
        >
          {!bookingEnabled
            ? 'ثبت نوبت غیرفعال است'
            : isNavigating
              ? <>در حال بارگذاری<span className="btn-spinner" /></>
              : 'دریافت / مشاهده نوبت'}
        </button>
      </div>

    </div>
  );
}
