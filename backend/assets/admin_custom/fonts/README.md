# فونت‌های پنل ادمین

فونت **Vazirmatn** به‌صورت self-hosted سرو می‌شود. هیچ لینک CDN‌ای در CSS نیست —
سرور در ایران است و CDNهای بیرونی قابل اتکا نیستند.

| فایل | وزن |
|------|-----|
| `Vazirmatn-Regular.woff2` | ۴۰۰ |
| `Vazirmatn-Medium.woff2` | ۵۰۰ |
| `Vazirmatn-Bold.woff2` | ۷۰۰ |

منبع: <https://github.com/rastikerdar/vazirmatn> → `fonts/webfonts/`
مجوز: SIL Open Font License 1.1 (فایل `OFL.txt` کنار همین فایل‌ها)

اگر روزی این فایل‌ها گم شدند، با همین نام‌ها از مخزن بالا دانلود و در همین پوشه
قرار دهید؛ نام‌ها در `assets/admin_custom/css/admin.css` هاردکد شده‌اند. بعد از
جایگزینی، `collectstatic` را اجرا کنید.
