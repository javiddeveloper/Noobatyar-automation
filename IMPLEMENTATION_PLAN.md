# پلن پیاده‌سازی Noobatyar / ProQueue

این سند نقشه‌ی راه اجراست برای سه هدف:

1. **تکمیل سرویس‌های موبایل اونر و Offline-First کردن کامل آن** (سرویس‌هایی که در بکند هست ولی در موبایل صدا زده نمی‌شوند).
2. **ساخت اپ کلاینت موبایل (`mobile_client`)** — KMP/CMP، کاملاً هم‌خانواده با اونر (همان نسخه لایبرری‌ها، معماری، رنگ، استایل، و همان کامپوننت تقویم).
3. **ساخت اپ وب کلاینت (`web_client`)** با همان رفتار و قواعد.

هدف بیزینسی کلاینت: کاربر عادی بتواند ثبت‌نام/لاگین کند، نوبت‌های خودش را ببیند، و نوبت ثبت کند؛ نوبت تا زمانی که owner تایید نکند «اخذ نشده» محسوب می‌شود.

---

## 0) وضعیت فعلی (یافته‌های بررسی کد)

- **بکند** (Django 4.2 + DRF + SimpleJWT، بخش‌هایی async با `adrf`): اپ‌های `api`, `business`, `visitor`, `appointment`, `accounting`, `versions`. مدل کاربر سفارشی با `phone`.
- **state machine نوبت در `AppointmentView`** فعلاً: `WAITING → CONFIRMED → COMPLETED/CANCELLED/NO_SHOW`. (توجه: مدل `STATUS_CHOICES` و این state machine کاملاً هم‌تراز نیستند — باید یکدست شوند.)
- **`appointment_query_view`** ownership را **چک نمی‌کند** (کد کامنت‌شده) و همه‌ی فیلدها را برمی‌گرداند → برای کلاینت ناامن است؛ endpoint عمومی جدا با ماسک لازم است.
- **موبایل اونر**: معماری MVI + Clean + Koin + Room + Ktor. اما **نوشتن‌ها (create/update/delete نوبت/مراجع/کسب‌وکار) فقط در Room لوکال انجام می‌شوند**؛ `AppointmentApiService` فقط `queryAppointments` (خواندن) دارد و repository فقط در `syncAppointments` به سرور وصل می‌شود. `BusinessApiService`/`VisitorApiService` متدهای CRUD دارند ولی repositoryها آن‌ها را **صدا نمی‌زنند**. یعنی اپ عملاً «local-only» است، نه «offline-first با sync».
- **`front_client` و `mobile_client` خالی‌اند** (`.gitkeep`).
- **Base URL موبایل**: `10.0.2.2:8000` هاردکد (فقط امولاتور).
- **پیکربندی**: `SECRET_KEY` ثابت، `DEBUG=True`، توکن‌های ملی‌پیامک و زیبال هاردکد، DB = SQLite.

---

## 1) تصمیم‌های باز (با پیش‌فرض پیشنهادی)

> این‌ها در سشن اجرا قابل تغییرند، ولی پلن بر اساس پیش‌فرض‌ها نوشته شده.

- **D1 — تکنولوژی وب (`web_client`):** پیشنهاد = **Compose Multiplatform Web (Wasm)** به‌عنوان یک target از همان کدبیس کلاینت، تا رنگ/استایل/معماری ۱۰۰٪ یکسان بماند و کد UI و دامین به‌اشتراک گذاشته شود. جایگزین‌ها: React/TS جدا، یا Kotlin/JS با همان design tokens. (اگر بلوغ Compose Web برای فرم/تقویم کافی نبود، fallback به React با همان design tokens.)
- **D2 — کشف کسب‌وکار (انتخاب بیزینس برای نوبت):** پیشنهاد = **لینک/QR اختصاصی هر کسب‌وکار** (`/b/<slug>`) به‌عنوان مسیر اصلی + **جستجو** به‌عنوان فاز دوم. owner لینک/QR را به مشتری می‌دهد؛ کلاینت مستقیم وارد صفحه‌ی نوبت‌گیری آن بیزینس می‌شود. مقیاس‌پذیر و بدون نیاز به دایرکتوری سنگین در شروع.
- **D3 — دیتابیس بکند برای مقیاس:** پیشنهاد = مهاجرت به **PostgreSQL** + **Redis** برای کش (هر دو در `requirements.txt` آماده‌اند). SQLite فقط برای توسعه.
- **D4 — هویت کلاینت در نوبت‌دهی:** کلاینت یک `User` است؛ هنگام رزرو، یک `Visitor` متناظر در همان بیزینس **به‌صورت خودکار ساخته/پیدا** می‌شود و نوبت هم به `visitor` و هم به `client_user` لینک می‌شود (جزئیات در A2).

