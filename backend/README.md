# بک‌اند نوبت‌یار

API اصلی پروژه — Django + Django REST Framework، با ویوهای async (`adrf`).
هم اپ موبایل صاحب کسب‌وکار ([`mobile_owner`](../mobile_owner)) و هم وب مشتری
([`front_client`](../front_client)) از همین بک‌اند تغذیه می‌شوند.

## اپ‌های جنگو

| اپ | مسئولیت |
|---|---|
| `core` | تنظیمات پروژه (`settings.py`)، پنل ادمین سفارشی‌سازی‌شده (فارسی/RTL)، داشبورد و سگمنت‌ها |
| `api` | مشترکات API: احراز هویت با شماره موبایل، JWT، تاریخ جلالی، ارسال SMS/OTP، pagination، permissions |
| `business` | پروفایل کسب‌وکار، خدمات، ساعات کاری |
| `appointment` | نوبت‌دهی و صف |
| `visitor` | مشتری/مراجعه‌کننده‌ی کسب‌وکار |
| `accounting` | اشتراک و پرداخت (درگاه زیبال) |
| `versions` | پلن‌های اشتراک |

## استک

- **Django 4.2** + **DRF** + **djangorestframework-simplejwt**
- اجرا با **ASGI** (uvicorn) — چون ویوها async هستند، نه WSGI.
- **PostgreSQL** پشت **pgbouncer** (transaction pooling) در production؛ SQLite در لوکال.
- **Redis** برای کش مشترک بین workerها (OTP، rate-limit، کش اندپوینت).
- **Zibal** برای پرداخت اشتراک.
- **Nginx** جلوی همه (reverse proxy + استاتیک) در production.

## راه‌اندازی لوکال

راهنمای کامل (شامل جدول هاست‌نیم برای هر کلاینت، `.env.local`، و تله‌های
شناخته‌شده مثل Argon2 و migration عقب‌افتاده) در
[`../docs/ENVIRONMENTS.md`](../docs/ENVIRONMENTS.md) است. خلاصه:

```bash
cd backend
source .venv/bin/activate
cp .env.example .env.local   # اگر هنوز نداری
python manage.py migrate
python manage.py runserver 0.0.0.0:8000
```

داده‌ی نمونه برای تست (اونر + پنج کسب‌وکار):

```bash
python manage.py shell < seed_test_data.py
```

## دیپلوی

سرویس‌های `docker-compose.yml`: `db` (Postgres) → `pgbouncer` → `web` (جنگو،
ASGI) ← `redis`، پشت `nginx`؛ `frontend` هم همینجا build/سرو می‌شود.

- [`DEPLOYMENT.md`](DEPLOYMENT.md) — تاریخچه‌ی داکرایز کردن (بخش‌هایی قدیمی‌اند).
- [`../DEPLOY_TLS_DOMAIN.md`](../DEPLOY_TLS_DOMAIN.md) — وضعیت فعلی سرور، دامنه و TLS (مرجع فعلی).
- [`../DEPLOY_UPDATE_BACKEND.md`](../DEPLOY_UPDATE_BACKEND.md) — گردش‌کار آپدیت روی سرور در حال اجرا.

## مرجع API

- `nobatyar.postman_collection.full.json` / `api_curl.md` — نمونه درخواست‌ها.
