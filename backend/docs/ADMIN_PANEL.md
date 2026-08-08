# پنل مدیریت نوبت‌یار (Django Admin)

پنل مدیریت، ابزار داخلی تیم است: بررسی و تایید کسب‌وکارها، اعطای دستی پلن و
بسته‌ی افزودنی، پیگیری پرداخت‌ها و پشتیبانی کاربران. این سند می‌گوید پنل کجاست،
چه کسی به چه چیزی دسترسی دارد و زیرساخت آن چطور چیده شده است.

---

## ۱. دسترسی به پنل

| مورد | مقدار |
|------|-------|
| مسیر پیش‌فرض | `/admin/` |
| متغیر محیطی مسیر | `ADMIN_URL` |
| شرط ورود | `is_staff = True` |
| نام کاربری | شماره‌ی موبایل (`USERNAME_FIELD = phone`) |

ساخت اولین کاربر مدیر:

```bash
python manage.py createsuperuser
```

### ۱.۱. متغیر `ADMIN_URL`

مسیر پنل از `ADMIN_URL` خوانده می‌شود (پیش‌فرض `admin/`) تا در پروداکشن بتوان آن
را از مسیر حدس‌زدنی جابه‌جا کرد و بخش بزرگی از ترافیک ربات‌های credential-stuffing
حذف شود. مقدار در `core/settings.py` نرمال‌سازی می‌شود، پس هر سه شکل زیر یکسان
کار می‌کنند:

```bash
ADMIN_URL=panel-x9
ADMIN_URL=panel-x9/
ADMIN_URL=/panel-x9/
```

> ⚠️ **هشدار nginx.** فایل `nginx/nginx.conf` فقط مسیرهای مشخصی را به سرویس
> `web` پروکسی می‌کند:
>
> ```nginx
> location ~ ^/(api|admin|plans|my-subscription|payment-result)
> ```
>
> اگر `ADMIN_URL` را عوض کنید **باید** پیشوند جدید را در همین regex (در هر دو
> بلوک `server`) اضافه کنید؛ وگرنه درخواست هرگز به جنگو نمی‌رسد و پنل از بیرون
> در دسترس نخواهد بود. تغییر `ADMIN_URL` بدون تغییر nginx = پنل ۴۰۴.

---

## ۲. نقش‌ها و دسترسی‌ها

نقش‌ها به‌صورت **گروه‌های auth جنگو** پیاده شده‌اند، نه یک فیلد روی کاربر — چون
`ModelAdmin` به‌صورت پیش‌فرض همین دسترسی‌ها را چک می‌کند و هیچ کد ادمینی لازم
نیست از وجود «نقش» خبر داشته باشد. اعطای نقش = فعال‌کردن `is_staff` + افزودن
کاربر به گروه.

| نقش | دسترسی | نمی‌تواند |
|-----|--------|-----------|
| `Superadmin` | همه‌ی دسترسی‌های موجود | — |
| `Moderator` | مشاهده/ویرایش `Business`؛ کامل روی `BusinessModerationLog`، `BannedKeyword`، `ContentReport` | ساخت/حذف کسب‌وکار، هیچ چیزی از accounting |
| `Support` | مشاهده‌ی `Business`، `User`، `Appointment`، `Visitor`؛ **افزودن** `Subscription` و `AddOnPurchase`؛ مشاهده‌ی `Plan` و `AddOnPack` | ویرایش/حذف هر چیزی |
| `Finance` | فقط مشاهده روی همه‌ی مدل‌های `accounting` | داده‌ی کاربران و کسب‌وکارها |

چند تصمیم که ارزش توضیح دارد:

- **Moderator فقط `view` و `change` روی `Business` دارد.** تصمیم مدیریتی یعنی
  تایید یا رد یک کسب‌وکار؛ ساخت یا حذف لیستینگِ یک صاحب کسب‌وکار هرگز کار
  ناظر نیست.
- **Support فقط `add` دارد، نه `change`.** اعطای دستی پلن یا اعتبار با
  *افزودن یک ردیف* انجام می‌شود (بخش ۶ سند [`PLANS.md`](../PLANS.md)) و همان
  منطق پرداخت واقعی زیبال را اجرا می‌کند. اشتباه با ردیف جدید اصلاح می‌شود نه
  با ویرایش ردیف قبلی، تا رد حسابرسی دست‌نخورده بماند.
- **`Plan` و `AddOnPack` برای Support فقط-خواندنی‌اند** چون فرم‌های اعطا از
  `autocomplete_fields` استفاده می‌کنند و اندپوینت autocomplete جنگو بدون
  دسترسی `view` روی مدل مقصد ۴۰۳ برمی‌گرداند — بدون این دو، انتخابگر پلن بی‌صدا
  خالی می‌ماند.
