/**
 * Client-side FCM web push for نوبت‌یار (Noobatyar customer web).
 *
 * Companion to public/sw.js, which handles the background/service-worker
 * half of push. This module only ever runs main-thread, in response to a
 * real user action (see PushPermissionPrompt) — `Notification.requestPermission()`
 * requires a user gesture, same constraint mobile_owner's web build hit.
 *
 * Bundled via `npm install firebase` rather than vendored `<script>` tags:
 * unlike the service worker (which can only load scripts via importScripts,
 * see public/sw.js), this runs through webpack like any other import, so
 * the SDK ships from this app's own origin in the built bundle either way —
 * no CDN fetch at runtime, satisfying the same Iran-CDN-unreachable
 * constraint that made public/vendor/firebase/ necessary for the SW.
 */
import { initializeApp, type FirebaseApp } from 'firebase/app';
import { getMessaging, getToken, isSupported, type Messaging } from 'firebase/messaging';
import { registerDeviceToken } from './api';

// Same Firebase project as mobile_owner (FirebaseWebConfig.kt) and the
// values duplicated into public/sw.js — a Web app's config is not
// domain-restricted by default, so reusing it here is safe.
const FIREBASE_CONFIG = {
  apiKey: 'AIzaSyBkj4QwHRnMjruW7BJniWijd4z5uGV1r8o',
  authDomain: 'nobatyar-79c53.firebaseapp.com',
  projectId: 'nobatyar-79c53',
  storageBucket: 'nobatyar-79c53.firebasestorage.app',
  messagingSenderId: '56921056578',
  appId: '1:56921056578:web:29eb94274ea5d99e6a14db',
};

const VAPID_KEY =
  'BA0GUU6qC085fE1rJL_i05LSAzfvWa0R654q8TZvyfdP4U9xJdmWY23qB1LI1GWl9ZglAsMQfTEHkgqX03idtsM';

let app: FirebaseApp | null = null;
let messaging: Messaging | null = null;

/** True once permission has been explicitly, unrecoverably denied — there is
 * no programmatic way back from that state; only the visitor's own browser
 * site settings can undo it. Used to keep the prompt from nagging. */
export function isPermissionDenied(): boolean {
  return typeof Notification !== 'undefined' && Notification.permission === 'denied';
}

export function hasPermission(): boolean {
  return typeof Notification !== 'undefined' && Notification.permission === 'granted';
}

/** True when it still makes sense to ask — undecided, and not already granted. */
export function canPrompt(): boolean {
  return typeof Notification !== 'undefined' && Notification.permission === 'default';
}

async function getMessagingInstance(): Promise<Messaging | null> {
  if (messaging) return messaging;
  if (typeof window === 'undefined') return null;
  // isSupported() rules out browsers/contexts the SDK can't run in at all
  // (older Safari, non-secure origins) — calling getMessaging() there throws.
  if (!(await isSupported())) return null;

  app = app ?? initializeApp(FIREBASE_CONFIG);
  messaging = getMessaging(app);
  return messaging;
}

/**
 * Requests notification permission (must be called from a click handler —
 * see the file header) and, if granted, fetches an FCM token and registers
 * it against the signed-in visitor. Returns whether permission ended up
 * granted; never throws — a failed registration should not block whatever
 * the user was doing (booking, browsing) that led here.
 */
/** Fired on `window` when something (currently: a successful booking) wants
 * the global permission prompt to consider showing itself. Not fired
 * directly from a click — see schedulePushPrompt. */
export const PUSH_PROMPT_EVENT = 'noobatyar:push-prompt';

/**
 * Asks the global PushPermissionPrompt (mounted once, in the root layout) to
 * show itself shortly. Deliberately delayed rather than fired immediately:
 * the caller (currently the booking success handler) is mid-way through its
 * own success toast + `router.push` redirect, and popping a modal on top of
 * that toast, an instant before the page navigates away under it, would be
 * jarring. The prompt is app-shell-level and survives the client-side route
 * change either way, so it lands cleanly just after the redirect instead.
 */
export function schedulePushPrompt(delayMs = 1600): void {
  if (typeof window === 'undefined') return;
  setTimeout(() => window.dispatchEvent(new Event(PUSH_PROMPT_EVENT)), delayMs);
}

export async function requestPushPermission(visitorToken: string): Promise<boolean> {
  try {
    if (typeof Notification === 'undefined') return false;
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return false;

    const instance = await getMessagingInstance();
    if (!instance) return true; // permission granted; nothing more we can do in this browser

    const swRegistration = await navigator.serviceWorker.ready.catch(() => undefined);
    const deviceToken = await getToken(instance, {
      vapidKey: VAPID_KEY,
      serviceWorkerRegistration: swRegistration,
    });
    if (deviceToken) {
      await registerDeviceToken(deviceToken, visitorToken).catch((err) => {
        console.error('[push] device registration failed', err);
      });
    }
    return true;
  } catch (err) {
    console.error('[push] permission request failed', err);
    return false;
  }
}
