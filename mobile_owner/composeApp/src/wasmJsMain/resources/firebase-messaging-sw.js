// Firebase Cloud Messaging service worker — background push for the owner
// web panel (docs/OWNER_WEB_PLAN.md section 10.1).
//
// Must be served from the SITE ROOT as /firebase-messaging-sw.js, not a
// subfolder: a service worker's registration scope defaults to the
// directory it's served from, so anywhere else here would only cover pushes
// for pages under that subfolder, not the app. This file lives directly in
// wasmJsMain/resources/ (next to index.html) because Kotlin/Wasm's `browser
// { }` Gradle config copies everything under that directory straight to the
// output root for both `wasmJsBrowserDevelopmentRun` and
// `wasmJsBrowserDistribution` — no extra wiring needed, and no other
// location under wasmJsMain/ would land at the root. Verified with:
//   curl -I http://localhost:8080/firebase-messaging-sw.js
//
// HTTPS is required for a service worker to register at all in production —
// browsers refuse plain http:// outright. `localhost` (and 127.0.0.1) is the
// one exemption, which is what makes the curl check above meaningful for
// local dev without also needing a cert.
//
// This only covers messages that arrive while no client page for this app is
// focused. The foreground case (tab open and focused) can't be handled here
// — the SDK does not invoke a service worker's message handling while a
// foreground client exists for it — so that path is handled in Kotlin
// instead, via `messaging.onMessage(...)` in
// core/push/FirebaseMessagingInterop.kt. Between the two, a push is never
// silently dropped regardless of what the tab is doing when it arrives.

// Vendored locally, not loaded from gstatic.com — see index.html's comment
// on the same SDK scripts for why: this app is served to users in Iran,
// where public CDNs are unreachable, and a service worker that can't even
// install (importScripts throws on a blocked/failed fetch, which aborts
// installation entirely) is worse than one that installs and then finds
// push unconfigured. Absolute path because this file's own URL is the site
// root, and importScripts resolves relative URLs against that.
importScripts('/vendor/firebase/firebase-app-compat.js');
importScripts('/vendor/firebase/firebase-messaging-compat.js');

// Same project config as core/push/FirebaseWebConfig.kt. Kept in sync by
// hand — a service worker can't import Kotlin/wasm output, and this file
// has to be self-contained JS. If you change either value here, change it
// in FirebaseWebConfig.kt too: out of sync means foreground push works and
// background push silently doesn't.
firebase.initializeApp({
    apiKey: 'AIzaSyBkj4QwHRnMjruW7BJniWijd4z5uGV1r8o',
    authDomain: 'nobatyar-79c53.firebaseapp.com',
    projectId: 'nobatyar-79c53',
    storageBucket: 'nobatyar-79c53.firebasestorage.app',
    messagingSenderId: '56921056578',
    appId: '1:56921056578:web:29eb94274ea5d99e6a14db'
});

// Wrapped in try/catch: this file has to *install* successfully even with a
// placeholder appId or a Firebase SDK version hiccup, or the browser drops
// the whole service worker (and with it, every background push this device
// would otherwise get) rather than just leaving push unconfigured.
try {
    // No explicit onBackgroundMessage handler needed: backend/api/services/push.py
    // already sends a top-level `notification` block (docs/NOTIFICATIONS.md
    // section 3), and the Firebase SDK displays that automatically for
    // background messages on its own. Adding a handler here would either
    // duplicate that notification or have to re-derive the same icon/
    // click-action push.py already sets.
    firebase.messaging();
} catch (e) {
    // Fail soft — see file header. Anything reaching here still leaves the
    // rest of the app (and the foreground onMessage path) unaffected.
}