- **Moderator هیچ دسترسی مالی ندارد** و **Finance هیچ داده‌ی کاربری** — جداسازی
  عمدی «تصمیم محتوایی» از «پول».

### ۲.۱. ساخت/به‌روزرسانی گروه‌ها

```bash
python manage.py setup_admin_roles            # ساخت یا همگام‌سازی هر چهار گروه
python manage.py setup_admin_roles --dry-run  # فقط گزارش، بدون تغییر
```

نکات:

- **idempotent است.** دسترسی‌های هر گروه `set()` می‌شوند نه `add()`، پس جدول‌های
  داخل `core/management/commands/setup_admin_roles.py` تنها منبع حقیقت‌اند؛
  حذف یک دسترسی از جدول، در اجرای بعدی از گروه هم حذف می‌شود.
- **قبل/بعد از migrate هم امن است.** اگر جداول `auth`/`contenttypes` هنوز
  ساخته نشده باشند، فقط هشدار می‌دهد و خارج می‌شود. مدل یا دسترسی‌ای که پیدا
  نشود (اپ هنوز migrate نشده، مدل تغییر نام داده) با هشدار رد می‌شود و باعث
  کرش نمی‌شود.
- در پروداکشن بعد از هر دیپلویی که مدل جدیدی اضافه کرده، یک‌بار اجرایش کنید:

  ```bash
  docker compose exec web python manage.py setup_admin_roles
  ```

---

## ۳. ظاهر پنل: RTL و تایپوگرافی فارسی

| فایل | نقش |
|------|-----|
| `core/apps.py` → `NobatyarAdminConfig` | جایگزین `django.contrib.admin` در `INSTALLED_APPS` |
| `core/admin_site.py` → `NobatyarAdminSite` | عنوان‌های فارسی + نقطه‌ی اتصال ویوهای سفارشی |
| `templates/admin/base_site.html` | بارگذاری استایل سفارشی |
| `assets/admin_custom/css/admin.css` | فونت، اعداد، خوانایی جدول‌ها |
| `assets/admin_custom/fonts/*.woff2` | Vazirmatn (self-hosted) |

- **راست‌به‌چپ:** `LANGUAGE_CODE = 'fa-ir'` به‌تنهایی کافی است و تست شد —
  ادمین `dir="rtl"` و `admin/css/rtl.css` را از روی زبانِ *فعال* تصمیم
  می‌گیرد، و بدون `LocaleMiddleware` زبان فعال همیشه `LANGUAGE_CODE` است.
  `django.middleware.locale.LocaleMiddleware` عمداً **اضافه نشده**: با آن،
  پنل از `Accept-Language` مرورگر پیروی می‌کند و لپ‌تاپی که روی `en-US` است
  ادمین انگلیسیِ چپ‌به‌راست می‌گیرد (در تست بازتولید شد). اگر روزی برای i18n
  واقعی لازم شد، هم‌زمان `LANGUAGES = [('fa', ...)]` را هم پین کنید.
- **فونت self-hosted است، بدون CDN.** سرور در ایران است و CDNهای عمومی قابل
  اتکا نیستند؛ یک `<link>` به گوگل‌فونت صرفاً یعنی فونت هرگز لود نمی‌شود.
  فایل‌ها و مجوز OFL در `assets/admin_custom/fonts/` هستند
  (راهنما: `assets/admin_custom/fonts/README.md`).
- **جایگزینی AdminSite هیچ `@admin.register` موجودی را نمی‌شکند**، چون
  `AdminConfig.default_site` سایت را *قبل از* import شدن `admin.py` اپ‌ها جای
  `django.contrib.admin.site` می‌نشاند.

### ۳.۱. چیدمان فایل‌های استاتیک

دو پوشه‌ی جدا و عمداً غیرهم‌نام:

| پوشه | نقش | در گیت |
|------|-----|:------:|
| `assets/` | فایل‌های **منبع** که خودمان می‌نویسیم (`STATICFILES_DIRS`) | ✅ |
| `static/` | **خروجی** `collectstatic` (`STATIC_ROOT`)، mount شده در nginx | ❌ (ignore) |

این دو نباید یکی باشند: اگر یک ورودی `STATICFILES_DIRS` برابر `STATIC_ROOT`
باشد جنگو خطای `staticfiles.E002` می‌دهد و `collectstatic` عملاً فایل‌ها را روی
خودشان کپی می‌کند. `entrypoint.sh` در هر بالا آمدن کانتینر `collectstatic` را
اجرا می‌کند، پس `static/` یک artefact ساخت است و در `.gitignore` قرار دارد.

