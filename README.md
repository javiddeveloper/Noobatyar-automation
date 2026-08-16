# نوبت‌یار (Noobatyar)

پلتفرم نوبت‌دهی و مدیریت صف برای کسب‌وکارهای کوچک (آرایشگاه، کلینیک و مشابه).
صاحب کسب‌وکار از طریق اپ موبایل نوبت‌ها را مدیریت می‌کند و مشتری‌ها از طریق وب
نوبت رزرو می‌کنند؛ هر دو روی یک بک‌اند Django مشترک صحبت می‌کنند.

## بخش‌های پروژه

| پوشه | چیست | استک | مستند اختصاصی |
|---|---|---|---|
| [`backend/`](backend) | API اصلی: احراز هویت، کسب‌وکارها، نوبت‌دهی، اشتراک/پرداخت | Django + DRF | [backend/README.md](backend/README.md) |
| [`front_client/`](front_client) | وب مشتری — پروفایل عمومی کسب‌وکار و رزرو نوبت | Next.js | [front_client/README.md](front_client/README.md) |
| [`mobile_owner/`](mobile_owner) | اپ موبایل صاحب کسب‌وکار (ProQueue) — اندروید + iOS | Kotlin Multiplatform / Compose | [mobile_owner/README.md](mobile_owner/README.md) |
| [`brand/`](brand) | هویت بصری، آیکن‌ها، رنگ‌ها و اسکریپت‌های اعمال روی همه‌ی اپ‌ها | SVG + sharp | [brand/README.md](brand/README.md) |

اپ موبایل مستقل برای مشتری وجود ندارد — رزرو نوبت فقط از طریق وب
(`front_client`) انجام می‌شود؛ جزئیات در [docs/ENVIRONMENTS.md](docs/ENVIRONMENTS.md#اپ-موبایل-مشتری).

## راه‌اندازی لوکال

هر سه بخش قابل‌اجرا (`backend`، `front_client`، `mobile_owner`) به‌صورت پیش‌فرض
به سرور production وصل می‌شوند؛ برای بالا آوردن یک استک کاملاً لوکال (که با
هم صحبت کنند) قبل از هر کاری این را بخوان:

**[docs/ENVIRONMENTS.md](docs/ENVIRONMENTS.md)** — چک‌لیست کامل، جدول
هاست‌نیم‌ها برای هر کلاینت (مرورگر / Android Emulator / iOS Simulator / دستگاه
واقعی روی LAN)، و تله‌های شناخته‌شده (Argon2، migration عقب‌افتاده، فلگ
`buildkonfig.flavor`).

خلاصه‌ی سریع (جزئیات کامل در همان فایل):

```bash
# بک‌اند
cd backend && source .venv/bin/activate && python manage.py runserver 0.0.0.0:8000

# وب مشتری
npm --prefix front_client run dev
```

اجراهای بالا در `.claude/launch.json` هم به‌عنوان تسک‌های `backend` و
`front_client` تعریف شده‌اند.

## دیپلوی

- [DEPLOY_TLS_DOMAIN.md](DEPLOY_TLS_DOMAIN.md) — سرور فعلی، دامنه و TLS (مرجع فعلی).
- [backend/DEPLOYMENT.md](backend/DEPLOYMENT.md) — تاریخچه‌ی داکرایز کردن و راه‌اندازی اولیه سرور (بخش‌هایی از آن قدیمی است، کنار سند بالا بخوان).
- [DEPLOY_UPDATE_BACKEND.md](DEPLOY_UPDATE_BACKEND.md) — گردش‌کار آپدیت بک‌اند روی سرور در حال اجرا.

## مستندات دیگر

- [CHANGELOG.md](CHANGELOG.md) — تاریخچه‌ی نسخه‌ها.
- [technical_roadmap.md](technical_roadmap.md) — نقشه‌ی راه فنی.
- [IDEAS_IRAN_MARKET.md](IDEAS_IRAN_MARKET.md) — ایده‌های محصول برای بازار ایران.
- [docs/BRAND_GUIDE_FOR_AI.md](docs/BRAND_GUIDE_FOR_AI.md) — راهنمای برند برای ایجنت‌ها.

## پرداخت

اشتراک کسب‌وکارها از طریق درگاه **زیبال (Zibal)** پرداخت می‌شود؛ جریان کامل
sandbox و ریدایرکت پس از پرداخت در
[docs/ENVIRONMENTS.md](docs/ENVIRONMENTS.md#تست-یک-پرداخت-واقعی-sandbox-زیبال-سر-تا-ته)
مستند شده است.
