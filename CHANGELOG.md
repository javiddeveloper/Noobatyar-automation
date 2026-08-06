# Changelog

<!--
  Each version section must include a Persian summary block delimited by
  `<!-- fa:start -->` / `<!-- fa:end -->` right after its `## vX.Y.Z` header.
  The Android release workflow (.github/workflows/android-release.yml)
  extracts exactly that block for the tag being released and uses it as
  both the GitHub Release body and the Telegram APK caption — so keep it
  short, user-facing, and in Persian. The rest of the section (English,
  per-package, technical) is for developers/agents and isn't shown to users.
-->

## v1.1.0 — 2026-08-06

<!-- fa:start -->
تغییرات این نسخه:
- نوتیفیکیشن‌ها (toast) حالا رنگ درستی دارن: موفقیت سبز، خطا قرمز، هشدار زرد — دیگه همه‌چیز قرمز نشون داده نمی‌شه.
- خطای «نشست منقضی شده» و «محدودیت درخواست» حالا پیام واقعی نشون می‌ده، نه ریست بی‌صدای برنامه.
- بنرهای خرید اشتراک در صفحه‌ی خانه: به‌جای چرخش خودکار (که قبل از خوندن رد می‌شد)، حالا با دست swipe می‌شن و لبه‌ی بنر بعدی هم دیده می‌شه.
- باگ Pull-to-refresh برطرف شد: متن «در حال بروزرسانی…» زیر محتوای صفحه بریده نمی‌شد.
- صفحه‌ی «درباره‌ی ما» بازطراحی شد؛ آیکون‌های شبکه‌های اجتماعی جدید و باکیفیت‌تر.
- پیام اضطراری کسب‌وکار، یادآوری چندکاناله، گزارش پیامک با فیلتر، و ثبت بیعانه‌ی پرداختی به نوبت اضافه شد.
- آمار «تکمیل‌شده» در داشبورد که اشتباه محاسبه می‌شد، درست شد.
- صفحه‌ی اصلی وب دیگه لیست کسب‌وکارها رو بدون ورود نشون نمی‌ده؛ حالا صفحه‌ی معرفی اپلیکیشنه.
<!-- fa:end -->

### mobile_owner
- Typed toasts: success/error/warning now render with distinct styling instead of every screen showing the same error-red toast. Settings and LastVisitors, which had no toast host at all, now surface their ViewModel error messages. Several actions (delete/complete/no-show/approve/reject/send-message in LastVisitors) previously succeeded with zero user feedback — they now confirm.
- Session-expired / rate-limit errors show an actual message instead of silently resetting app state.
- Home screen plan banners: replaced the auto-rotating carousel with manual swipe — each banner is now narrower than the screen so the next one peeks at the edge, plus a hint telling the user they can swipe. Auto-rotation was disorienting and skipped past banners before anyone could read them.
- Pull-to-refresh: fixed the resting indicator height being smaller than its own label text, which clipped the bottom of "در حال بروزرسانی…" under the page content after a release.
- About Us screen redesign: animated gradient hero card, and the "ارتباط با ما" social links moved from an uneven icon grid to a vertical list with handle text, redesigned icons (bale/eitaa/rubika/instagram/website) replacing heavy, illegible bitmap auto-traces.
- Version check now sends the real numeric `versionCode` instead of deriving one by stripping dots out of the version name (`"1.2.3"` → `123`).
- Release build config fixed: CI now builds `assembleProdRelease` (the `prod` flavor) explicitly, since the module gained a `local`/`prod` flavor split and the untargeted build was producing both and moving the APK output path.
- New: emergency notice banner (owner-configurable, shown to clients), tiered reminder delivery, SMS report screen with quota + status filtering, deposit-payment tracking on appointments, dashboard "completed" stat fixed to actually count completed appointments, appointment edit no longer blocks on the appointment's own occupied slot, list filters (date range / sort) now apply correctly after sync.

### front_client
- Home page (`/`) no longer lists every registered business unauthenticated; it's now a landing/promo page for the app itself, since `/` was never the real booking entry point (`/b/<code>` is).
- Emergency notice banner shown on business profile and booking pages when the owner has one enabled.
- Fixed a dark-theme color token (`--color-warning-tint`) missing from the dark media block.

### backend
- Emergency notice, reminder delivery, and SMS report endpoints/models.
- Deposit payment method + tracking code/payment reference now exposed on appointment serialization.

### tooling / docs
- Documented the `-Pbuildkonfig.flavor` requirement for local Android builds — building the `local` product flavor without this flag silently produces prod's `BASE_URL` instead of `10.0.2.2`.
- Local vs. production config separated for backend (`.env.local`) and mobile (Gradle flavors), documented in `docs/ENVIRONMENTS.md`.

Full commit range: [`v1.0.7...v1.1.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.0.7...v1.1.0)
