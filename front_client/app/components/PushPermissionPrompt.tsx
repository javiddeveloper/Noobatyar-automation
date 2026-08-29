'use client';

import { useEffect, useState } from 'react';
import {
  PUSH_PROMPT_EVENT,
  canPrompt,
  isPermissionDenied,
  requestPushPermission,
} from '@/lib/push';

/**
 * App-wide "may we notify you" dialog — mounted once in the root layout,
 * shown only when something dispatches PUSH_PROMPT_EVENT (currently: right
 * after a successful booking, see the `book` page's `schedulePushPrompt`
 * call — that is the moment the value of a reminder is most obvious to a
 * customer, rather than an on-load prompt that tanks grant rates).
 *
 * Suppressed whenever permission is already decided either way
 * (`canPrompt()` false) — most commonly because the browser already denied
 * it, in which case no website can re-trigger the native prompt; showing an
 * "allow" button that silently does nothing would be worse than nothing.
 * Same reasoning already proven in mobile_owner's own app-wide prompt.
 */
export default function PushPermissionPrompt() {
  const [visible, setVisible] = useState(false);
  const [requesting, setRequesting] = useState(false);

  useEffect(() => {
    const handler = () => {
      const visitorToken =
        typeof window !== 'undefined' ? localStorage.getItem('visitor_token') : null;
      if (!visitorToken) return;
      if (isPermissionDenied() || !canPrompt()) return;
      setVisible(true);
    };
    window.addEventListener(PUSH_PROMPT_EVENT, handler);
    return () => window.removeEventListener(PUSH_PROMPT_EVENT, handler);
  }, []);

  if (!visible) return null;

  const dismiss = () => setVisible(false);

  const allow = async () => {
    const visitorToken = localStorage.getItem('visitor_token');
    if (!visitorToken) {
      dismiss();
      return;
    }
    setRequesting(true);
    await requestPushPermission(visitorToken);
    setRequesting(false);
    dismiss();
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      style={{
        position: 'fixed',
        inset: 0,
        zIndex: 1000,
        background: 'rgba(0,0,0,0.45)',
        display: 'flex',
        alignItems: 'flex-end',
        justifyContent: 'center',
      }}
      onClick={dismiss}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{
          width: '100%',
          maxWidth: 420,
          background: 'var(--color-surface)',
          borderRadius: 'var(--radius-banner) var(--radius-banner) 0 0',
          padding: '24px 20px calc(20px + env(safe-area-inset-bottom, 0px))',
          boxShadow: 'var(--shadow-card)',
        }}
      >
        <div style={{ fontSize: 32, marginBottom: 8 }}>🔔</div>
        <h2 style={{ margin: '0 0 8px', fontSize: 17, color: 'var(--color-text)' }}>
          یادت نره اعلان رو فعال کنی!
        </h2>
        <p style={{ margin: '0 0 20px', fontSize: 14, color: 'var(--color-muted)', lineHeight: 1.7 }}>
          نوبتت با موفقیت ثبت شد. با فعال کردن اعلان، دقیقاً قبل از نوبتت یک
          یادآوری برات می‌فرستیم — علاوه بر پیامک، رایگان و آنی.
        </p>
        <div style={{ display: 'flex', gap: 10 }}>
          <button
            onClick={allow}
            disabled={requesting}
            style={{
              flex: 1,
              padding: '13px 0',
              borderRadius: 'var(--radius-btn)',
              border: 'none',
              background: 'var(--color-primary)',
              color: 'var(--color-on-primary)',
              fontSize: 15,
              fontWeight: 700,
              cursor: requesting ? 'default' : 'pointer',
              opacity: requesting ? 0.7 : 1,
            }}
          >
            {requesting ? 'در حال فعال‌سازی...' : 'فعال کن'}
          </button>
          <button
            onClick={dismiss}
            disabled={requesting}
            style={{
              padding: '13px 18px',
              borderRadius: 'var(--radius-btn)',
              border: '1px solid var(--color-border)',
              background: 'transparent',
              color: 'var(--color-muted)',
              fontSize: 15,
              cursor: requesting ? 'default' : 'pointer',
            }}
          >
            بعداً
          </button>
        </div>
      </div>
    </div>
  );
}