---

## 2) قراردادهای دامنه (Domain rules) که هر سه کلاینت باید رعایت کنند

- **وضعیت‌های قابل‌نمایش به کلاینت فقط دو تا:** `PENDING` (در انتظار تایید) و `CONFIRMED` (تایید شده). بقیه‌ی وضعیت‌ها (COMPLETED/NO_SHOW/CANCELLED/IN_PROGRESS) سمت کلاینت در همین دو نگاشت یا مخفی می‌شوند (نگاشت در A1).
- **حریم خصوصی:** در نمای کلاینت، نوبت‌های **دیگران** فقط **بازه‌ی زمانی (busy slot)** را نشان می‌دهند — بدون نام، یادداشت، و حتی بدون وضعیت. نوبت‌های **خود کاربر** کامل نمایش داده می‌شوند.
- **رزرو تا قبل از تایید owner «اخذ نشده» است:** اسلات در حالت PENDING به‌صورت «soft-hold» نگه داشته می‌شود (برای جلوگیری از دابل‌بوکینگ هم‌زمان) ولی نهایی نیست تا owner تایید کند.
- **ورود اجباری:** کلاینت برای ثبت نوبت باید ثبت‌نام/لاگین کند (همان فلو اونر: register/login/OTP).

---

## بخش A — تغییرات بکند (مشترک برای اونر و کلاینت)

### A1. مدل وضعیت تایید نوبت
- در `appointment/models.py`:
  - افزودن فیلد `approval_status` با choices: `PENDING`, `CONFIRMED`, `REJECTED` (پیش‌فرض برای رزرو کلاینت = `PENDING`؛ برای نوبت ساخته‌شده توسط owner = `CONFIRMED`).
  - افزودن `source` با choices: `OWNER`, `CLIENT` (برای تفکیک منشأ رزرو).
  - افزودن `client_user` (FK به `api.User`, `null=True, blank=True`, `related_name='client_appointments'`) — کاربری که رزرو کرده (در رزرو owner خالی است).
  - افزودن `slot_hold_expires_at` (DateTimefor soft-hold؛ اختیاری در فاز اول).
- **یکدست‌سازی state machine:** `STATUS_CHOICES` مدل و `STATUS_TRANSITIONS` در `AppointmentView` را هماهنگ کنید (الان مدل `IN_PROGRESS` دارد ولی state machine `CONFIRMED`). نگاشت نهایی پیشنهادی برای کلاینت: `PENDING ↔ approval_status=PENDING`, `CONFIRMED ↔ approval_status=CONFIRMED`.
- مهاجرت‌ها (`makemigrations`/`migrate`) + مقداردهی پیش‌فرض رکوردهای موجود به `CONFIRMED`.
- ایندکس‌های لازم: `('business', 'appointment_date')` (موجود)، افزودن `('business', 'approval_status', 'appointment_date')` و `('client_user', 'appointment_date')`.

### A2. هویت کلاینت و نوبت multi-tenant
- مشکل فعلی: `Appointment.user`, `Visitor.user`, `Business.user` همه به owner گره خورده‌اند. کلاینت در بیزینسِ یک owner دیگر رزرو می‌کند.
- راهکار رزرو کلاینت (سرویس `BookingService`):
  1. ورودی: `business_id`, `appointment_date`, (اختیاری) `service_duration`/`description`، از روی `request.user` (=client).
  2. پیدا/ساخت `Visitor` در آن بیزینس با `phone_number=client.phone` و `full_name=client.name` (رعایت `unique_user_phone` که per-owner است — یعنی Visitor متعلق به owner بیزینس ساخته می‌شود، نه کلاینت).
  3. ساخت `Appointment` با `business`, `visitor`, `appointment_date`, `approval_status=PENDING`, `source=CLIENT`, `client_user=request.user`, `user=business.user` (owner).
  4. چک تداخل (conflict) + soft-hold اتمیک (A7).
