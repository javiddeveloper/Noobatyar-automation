import SupportLinks from '@/app/components/SupportLinks';

export const metadata = {
  title: 'نوبت‌یار | رزرو آنلاین نوبت',
  description: 'با اپلیکیشن نوبت‌یار، نوبت کسب‌وکارهای مورد علاقه‌تان را آنلاین رزرو کنید',
};

/**
 * "/" has no business context — it's not the entry point for booking (that's
 * always /b/<code>), so it must not leak an unauthenticated directory of every
 * registered business. It's the marketing landing spot instead: promote the
 * Noobatyar app itself. See app/b/[slug]/BusinessProfileClient.tsx for the
 * equivalent "promo-banner" pattern used inline on business pages.
 */
export default function Home() {
  return (
    <div className="page-content">
      <div
        style={{
          minHeight: '100dvh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '40px 24px',
          textAlign: 'center',
        }}
      >
        {/* eslint-disable-next-line @next/next/no-img-element -- local static brand mark */}
        <img
          src="/icons/icon-192.png"
          alt="نوبت‌یار"
          width={84}
          height={84}
          style={{ borderRadius: 22, marginBottom: 22 }}
        />

        <h1 style={{ fontSize: 20, fontWeight: 700, color: 'var(--color-text)', marginBottom: 8 }}>
          نوبت‌یار
        </h1>

        <p
          style={{
            fontSize: 13,
            lineHeight: 2,
            color: 'var(--color-muted)',
            maxWidth: 300,
            marginBottom: 26,
          }}
        >
          نوبت کسب‌وکارهای مورد علاقه‌تان را آنلاین و در چند ثانیه رزرو کنید
        </p>

        <div className="promo-banner purple" style={{ width: '100%', maxWidth: 360, marginBottom: 30 }}>
          <div
            style={{
              position: 'absolute',
              bottom: -20,
              insetInlineEnd: -20,
              width: 100,
              height: 100,
              borderRadius: '50%',
              background: 'rgba(255,255,255,0.07)',
            }}
          />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
            <div style={{ textAlign: 'right' }}>
              <h2 style={{ color: 'white', fontSize: 13 }}>اپلیکیشن نوبت‌یار</h2>
              <p style={{ fontSize: 11, color: 'rgba(237,229,255,0.9)', marginTop: 4 }}>
                نوبت‌دهی آنلاین برای مشتریان و کسب‌وکارها
              </p>
              <a
                href="https://noobatyar.ir"
                target="_blank"
                rel="noopener noreferrer"
                style={{ textDecoration: 'none', display: 'block' }}
              >
                <div className="promo-badge" style={{ color: 'var(--color-primary-dark)', marginTop: 10 }}>
                  <span>مشاهده در noobatyar.ir</span>
                </div>
              </a>
            </div>
            <div
              style={{
                width: 44,
                height: 44,
                borderRadius: '50%',
                background: 'rgba(255,255,255,0.18)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 22,
                flexShrink: 0,
              }}
            >
              📱
            </div>
          </div>
        </div>

        <p
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: 'var(--color-text)',
            marginBottom: 12,
          }}
        >
          ارتباط با ما
        </p>
        <SupportLinks />
      </div>
    </div>
  );
}
