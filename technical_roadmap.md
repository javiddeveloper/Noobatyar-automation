# نقشه راه فنی توسعه سیستم نوبت‌یار (Noobatyar Technical Roadmap)

این سند به عنوان یک راهنمای فنی جامع برای مدل هوش مصنوعی (Gemini/Claude) جهت پیاده‌سازی فاز به فاز نیازمندی‌های پروژه «نوبت‌یار» تدوین شده است.

## وضعیت فعلی پروژه (Current State)
- **بک‌اند:** Django REST Framework با ساختار اپ‌های مجزا (`api`, `business`, `appointment`, `visitor`, `accounting`).
- **اپلیکیشن مدیران (Owner App):** Kotlin Multiplatform (KMP) / Compose Multiplatform با معماری لایه‌بندی شده (Data, Domain, UI) و استفاده از Ktor برای شبکه.
- **اپلیکیشن کلاینت (وب):** هنوز پیاده‌سازی نشده است (`front_client/` خالی است).

---

## تاریخچه تغییرات (Changelog)

### `2026-07-13` — Phase 1.1 Security & Concurrency Hardening (Backend)
> **برنچ:** `fix` | **کامیت:** `feat: phase 1.1 - security, concurrency & OTP hardening`

تمام چهار «خط قرمز» امنیتی اولیه در لایه بک‌اند پیاده‌سازی شد:

#### ✅ خط قرمز ۱ — ایزوله‌سازی داده عمومی (`PublicBusinessSerializer`)
- **`business/serializers.py`:** سریالایزر جدید `PublicBusinessSerializer` با whitelist سخت اضافه شد. این سریالایزر **تنها** فیلدهای `id`, `title`, `logo`, `address`, `work_start_hour`, `work_end_hour`, `default_service_duration`, `payment_method` را برمی‌گرداند. هیچ اطلاعات مالی، پیامکی یا تنظیماتی به کلاینت ارسال نمی‌شود.
- **`BusinessSerializer`** (اونر) با ۶ فیلد جدید به‌روز شد.

#### ✅ خط قرمز ۲ — قفل ۱۵ دقیقه‌ای نوبت
- **`appointment/models.py`:** فیلدهای `locked_at`, `expires_at` (با DB index), `tracking_code`, `payment_reference` اضافه شدند.
- وضعیت‌های جدید `LOCKED`, `PENDING_VERIFICATION`, `CONFIRMED` به `STATUS_CHOICES` افزوده شدند.

#### ✅ خط قرمز ۳ — محدودیت OTP و ارسال غیرهمزمان پیامک
- **`api/services/otp.py`:** بازنویسی کامل. حافظه in-process (`TTLCache`) با **Django Cache (Redis)** جایگزین شد که در multi-worker محیط‌ها پایدار است.
  - کلید `otp:rate:{phone}` — محدودیت ۳ دقیقه‌ای
  - کلید `otp:daily_fail:{phone}` — بن ۲۴ ساعته پس از ۵ خطای متوالی
  - ارسال SMS در **Thread دیمون جداگانه** — API بلافاصله پاسخ می‌دهد
- **`api/views.py`:** `send_otp_view` و `verify_otp_view` به سرویس جدید متصل شدند.

#### ✅ خط قرمز ۴ — پاسخ بهینه تقویم (بدون اطلاعات شخصی)
- **`appointment/views/public_slots_view.py`:** اندپوینت عمومی جدید:
  ```
  GET /api/client/appointments/slots/<business_id>/?date=YYYY-MM-DD
  ```
  فقط `{start_time, end_time, status}` برمی‌گرداند. Visitor هرگز وارد حافظه نمی‌شود (`.only()` projection).

#### ✅ میگریشن‌ها
- `appointment/migrations/0003_add_payment_sms_lock_fields.py` ✓
- `business/migrations/0004_add_payment_sms_lock_fields.py` ✓

---

## فاز ۱: تکمیل، همگام‌سازی و رفع باگ‌های اپلیکیشن Owner و Backend

**هدف:** تثبیت اپلیکیشن مدیران، همگام‌سازی کامل داده‌های آفلاین/آنلاین و رفع نواقص فعلی.

### ۱.۱. توسعه مدل کسب‌وکار و امنیت داده ✅ **انجام شد**
- **`business/models.py`:**
  - ✅ فیلدهای `category`, `unique_code` (قبلاً پیاده‌سازی شده بودند)
  - ✅ فیلدهای جدید: `payment_method` (NONE/CARD/GATEWAY), `merchant_id`, `card_number`, `card_owner_name`, `enable_reminder_sms`, `enable_promotional_sms`
