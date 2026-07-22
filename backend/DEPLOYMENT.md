# مستندات راه‌اندازی و دیپلوی بک‌اند روی سرور

این فایل شامل تمام کارهایی است که برای دیپلوی کردن پروژه بک‌اند روی سرور لینوکس ابونتو (IP: `93.127.223.93`) انجام شده است.

## ۱. داکرایز کردن پروژه (Dockerization)
برای اینکه پروژه قابلیت اجرای مستقل و پایدار روی سرور داشته باشه، فایل‌های زیر ایجاد شدند:
- **`Dockerfile`**: برای ساخت ایمیج بک‌اند جنگو (با استفاده از `python:3.10-slim` و نصب `gunicorn` + `uvicorn`).
- **`docker-compose.yml`**: برای هماهنگی و بالا آوردن کانتینرها. با بهینه‌سازی برای بار بالا، سرویس‌ها به این صورت هستند:
  1. `db`: دیتابیس PostgreSQL نسخه ۱۵
  2. `pgbouncer`: کانکشن‌پولر بین `web` و `db` (حالت `transaction`) برای مدیریت هزاران کانکشن همزمان
  3. `redis`: کش مشترک بین تمام workerها (برای OTP، rate-limit و کش اندپوینت‌ها)
  4. `web`: اپلیکیشن اصلی جنگو — با **ASGI** اجرا می‌شود (چند worker یووی‌کورن)
  5. `frontend`: کلاینت Next.js
  6. `nginx`: وب‌سرور Nginx برای دریافت ریکوئست‌ها روی پورت ۸۰ و انتقال آن‌ها به جنگو/فرانت.
- **`nginx/nginx.conf`**: تنظیمات Nginx برای Reverse Proxy و سرو کردن فایل‌های استاتیک جنگو.
- **`entrypoint.sh`**: اسکریپتی که موقع بالا آمدن کانتینر `web`، ابتدا منتظر دیتابیس می‌مونه، بعد مایگریشن‌ها (`migrate`) رو اجرا می‌کنه، فایل‌های استاتیک (`collectstatic`) رو جمع‌آوری می‌کنه و در نهایت پروژه رو ران می‌کنه.

> **مهم — اجرای ASGI:** ویوهای پروژه async هستند (`adrf`). دستور اجرای سرویس `web` در `docker-compose.yml` باید ASGI باشد نه WSGI:
> ```
> gunicorn core.asgi:application -k uvicorn.workers.UvicornWorker --workers ${GUNICORN_WORKERS:-4} --bind 0.0.0.0:8000 --timeout 60 --graceful-timeout 30
> ```
> اجرای قبلی (`gunicorn core.wsgi:application` با یک worker) گلوگاه اصلی زیر بار بود.

## ۲. تنظیمات دیتابیس و جنگو
فایل `core/settings.py` تغییر پیدا کرد تا تنظیمات زیر رو از طریق متغیرهای محیطی (Environment Variables) دریافت کنه:
- اتصال به دیتابیس PostgreSQL به جای SQLite (از طریق `pgbouncer`).
- کلیدهای `SECRET_KEY` و `DEBUG` و `ALLOWED_HOSTS`.
- **کش Redis** (`CACHES` با `django-redis`) — سرویس OTP و throttling روی این کش مشترک کار می‌کنند. بدون این، پیش‌فرض جنگو `LocMemCache` (مخصوص همان پروسه) بود و در حالت چند-worker باعث می‌شد کد OTP بین workerها گم شود.
- **کانکشن دیتابیس**: `CONN_MAX_AGE` (پیش‌فرض `0`، چون pooling را به `pgbouncer` سپرده‌ایم) و `CONN_HEALTH_CHECKS`.
- **امنیت**: `CORS_ALLOWED_ORIGINS`، `ZIBAL_MERCHANT_ID`، `SITE_URL` و توکن‌های پیامک از env خوانده می‌شوند؛ در پروداکشن اگر `SECRET_KEY` ست نشده باشد اجرا با خطا متوقف می‌شود (fail-fast).
- **JWT**: عمر access token به ۲۴ ساعت و refresh به ۱۴ روز کاهش یافت (قابل تنظیم با `JWT_ACCESS_HOURS` و `JWT_REFRESH_DAYS`).
- مسیر `STATIC_ROOT` برای ذخیره فایل‌های استاتیک و ارائه آن‌ها توسط Nginx تنظیم شد.