- این طراحی باعث می‌شود نمای owner بدون تغییر کار کند (نوبت در لیست بیزینسش با وضعیت «در انتظار تایید» می‌آید) و نمای کلاینت هم نوبت‌های `client_user=self` را ببیند.

### A3. کشف کسب‌وکار (Discovery) — مطابق D2
- افزودن فیلد `slug` (یکتا، ایندکس‌دار) به `Business` (تولید خودکار از `title`+شناسه کوتاه).
- Endpoint عمومی (`AllowAny`):
  - `GET /api/public/business/<slug>/` → پروفایل عمومی بیزینس: `title, address, logo, work_start_hour, work_end_hour, default_service_duration`. (بدون `phone` کامل/داده‌های حساس owner.)
  - (فاز ۲) `GET /api/public/business/search/?q=&city=` با ایندکس مناسب/Postgres trigram.
- (اختیاری) endpoint تولید QR در سمت کلاینت‌ها انجام شود (نه بکند).

### A4. Endpoint در دسترس‌پذیری با ماسک حریم خصوصی (هسته‌ی privacy)
- `GET /api/public/business/<slug>/availability/?date=<unix_ms>` (نیازمند لاگین کلاینت برای رزرو؛ ولی availability می‌تواند `AllowAny` باشد):
  - خروجی: لیست اسلات‌های روز بر اساس `work_start_hour..work_end_hour` و `default_service_duration`، هر اسلات با وضعیت `free` یا `busy`.
  - برای اسلات‌های busy **فقط زمان شروع/پایان** برگردد — **بدون** نام، یادداشت، یا وضعیت تایید/رد.
  - اسلات‌های `PENDING` (soft-hold فعال) هم به‌عنوان busy نمایش داده شوند تا دابل‌بوکینگ نشود.
  - این endpoint serializer **جدا و حداقلی** داشته باشد (نه `AppointmentQuerySerializer` که همه‌چیز را لو می‌دهد).
- `GET /api/client/appointments/?status=&date_from=&date_to=` → فقط نوبت‌های `client_user=request.user`، **کامل** (چون متعلق به خود کاربرند). صفحه‌بندی‌شده.

### A5. تایید/رد توسط owner
- Endpoint: `PATCH /api/appointment/<id>/approval/` body `{ "action": "approve" | "reject" }` (نیازمند owner بودنِ بیزینس).
  - approve → `approval_status=CONFIRMED` (+ آزادسازی hold، نهایی‌سازی اسلات).
  - reject → `approval_status=REJECTED`/`status=CANCELLED` و آزادسازی اسلات.
- در نمای owner، شمارش «در انتظار تایید» به stats اضافه شود.
- (اختیاری) نوتیف/پیام به کلاینت هنگام تایید/رد (فاز بعد؛ از همان زیرساخت پیام).

### A6. زیرساخت Offline-First Sync (برای موبایل اونر و کلاینت)
هدف: لوکال = منبع حقیقت برای UI؛ تغییرات لوکال در یک **outbox** صف می‌شوند و به سرور push می‌شوند؛ pull تغییرات سرور merge می‌شود.
- **سمت بکند:**
  - افزودن `updated_at`/`server_updated_at` (موجود است) و پشتیبانی از **delta pull**: پارامتر `updated_after=<unix_ms>` در endpointهای query (visitor/appointment/business) تا فقط رکوردهای تغییرکرده برگردند.
  - پشتیبانی از **idempotency**: هدر `Idempotency-Key` (یا `client_uuid` در بدنه) برای create تا ارسال مجدد، رکورد تکراری نسازد.
  - بازگرداندن `id` سروری در پاسخ create تا کلاینت map لوکال↔سرور را به‌روزرسانی کند.
  - (اختیاری) endpoint دسته‌ای `POST /api/sync/batch/` برای push چند تغییر در یک درخواست (کاهش round-trip).
- **سیاست تعارض:** Last-Write-Wins بر اساس `updated_at` سرور؛ برای وضعیت نوبت، state machine سرور حاکم است (سرور می‌تواند یک تغییر نامعتبر را رد کند و کلاینت rollback کند).

