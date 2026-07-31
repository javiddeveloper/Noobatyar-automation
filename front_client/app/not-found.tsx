import Link from 'next/link';
import SupportLinks from '@/app/components/SupportLinks';

/** Catch-all 404 for any route that isn't a business code. */
export default function NotFound() {
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

        <h1
          style={{
            fontSize: 19,
            fontWeight: 700,
            color: 'var(--color-text)',
            marginBottom: 10,
          }}
        >
          صفحه یافت نشد
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
          نشانی‌ای که وارد کرده‌اید وجود ندارد یا جابه‌جا شده است
        </p>

        <p
          style={{
            fontSize: 12,
            fontWeight: 700,
            color: 'var(--color-text)',
            marginBottom: 12,
          }}
        >
          نیاز به کمک دارید؟
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
