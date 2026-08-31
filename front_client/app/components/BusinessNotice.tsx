import type { Business } from '@/lib/api';
import Icon from './Icon';

/**
 * The owner's short advisory — "closed today", "running an hour late",
 * "emergency, please call first" — so a client sees it before spending time
 * picking a slot. Shown on the profile page and again on the booking page,
 * since a deep link can drop someone straight into booking.
 *
 * Deliberately separate from `booking_enabled`: a business can be running
 * late *and* still taking bookings, so this never blocks the flow. The hard
 * "booking is off" state keeps the red error styling; this is amber.
 *
 * No hooks and no browser APIs, so it stays a plain component that either
 * Server or Client Components can render.
 */

interface Props {
  /** Only the notice fields are read; any Business shape satisfies it. */
  business: Pick<Business, 'notice_enabled' | 'notice_message'>;
  /** Overrides for the outer spacing wrapper, for pages with their own padding. */
  style?: React.CSSProperties;
}

export default function BusinessNotice({ business, style }: Props) {
  const message = business.notice_message?.trim() ?? '';

  // Nothing renders at all when there is no live notice — not the wrapper,
  // not the padding. `!== false` rather than `=== true` mirrors the
  // `booking_enabled` idiom used elsewhere, so a payload from a backend that
  // predates the flag still shows a notice it bothered to send.
  if (business.notice_enabled === false || !message) return null;

  return (
    <div className="section" style={style}>
      {/* role="status" (polite) rather than "alert": this is advisory, it
          should be announced without interrupting whatever the screen reader
          is already saying. */}
      <div className="biz-notice" role="status">
        <div className="ico" aria-hidden="true">
          <Icon name="info" size={18} />
        </div>
        <div className="notice-text">
          <div className="notice-k">اطلاعیه کسب‌وکار</div>
          {/* dir="auto" so a notice written in Persian, or one that opens with
              a Latin word or a number, still lays out from the correct side. */}
          <div className="notice-v" dir="auto">{message}</div>
        </div>
      </div>
    </div>
  );
}
