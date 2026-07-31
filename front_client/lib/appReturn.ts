/**
 * Handing a payer back to the owner app after they paid in a browser.
 *
 * The owner app opens the gateway in a browser, so Zibal's callback lands on a
 * web page, not in the app. Verification happens there correctly — but the user
 * was left sitting in the browser with no way back, and had to find the app and
 * refresh it to see what they had bought.
 *
 * The Android manifest already registers `noobatyar://payment/result`, and
 * MainNavHost already routes it to the payment-result dialog. Nothing was ever
 * navigating to it.
 */

/** Matches the navDeepLink pattern in MainNavHost.kt. */
export const APP_SCHEME = 'noobatyar://payment/result';

export function buildAppReturnLink(opts: {
  success: boolean;
  txn?: string | null;
  ref?: string | null;
  amount?: string | null;
}): string {
  const params = new URLSearchParams();
  // `success` is a non-null Int on the Kotlin route, so it is always sent.
  params.set('success', opts.success ? '1' : '0');
  // ref/amount/txn are nullable with defaults there, so they are only sent when
  // known — an empty value would show up as an empty field in the app.
  if (opts.txn) params.set('txn', opts.txn);
  if (opts.ref) params.set('ref', opts.ref);
  if (opts.amount) params.set('amount', opts.amount);
  return `${APP_SCHEME}?${params.toString()}`;
}

/**
 * Try to jump back into the app. Browsers routinely refuse a custom-scheme
 * navigation that no user gesture asked for, so this is best-effort only and
 * every caller must still render a visible button pointing at the same URL.
 */
export function tryReturnToApp(link: string): void {
  if (typeof window === 'undefined') return;
  try {
    window.location.href = link;
  } catch {
    // No app installed, or the browser blocked the scheme — the button remains.
  }
}
