import type { Metadata } from 'next';
import './globals.css';
import ThemeToggle from './components/ThemeToggle';

export const metadata: Metadata = {
  title: 'نوبت‌یار | رزرو آنلاین نوبت',
  description: 'سیستم نوبت‌دهی آنلاین نوبت‌یار — از هر کجا، هر زمان نوبت بگیرید',
  manifest: '/manifest.json',
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
          {children}
        </div>
      </body>
    </html>
  );
}
