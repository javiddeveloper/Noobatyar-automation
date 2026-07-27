# دستور کار: انتقال از IP به دامنه + HTTPS

مخاطب: ایجنتی که دسترسی SSH به سرور پروداکشن دارد.
وضعیت: DNS آماده است، کد آماده است. فقط اجرای روی سرور مانده.

---

## ۰. اطلاعات پایه

| | |
|---|---|
| سرور | `93.127.223.93` (کاربر `root`) |
| مسیر پروژه | `/root/app/backend` |
| برنچ | `develop` |
| ارکستریشن | `docker compose` (سرویس‌ها: `db`, `pgbouncer`, `redis`, `web`, `frontend`, `nginx`) |
| nginx | داخل کانتینر، ایمیج `nginx:1.25` |

**DNS از قبل تنظیم و راستی‌آزمایی شده — دست نزن:**

| رکورد | مقدار | توضیح |
|---|---|---|
| `api.noobatyar.ir` | `93.127.223.93` | ✅ فعال |
| `app.noobatyar.ir` | `93.127.223.93` | ✅ فعال |
| `noobatyar.ir` | `185.112.35.194` | هاست اشتراکی — **نباید تغییر کند** |
| `www.noobatyar.ir` | CNAME → `noobatyar.ir` | **نباید تغییر کند** |
| MX | `noobatyar.ir` | ایمیل روی هاست اشتراکی — **نباید تغییر کند** |

دامنه‌ی اصلی عمداً روی هاست اشتراکی می‌ماند تا بار سرور کم بماند.

---

## ۱. اول از همه: بکاپ و pull

```bash
cd /root/app/backend
cp docker-compose.yml docker-compose.yml.bak
cp nginx/nginx.conf nginx/nginx.conf.bak
git -C /root/app pull origin develop   # اگر /root/app یک چک‌اوت گیت نیست، کد را به روش فعلی همگام کن
mkdir -p /root/app/backend/certbot/www
```

`docker-compose.yml` و `nginx/nginx.conf` در همین کامیت به‌روز شده‌اند و کانفیگ نهایی TLS داخلشان هست.

---

## ۲. نصب certbot روی خود سرور

```bash
apt-get update && apt-get install -y certbot
```

بدون `python3-certbot-nginx` — چون nginx داخل کانتینر است و پلاگین نمی‌تواند کانفیگش را ببیند. از حالت `standalone` استفاده می‌کنیم.

---

## ۳. گرفتن گواهی

روش `standalone` انتخاب شده چون nginx داخل کانتینر است و روش webroot اینجا مشکل مرغ‌وتخم‌مرغ دارد (کانفیگ TLS بدون گواهی بالا نمی‌آید، گواهی بدون nginx گرفته نمی‌شود). هزینه‌اش حدود ۳۰ ثانیه قطعی است.

```bash
cd /root/app/backend
docker compose stop nginx

certbot certonly --standalone \
  -d api.noobatyar.ir \
  -d app.noobatyar.ir \
  --non-interactive --agree-tos \
  -m <ایمیل-معتبر> \
  --pre-hook  "docker compose -f /root/app/backend/docker-compose.yml stop nginx" \
  --post-hook "docker compose -f /root/app/backend/docker-compose.yml start nginx"

docker compose start nginx
```

نکات:

- **یک گواهی برای هر دو دامنه** صادر می‌شود و زیر `/etc/letsencrypt/live/api.noobatyar.ir/` می‌نشیند (نام اولین `-d`). کانفیگ nginx هر دو server block را به همین مسیر بسته — عوضش نکن.
- هوک‌ها داخل خود گواهی ذخیره می‌شوند، پس تمدید خودکار هر ۶۰ روز خودش nginx را stop/start می‌کند.
- قبل از اجرا مطمئن شو پورت ۸۰ آزاد است (`ss -lntp | grep :80`) وگرنه standalone شکست می‌خورد.
- اگر Let's Encrypt خطای rate limit داد، با `--dry-run` تست کن؛ سقف ۵ گواهی در هفته برای یک مجموعه دامنه است.

بررسی:

```bash
certbot certificates
ls -l /etc/letsencrypt/live/api.noobatyar.ir/
```

---

## ۴. بالا آوردن nginx با کانفیگ جدید

`docker-compose.yml` حالا پورت ۴۴۳ را publish می‌کند و `/etc/letsencrypt` را read-only داخل کانتینر مانت می‌کند.

```bash
cd /root/app/backend
docker compose up -d nginx
docker compose exec nginx nginx -t     # تست سینتکس
docker compose logs nginx --tail 30
```

اگر `nginx -t` خطا داد، فوراً برگرد:

```bash
cp nginx/nginx.conf.bak nginx/nginx.conf && docker compose restart nginx
```

---

## ۵. متغیرهای محیطی جنگو

