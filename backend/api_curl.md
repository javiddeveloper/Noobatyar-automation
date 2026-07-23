# مستندات API پروژه نوبت‌یار (Noobatyar API Documentation)

این مستند بر اساس منطق کسب‌وکار (Business Domains) دسته‌بندی شده است.

> **نکات سراسری (Cross-cutting):**
> - **توکن‌ها:** عمر access token برابر ۲۴ ساعت و refresh برابر ۱۴ روز است (قابل تنظیم با env). کلاینت باید هنگام دریافت `401` با refresh token، توکن جدید بگیرد.
> - **محدودیت نرخ (Rate limiting):** روی همه‌ی اندپوینت‌ها سقف نرخ فعال است (پیش‌فرض: `anon = 60/min`، `user = 300/min`، اندپوینت‌های OTP سخت‌گیرانه‌تر). عبور از سقف → پاسخ **`429 Too Many Requests`**.
> - **کش:** اندپوینت‌های عمومی نمایش ساعت‌های خالی/اشغال (`/api/client/.../slots|available-slots`) تا ۳۰ ثانیه کش می‌شوند و پس از هر تغییر نوبت به‌صورت خودکار باطل می‌شوند.

---

## ۱. احراز هویت و مدیریت کاربران (Authentication & User Management)

### ۱.۱. ثبت‌نام کاربر جدید
**توضیحات:** ثبت‌نام کاربر با شماره موبایل و رمز عبور.
- **مسیر و متد:** `POST /api/auth/register/`
- **هدرها:** `Content-Type: application/json`

**نمونه درخواست:**
```bash
curl -X POST http://127.0.0.1:8000/api/auth/register/ \
  -H "Content-Type: application/json" \
  -d '{"phone": "09178516035", "password": "password123", "name": "جاوید"}'
```

**نمونه پاسخ موفق:**
```json
{
  "status": "success", "code": 201, "message": "ثبت‌نام موفق",
  "data": {
    "user": {"id": 1, "phone": "09178516035", "name": "جاوید", "role": "CLIENT", "is_employee": false, "joined_at": "2024-05-07T..."},
    "tokens": {"refresh": "eyJ...", "access": "eyJ..."}
  }
}
```

### ۱.۲. ورود به سیستم (Login)
**توضیحات:** ورود کاربران با شماره و رمز عبور و دریافت توکن دسترسی.
- **مسیر و متد:** `POST /api/auth/login/`

**نمونه درخواست:**
```bash
curl -X POST http://127.0.0.1:8000/api/auth/login/ \
  -H "Content-Type: application/json" \
  -d '{"phone": "09178516035", "password": "password123"}'
```

### ۱.۳. مشاهده و ویرایش پروفایل کاربر
**توضیحات:** دریافت اطلاعات کاربر فعلی و ویرایش نام.
- **مسیر و متد:** `GET / PATCH /api/users/<id>/`
- **هدرها:** `Authorization: Bearer <access_token>`

**نمونه درخواست:**
```bash
curl -X PATCH http://127.0.0.1:8000/api/users/1/ \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "نام جدید"}'
```

---

## ۲. مدیریت کسب‌وکار (Business Management)

### ۲.۱. ایجاد کسب‌وکار جدید
**توضیحات:** ایجاد یک کسب‌وکار جدید برای کاربر (نیاز به لاگین).
- **مسیر و متد:** `POST /api/business/`
- **هدرها:** `Authorization: Bearer <access_token>`

**نمونه درخواست:**
```bash
curl -X POST http://127.0.0.1:8000/api/business/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
  "title": "آرایشگاه زنانه پارسیان",
  "category": "BEAUTY_SALON",
  "phone": "09123456789",
  "address": "تهران، خیابان انقلاب",
  "default_service_duration": 45,
  "work_start_hour": 9,
  "work_end_hour": 21,
  "notification_enabled": true,
  "notification_types": "SMS,WHATSAPP",
  "notification_minutes_before": 60
}'
```

