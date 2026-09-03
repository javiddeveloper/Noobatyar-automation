import Icon from '@/app/components/Icon';

/**
 * The Noobatyar promo banner.
 *
 * Previously this markup was pasted into the home screen and the business
 * profile, which is exactly how two copies of one advert drift apart. It now
 * lives here so every placement stays identical, and so adding a placement is
 * one line rather than twenty.
 *
 * ── Colour ──────────────────────────────────────────────────────────────────
 *
 * The banner is Noobatyar purple on Noobatyar's own screens, and takes the
 * business's colour inside a `.themed` page (see `.themed .app-banner` in
 * globals.css). On a themed page it was the one purple object left on an
 * otherwise red or olive screen, which read as a rendering fault rather than
 * as an advert. Brand identity is carried by the logo and the wording, both of
 * which survive the recolour; the gradient was doing none of that work.
 */

/**
 * Which pitch to make.
 *
 * `owner` — "are you a business owner?". For screens where the reader has just
 * seen the product work for someone else's business, which is the moment the
 * pitch actually lands.
 *
 * `consumer` — the booking app itself. For screens the reader reached as a
 * customer with no business of their own in view.
 */
export type AppBannerVariant = 'owner' | 'consumer';

const COPY: Record<AppBannerVariant, { title: string; body: string; cta: string }> = {
  owner: {
    title: 'صاحب کسب‌وکار هستید؟',
    body: 'نوبت‌دهی آنلاین با نوبت‌یار برای کسب‌وکار شما',
    cta: 'شروع رایگان در noobatyar.ir',
  },
  consumer: {
    title: 'اپلیکیشن نوبت‌یار',
    body: 'نوبت کسب‌وکارهای مورد علاقه‌تان را در چند ثانیه رزرو کنید',
    cta: 'مشاهده در noobatyar.ir',
  },
};

export default function AppBanner({
  variant = 'consumer',
  className,
  style,
}: {
  variant?: AppBannerVariant;
  /** Extra classes for the anchor — used to hook page-specific animations. */
  className?: string;
  style?: React.CSSProperties;
}) {
  const copy = COPY[variant];

  return (
    <a
      className={className ? `app-banner ${className}` : 'app-banner'}
      style={style}
      href="https://noobatyar.ir"
      target="_blank"
      rel="noopener noreferrer"
    >
      <span className="app-banner-blob one" aria-hidden="true" />
      <span className="app-banner-blob two" aria-hidden="true" />

      <span className="app-banner-logo">
        {/* eslint-disable-next-line @next/next/no-img-element -- local static brand mark */}
        <img src="/icons/icon-192.png" alt="" width={58} height={58} />
      </span>

      <span className="app-banner-body">
        <h2>{copy.title}</h2>
        <p>{copy.body}</p>
        <span className="app-banner-cta">
          {copy.cta}
          <Icon name="chevronLeft" size={14} />
        </span>
      </span>
    </a>
  );
}