## ۳. مدیریت پیش‌نیازها
فایل `requirements_prod.txt` ایجاد شد که شامل پیش‌نیازهای اصلی و سازگار با سرور لینوکس بود (مثل `psycopg2-binary`، `gunicorn`، `uvicorn[standard]`، `django-redis`، `httpx`، `redis` و ...). پکیج‌های اضافی مربوط به هوش مصنوعی و مک حذف شدند تا سایز ایمیج بهینه‌تر بشه.

## ۴. راه‌اندازی و سید (Seed) دیتابیس
بعد از اجرای داکر روی سرور، دیتابیس کاملاً خالی بود. برای تست راحت‌تر، اسکریپت `seed_test_data.py` با دستور زیر روی کانتینر داکر سرور اجرا شد:
```bash
docker compose exec web python manage.py shell < seed_test_data.py
```
با این کار، یوزر تستی (۰۹۱۰۰۰۰۰۰۰۱) و ۳ کسب‌وکار پیش‌فرض ایجاد شدند.

## ۵. پنل مدیریت Portainer
برای مانیتورینگ و مدیریت راحت‌تر سرور بدون نیاز به ترمینال، پنل **Portainer** روی سرور نصب شد.
- **آدرس دسترسی:** `http://93.127.223.93:9000/`
- در این پنل به راحتی می‌تونید کانتینرها رو مشاهده کنید، لاگ ارورهای جنگو رو زنده ببینید، و کانتینرها رو ری‌استارت کنید.

## ۶. تنظیمات کلاینت (موبایل)
در سورس کد اپلیکیشن موبایل (`mobile_owner`)، تمام آدرس‌های Localhost (`10.0.2.2:8000`) که برای شبیه‌ساز اندروید تنظیم شده بودند، به آدرس واقعی سرور (`http://93.127.223.93`) تغییر پیدا کردند تا اپلیکیشن بتونه به سرور واقعی وصل بشه.

## ۷. معماری بهینه‌سازی برای بار بالا (هزاران درخواست همزمان)
برای پایداری و ظرفیت واقعی زیر بار سنگین، این تغییرات اعمال شد:

- **ASGI + چند worker**: سرویس `web` با گانیکورن + یووی‌کورن اجرا می‌شود تا ویوهای async واقعاً همزمان سرو شوند.
- **کش Redis**: سرویس OTP (`api/services/otp.py`) و throttling روی کش مشترک Redis کار می‌کنند (بین همه‌ی workerها پایدار).
- **PgBouncer**: پولینگ کانکشن دیتابیس در حالت `transaction`؛ سرویس `web` به‌جای `db` مستقیم، به `pgbouncer` (پورت `6432`) وصل می‌شود.
- **کش اندپوینت‌های عمومی slot**: پاسخ `PublicAvailableSlotsView` و `AvailableSlotsView` با TTL کوتاه (۳۰ ثانیه) کش می‌شود. هنگام ایجاد/تغییر/حذف نوبت، کش مربوط به همان `business + تاریخ` از طریق helper مشترک `appointment/cache_utils.py::invalidate_slots_cache` باطل می‌شود.
- **Throttling**: سقف نرخ سراسری (`anon`/`user`) و اختصاصی برای اندپوینت‌های OTP و slot. عبور از سقف → پاسخ `429`.
- **Pagination**: لیست نوبت مشتری (`ClientAppointmentListView`) دیگر کل تاریخچه را در حافظه لود نمی‌کند و صفحه‌بندی‌شده است (شکل پاسخ: `count / total_pages / current_page / next / previous / results`).
- **ایندکس ترکیبی** `(business, status, appointment_date)` روی جدول نوبت‌ها (migration `0005`).
- **پیامک پس‌زمینه**: ارسال پیامک رزرو از طریق ترد daemon انجام می‌شود تا مستقل از حلقه‌ی رویداد ریکوئست باشد.