**نمونه پاسخ موفق:**
```json
{
  "status": "success", "code": 201, "message": "کسب و کار با موفقیت ایجاد شد",
  "data": {
    "id": 1, "title": "آرایشگاه زنانه پارسیان", "category": "BEAUTY_SALON",
    "unique_code": "2FFX6A7G", "phone": "09123456789", ...
  }
}
```

### ۲.۲. دریافت لیست کسب‌وکارها
**توضیحات:** دریافت کسب‌وکارهای ثبت شده برای کاربر جاری (همراه با صفحه‌بندی).
- **مسیر و متد:** `GET /api/business/`

**نمونه درخواست:**
```bash
curl -X GET 'http://127.0.0.1:8000/api/business/?page=1&page_size=10' \
  -H "Authorization: Bearer <access_token>"
```

### ۲.۳. بروزرسانی و دریافت اطلاعات کسب‌وکار
**توضیحات:** دریافت اطلاعات یک کسب‌وکار خاص یا ویرایش آن.
- **مسیر و متد:** `GET / PUT / DELETE /api/business/<id>/`

**نمونه درخواست ویرایش:**
```bash
curl -X PUT "http://127.0.0.1:8000/api/business/3/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "title": "آرایشگاه آپدیت شده",
    "category": "BEAUTY_SALON",
    "phone": "09123456789"
  }'
```

---

## ۳. مدیریت مشتریان (Visitor Management)

### ۳.۱. ایجاد مشتری جدید
**توضیحات:** افزودن یک مشتری به لیست مخاطبین کسب‌وکار.
- **مسیر و متد:** `POST /api/visitor/`

**نمونه درخواست:**
```bash
curl -X POST "http://127.0.0.1:8000/api/visitor/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "full_name": "احمد کاوه",
    "phone_number": "09123456783"
  }'
```

### ۳.۲. دریافت لیست و مدیریت مشتریان
**توضیحات:** دریافت، ویرایش و حذف اطلاعات مشتریان.
- **مسیرها:** 
  - دریافت لیست: `GET /api/visitor/?page=1&page_size=20`
  - ویرایش/حذف: `PUT / DELETE /api/visitor/<id>/`

---

## ۴. مدیریت نوبت‌ها (Appointment Management)

### ۴.۱. ایجاد نوبت جدید
**توضیحات:** ثبت نوبت جدید برای مشتری در یک کسب‌وکار.
- **مسیر و متد:** `POST /api/appointment/`

**نمونه درخواست:**
```bash
curl -X POST "http://127.0.0.1:8000/api/appointment/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{
    "business_id": 1,
    "visitor_id": 2,
    "appointment_date": 1778198400000,
    "service_duration": 60,
    "description": "قرار ملاقات مشاوره"
  }'
```

### ۴.۲. لیست و جستجوی پیشرفته نوبت‌ها
**توضیحات:** فیلتر نوبت‌ها بر اساس کسب‌وکار، مشتری، وضعیت و زمان.
- **مسیر و متد:** `GET /api/appointment/query`

**پارامترهای جستجو:**
- `business_id` (الزامی)
- `visitor_id` (اختیاری)
- `status` (اختیاری): `WAITING`, `COMPLETED`, `CANCELLED`, `NO_SHOW`
- `date`, `date_from`, `date_to` (اختیاری - Timestamp به ثانیه)
- `ordering`, `page`, `page_size`

**نمونه درخواست:**
```bash
curl -X GET 'http://127.0.0.1:8000/api/appointment/query?business_id=1&status=COMPLETED' \
  -H "Authorization: Bearer <access_token>"
```

### ۴.۳. ویرایش وضعیت نوبت
**توضیحات:** تغییر وضعیت نوبت (مثلاً به تکمیل شده یا کنسل شده).
- **مسیر و متد:** `PATCH /api/appointment/<id>/`

