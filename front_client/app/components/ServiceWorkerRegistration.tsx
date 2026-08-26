'use client';

import { useEffect } from 'react';

/**
 * Registers `public/sw.js` — the app's offline shell / static-asset cache.
 *
 * Production only. A service worker that caches during `next dev` turns
 * every code change into a "hard refresh and hope" debugging session, since
 * `next dev` itself rewrites files constantly and the whole point of dev
 * mode is seeing those changes immediately. `next dev` always sets
 * `NODE_ENV=development` regardless of `.env`, so this check is reliable.
 *
 * Renders nothing; it only runs the registration side effect once mounted.
 */
export default function ServiceWorkerRegistration() {
  useEffect(() => {
    if (process.env.NODE_ENV !== 'production') return;
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) return;

    navigator.serviceWorker
      .register('/sw.js', {
        scope: '/',
        // Don't let the browser's plain HTTP cache serve a stale sw.js —
        // update checks must always hit the network. (Belt-and-suspenders
        // with the no-cache header set on /sw.js in next.config.ts.)
        updateViaCache: 'none',
      })
      .catch((err) => {
        console.error('[sw] registration failed', err);
      });
  }, []);

  return null;
}