- **`business/serializers.py`:**
  - ✅ `PublicBusinessSerializer` — سریالایزر عمومی ایزوله برای کلاینت‌ها
  - ✅ `BusinessSerializer` — سریالایزر کامل برای اونر (شامل فیلدهای جدید)
- **اقدام باقی‌مانده در Owner App:**
  - [ ] به‌روزرسانی `BusinessDto` و `BusinessEntity` برای فیلدهای `payment_method`, `card_number`, `card_owner_name`, `merchant_id`, `enable_reminder_sms`, `enable_promotional_sms`
  - [ ] افزودن UI (dropdown + conditional inputs) در `feature/settings` و `feature/createBusiness`

### ۱.۲. سیستم همگام‌سازی (Offline/Online Synchronization)
- **وضعیت فعلی:** داده‌ها در دیتابیس لوکال (Room/SqlDelight) ذخیره می‌شوند اما سینک کامل و دوطرفه برای مشتریان و نوبت‌ها دارای نقص است.
- **اقدام فنی:**
  - پیاده‌سازی مکانیزم Sync Worker (WorkManager در اندروید / BGTask در iOS) برای همگام‌سازی دوره‌ای.
  - پیاده‌سازی منطق Conflict Resolution بر اساس فیلد `updated_at`.
  - تمام تغییرات (ساخت/ویرایش نوبت، حذف، تغییر وضعیت) باید ابتدا در Local Database ذخیره شده (تغییر وضعیت به `pending_sync`) و سپس به بک‌اند ارسال شوند (Optimistic UI Update).

### ۱.۳. آمار نوبت‌ها و صف انتظار (Queue Management & Stats)
- **اقدام در Backend (`appointment/views/appointment_stats_view.py`):**
  - در حال حاضر `AppointmentStatsView` آمار کلی (total, completed, no_show) را می‌دهد.
  - **نیاز جدید:** اضافه شدن سرویس محاسبه تعداد افراد منتظر قبل از یک نوبت خاص. کوئری: `Count(appointments)` با شرط `status='WAITING'` و `appointment_date < target_date` یا بر اساس زمان ثبت (`created_at`).
- **اقدام در Owner App:** نمایش این آمار در داشبورد به صورت Real-time یا با Polling/Websocket.

### ۱.۴. رفع مشکل صفحه‌بندی (Pagination) در موبایل
- **تحلیل مشکل:** بک‌اند از `PageNumberPagination` استاندارد موبایل استفاده نمیکند و `next`, `previous`, `count`, `results` را برنمی‌گرداند. مشکل از سمت UI در نیز یاسد بررسی و اصلاح شود
- **اقدام در Owner App:**
  - استفاده از کتابخانه `app.cash.paging:paging-compose` یا پیاده‌سازی یک `LazyColumn` استاندارد با منطق `onLoadMore` که `results` جدید را به `MutableStateFlow<List<T>>` اضافه (`addAll`) کند.

### ۱.۵. مدیریت اشتراک‌ها (Subscriptions)
- **وضعیت فعلی:** بک‌اند ماژول `accounting` با اتصال به زرین‌پال/زیبال را دارد (`ZibalPaymentService`).
- **اقدام در Owner App:**
  - اتصال UI به اندپوینت `GET /api/accounting/plans/`.
  - ارسال درخواست پرداخت به `POST /api/accounting/plans/payment/`.
  - هندل کردن Deep Link برای بازگشت از مرورگر (درگاه پرداخت) به اپلیکیشن و فراخوانی مجدد `syncSubscription`.

### ۱.۶. سوابق مشتریان و پیام‌ها
- اتصال UI بخش `Visitor History` به اندپوینت‌های فیلتر نوبت بر اساس `visitor_id` (موجود در `AppointmentQueryView`).
- نمایش تاریخچه پیامک‌های ارسال شده به کاربر (نیاز به ایجاد یک مدل `SmsLog` در بک‌اند در صورت عدم وجود).

### ۱.۶. لیست مشتریان و پیام‌ها
<<<<<<< HEAD
- **اقدام در Backend:** توسعه اندپوینت‌های لازم برای دریافت لیست مشتریان یک کسب‌وکار به همراه تاریخچه پیام‌های ارسال شده (پیاده‌سازی مدل `SmsLog` در صورت نیاز).
- **اقدام در Owner App:** طراحی سرویس‌ها و UI مجزا برای نمایش این بخش در موبایل اونر.

