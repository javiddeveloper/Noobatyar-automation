import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'نوبت‌یار | رزرو آنلاین نوبت',
  description: 'سیستم نوبت‌دهی آنلاین نوبت‌یار — از هر کجا، هر زمان نوبت بگیرید',
  manifest: '/manifest.json',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fa" dir="rtl">
      <body>
        <div id="app-shell">
          {children}
        </div>
      </body>
    </html>
  );
}
