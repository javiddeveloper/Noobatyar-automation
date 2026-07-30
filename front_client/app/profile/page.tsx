'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getMe, getMyActivity, type ActivityEntry, type Visitor } from '@/lib/api';
import { formatIsoDate, STATUS_LABELS } from '@/lib/format';

// One glyph per action so the log is scannable without reading every line.
const ACTION_ICON: Record<string, string> = {
  APPOINTMENT_BOOKED: '🗓',
  APPOINTMENT_STATUS_CHANGED: '🔄',
  APPOINTMENT_CANCELLED: '✖️',
  PROFILE_UPDATED: '✏️',
  ARCHIVED_BY_OWNER: '📦',
  RESTORED_BY_OWNER: '↩️',
};

/** Turn a status code into its Persian label, falling back to the raw code. */
function statusText(code: unknown): string {
  if (typeof code !== 'string') return '';
  return STATUS_LABELS[code]?.label ?? code;
}

/**
 * A short human sentence for the row's `detail` payload. The backend stores
 * old/new values rather than pre-rendered prose, so the wording lives here.
 */
function describe(entry: ActivityEntry): string {
  const d = entry.detail ?? {};

  if (entry.action === 'APPOINTMENT_STATUS_CHANGED' || entry.action === 'APPOINTMENT_CANCELLED') {
    const from = statusText(d.old);
    const to = statusText(d.new);
    if (from && to) return `از «${from}» به «${to}»`;
    return to ? `به «${to}»` : '';
  }

  if (entry.action === 'APPOINTMENT_BOOKED') {
    const s = statusText(d.status);
    return s ? `وضعیت: ${s}` : '';
  }

  if (entry.action === 'PROFILE_UPDATED') {
    const changed = d.changed as Record<string, { from?: string; to?: string }> | undefined;
    if (!changed) return '';
    const names: Record<string, string> = { full_name: 'نام', phone_number: 'شماره' };
    return Object.entries(changed)
      .map(([field, v]) => `${names[field] ?? field}: «${v.from ?? '—'}» → «${v.to ?? '—'}»`)
      .join(' · ');
  }

  return '';
}

export default function ProfilePage() {
  const router = useRouter();
  const [visitor, setVisitor] = useState<Visitor | null>(null);
  const [activity, setActivity] = useState<ActivityEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  function signOut() {
    localStorage.removeItem('visitor_token');
    router.replace('/auth/login');
  }

  useEffect(() => {
    const token = localStorage.getItem('visitor_token');
    if (!token) {
      router.replace('/auth/login?redirect=/profile');
      return;
    }
    Promise.all([getMe(token), getMyActivity(token)])
      .then(([me, log]) => {
        setVisitor(me);
        setActivity(log);
      })
      .catch(() => setError('خطا در بارگذاری پروفایل'))
      .finally(() => setLoading(false));
  }, [router]);

  return (
    <div className="page-content">
      <div className="toolbar">
        <div className="toolbar-placeholder" />
        <h1 className="toolbar-title">پروفایل من</h1>
        <button className="toolbar-back" onClick={() => router.back()}>›</button>
      </div>

      <div style={{ padding: '24px 24px' }}>
        {loading ? (
          <>
            <div className="skeleton" style={{ height: 110, marginBottom: 20 }} />
            <div className="skeleton" style={{ height: 80, marginBottom: 12 }} />
            <div className="skeleton" style={{ height: 80 }} />
          </>
        ) : error ? (
          <div style={{ textAlign: 'center', padding: '48px 0' }}>
            <div style={{ fontSize: 44, marginBottom: 12 }}>😕</div>
            <p style={{ color: 'var(--color-muted)', marginBottom: 20 }}>{error}</p>
            <button className="btn-primary" onClick={() => location.reload()}>
              تلاش مجدد
            </button>
            {/* Always offer the way out: if the failure is the stored session
                itself, retrying forever is the one thing that cannot help. */}
            <div style={{ marginTop: 14 }}>
              <button
                onClick={signOut}
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'var(--color-muted)',
                  fontSize: 13,
                  textDecoration: 'underline',
                  cursor: 'pointer',
                  fontFamily: 'inherit',
                }}
              >
                خروج از حساب و ورود دوباره
              </button>
            </div>
          </div>
        ) : (
          <>
            {visitor && (
              <div className="info-card" style={{ marginBottom: 28 }}>
                <div className="info-row">
                  <span className="label">نام</span>
                  <span className="value">{visitor.full_name || '—'}</span>
                </div>
                <div className="info-divider" />
                <div className="info-row">
                  <span className="label">شماره موبایل</span>
                  <span className="value" style={{ direction: 'ltr' }}>
                    {visitor.phone_number}
                  </span>
                </div>
              </div>
            )}

            <h2 className="section-title">تاریخچه فعالیت</h2>

            {activity.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '40px 0' }}>
                <div style={{ fontSize: 44, marginBottom: 12 }}>📋</div>
                <p style={{ color: 'var(--color-text)', fontWeight: 700, marginBottom: 6 }}>
                  فعالیتی ثبت نشده
                </p>
                <p style={{ color: 'var(--color-muted)', fontSize: 13 }}>
                  رویدادهای مربوط به نوبت‌ها و حساب شما اینجا نمایش داده می‌شود.
                </p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                {activity.map((entry) => {
                  const note = describe(entry);
                  return (
                    <div key={entry.id} className="biz-address" style={{ alignItems: 'flex-start' }}>
                      <span style={{ fontSize: 20, lineHeight: 1.4 }}>
                        {ACTION_ICON[entry.action] ?? '•'}
                      </span>
                      <div style={{ flex: 1, minWidth: 0, textAlign: 'right' }}>
                        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--color-text)' }}>
                          {entry.action_label}
                        </div>
                        {note && (
                          <div
                            style={{
                              fontSize: 12,
                              color: 'var(--color-text)',
                              marginTop: 4,
                              wordBreak: 'break-word',
                            }}
                          >
                            {note}
                          </div>
                        )}
                        <div style={{ fontSize: 11, color: 'var(--color-muted)', marginTop: 6 }}>
                          {formatIsoDate(entry.created_at)}
                          {entry.business_title ? ` · ${entry.business_title}` : ''}
                          {` · توسط ${entry.actor_label}`}
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            <button
              onClick={signOut}
              style={{
                width: '100%',
                marginTop: 28,
                padding: '14px 16px',
                borderRadius: 16,
                background: 'var(--color-surface)',
                border: '1.5px solid var(--color-border)',
                color: 'var(--color-danger, #b91c1c)',
                fontSize: 14,
                fontWeight: 700,
                fontFamily: 'inherit',
                cursor: 'pointer',
              }}
            >
              خروج از حساب
            </button>
          </>
        )}
      </div>
    </div>
  );
}