### ۱.۷. تایید نوبت‌های ثبت شده توسط کلاینت ✅ **جزئاً انجام شد**
- ✅ وضعیت‌های `PENDING_APPROVAL`, `PENDING_VERIFICATION`, `CONFIRMED`, `LOCKED` در مدل `Appointment` پیاده‌سازی شدند.
- ✅ اندپوینت ثبت نوبت کلاینت (`POST /api/client/appointments/`) با وضعیت `PENDING_APPROVAL` وجود دارد.
- **اقدام باقی‌مانده در Owner App:** توسعه سرویس و یک UI اختصاصی (مانند تب "درخواست‌های بررسی‌نشده") برای مشاهده درخواست‌های کلاینت‌ها و تایید/رد آن‌ها — نمایش `PENDING_VERIFICATION` با indicator بصری متمایز.

=======
- باید هم در بکند و هم در موبایل اونر. پیاده شود
>>>>>>> cab9b79 (feat(front_client): Phase 1 — Business Profile & Booking pages)
---

## فاز ۲: توسعه اپلیکیشن Client (موبایل و وب)

**هدف:** ایجاد بستر برای مشتریان نهایی جهت جستجو، مشاهده صف و اخذ نوبت.

### ۲.۱. توسعه APIهای عمومی و کلاینت در Backend
<<<<<<< HEAD
- **اندپوینت‌های موجود:**
  - ✅ `GET /api/client/appointments/` — لیست نوبت‌های کاربر
  - ✅ `POST /api/client/appointments/` — ثبت نوبت جدید
  - ✅ `GET /api/client/appointments/slots/<id>/?date=` — تقویم خالی/پُر (بدون اطلاعات شخصی)
- **اندپوینت‌های باقی‌مانده:**
  - [ ] `GET /api/client/businesses/` — لیست کسب‌وکارها با فیلتر `category` و جستجو روی `name`/`unique_code`
  - [ ] `GET /api/client/businesses/<id>/` — پروفایل عمومی کسب‌وکار (با `PublicBusinessSerializer`)
  - [ ] `GET /api/client/appointments/my-queue/` — وضعیت صف (تعداد و زمان تقریبی)
- **امنیت:**
  - **مهم:** توکن کلاینت‌ها باید از نظر سطح دسترسی با توکن‌های اونر کاملاً ایزوله باشد.
  - کلاینت‌ها نباید اطلاعات هویتی سایر مراجعین را ببینند.

### ۲.۲. توسعه فرانت‌اند (وب) — `front_client/`
- **تکنولوژی انتخابی:** Next.js (App Router) + TailwindCSS
- **State Management:** Zustand برای wizard چند مرحله‌ای رزرو
- **کامپوننت‌های اصلی:**
  - `PublicHeader` — نام و لوگوی کسب‌وکار
  - `ServiceSelector` — لیست خدمات از اندپوینت عمومی
  - `TimeSlotGrid` — تقویم ساعت‌های خالی
  - `AuthModal` — جریان OTP
  - `PaymentGateway` — ۳ حالت: رایگان / کارت‌به‌کارت / درگاه آنلاین
- **الزامات:** Responsive + SSR برای SEO
=======
- **ایجاد اپلیکیشن جدید Django (مثلاً `client_api`):**
  - `GET /api/client/businesses/`: لیست کسب‌وکارها با قابلیت فیلتر بر اساس `category` و سرچ روی `name` و `unique_code`. (نیاز به پیاده‌سازی سیستم Search کارآمد، ترجیحاً استفاده از `PostgreSQL Full-Text Search` در آینده).
  - `GET /api/client/businesses/{id}/`: پروفایل کسب‌وکار شامل ساعات کاری و میانگین زمان ویزیت.
  - `POST /api/client/appointments/book/`: ثبت نوبت توسط کلاینت ولی باید به تایید اونر بیزینس برسد پس باید برای بیزینس اونر سمت بکند و موبایل اونر سرویس و یوآی مجزا زده بشود.
  - `GET /api/client/appointments/my-queue/`: مشاهده وضعیت صف (تعداد افراد جلوتر و زمان تقریبی).
- **امنیت (Security):**
  - **بسیار مهم:** کلاینت‌ها **نباید** اطلاعات سایر کلاینت‌ها (نام، شماره تماس) را در خروجی APIهای صف دریافت کنند. خروجی فقط باید شامل «تعداد افراد» و «میانگین زمان انتظار» باشد.
  - توکن تولید شده برای کلاینت باید با توکن لونر ها از نظر دسترسی متفاپت باشد
