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

## v1.2.0 — 2026-08-07

<!-- fa:start -->
تغییرات این نسخه:
- بهبودهای جزئی پشت‌صحنه (بدون تغییر در تجربه‌ی کاربری).
<!-- fa:end -->

### mobile_owner
- Untracked Kotlin/Gradle build artifacts from git (`.gitignore` update); no user-facing change.

## v1.1.1 — 2026-08-07

<!-- fa:start -->
تغییرات این نسخه:
- داشبورد خانه بازطراحی شد: ساعت فعلی حذف شد، تعداد نوبت‌های امروز کنار تاریخ نمایش داده می‌شه، مستطیل‌های آمار به ۴ تا رسیدن، و یک ردیف جدید «افراد در صف» اضافه شد.
- همه‌ی کارت‌های آمار خانه حالا قابل کلیکن و با فیلتر درست شما رو به تب «مراجعین» می‌برن.
- محدودیت جدید برای «خدمات دریافت‌شده»: به‌جای متن آزاد، از یک لیست آماده (قابل افزودن) بر اساس دسته‌بندی کسب‌وکار انتخاب می‌کنید؛ خدمت جدید برای بقیه‌ی کسب‌وکارهای همون دسته هم قابل استفاده می‌شه.
- حداکثر مدت زمان سرویس از ۳ ساعت به ۸ ساعت افزایش پیدا کرد.
- پیام اضطراری از صفحه‌ی ساخت/ویرایش کسب‌وکار حذف شد (در تنظیمات همچنان در دسترسه).
- گزارش پیامک‌ها حالا فیلتر جست‌وجو بر اساس مشتری و بازه‌ی تاریخ داره.
- وقتی اعتبار پیامک این ماه تموم بشه، پیامک‌های ارسال‌نشده حالا در گزارش پیامک ثبت می‌شن و یک هشدار توی خانه نشون داده می‌شه.
- باگ لینک اینستاگرام «درباره‌ی ما» برطرف شد (آدرس اشتباه بود).
- باگ swipe نکردن بنرهای پلن که در نسخه‌ی قبل تازه اضافه شده بود، برطرف شد.
- دکمه‌ی «بروزرسانی» در پیام نسخه‌ی جدید حالا به سایت نوبت‌یار می‌ره.
- انتشار هر نسخه‌ی جدید حالا در GitHub Release و کپشن تلگرام هم به‌صورت خودکار توضیح داده می‌شه.
<!-- fa:end -->

### mobile_owner
- Home dashboard rework: removed the live clock, moved today's appointment count inline next to the date header, dropped the redundant "نوبت‌های امروز" card (4 stat cards remain), and added a full-width "افراد در صف" row sourced from data the ViewModel already fetched.
- All four stat cards plus the queue row now navigate to the existing "آخرین نوبت‌ها" tab with the right filter applied — previously this pushed a second, separately-stacked screen that lost the bottom nav bar; fixed to reuse the same tab-switch call the bottom bar itself uses (`PendingVisitorsFilter`, a one-shot holder consumed on tab entry instead of route arguments). A related bug where the status filter applied correctly but landed on the wrong sub-tab ("صف" instead of "مراجعین") was also fixed.
- Visitors tab now defaults to a 7-day window instead of showing everything; the Home 7-day trend chart navigates in with its own explicit past-date range since it summarizes the *past* week while the tab otherwise looks forward.
- Fixed two bugs rooted in Home's payment/business-switch handling: the payment-result dialog could replay itself after switching business (a stale deep-link intent being reprocessed on nav-graph rebuild), and today's stats could show a stale business's numbers if a slow-finishing load for the previously-selected business completed after the new one's.
- New service catalog: "خدمات دریافت‌شده" on the appointment-intake screen is now a chip picker (with an "add new" option) scoped to the business's category and shared across every business in that category, instead of unrestricted free text — the existing description field stays alongside it, unchanged.
- Service duration cap raised from 180 to 480 minutes (8 hours).
- Removed the inline emergency-notice section from the create/edit-business screen (the dedicated Settings screen is unaffected).
- SMS report: added search (customer name/phone) and date-range (today/this week/this month) filters. Messages skipped because the month's SMS quota ran out are now actually logged (a new `SKIPPED_QUOTA` status) instead of silently dropped, rendered with their own amber status and filter chip instead of misleadingly appearing as "sent".
- Home now shows a one-time warning toast per business when any messages were skipped this month for quota reasons, driven by a real server-side count, not a guess from the remaining-balance number.
- Fixed the Instagram link in "درباره‌ی ما" (`ajviddev` → `javiddev` — wrong since the very first commit).
- Fixed the plan banner regression from v1.1.0: wrapping `HorizontalPager` in a `SubcomposeLayout` for height-equalization silently broke swipe entirely. Replaced with an `onSizeChanged`-based max-height tracker that doesn't intercept touch input. Also fixed card height consistency and sort order (trial first, then ascending by duration) directly in the same pass.
- The out-of-date-version update button now opens noobatyar.ir instead of a Cafe Bazaar developer listing page.

### backend
- New `ServiceCatalogItem` model (category-scoped, not business-scoped — the mechanism for cross-business sharing) plus `Appointment.selected_services`, both backing the new service catalog feature.
- SMS report search/date filters on `GET business/<id>/sms-logs/`.
- `visitor.SmsLog` gains a `SKIPPED_QUOTA` status, written by all four `consume_sms()` call sites when quota is exhausted; `accounting.usage.sms_balance()` exposes a real `skipped_this_month` count via `GET accounting/my-entitlements/`.

### CI/CD
- Release workflow now creates an actual GitHub Release (previously it only built and sent to Telegram) and includes a Persian summary — sourced from this file's `fa:start`/`fa:end` block — in both the Release body and the Telegram APK caption. A tag with no matching block now fails the build loudly instead of shipping a placeholder release.

Full commit range: [`v1.1.0...v1.1.1`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.1.0...v1.1.1)

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
