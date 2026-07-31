import Link from 'next/link';
import SupportLinks from '@/app/components/SupportLinks';

/**
 * Shown when a business code does not resolve — the page a customer lands on
 * from a stale QR code, a mistyped link, or a business that is no longer
 * listed. Next's stock 404 is a bare line of text, which for a booking link
 * someone scanned in a shop reads as "the site is broken" rather than "this
 * particular link is out of date".
 */
export default function BusinessNotFound() {
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
        {/* eslint-disable-next-line @next/next/no-img-element -- brand mark is a
            local static SVG; next/image adds no value and needs explicit sizing. */}
        <img
          src="/icons/icon-192.png"
          alt="نوبت‌یار"
          width={84}
          height={84}
          style={{ borderRadius: 22, marginBottom: 22 }}
        />

        <h1
          style={{
            fontSize: 19,
            fontWeight: 700,
            color: 'var(--color-text)',
            marginBottom: 10,
          }}
        >
          کسب‌وکار یافت نشد
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
          این لینک معتبر نیست یا این کسب‌وکار دیگر در نوبت‌یار فعال نیست
          <br />
          نشانی را دوباره بررسی کنید یا از خود کسب‌وکار لینک تازه بگیرید
        </p>

        <p
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: 'var(--color-text)',
            marginBottom: 12,
          }}
        >
          فکر می‌کنید اشتباهی رخ داده؟
        </p>

        <SupportLinks />

        <Link
          href="/"
          style={{
            marginTop: 26,
            fontSize: 13,
            color: 'var(--color-primary)',
            textDecoration: 'none',
            fontWeight: 600,
          }}
        >
          بازگشت به نوبت‌یار
        </Link>
      </div>
    </div>
  );
}
