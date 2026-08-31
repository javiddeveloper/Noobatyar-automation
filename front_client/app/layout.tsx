import type { Metadata, Viewport } from 'next';
import './globals.css';
import ThemeToggle from './components/ThemeToggle';
import ServiceWorkerRegistration from './components/ServiceWorkerRegistration';
import PushPermissionPrompt from './components/PushPermissionPrompt';
import { SITE_URL } from '@/lib/site';

export const metadata: Metadata = {
  // Without metadataBase, every relative URL a page puts in `alternates` or
  // `openGraph` is emitted relative to whatever host rendered it, and Next
  // warns at build time. Crawlers and chat previews need absolute ones.
  metadataBase: new URL(SITE_URL),
  title: 'نوبت‌یار | رزرو آنلاین نوبت',
  description: 'سیستم نوبت‌دهی آنلاین نوبت‌یار — از هر کجا، هر زمان نوبت بگیرید',
  openGraph: {
    title: 'نوبت‌یار | رزرو آنلاین نوبت',
    description: 'سیستم نوبت‌دهی آنلاین نوبت‌یار — از هر کجا، هر زمان نوبت بگیرید',
    siteName: 'نوبت‌یار',
    locale: 'fa_IR',
    type: 'website',
  },
};

// Icons and the web manifest are wired up by file convention, not from here:
// app/icon.svg, app/apple-icon.png, app/favicon.ico and app/manifest.ts each
// get their own <link> tag emitted by Next. Re-declaring `manifest` in the
// metadata above is what previously pointed the browser at a non-existent
// /manifest.json.
export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#7c3aed' },
    { media: '(prefers-color-scheme: dark)', color: '#0f0f0f' },
  ],
};

// Applied before paint to prevent a flash of the wrong theme.
const themeScript = `
(function () {
  try {
    var t = localStorage.getItem('theme');
    if (t === 'dark' || t === 'light') {
      document.documentElement.setAttribute('data-theme', t);
    }
  } catch (e) {}
})();
`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fa" dir="rtl">
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body>
        <div id="app-shell">
          <ThemeToggle />
          <ServiceWorkerRegistration />
          <PushPermissionPrompt />
          {children}
        </div>
      </body>
    </html>
  );
}
