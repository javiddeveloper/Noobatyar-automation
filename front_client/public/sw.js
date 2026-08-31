/**
 * Hand-rolled service worker for نوبت‌یار (Noobatyar customer web).
 *
 * Scope: served from the site root (`/sw.js`) so it controls every route.
 *
 * Push notifications (added once the backend gained a path that pushes to
 * *customer* accounts — visitor.models.VisitorDeviceToken /
 * api.services.push.send_to_visitor): see the Firebase block right below
 * this comment. Deliberately appended to this *same* file rather than
 * registered as a second service worker — a second `register()` call at the
 * same root scope (`/`) would just fight this one for control of it; there
 * can only be one active SW per scope. No explicit `push`/`notificationclick`
 * listeners are added here: `firebase.messaging()` registers its own
 * internally, and the backend already sends a top-level FCM `notification`
 * block (api/services/push.py) that the SDK displays on its own — same
 * pattern already proven in mobile_owner's firebase-messaging-sw.js.
 *
 * Caching strategy (deliberately conservative — a booking app is the one
 * place where "fast but wrong" is worse than "slow but right"):
 *
 *   - `/api/*`            → network ONLY, never cached, no offline fallback.
 *                            Appointment/slot availability must never be
 *                            served stale; showing a cached slot list as
 *                            available when it's actually booked is worse
 *                            than showing nothing. Matched by pathname so it
 *                            also covers cross-origin API calls (production
 *                            calls NEXT_PUBLIC_API_URL directly, e.g.
 *                            https://api.noobatyar.ir/api/...).
 *   - `/_next/static/*`   → cache-first. These filenames are content-hashed
 *                            by the Next.js build, so a given URL's bytes
 *                            never change; safe to keep indefinitely.
 *   - known static assets → cache-first (icons, fonts, favicon, manifest,
 *                            root-level svgs). Rarely change and aren't
 *                            booking data.
 *   - navigations (pages) → network-first. Always prefer a live render;
 *                            fall back to a cached copy of the same URL,
 *                            then to the offline shell, only when the
 *                            network request fails outright (i.e. the user
 *                            is actually offline).
 *   - anything else        → left alone (not intercepted), default browser
 *                            behavior.
 */

// Vendored locally, not loaded from gstatic.com: this app is served to users
// in Iran, where public CDNs are unreachable, and a service worker whose
// importScripts throws on a blocked fetch fails to install *at all* — losing
// every bit of the caching logic below too, not just push. Same reasoning as
// mobile_owner/composeApp/src/wasmJsMain/resources/firebase-messaging-sw.js.
importScripts('/vendor/firebase/firebase-app-compat.js');
importScripts('/vendor/firebase/firebase-messaging-compat.js');

// Same Firebase project as the owner app (mobile_owner's FirebaseWebConfig.kt)
// — a Web app's config is not domain-restricted by default, so the same
// values work here too. Wrapped in try/catch: this file must still install
// successfully (and keep serving the cache below) even if the Firebase SDK
// itself has a hiccup — losing push must never cost the app its offline
// shell.
try {
  firebase.initializeApp({
    apiKey: 'AIzaSyBkj4QwHRnMjruW7BJniWijd4z5uGV1r8o',
    authDomain: 'nobatyar-79c53.firebaseapp.com',
    projectId: 'nobatyar-79c53',
    storageBucket: 'nobatyar-79c53.firebasestorage.app',
    messagingSenderId: '56921056578',
    appId: '1:56921056578:web:29eb94274ea5d99e6a14db',
  });
  // No explicit onBackgroundMessage handler: backend/api/services/push.py
  // already sends a top-level `notification` block, which the SDK displays
  // automatically for background messages on its own.
  firebase.messaging();
} catch (e) {
  // Fail soft — see comment above.
}

// Bump this on every deploy that changes what gets precached. `activate`
// deletes any cache whose name doesn't match, so a stale bundle never
// lingers for returning users.
const CACHE_VERSION = 'v1';
const STATIC_CACHE = `noobatyar-static-${CACHE_VERSION}`;

const OFFLINE_URL = '/offline.html';

// The app shell: enough to render *something* coherent offline. Deliberately
// small and hand-enumerated (there's no build-time asset manifest here) —
// everything else is picked up opportunistically by the runtime cache-first
// handlers below as it's requested.
const PRECACHE_URLS = [
  '/',
  OFFLINE_URL,
  '/manifest.webmanifest',
  '/favicon.ico',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/icons/icon-maskable-512.png',
  '/fonts/YekanBakhFaNum-Regular.ttf',
  '/fonts/YekanBakhFaNum-SemiBold.ttf',
  '/fonts/YekanBakhFaNum-Bold.ttf',
];

// Extensions handled cache-first at runtime (icons/images/fonts/etc.).
// `/_next/static/` is handled separately since it's hash-versioned.
const STATIC_ASSET_RE = /\.(?:png|jpg|jpeg|svg|webp|gif|ico|ttf|woff2?|css)$/;

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(STATIC_CACHE).then((cache) =>
      // Precache best-effort: one missing/renamed asset shouldn't fail
      // install and leave the whole SW unregistered.
      Promise.all(
        PRECACHE_URLS.map((url) =>
          cache.add(url).catch((err) => {
            console.warn('[sw] precache skipped for', url, err);
          })
        )
      )
    )
  );
  // Take over as soon as install finishes, instead of waiting for every
  // open tab of the old worker to close. Paired with clients.claim() below.
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter((key) => key !== STATIC_CACHE)
            .map((key) => caches.delete(key))
        )
      )
      .then(() => self.clients.claim())
  );
});

async function cacheFirst(request) {
  const cached = await caches.match(request);
  if (cached) return cached;

  const response = await fetch(request);
  // Only cache successful, basic/cors responses — never opaque errors.
  if (response && response.ok) {
    const cache = await caches.open(STATIC_CACHE);
    cache.put(request, response.clone());
  }
  return response;
}

async function networkFirstNavigation(request) {
  try {
    return await fetch(request);
  } catch {
    // Offline (or the network request otherwise failed to complete): fall
    // back to whatever we have for this exact page, then to the generic
    // offline shell. We do NOT synthesize a cached response for API/data —
    // only for the document itself.
    const cached = await caches.match(request);
    if (cached) return cached;
    const offline = await caches.match(OFFLINE_URL);
    return offline || Response.error();
  }
}

self.addEventListener('fetch', (event) => {
  const { request } = event;

  // Only GET is safe/idempotent to intercept; let POST/PUT/DELETE etc. pass
  // through untouched (booking actions must always hit the network).
  if (request.method !== 'GET') return;

  const url = new URL(request.url);

  // Never intercept the API. Plain passthrough, no cache read or write.
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(fetch(request));
    return;
  }

  // Next.js build output — filenames are content hashes, safe to cache hard.
  if (url.pathname.startsWith('/_next/static/')) {
    event.respondWith(cacheFirst(request));
    return;
  }

  // Known static assets (icons, fonts, root svgs, etc.) — same-origin only,
  // so we don't start caching third-party responses.
  if (url.origin === self.location.origin && STATIC_ASSET_RE.test(url.pathname)) {
    event.respondWith(cacheFirst(request));
    return;
  }

  // Page navigations — network-first with an offline fallback.
  if (request.mode === 'navigate') {
    event.respondWith(networkFirstNavigation(request));
    return;
  }

  // Everything else (cross-origin, RSC data fetches, etc.): don't intercept.
});
