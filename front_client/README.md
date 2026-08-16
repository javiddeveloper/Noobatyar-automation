# وب مشتری نوبت‌یار

اپ Next.js که مشتری‌های نهایی برای دیدن پروفایل عمومی یک کسب‌وکار و رزرو
نوبت استفاده می‌کنند. اپ موبایل مستقلی برای مشتری وجود ندارد — این تنها راه
رزرو نوبت است (جزئیات در
[`../docs/ENVIRONMENTS.md`](../docs/ENVIRONMENTS.md#اپ-موبایل-مشتری)).

## استک

- **Next.js 16** (App Router) + **React 19**
- **Tailwind CSS 4**
- **Zustand** برای state
- **axios** برای فراخوانی API بک‌اند
- **moment-jalaali** برای تاریخ شمسی

## ساختار `app/`

| مسیر | چیست |
|---|---|
| `home/` | صفحه‌ی اصلی، شامل نتیجه‌ی پرداخت (`home/payment-result`) |
| `b/[slug]/` | پروفایل عمومی کسب‌وکار |
| `appointments/` | رزرو و مدیریت نوبت‌های مشتری |
| `auth/` | ورود/احراز هویت با شماره موبایل |
| `profile/` | پروفایل مشتری |
| `components/` | کامپوننت‌های مشترک |

## راه‌اندازی لوکال

```bash
npm --prefix front_client run dev
```

روی `http://localhost:3000` بالا می‌آید. `.env.local` (gitignore شده) باید
`NEXT_PUBLIC_API_URL` را به بک‌اند لوکال اشاره کند — جزئیات کامل و جدول
هاست‌نیم‌ها برای هر کلاینت در
[`../docs/ENVIRONMENTS.md`](../docs/ENVIRONMENTS.md) است.

## برند

آیکن‌ها و رنگ‌ها از [`../brand/`](../brand) اعمال می‌شوند؛ رنگ اصلی محصول
(`--color-primary`) در `app/globals.css` تعریف شده — جزئیات در
[`../brand/README.md`](../brand/README.md) و
[`../docs/BRAND_GUIDE_FOR_AI.md`](../docs/BRAND_GUIDE_FOR_AI.md).

## دیپلوی

Build/سرو در production از طریق سرویس `frontend` در
[`../backend/docker-compose.yml`](../backend/docker-compose.yml) انجام می‌شود؛
`Dockerfile.prod` را ببین.