### A7. مقیاس‌پذیری و کارایی (هدف: هزاران درخواست هم‌زمان)
- **DB:** مهاجرت به PostgreSQL؛ تنظیم connection pooling (مثلاً `pgbouncer` یا `CONN_MAX_AGE`).
- **رزرو اتمیک / جلوگیری از دابل‌بوکینگ:** ساخت نوبت داخل `transaction.atomic()` با `select_for_update()` روی بازه‌ی بیزینس+زمان، یا یک `UniqueConstraint`/exclusion روی (business, slot) برای جلوگیری از دو رزرو هم‌زمان روی یک اسلات. (الان منطق conflict هست ولی atomic-lock نیست → race condition در بار بالا.)
- **کش (Redis / django-redis):** کش availability و پروفایل عمومی بیزینس (TTL کوتاه، invalidate روی رزرو/تایید). کش لیست پلن‌ها و نسخه.
- **کوئری‌ها:** استفاده‌ی سراسری از `select_related`/`prefetch_related` (تا حدی هست)، فقط فیلدهای لازم با `.only()/.values()`، صفحه‌بندی keyset برای لیست‌های بزرگ.
- **ایندکس‌ها:** طبق A1 + ایندکس روی `Business.slug`, `Visitor(user, phone_number)` (موجود)، `Appointment(client_user, appointment_date)`.
- **Async:** ادامه‌ی استفاده از `adrf`؛ سرویس‌های I/O-bound (SMS/پرداخت) async/صف‌محور شوند.
- **Rate limiting:** DRF throttling روی auth/OTP/booking (مثلاً per-IP و per-user). جلوگیری از سوءاستفاده OTP.
- **Observability:** logging ساخت‌یافته (تا حدی هست) + متریک زمان پاسخ endpointهای داغ (availability/booking).

### A8. سخت‌سازی پیکربندی و امنیت (پیش‌نیاز production)
- انتقال به `django-environ`/متغیرهای محیطی: `SECRET_KEY`, `DEBUG`, `ALLOWED_HOSTS`, `ZIBAL_MERCHANT_ID`, توکن ملی‌پیامک (الان در `api/sms.py` هاردکد)، `DATABASE_URL`, `REDIS_URL`, `SITE_URL`.
- `DEBUG=False` در production، تنظیم `CORS` برای `web_client`، `SECURE_*` headerها.
- جداسازی `settings` به `base/dev/prod`.

> **توجه:** توکن‌ها/سکرت‌هایی که الان در ریپو commit شده‌اند باید چرخانده (rotate) و از تاریخچه پاک شوند.

---

## بخش B — تکمیل موبایل اونر (Offline-First واقعی + سرویس‌های جا‌افتاده)

### B1. الگوی Outbox + فیلدهای sync در Room
- افزودن به Entityها (`AppointmentEntity`, `VisitorEntity`, `BusinessEntity`): `serverId: Long?`, `syncState` (`PENDING_CREATE/PENDING_UPDATE/PENDING_DELETE/SYNCED`), `updatedAt`, `clientUuid`.
- ساخت `OutboxDao`/`SyncManager` که تغییرات `PENDING_*` را به ترتیب به API می‌فرستد و پس از موفقیت `serverId` را ست و `syncState=SYNCED` می‌کند.
- اجرای sync: هنگام آنلاین‌شدن، باز شدن اپ، و pull-to-refresh (با استفاده از `NoInternetDialog`/وضعیت شبکه‌ی موجود).

### B2. سرویس‌هایی که در بکند هست ولی موبایل صدا نمی‌زند — اتصالشان
- **Appointment:** افزودن به `AppointmentApiService`: `createAppointment` (`POST appointment/`)، `updateAppointment`/`updateStatus` (`PATCH appointment/<id>/`)، `deleteAppointment` (`DELETE appointment/<id>/`)، `getStats` (`GET appointment/stats?business_id=`)، و `approve/reject` (`PATCH appointment/<id>/approval/`). سپس `AppointmentRepositoryImpl` در همه‌ی نوشتن‌ها بعد از Room، outbox/sync را صدا بزند (الان فقط Room).
- **Business:** `BusinessRepositoryImpl` در create/update/delete، `BusinessApiService` (که از قبل آماده است) را از طریق outbox صدا بزند.
- **Visitor:** همان‌طور برای `VisitorRepositoryImpl` + `VisitorApiService`.
- **Stats:** `GetTodayStatsUseCase` بتواند هم لوکال هم سروری بخواند (sync دوره‌ای).
- **Subscription:** `SyncSubscriptionUseCase` موجود؛ اطمینان از صدا زدن آن بعد از پرداخت.