**نمونه درخواست:**
```bash
curl -X PATCH "http://127.0.0.1:8000/api/appointment/7/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>" \
  -d '{"status": "COMPLETED"}'
```

---

## ۵. اشتراک و حسابداری (Accounting & Subscription)

پلن‌های نوبت‌یار بر پایه‌ی **نردبان تعهد** طراحی شده‌اند: هر پلن علاوه بر مدت،
یک **بسته‌ی قابلیت** (`features`) باز می‌کند و هرچه تعهد بلندتر باشد، قابلیت‌ها و
سقف‌های بیشتری در اختیار کاربر قرار می‌گیرد. جزئیات کامل قابلیت‌ها، سقف‌ها و
چرخه‌ی عمر اشتراک در سند [PLANS.md](PLANS.md) آمده است.

### ۵.۱. لیست پلن‌ها و مشاهده اشتراک فعال
**توضیحات:** مشاهده پلن‌های موجود و وضعیت اشتراک کاربر.
- **دریافت لیست پلن‌ها:** `GET /api/accounting/plans/` — هر پلن شامل فیلد `features` (قابلیت‌ها و سقف‌ها) است.
- **وضعیت اشتراک من:** `GET /api/accounting/my-subscription/`

پاسخ `my-subscription` علاوه بر اشتراک، قابلیت‌های مؤثر و میزان مصرف این ماه را برمی‌گرداند:
```jsonc
{
  "subscription": { "plan": { "name": "سه ماهه", "features": { ... } }, "days_left": 47, ... },
  "entitlements": { "max_businesses": 3, "monthly_appointments": -1, "online_gateway": true, ... },
  "usage": {
    "appointments": { "used": 87, "quota": -1 },
    "sms": { "quota": 300, "monthly_remaining": 268, "wallet": 0 }
  }
}
```
> در سقف‌ها مقدار `-1` یعنی **نامحدود**.

### ۵.۲. قابلیت‌ها و مصرف (Entitlements & Usage)
**توضیحات:** قابلیت‌های پلن فعلی و میزان مصرف ماهانه (نوبت و پیامک).
- **مسیر و متد:** `GET /api/accounting/my-entitlements/`

### ۵.۳. خرید و پرداخت اشتراک
**توضیحات:** شروع فرآیند خرید یک پلن اشتراکی (اتصال به زیبال).
- **مسیر و متد:** `POST /api/accounting/plans/payment/`

**نمونه درخواست:**
```bash
curl -X POST http://127.0.0.1:8000/api/accounting/plans/payment/ \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"plan_id": 2}'
```

### ۵.۴. بسته‌های افزودنی (Add-ons)
**توضیحات:** خرید تکی یک قابلیت یا اعتبار پیامک، بدون ارتقای کل پلن. بسته‌های
پیامکی به کیف‌پول پیامک کاربر افزوده می‌شوند و بسته‌های قابلیتی یک قابلیت را برای
مدت مشخص فعال می‌کنند.
- **لیست بسته‌ها:** `GET /api/accounting/addons/`
- **خرید بسته:** `POST /api/accounting/addons/buy/`  (بدنه: `{"pack_id": 1}`)

```bash
curl -X POST http://127.0.0.1:8000/api/accounting/addons/buy/ \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"pack_id": 1}'
```

> **گیت‌شدن قابلیت‌ها:** فعال‌کردن تنظیماتی مثل درگاه آنلاین، بیعانه، پیامک تبلیغاتی
> یا کنترل ظرفیت روی کسب‌وکار، و همچنین تعداد کسب‌وکارها و تعداد نوبت ماهانه، بر اساس
> پلن فعال کاربر کنترل می‌شود؛ در صورت نبود قابلیت، پاسخ **`403`** و در صورت پر شدن
> سقف نوبت ماهانه، پاسخ **`409`** برگردانده می‌شود.