### متغیرهای محیطی (Environment Variables)
این متغیرها باید در فایل `.env` کنار `docker-compose.yml` تنظیم شوند:

| متغیر | نمونه / پیش‌فرض | توضیح |
|-------|-----------------|-------|
| `SECRET_KEY` | (اجباری در پروداکشن) | کلید جنگو؛ اگر ست نشود اجرا متوقف می‌شود |
| `DEBUG` | `False` | در پروداکشن حتماً `False` |
| `ALLOWED_HOSTS` | `93.127.223.93,domain.com` | جدا با کاما |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | — | اطلاعات دیتابیس |
| `CORS_ALLOWED_ORIGINS` | `https://domain.com` | دامنه‌های مجاز CORS (در پروداکشن؛ جدا با کاما) |
| `ZIBAL_MERCHANT_ID` | `zibal` (سندباکس) | مرچنت آیدی درگاه زیبال |
| `SITE_URL` | `http://93.127.223.93` | آدرس پایه سایت (کال‌بک پرداخت) |
| `MELIPAYAMAK_OTP_TOKEN` | — | توکن ارسال OTP ملی‌پیامک |
| `MELIPAYAMAK_FROM` | — | شماره فرستنده پیامک |
| `GUNICORN_WORKERS` | `4` | تعداد workerها (پیشنهاد: `2*CPU+1`) |
| `JWT_ACCESS_HOURS` | `24` | عمر access token (ساعت) |
| `JWT_REFRESH_DAYS` | `14` | عمر refresh token (روز) |
| `THROTTLE_ANON` / `THROTTLE_USER` | `60/min` / `300/min` | سقف نرخ عمومی |
| `THROTTLE_OTP` / `THROTTLE_PUBLIC_SLOTS` | `5/min` / `120/min` | سقف نرخ اختصاصی |

> **نکته برای تیم موبایل:** به‌خاطر کاهش عمر access token به ۲۴ ساعت، اپ‌ها باید منطق **refresh token** را پیاده کرده باشند؛ در غیر این صورت یا رفرش را اضافه کنید یا موقتاً `JWT_ACCESS_HOURS=168` بگذارید. همچنین اگر اپ polling تهاجمی دارد، مراقب سقف `THROTTLE_USER` (پاسخ `429`) باشید.

---

### راهنمای آپدیت برای آینده
اگر در کد بک‌اند تغییری دادید و خواستید روی سرور اعمال کنید، مراحل زیر رو طی کنید:
1. فایل‌های تغییر یافته رو با SCP یا از طریق Git به سرور (`/root/app/backend/`) منتقل کنید.
2. از طریق SSH به سرور متصل بشید و دستورات زیر رو اجرا کنید:
```bash
cd /root/app/backend
docker compose build --no-cache web
docker compose up -d --force-recreate web
```
*(یا می‌تونید از داخل خود پنل Portainer گزینه Recreate Container رو با آپشن Pull image بزنید).*

> **بار اول بعد از این بهینه‌سازی‌ها:** چون سرویس‌های جدید (`redis`, `pgbouncer`) اضافه شده‌اند و متغیرهای env جدید لازم است، ابتدا فایل `.env` را طبق جدول بالا کامل کنید و سپس کل استک را بالا بیاورید:
> ```bash
> cd /root/app/backend
> docker compose up -d --build
> ```
> مایگریشن‌ها (از جمله ایندکس جدید `0005`) به‌صورت خودکار توسط `entrypoint.sh` اجرا می‌شوند. برای اعتبارسنجی تنظیمات پروداکشن هم می‌توانید یک‌بار اجرا کنید:
> ```bash
> docker compose exec web python manage.py check --deploy
> ```
