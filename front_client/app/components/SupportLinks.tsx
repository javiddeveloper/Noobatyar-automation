/**
 * The same channels the owner app lists under "درباره ما", so a customer who
 * hits a dead end has the identical set of ways to reach us. Kept in one place
 * because these live in three surfaces now — change them here, not inline.
 */

export interface SupportChannel {
  key: string;
  label: string;
  href: string;
  /** Inline SVG-free glyph; the brand marks are not bundled on the web yet. */
  glyph: string;
  tint: string;
}

export const SUPPORT_CHANNELS: SupportChannel[] = [
  { key: 'bale', label: 'بله', href: 'https://ble.ir/noobatyar', glyph: '💬', tint: '#e0f2fe' },
  { key: 'eitaa', label: 'ایتا', href: 'https://eitaa.com/noobatyar', glyph: '📨', tint: '#fef3c7' },
  { key: 'rubika', label: 'روبیکا', href: 'https://rubika.ir/noobatyar', glyph: '📩', tint: '#ede9fe' },
  { key: 'instagram', label: 'اینستاگرام', href: 'https://instagram.com/ajviddev', glyph: '📷', tint: '#fce7f3' },
  { key: 'website', label: 'سایت', href: 'https://noobatyar.ir', glyph: '🌐', tint: '#dcfce7' },
];

export default function SupportLinks() {
  return (
    <div
      style={{
        // A five-column grid rather than a wrapping flex row: at 375px the
        // items are one hair too wide to fit on a line, and wrapping stranded
        // "سایت" alone on a second row.
        display: 'grid',
        gridTemplateColumns: 'repeat(5, 1fr)',
        gap: 7,
        width: '100%',
        maxWidth: 340,
      }}
    >
      {SUPPORT_CHANNELS.map((c) => (
        <a
          key={c.key}
          href={c.href}
          target="_blank"
          rel="noopener noreferrer"
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 6,
            minWidth: 0,
            padding: '10px 2px',
            borderRadius: 14,
            background: 'var(--color-surface)',
            border: '1.5px solid var(--color-border)',
            textDecoration: 'none',
            color: 'var(--color-text)',
          }}
        >
          <span
            style={{
              width: 36,
              height: 36,
              borderRadius: 11,
              display: 'grid',
              placeItems: 'center',
              fontSize: 17,
              background: c.tint,
            }}
          >
            {c.glyph}
          </span>
          <span
            style={{
              fontSize: 10,
              fontWeight: 600,
              whiteSpace: 'nowrap',
              maxWidth: '100%',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {c.label}
          </span>
        </a>
      ))}
    </div>
  );
}
