'use client';

import { useEffect, useState, useSyncExternalStore } from 'react';
import { useRouter } from 'next/navigation';
import { getMyAppointments, type Appointment } from '@/lib/api';
import SupportLinks from '@/app/components/SupportLinks';
import AppointmentCard from '@/app/components/AppointmentCard';
import Icon from '@/app/components/Icon';
import AppBanner from '@/app/components/AppBanner';

/** Statuses that are over and done with — never "upcoming". */
const CLOSED = ['COMPLETED', 'CANCELLED', 'NO_SHOW'];
/** How many appointments the home screen shows before deferring to the full list. */
const HOME_LIMIT = 3;

/**
 * The token only changes through login or logout, both of which navigate away
 * and remount this screen — so there is nothing to subscribe to for the life
 * of one mount.
 */
const NEVER_CHANGES = () => () => {};
const readToken = () => localStorage.getItem('visitor_token');
/** `undefined` = not known yet (server render + the hydration pass). */
const noTokenOnServer = () => undefined;

/**
 * Read the stored visitor token without a setState-in-effect cascade.
 *
 * localStorage is an external store, so this is what useSyncExternalStore is
 * for: the server snapshot keeps SSR and the hydration render agreeing on
 * "unknown", and the real value arrives on the first post-hydration render.
 */
function useVisitorToken(): string | null | undefined {
  return useSyncExternalStore(NEVER_CHANGES, readToken, noTokenOnServer);
}

/**
 * The home screen.
 *
 * "/" has no business context, so it is deliberately not a directory of every
 * registered business (see the note that used to live in page.tsx). It is the
 * customer's own dashboard: their upcoming appointments, with the app banner
 * above them. Signed-out visitors get the same banner plus a sign-in prompt,
 * which keeps this route useful as the public landing page for SEO instead of
 * bouncing crawlers straight to /auth/login.
 */
export default function HomeClient() {
  const router = useRouter();
  const token = useVisitorToken();
  const [appointments, setAppointments] = useState<Appointment[] | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!token) return; // unknown or signed out — nothing to fetch
    let live = true;
    getMyAppointments(token)
      .then((list) => live && setAppointments(list))
      .catch(() => live && setError('نوبت‌های شما بارگذاری نشد'));
    return () => {
      live = false;
    };
  }, [token]);

  // Derived rather than a fourth piece of state, so "checking" and "loading"
  // cannot drift out of sync with the token or the fetch.
  const auth = token === undefined ? 'checking' : token ? 'authed' : 'guest';
  const loading = auth === 'checking' || (auth === 'authed' && !appointments && !error);

  const upcoming = (appointments ?? []).filter((a) => !CLOSED.includes(a.status));
  const shown = upcoming.slice(0, HOME_LIMIT);

  return (
    <div className="page-content" style={{ paddingBottom: 40 }}>
      {/* ── Brand row ── */}
      <div className="home-brand">
        {/* eslint-disable-next-line @next/next/no-img-element -- local static brand mark */}
        <img src="/icons/icon-192.png" alt="" width={34} height={34} />
        <span>نوبت‌یار</span>
      </div>

      {/* ── App banner ── */}
      <div className="section" style={{ paddingTop: 4, paddingBottom: 8 }}>
        <AppBanner variant="consumer" />
      </div>

      {/* ── My appointments ── */}
      <div className="section" style={{ paddingTop: 12 }}>
        <div className="section-head">
          <Icon name="calendar" size={18} color="var(--color-primary)" />
          <h2>نوبت‌های من</h2>
          {auth === 'authed' && upcoming.length > HOME_LIMIT && (
            <button className="link" onClick={() => router.push('/appointments')}>
              همه
              <Icon name="chevronLeft" size={14} />
            </button>
          )}
        </div>

        {auth === 'checking' || loading ? (
          <>
            {[1, 2].map((i) => (
              <div key={i} className="skeleton" style={{ height: 118, marginBottom: 14, borderRadius: 16 }} />
            ))}
          </>
        ) : auth === 'guest' ? (
          <div className="empty-state">
            <span className="empty-state-icon">
              <Icon name="person" size={26} />
            </span>
            <h3>برای دیدن نوبت‌هایتان وارد شوید</h3>
            <p>پس از ورود، همهٔ نوبت‌های رزروشده اینجا نمایش داده می‌شود</p>
            <button
              className="btn-primary"
              style={{ marginTop: 18, height: 48 }}
              onClick={() => router.push('/auth/login?redirect=/')}
            >
              ورود / ثبت‌نام
            </button>
          </div>
        ) : error ? (
          <div className="empty-state">
            <span className="empty-state-icon" style={{ color: 'var(--color-error)' }}>
              <Icon name="error" size={26} />
            </span>
            <h3>{error}</h3>
            <button
              className="btn-primary"
              style={{ marginTop: 18, height: 48 }}
              onClick={() => location.reload()}
            >
              <Icon name="refresh" size={17} style={{ display: 'inline-block', verticalAlign: '-3px', marginLeft: 6 }} />
              تلاش مجدد
            </button>
          </div>
        ) : shown.length === 0 ? (
          <div className="empty-state">
            <span className="empty-state-icon">
              <Icon name="eventBusy" size={26} />
            </span>
            <h3>نوبت پیش‌رویی ندارید</h3>
            <p>وقتی نوبتی رزرو کنید، اینجا نمایش داده می‌شود</p>
            <button
              className="btn-primary"
              style={{ marginTop: 18, height: 48, background: 'var(--color-surface)', color: 'var(--color-primary)', border: '1.5px solid var(--color-primary)', boxShadow: 'none' }}
              onClick={() => router.push('/appointments')}
            >
              سوابق نوبت‌ها
            </button>
          </div>
        ) : (
          shown.map((appt) => <AppointmentCard key={appt.id} appt={appt} />)
        )}
      </div>

      {/* ── Support ── */}
      <div className="section" style={{ paddingTop: 8, textAlign: 'center' }}>
        <p style={{ fontSize: 12, fontWeight: 700, color: 'var(--color-text)', marginBottom: 12 }}>
          ارتباط با ما
        </p>
        <SupportLinks />
      </div>
    </div>
  );
}
