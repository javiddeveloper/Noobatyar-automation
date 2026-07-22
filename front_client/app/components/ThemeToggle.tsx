'use client';

import { useEffect, useState } from 'react';

/**
 * Floating light/dark theme toggle, mirroring the owner app's theme modes.
 * Defaults to the system preference; once the user toggles, the choice is
 * remembered in localStorage and applied via [data-theme] on <html>.
 */
export default function ThemeToggle() {
  const [theme, setTheme] = useState<'light' | 'dark' | null>(null);

  useEffect(() => {
    const stored = localStorage.getItem('theme');
    if (stored === 'light' || stored === 'dark') {
      setTheme(stored);
    } else {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      setTheme(prefersDark ? 'dark' : 'light');
    }
  }, []);

  const toggle = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    setTheme(next);
    localStorage.setItem('theme', next);
    document.documentElement.setAttribute('data-theme', next);
  };

  // Avoid a hydration mismatch: render nothing until we know the theme.
  if (theme === null) return null;

  return (
    <button
      type="button"
      className="theme-toggle"
      onClick={toggle}
      aria-label={theme === 'dark' ? 'روشن کردن تم' : 'تیره کردن تم'}
      title={theme === 'dark' ? 'حالت روشن' : 'حالت تیره'}
    >
      {theme === 'dark' ? '☀️' : '🌙'}
    </button>
  );
}