---

## ۴. توسعه‌ی پنل در فازهای بعدی

`NobatyarAdminSite.get_urls()` نقطه‌ی اتصال ویوهای سفارشی است (داشبورد KPI،
خروجی‌های گزارشی). دو نکته:

- هر ویو باید با `self.admin_view(...)` پیچیده شود؛ همان چیزی است که چک
  `is_staff` و هدرهای no-cache را اعمال می‌کند. ویوی خام آن‌جا برای همه باز است.
- URLهای سفارشی باید **قبل از** `super().get_urls()` بیایند تا الگوی
  catch-all جنگو آن‌ها را نبلعد.
- **صف بررسی محتوا (moderation queue)** عمداً این‌جا ساخته نشده و به
  `business/admin.py` وصل می‌شود تا از queryset و دسترسی‌های `BusinessAdmin`
  استفاده کند.

`NobatyarAdminSite.each_context()` هم برای دادن مقادیر مشترک به همه‌ی صفحه‌ها
هست؛ هر چیزی که آن‌جا اضافه شود روی **تک‌تک** درخواست‌های ادمین اجرا می‌شود
(از جمله ۴۰۴های ربات‌ها)، پس باید ارزان بماند.

---

## ۵. مسئله‌ی `role='ADMIN'` در برابر `is_staff`

در `api/models.py` دو مفهوم بی‌ارتباط از «مدیر» وجود دارد:

```python
ROLE_CHOICES = [
    ('BUSINESS_OWNER', 'صاحب کسب‌وکار'),
    ('CLIENT', 'مشتری'),
    ('ADMIN', 'مدیر'),
]
role = models.CharField(max_length=20, choices=ROLE_CHOICES, default='CLIENT')
is_staff = models.BooleanField(default=False)
```

**آن‌چه واقعاً دسترسی پنل را کنترل می‌کند `is_staff` است** (به‌علاوه‌ی گروه‌ها
برای دسترسی به هر مدل). فیلد `role` هیچ نقشی در ورود به `/admin/` ندارد.

تنها جایی که این دو به هم می‌رسند `UserManager.create_superuser()` است که هر سه
مقدار را با هم ست می‌کند. یعنی:

- کاربری با `role='ADMIN'` ولی `is_staff=False` **نمی‌تواند** وارد پنل شود.
- کاربری با `is_staff=True` ولی `role='CLIENT'` **می‌تواند** وارد شود.

هر دو حالت به‌سادگی پیش می‌آید (مثلاً ست‌کردن دستی `is_staff` از خود پنل)، و
نتیجه‌اش این است که هیچ‌کس نمی‌تواند با نگاه به `role` بگوید چه کسی به پنل
دسترسی دارد — یک تله‌ی امنیتی و ممیزی.

**پیشنهاد برای فاز بعد** (خارج از دامنه‌ی این تغییر، چون `api/models.py` دست
نخورده باقی مانده):

1. `role` را به‌عنوان تنها منبع حقیقتِ *دامنه‌ای* نگه دارید و `ADMIN` را از
   `ROLE_CHOICES` حذف کنید؛ «مدیر بودن» یعنی `is_staff` + گروه.
2. یا اگر `role='ADMIN'` باید بماند، آن را با یک `save()`/سیگنال به `is_staff`
   همگام کنید و در `UserAdmin` هر دو را کنار هم و فقط-خواندنی نشان دهید.

گزینه‌ی ۱ ترجیح دارد: مدل دسترسی جنگو (staff + groups + permissions) از قبل
همه‌ی چیزی که لازم داریم را دارد و یک فیلد موازی فقط دو منبع حقیقت می‌سازد.

---

## ۶. نقشه‌ی فایل‌ها

| فایل | نقش |
|------|-----|
| `core/apps.py` | `NobatyarAdminConfig` (جایگزینی سایت ادمین) + `CoreConfig` |
| `core/admin_site.py` | کلاس `NobatyarAdminSite` و نقطه‌ی توسعه‌ی ویوها |
| `core/urls.py` | mount کردن پنل روی `settings.ADMIN_URL` |
| `core/settings.py` | `ADMIN_URL`، `LocaleMiddleware`، `TEMPLATES.DIRS`، `STATICFILES_DIRS` |
| `core/management/commands/setup_admin_roles.py` | ساخت idempotent چهار گروه نقش |
| `templates/admin/base_site.html` | override قالب برندینگ ادمین |
| `assets/admin_custom/` | CSS و فونت self-hosted |
| `nginx/nginx.conf` | regex پروکسی که با `ADMIN_URL` باید همگام بماند |