### ۲.۲. توسعه فرانت‌اند (وب)
- **تکنولوژی پیشنهادی:** Angular (همانطور که درخواست شده) به دلیل ساختار ماژولار و مدیریت State قوی، یا Next.js (برای SEO بهتر در صفحات کسب‌وکارها).
- **الزامات:** 
  - طراحی کاملاً Responsive (Mobile-first).
  - استفاده از Angular Universal (SSR) برای ایندکس شدن صفحات کسب‌وکارها توسط موتورهای جستجو (SEO).
>>>>>>> cab9b79 (feat(front_client): Phase 1 — Business Profile & Booking pages)

### ۲.۳. توسعه موبایل کلاینت
- **تکنولوژی پیشنهادی:** استفاده مجدد از بستر KMP (Kotlin Multiplatform) برای به اشتراک‌گذاری ماژول‌های شبکه و دیتابیس بین اپلیکیشن Owner و Client، و نوشتن UI مجزا.

---

## فاز ۳: بهینه‌سازی، یکپارچه‌سازی و معماری دسترسی‌ها

**هدف:** پایداری سیستم، امنیت و ری‌فکتورینگ کدهای بک‌اند.

### ۳.۱. سیستم سطوح دسترسی (RBAC - Role Based Access Control)
- تغییر مدل `User` در `api/models.py`:
  - در حال حاضر فیلد `user_type` دارای مقادیر `vip` و `normal` است. این ساختار برای سیستمی با دو نوع کاربر کاملاً متفاوت (مدیر کسب‌وکار و مشتری) مناسب نیست.
  - **اقدام:** تغییر نقش‌ها به `BUSINESS_OWNER`, `CLIENT`, `ADMIN`.
  - ایجاد Permission Classهای اختصاصی در Django REST (مثل `IsBusinessOwner`, `IsClient`).

### ۳.۲. بهینه‌سازی دیتابیس و کوئری‌ها
- جداسازی منطق‌های تکراری در Views و انتقال آن‌ها به لایه Services (Business Logic Layer).
- اضافه کردن ایندکس‌های مناسب به دیتابیس، به ویژه برای `unique_code` کسب‌وکارها و `appointment_date` در ترکیب با `status`.
- کش کردن آمار و کاتالوگ کسب‌وکارها با استفاده از Redis برای کاهش بار دیتابیس.

<<<<<<< HEAD
---

## اندپوینت‌های فعال (API Reference)

| Method | URL | Auth | توضیح |
|--------|-----|------|-------|
| `POST` | `/api/send-otp/` | — | ارسال کد OTP (ریت‌لیمیت ۳ دقیقه) |
| `POST` | `/api/verify-otp/` | — | تأیید کد OTP |
| `POST` | `/api/register/` | — | ثبت‌نام کاربر جدید |
| `POST` | `/api/login/` | — | ورود با رمز عبور |
| `GET/PATCH` | `/api/business/<id>/` | Owner JWT | مدیریت کسب‌وکار (کامل) |
| `GET` | `/api/client/appointments/slots/<id>/?date=` | — | تقویم عمومی (فقط زمان‌ها) |
| `GET/POST` | `/api/client/appointments/` | Client JWT | نوبت‌های کلاینت |

---

## اولویت‌بندی اجرایی به‌روزشده

1. **✅ انجام شد — Phase 1.1:** امنیت داده عمومی، قفل نوبت، ریت‌لیمیت OTP، تقویم بهینه
2. **بعدی — Owner App Updates:**
   - به‌روزرسانی `BusinessDto`/`BusinessEntity` برای فیلدهای payment و SMS
   - UI تنظیمات پرداخت (dropdown + conditional fields)
   - UI تایید/رد نوبت‌های `PENDING_APPROVAL`/`PENDING_VERIFICATION`
3. **بعدی — Backend Client APIs:**
   - اندپوینت لیست و پروفایل کسب‌وکار با `PublicBusinessSerializer`
   - اندپوینت صف انتظار
   - RBAC (تفکیک توکن اونر و کلاینت)
4. **آینده — Front Client (Next.js):**
   - راه‌اندازی پروژه در `front_client/`
   - پیاده‌سازی ویزارد رزرو با Zustand
   - ۳ جریان پرداخت (رایگان، کارت‌به‌کارت، درگاه)
=======
>>>>>>> cab9b79 (feat(front_client): Phase 1 — Business Profile & Booking pages)