### B3. پشتیبانی از وضعیت تایید در نمای اونر
- DTO/Entity نوبت فیلد `approvalStatus` و `source` بگیرد.
- صف خانه: بخش جدید «در انتظار تایید» با اکشن تایید/رد (اتصال به A5).
- ماسک‌سازی لازم نیست (owner همه‌چیز را می‌بیند).

### B4. پیکربندی
- Base URL از build config / محیط (dev=`10.0.2.2:8000`, prod=دامنه‌ی واقعی) خوانده شود نه هاردکد.

---

## بخش C — اپ کلاینت موبایل (`mobile_client`) — KMP/CMP، هم‌خانواده با اونر

### C1. راه‌اندازی پروژه (یکسان با اونر)
- کپی ساختار `mobile_owner` (همان `settings.gradle.kts` با همان مخازن myket/runflare، همان `libs.versions.toml` با **همان نسخه‌ی دقیق لایبرری‌ها**، همان پلاگین‌ها: KMP, CMP, Compose Compiler, Serialization, Room, KSP, Koin, Ktor, Navigation, kotlinx-datetime).
- **به‌اشتراک‌گذاری استایل:** انتقال `ui/theme` (Color/Typography/Theme)، فونت‌های یکان، و کامپوننت‌های مشترک به یک منبع واحد. گزینه‌ها:
  - (ساده) کپی یکسان `ui/theme` + `core/ui/components` (همان رنگ/استایل).
  - (تمیزتر) ساخت ماژول مشترک `:designSystem`/`:shared-ui` که اونر و کلاینت هر دو از آن استفاده کنند. **پیشنهاد: شروع با کپی، سپس استخراج ماژول مشترک.**
- همان معماری: MVI + Clean + Koin + Room + Ktor؛ همان `BaseViewModel`, `ApiResponse`, `AuthPlugin/TokenManager`, `HttpClientFactory` (با Base URL محیط).

### C2. فیچرهای کلاینت (فقط آنچه لازم است)
- **Auth:** register/login/forgot-password/OTP — **دقیقاً مثل اونر** (همان feature ها قابل کپی‌اند).
- **انتخاب بیزینس (D2):** ورود از طریق لینک/QR (`/b/<slug>`) یا اسکن QR؛ صفحه‌ی پروفایل عمومی بیزینس (از A3). (فاز ۲: جستجو.)
- **تقویم نوبت‌گیری:** استفاده از **همان `CalendarScreen`/کامپوننت تقویم اونر**؛ ولی به‌جای داده‌ی لوکال، از endpoint availability (A4) تغذیه شود.
  - نمایش اسلات‌های روز: free/busy. اسلات‌های دیگران فقط **زمان** (بدون نام/نوت/وضعیت).
  - نوبت‌های **خود کاربر** کامل (با وضعیت «در انتظار تایید»/«تایید شده»).
- **ثبت نوبت:** انتخاب اسلات free → `POST /api/client/appointments/` (A2) → وضعیت اولیه «در انتظار تایید».
- **نوبت‌های من:** لیست از `GET /api/client/appointments/` (A4)، با همان دو وضعیت.
- **پروفایل/خروج/نسخه/درباره‌ما:** کپی سبک از اونر (بدون بخش‌های مخصوص کسب‌وکار مثل ساخت بیزینس/مدیریت مراجع/پیام یادآوری).

### C3. Offline-First کلاینت
- همان زیرساخت Room + outbox (B1) برای «نوبت‌های من»: کاربر آفلاین نوبت‌هایش را ببیند؛ رزروِ آفلاین در outbox صف شود و آنلاین‌شدن sync شود (با idempotency از A6).
- availability چون real-time و مشترک است، آنلاین خوانده و کوتاه‌مدت کش شود (نه منبع حقیقت آفلاین برای رزرو نهایی).

### C4. تفاوت‌های کلیدی با اونر (چه چیز حذف می‌شود)
- بدون: ساخت/مدیریت بیزینس، مدیریت مراجع، ارسال پیام یادآوری، آمار owner، تایید/رد.
- ماسک privacy در همه‌ی نماهای تقویم/لیستی که نوبت دیگران را نشان می‌دهد.

---

## بخش D — اپ وب کلاینت (`web_client`/`front_client`)

> مطابق D1، پیش‌فرض = Compose Multiplatform Web (Wasm) از همان کدبیس کلاینت.

