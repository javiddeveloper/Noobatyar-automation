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

## v1.5.0 — 2026-09-03

<!-- fa:start -->
تغییرات این نسخه:
- دسته‌بندی مشاغل کامل‌تر و دقیق‌تر شد (آرایشگاه زنانه/مردانه و رشته‌های پزشکی از هم تفکیک شدند)، و افزودن دسته‌های جدید در آینده دیگر نیازی به آپدیت اپ ندارد.
- رنگ صفحه‌ی عمومی کسب‌وکار حالا خودکار از روی لوگوی شما انتخاب می‌شود و نماد مرتبط با نوع شغلتان در صفحه نمایش داده می‌شود.
- امکان ارسال پیام تبلیغاتی هدفمند از سمت نوبت‌یار به مشتریان یا صاحبان کسب‌وکار.
- رفع اشکال: کسب‌وکارهایی که سابقه‌ی بررسی داشتند از پنل مدیریت قابل حذف نبودند.
- چند رفع اشکال جزئی در نمایش رنگ‌ها و بنر صفحه‌ی وب.
<!-- fa:end -->

### mobile_owner
- Category picker now fetches the live vocabulary from `GET business/categories/` and falls back to a bundled list only when the server is unreachable — adding a category on the backend no longer requires an app release.

### backend
- Business categories expanded from 37 to 68 and regrouped for precision (salon vs. barbershop, medical specialties split out individually).
- `Business.theme_color` derived from the logo at save time (WCAG-contrast-aware against white), replacing the fixed purple business header; `category_display` and the new `GET business/categories/` endpoint let clients read the vocabulary and its Persian labels without hardcoding either.
- Promotional push campaign system: a shared `PushCampaign` definition for admin-initiated, segmented pushes to customers or business owners, with optional image/link/deep-link.
- Fixed: a business with any moderation history — every business past its first review decision — could not be deleted from the admin, because the audit-log admin's blanket delete refusal was also consulted for the cascade and blocked it for the business itself, not just the log.

### front_client
- Business hero background colour follows `theme_color`; buttons, focus rings, and shadows across the themed page now derive from it instead of a hardcoded purple.
- Category-specific icon art (Tabler icon set) drawn in the hero, sized and positioned fully inside the header.
- Noobatyar promo banner recoloured to match the business's theme on business pages, and added to the booking-success and appointments screens (previously home + business page only).

Full commit range: [`v1.4.0...v1.5.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.4.0...v1.5.0)

## v1.4.0 — 2026-09-01

<!-- fa:start -->
تغییرات این نسخه:
- اعلان پوش (نوتیفیکیشن) برای نوبت جدید، تأیید/رد نوبت، و یادآوری نوبت، هم توی اپ و هم توی وب — رایگان و بدون نیاز به پیامک.
- پرداخت بیعانه از طریق درگاه زیبال حالا داخل خود اپ باز می‌شه (به‌جای مرورگر جدا)، هم اندروید هم iOS.
- تب جدید «پوش» در گزارش پیامک‌ها برای دیدن اعلان‌های ارسال‌شده.
- رفع باگ: ویرایش کسب‌وکار دیگه باعث آفلاین‌شدن موقتش نمی‌شه.
- رفع باگ امنیتی: پیام‌های مشتری بین کسب‌وکارهای مختلف یک صاحب دیگه با هم قاطی نمی‌شن.
- امکان تعیین کد یکتای دلخواه برای صفحه‌ی کسب‌وکار.
<!-- fa:end -->

### mobile_owner
- Push tab in the SMS report screen showing delivery status of owner/customer push notifications (Phase 1.5).
- App-wide notification permission prompt on first launch; warns when the saved reminder setting outruns the browser's actual permission state.
- Push notifications (booking/confirm/reject/reminder) gated behind پرو/پرو پلاس plans, matching the existing SMS reminder gate.
- In-app WebView for Zibal deposit payments on both Android and iOS, fixing a Referer-header issue the external browser flow had.
- Notification channel now created at app startup rather than on first use, so Android 8+ never silently drops the very first push.

### backend
- End-to-end FCM push system: booking/confirm/reject/reminder push to owners and customers, an owner-facing push-log report endpoint, admin-to-owner messaging (push and/or SMS), and promotional push to customer segments.
- Reminder delivery status (SMS + push) now exposed on the visitor's own appointment records.
- Fixed a data-isolation bug where an owner running multiple businesses saw one business's customer messages mixed into another's.
- Fixed a bug where editing an approved business could return it to moderation review and take it offline mid-edit.
- Accounting plan ladder pricing/quotas reworked; businesses can now be given a custom `unique_code` from the admin.
- Bale bot support for reviewing and deciding on the content-moderation queue directly from Telegram/Bale.

### front_client
- Redesigned client web UI: Material icons throughout (replacing emoji), animated hero/banner, per-field validation with Persian-digit normalization, 4-digit-grouped card number with Luhn checksum warning, receipt upload with crop + client-side compression, and an animated booking-confirmation screen.
- Home page now shows the customer's own upcoming appointments instead of a static promo screen.
- FCM web push for appointment reminders; the app is now an installable PWA.
- Business pages made discoverable/indexable for SEO; booking button shows a loading spinner while navigating to checkout.

Full commit range: [`v1.3.0...v1.4.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.3.0...v1.4.0)

## v1.3.0 — 2026-08-11

<!-- fa:start -->
تغییرات این نسخه:
- سیستم نظارت بر محتوای کسب‌وکارها اضافه شد: کسب‌وکارهای جدید و ویرایش‌های حساس (نام، توضیحات، آدرس، لوگو) پیش از نمایش عمومی بررسی می‌شن.
- در اپ صاحب کسب‌وکار، وضعیت بررسی و دلیل رد (در صورت وجود) روی صفحه‌ی تنظیمات نمایش داده می‌شه.
- تیک «تأیید شده»ی قبلی که صرفاً تزئینی بود و ممکن بود گمراه‌کننده باشه، حذف شد.
<!-- fa:end -->

### mobile_owner
- Business moderation status surfaced in Settings: badge (pending/approved/rejected/suspended), rejection reason when present, and a warning shown before an edit that would return an approved business to review.
- Removed the decorative "Verified" checkmark from the settings hero (it wasn't tied to real moderation state and read as a false claim); the real moderation badge replaces it.
- `is_locked` wired end-to-end (DTO → entity → mapper → domain) so `Business.isPubliclyVisible` matches the backend's combined billing+moderation check instead of moderation alone.
- Room DB bumped to version 11 for the new `isLocked` column (schema 14 exported).

### backend
- New content moderation system for businesses: review queue (oldest-submitted first, keyword matches highlighted), banned-keyword list (advisory only, never auto-rejects), POST-only/CSRF-protected/logged decisions, and automatic re-queue when a moderated field (title/bio/address/logo) is edited.
- Public business/slot endpoints now consistently check both billing lock and moderation state; a suspended business returns the same response as one that never existed.
- New admin panel foundation (`NobatyarAdminSite`) — RTL, self-hosted Vazirmatn font, role setup command (Superadmin/Moderator/Support/Finance).
- Redis `maxmemory-policy` changed from `allkeys-lru` to `volatile-lru` so paid SMS/appointment credit (stored with no TTL) can no longer be silently evicted under memory pressure.

### front_client
- Fixed a 404-from-slots bug that made a suspended business look "fully booked" instead of unavailable; shared `BusinessUnavailable` empty state and status-aware `ApiError`.

Full commit range: [`v1.2.0...v1.3.0`](https://github.com/javiddeveloper/Noobatyar-automation/compare/v1.2.0...v1.3.0)

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
