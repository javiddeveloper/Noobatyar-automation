/**
 * Shared display formatters.
 *
 * These were duplicated verbatim across the appointment pages; the profile page
 * would have been a third copy. Persian dates come from Intl with the `fa-IR`
 * locale, which yields Jalali output — `moment-jalaali` is in package.json but
 * has never been imported anywhere.
 */

/* Category glyphs now live in app/components/Icon.tsx as CATEGORY_ICON —
   Material SVG paths rather than emoji, which rendered differently on every
   Android skin and could not take a theme colour. */

export const STATUS_LABELS: Record<string, { label: string; color: string; bg: string }> = {
  LOCKED:               { label: 'در انتظار پرداخت', color: '#b45309', bg: '#fef3c7' },
  PENDING_VERIFICATION: { label: 'در انتظار تایید پرداخت', color: '#d97706', bg: '#fef3c7' },
  PENDING_APPROVAL:     { label: 'در انتظار تایید', color: '#d97706', bg: '#fef3c7' },
  WAITING:              { label: 'در صف',          color: '#047857', bg: '#d1fae5' },
  CONFIRMED:            { label: 'تایید شده',      color: '#047857', bg: '#d1fae5' },
  IN_PROGRESS:          { label: 'در حال سرویس',   color: '#1d4ed8', bg: '#dbeafe' },
  COMPLETED:            { label: 'انجام شد',       color: '#374151', bg: '#f3f4f6' },
  CANCELLED:            { label: 'لغو شد',         color: '#b91c1c', bg: '#fee2e2' },
  NO_SHOW:              { label: 'غیبت',           color: '#b91c1c', bg: '#fee2e2' },
};

/** Appointment timestamps arrive from the API as JS milliseconds. */
export function formatDate(ts: number): string {
  const d = new Date(ts);
  return d.toLocaleString('fa-IR', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** Activity rows carry ISO-8601 strings rather than epoch millis. */
export function formatIsoDate(iso: string): string {
  const ts = Date.parse(iso);
  return Number.isNaN(ts) ? '—' : formatDate(ts);
}

export function toPersianNumerals(n: number): string {
  const map = ['۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹'];
  return String(n).replace(/[0-9]/g, (d) => map[Number(d)]);
}