- **D-Plan A (پیشنهادی):** افزودن target وب (`wasmJs`) به ماژول کلاینت؛ اشتراک کامل دامین/داده/UI؛ پیاده‌سازی `actual`های پلتفرمی وب (`HttpClientFactory` با Ktor JS، `PreferencesManager` با localStorage، `TokenManager`، باز کردن لینک/تماس). رفتار و قواعد دقیقاً مثل کلاینت موبایل (همان دو وضعیت، همان ماسک privacy، همان فلو لاگین و تقویم).
- **D-Plan B (fallback اگر Compose Web ناکافی بود):** React/TS با همان **design tokens** (رنگ‌ها/تایپوگرافی استخراج‌شده از `ui/theme`) و همان API/قواعد. ساختار صفحات آینه‌ی کلاینت موبایل.
- نکات وب: نیاز به `CORS` در بکند (A8)، مدیریت توکن امن، روتینگ مبتنی بر `/b/<slug>` برای ورود از لینک.

---

## بخش E — ترتیب اجرا (Milestones)

**M1 — پایه‌ی بکند برای کلاینت و مقیاس** (پیش‌نیاز همه‌چیز)
- A1 (وضعیت تایید + یکدست‌سازی state machine + مهاجرت)، A2 (BookingService)، A3 (slug + پروفایل عمومی)، A4 (availability ماسک‌دار + لیست نوبت کلاینت)، A5 (approve/reject)، A8 (سکرت‌ها/settings)، بخش‌های حیاتی A7 (رزرو اتمیک + ایندکس + throttling).

**M2 — Offline-First اونر**
- A6 (delta pull + idempotency)، B1, B2, B3, B4. خروجی: اونر کاملاً با سرور sync می‌شود و «در انتظار تایید»ها را تایید می‌کند.

**M3 — کلاینت موبایل**
- C1 (اسکلت + اشتراک استایل)، C2 (فیچرها)، C3 (offline)، C4 (privacy/حذف‌ها).

**M4 — وب کلاینت**
- D (Compose Web یا fallback).

**M5 — سخت‌سازی و بار**
- بقیه‌ی A7 (کش Redis، keyset pagination، connection pool)، تست بار، observability.

---

## بخش F — معیارهای پذیرش و تست

- **بکند:** تست‌های unit/integration برای BookingService (تداخل، دابل‌بوکینگ هم‌زمان با تست concurrency)، ماسک privacy availability (نبود نام/نوت/وضعیت در اسلات دیگران)، approve/reject، ownership، throttling. بار: هدف پاسخ availability/booking زیر ~۲۰۰ms در بار هدف؛ بدون دابل‌بوکینگ تحت concurrency.
- **اونر:** ساخت/ویرایش/حذف آفلاین و sync صحیح پس از آنلاین‌شدن؛ idempotency (ارسال دوباره رکورد تکراری نسازد)؛ نمایش/تایید «در انتظار تایید».
- **کلاینت موبایل/وب:** ثبت نوبت → وضعیت «در انتظار تایید» → پس از تایید owner «تایید شده»؛ عدم نمایش نام/نوت/وضعیت دیگران؛ نمایش کامل نوبت‌های خود؛ یکسانی بصری با اونر (رنگ/فونت/تقویم).

---

## بخش G — ریسک‌ها و نکات

- **بلوغ Compose Multiplatform Web** برای فرم/تقویم/RTL فارسی باید زود اعتبارسنجی شود (اسپایک کوتاه قبل از M4)؛ در غیر این صورت fallback React.
- **همگام‌سازی نسخه‌ی لایبرری‌ها** بین اونر و کلاینت: استفاده از یک `libs.versions.toml` مشترک (در صورت monorepo شدن) یا کپی دقیق.
- **مدل `Visitor` per-owner:** رزرو کلاینت یک Visitor در دامنه‌ی owner می‌سازد؛ باید مراقب نشتی اطلاعات بین بیزینس‌ها بود (Visitor یک بیزینس نباید در بیزینس دیگر دیده شود).
- **چرخاندن سکرت‌های لو‌رفته** (زیبال/ملی‌پیامک) الزامی است.
- **Race condition رزرو:** بدون قفل اتمیک (A7) در بار بالا دابل‌بوکینگ رخ می‌دهد — این از اولویت‌های M1 است.
- **مهاجرت داده:** رکوردهای نوبت موجود باید `approval_status=CONFIRMED` و `source=OWNER` بگیرند.