فایل `/root/app/backend/.env` روی سرور (در گیت نیست). این دو را ویرایش کن:

```env
ALLOWED_HOSTS=api.noobatyar.ir,app.noobatyar.ir,93.127.223.93,localhost,127.0.0.1
CORS_ALLOWED_ORIGINS=https://app.noobatyar.ir,https://api.noobatyar.ir
```

`93.127.223.93` عمداً می‌ماند چون نسخه‌های فعلی اپ اونر هنوز با IP کار می‌کنند.

در `settings.py` **هیچ تغییری لازم نیست** — `SECURE_PROXY_SSL_HEADER` و کوکی‌های امن و `CSRF_TRUSTED_ORIGINS` (که خودکار از `ALLOWED_HOSTS` ساخته می‌شود) از قبل زیر `if not DEBUG` تعریف شده‌اند.

```bash
docker compose up -d web
```

---

## ۶. بیلد دوباره‌ی فرانت

`NEXT_PUBLIC_API_URL` در زمان **build** داخل باندل Next.js پخته می‌شود، پس restart تنهایی کافی نیست. مقدار جدید (`https://api.noobatyar.ir`) در `docker-compose.yml` کامیت شده:

```bash
cd /root/app/backend
docker compose up -d --build frontend
```

⚠️ روی این سرور بیلد Next.js سنگین است. اگر با OOM کشته شد، اول swap را چک کن (`free -h`) و در صورت نیاز موقتاً اضافه کن.

---

## ۷. راستی‌آزمایی

```bash
curl -I https://api.noobatyar.ir/api/accounting/plans/
curl -I https://app.noobatyar.ir/
curl -I http://api.noobatyar.ir/            # باید 301 به https بدهد
curl -I http://93.127.223.93/               # باید هنوز 200/307 بدهد (اپ‌های قدیمی)
echo | openssl s_client -connect api.noobatyar.ir:443 -servername api.noobatyar.ir 2>/dev/null | openssl x509 -noout -dates -subject -ext subjectAltName
```

انتظار: هر دو دامنه در SAN گواهی، انقضا حدود ۹۰ روز بعد.

تست تمدید خودکار:

```bash
certbot renew --dry-run
systemctl list-timers | grep certbot
```

بعد یک بار سر تا ته جریان را دستی بزن: ورود با OTP → رزرو نوبت → پرداخت → تایید از پنل اونر، و مطمئن شو پیامک‌ها می‌رسند.

---

## ۸. اپ موبایل (بعد از سبز شدن مرحله ۷)

در `mobile_owner/composeApp/build.gradle.kts`:

```kotlin
buildConfigField(STRING, "BASE_URL", "https://api.noobatyar.ir")
buildConfigField(STRING, "BOOKING_BASE_URL", "https://app.noobatyar.ir")
```

و بعد از اینکه بیلد جدید تست شد، این دو استثنای cleartext حذف شوند:

- `mobile_owner/composeApp/src/androidMain/res/xml/network_security_config.xml`
- کلید `NSAllowsArbitraryLoads` در `mobile_owner/iosApp/iosApp/Info.plist`

**ترتیب مهم است:** اول سرور HTTPS بدهد، بعد اپ به دامنه برود، آخر استثناها حذف شوند.

---

## هشدارها

**۱. کال‌بک زیبال شکسته است — این کار را انجام نده، فقط گزارش کن.**
در `backend/accounting/views.py` خطوط ۱۶۲ و ۲۲۱ آدرس کال‌بک هاردکد شده:

```
https://noobatyar.ir/home/payment-result
https://noobatyar.ir/home/payment-result-addon
```

این مسیر نه در جنگو تعریف شده نه در Next.js، و `noobatyar.ir` هاست اشتراکی است نه این سرور. متغیر `SITE_URL` هم در settings تعریف شده ولی هیچ‌جا استفاده نمی‌شود. یعنی خرید پلن و بسته‌ی افزودنی احتمالاً همین حالا هم کال‌بکش به جایی نمی‌رسد. **این خارج از محدوده‌ی این تسک است** و تصمیم محصولی می‌خواهد (صفحه‌ی نتیجه‌ی پرداخت کجا باشد). دست نزن، فقط تایید کن که خراب است یا نه.

**۲. رمز root در ریپو.** فایل‌های `check_*.exp` و `update_backend.exp` و `DEPLOY_UPDATE_BACKEND.md` و `backend/deploy.py` (جمعاً ۱۹ فایل) رمز root سرور را به‌صورت plaintext دارند و push شده‌اند. جزو این تسک نیست، ولی اگر دستت رسید رمز باید عوض شود و SSH روی key-only برود.

**۳. هیچ رکورد DNS دیگری اضافه یا حذف نکن.** فقط `api` و `app` قرار است به این سرور اشاره کنند.
