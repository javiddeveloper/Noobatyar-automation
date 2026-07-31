import Link from 'next/link';
import { listBusinesses, categoryLabel, mediaUrl, type Business } from '@/lib/api';
import SupportLinks from '@/app/components/SupportLinks';
import { CATEGORY_EMOJI } from '@/lib/format';

// Businesses come and go; a cached-forever home page would list dead ones.
export const revalidate = 60;

export const metadata = {
  title: 'نوبت‌یار | رزرو آنلاین نوبت',
  description: 'کسب‌وکار خود را پیدا کنید و آنلاین نوبت بگیرید',
};

/**
 * The home page used to `redirect('/b/Noobatyar-4C05H1I2')` — a demo business
 * hardcoded during development. Once that record stopped existing, everything
 * pointing at "/" dead-ended on "کسب‌وکار یافت نشد": the installed PWA, whose
 * start_url is "/", and the "بازگشت به نوبت‌یار" link on the 404 page itself.
 */
export default async function Home() {
  let businesses: Business[] = [];
  let failed = false;
  try {
    const data = await listBusinesses();
    businesses = data.results ?? [];
  } catch {
    failed = true;
  }

  return (
    <div className="page-content">
      <div style={{ padding: '36px 24px 40px' }}>
        <div style={{ textAlign: 'center', marginBottom: 30 }}>
          {/* eslint-disable-next-line @next/next/no-img-element -- local static brand mark */}
          <img
            src="/icons/icon-192.png"
            alt="نوبت‌یار"
            width={72}
            height={72}
            style={{ borderRadius: 20, marginBottom: 16 }}
          />
          <h1 style={{ fontSize: 20, fontWeight: 700, color: 'var(--color-text)', marginBottom: 8 }}>
            نوبت‌یار
          </h1>
          <p style={{ fontSize: 13, color: 'var(--color-muted)', lineHeight: 1.9 }}>
            کسب‌وکار مورد نظرتان را انتخاب کنید و آنلاین نوبت بگیرید
          </p>
        </div>

        {failed ? (
          <div style={{ textAlign: 'center', padding: '32px 0' }}>
            <div style={{ fontSize: 40, marginBottom: 12 }}>😕</div>
            <p style={{ fontSize: 13, color: 'var(--color-muted)' }}>
              فهرست کسب‌وکارها در دسترس نیست
            </p>
          </div>
        ) : businesses.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '32px 0' }}>
            <div style={{ fontSize: 40, marginBottom: 12 }}>🏪</div>
            <p
              style={{
                fontSize: 14,
                fontWeight: 700,
                color: 'var(--color-text)',
                marginBottom: 6,
              }}
            >
              هنوز کسب‌وکاری ثبت نشده
            </p>
            <p style={{ fontSize: 12, color: 'var(--color-muted)' }}>
              اگر لینک یا کد کسب‌وکار دارید، مستقیم از همان وارد شوید
            </p>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 32 }}>
            {businesses.map((b) => (
              <Link
                key={b.id}
                href={`/b/Noobatyar-${b.unique_code}`}
                className="biz-address"
                style={{ color: 'inherit' }}
              >
                <span className="pin" style={{ overflow: 'hidden' }}>
                  {b.logo ? (
                    // eslint-disable-next-line @next/next/no-img-element -- remote logo, sized by CSS
                    <img
                      src={mediaUrl(b.logo) ?? ''}
                      alt=""
                      style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    />
                  ) : (
                    CATEGORY_EMOJI[b.category] ?? '🏢'
                  )}
                </span>
                <div className="addr-text">
                  <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--color-text)' }}>
                    {b.title}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--color-muted)', marginTop: 3 }}>
                    {categoryLabel(b.category)}
                  </div>
                </div>
                <span style={{ color: 'var(--color-faint)', fontSize: 18 }}>‹</span>
              </Link>
            ))}
          </div>
        )}

        <p
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: 'var(--color-text)',
            textAlign: 'center',
            marginBottom: 12,
          }}
        >
          ارتباط با ما
        </p>
        <div style={{ display: 'flex', justifyContent: 'center' }}>
          <SupportLinks />
        </div>
      </div>
    </div>
  );
}
