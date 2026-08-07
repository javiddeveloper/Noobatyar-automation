# راهنمای بصری نوبت‌یار — برای تولید تصویر با هوش مصنوعی

این سند برای این ساخته شده که مستقیم به ابزارهای تولید تصویر با هوش مصنوعی (Midjourney، DALL·E، Ideogram، Adobe Firefly و مشابه) داده بشه تا عکس‌های تبلیغاتی هم‌راستا با هویت بصری نوبت‌یار بسازن. کل متن یا بخش‌های لازمش رو کپی کن و همراه با توضیح تصویر موردنظرت به ابزار بده.

---

## ۱. نوبت‌یار چیست

نوبت‌یار یک سیستم هوشمند مدیریت نوبت و صف برای کسب‌وکارهاست (سالن‌های زیبایی، کلینیک‌ها، مشاوره‌ها و مشابه). به صاحبان کسب‌وکار کمک می‌کنه مراجعین‌شون رو مدیریت کنن و صف‌های طولانی و بی‌نظم رو به یک فرایند منظم و دیجیتال تبدیل کنن. مخاطب اصلی هم صاحبان کسب‌وکار (اپ owner) و هم مشتریان نهایی‌ای هستن که آنلاین نوبت می‌گیرن (وب/اپ کلاینت).

## ۲. حال‌وهوا و شخصیت بصری (Mood)

وقتی از AI عکس می‌خوای، این کلمات رو به‌عنوان فضای کلی توصیف کن:

- **مدرن و تکنولوژیک** — نه سنتی، نه شلوغ؛ حس یک اپ SaaS تمیز و امروزی
- **هوشمند و مطمئن** — قابل‌اعتماد برای صاحب کسب‌وکار، نه شوخ یا کارتونی
- **گرم و در دسترس** — با اینکه تکنولوژیک هست، سرد و رباتیک نیست؛ حس کمک به آدم‌ها رو داره
- **منظم و آرام** — بر خلاف مفهوم "صف شلوغ"، تصویر باید حس نظم، سادگی و آرامش بده (کنتراست با آشفتگی صف سنتی)
- **درخشان و نرم (glow/soft)** — نور بنفش نرم، گرادیان‌های ملایم، حس شیشه‌ای/glassmorphism به‌جای رنگ‌های تخت و سخت

**کلمات کلیدی برای پرامپت (انگلیسی، چون اکثر ابزارها بهتر این‌طوری جواب می‌دن):**
`modern SaaS app, clean minimal UI aesthetic, soft purple glow, calm and organized, trustworthy, premium, smart technology, glassmorphism, soft gradient lighting, high-end mobile app marketing visual`

## ۳. پالت رنگی (خیلی مهم — دقیق رعایت بشه)

رنگ اصلی برند **بنفش** (violet/purple) است، با کمی نارنجی/کهربایی به‌عنوان رنگ تاکیدی (accent) روی لوگو. این پالت دقیقاً همون چیزیه که توی اپ و سایت واقعی استفاده می‌شه:

| نقش | هگز | توضیح |
|---|---|---|
| بنفش اصلی (Primary) | `#8B5CF6` | رنگ اصلی برند، پایه‌ی اکثر گرادیان‌ها |
| بنفش تیره (Primary Dark) | `#7C3AED` | انتهای تیره‌ی گرادیان بنفش |
| بنفش روشن | `#A78BFA` | ابتدای روشن گرادیان، هایلایت |
| نارنجی/کهربایی تاکیدی | `#F59E0B` تا `#FBBF24` | نقطه‌ی کوچک روی لوگو، هشدار/توجه — به‌مقدار کم استفاده می‌شه، نه به‌عنوان رنگ غالب |
| سبز موفقیت | `#10B981` | فقط برای نشانه‌ی موفقیت/تایید، نه رنگ اصلی تصویر |
| پس‌زمینه‌ی تیره | `#0F0F0F` | برای حالت dark mode / پس‌زمینه‌ی درام‌دار |
| پس‌زمینه‌ی روشن | `#F9FAFB` | برای حالت روشن، تقریباً سفید با کمی خاکستری |
| متن روی پس‌زمینه‌ی روشن | `#111827` | تقریباً مشکی، نه مشکی خالص |

**گرادیان استاندارد برند:** از `#A78BFA` (بالا/چپ) به `#8B5CF6` به `#7C3AED` (پایین/راست) — یک گرادیان بنفش نرم و مورب.

**ممنوعیت‌ها:**
- رنگ قرمز به‌عنوان رنگ اصلی تصویر استفاده نشه (فقط برای خطا در UI واقعی به‌کار می‌ره، نه در تبلیغات)
- از پالت‌های شلوغ رنگین‌کمانی یا نئون تند پرهیز بشه؛ برند مینیمال و تک‌رنگ‌محور (بنفش) است
- پس‌زمینه‌ی سفید خام و بی‌روح نه — همیشه یک گرادیان نرم یا نور بنفش ملایم در پس‌زمینه باشه

## ۴. لوگو و نمادها

لوگوی نوبت‌یار یک **تیک (checkmark) سفید داخل یک شکل شبه‌ساعت/فلش دایره‌ای** روی زمینه‌ی گرادیان بنفش (از روشن به تیره) است، با یک **نقطه‌ی کوچک نارنجی/کهربایی** بالای نماد (اشاره به نوبت/زمان). این نماد رو مستقیماً کپی نکن (لوگوی رسمی برند)، ولی می‌تونی از این **مفاهیم بصری** الهام بگیری:

- تیک/چک‌مارک = تاییدشدن نوبت
- دایره‌ی نیمه‌باز شبیه فلش/ساعت = گردش زمان، نوبت‌دهی، جریان منظم
- نقطه‌ی رنگی تکی = یک نوبت خاص، یک لحظه‌ی مهم

نمادهای مرتبط دیگری که با فضای برند هم‌خونی دارن: تقویم مینیمال، ساعت ساده، صف نظم‌یافته‌ی نقطه‌ها/آیکون‌ها، موج/نبض ملایم (signal).

## ۵. تایپوگرافی و متن در تصویر

- برند فارسی/راست‌به‌چپ است. اگه تصویر قراره متن فارسی داشته باشه، از فونت‌های هندسی و مدرن فارسی استفاده بشه (شبیه IRANSans / Vazirmatn)، نه فونت‌های سنتی/نستعلیق
- وزن فونت عناوین: **بولد/ضخیم**، برای وضوح در سایز کوچک (موبایل)
- اکثر ابزارهای تولید تصویر متن فارسی رو درست رندر نمی‌کنن — بهتره تصویر رو **بدون متن** یا با **جای خالی مشخص برای متن** (negative space) بخوای، و متن فارسی رو بعداً جدا اضافه کنی

## ۶. سبک ترکیب‌بندی (Composition)

- فضای خالی (negative space) زیاد؛ شلوغ نکردن قاب
- المان اصلی (موبایل/کارت/آیکون) وسط یا یک‌سوم قاب، با نور بنفش نرم پشتش (glow نرم، نه سایه‌ی تند)
- اگه آدم توی تصویر هست: صاحب کسب‌وکار (آرایشگر، پزشک، مشاور) با موبایل یا تبلت در دست، در محیط واقعی کسب‌وکار (سالن/مطب)، لبخند آروم و حرفه‌ای، نه ژست تصنعی
- زوایای نرم، سایه‌های ملایم، حس محصول پرمیوم دیجیتال (شبیه تبلیغات اپ‌های فین‌تک/پرمیوم SaaS)

## ۷. الگوی پرامپت آماده (کپی و ویرایش کن)

### پرامپت پایه (انگلیسی — پیشنهادی برای اکثر ابزارها)

```
A premium, modern mobile app promotional image for "Noobatyar", a smart
appointment & queue management SaaS.
Color palette: violet/purple gradient from #A78BFA to #7C3AED as the
dominant color, with a small warm amber (#FBBF24) accent, on a soft dark
background (#0F0F0F) or clean light background (#F9FAFB).
Mood: modern, calm, trustworthy, organized, premium tech product,
glassmorphism, soft purple glow lighting, minimal composition, lots of
negative space.
Visual elements: a smartphone or tablet showing a clean checkmark/calendar
UI, soft rounded shapes, subtle glowing circular clock/arrow motif.
No text in the image. High-end app store marketing style, 4k, soft
studio lighting.
```

### نمونه برای پست اینستاگرام (موضوع: راحتی نوبت‌گیری آنلاین)

```
Minimal lifestyle photo of a small business owner (hair salon / clinic)
checking their phone with a calm smile, phone screen glowing soft violet
(#8B5CF6), warm professional environment, shallow depth of field,
background softly blurred, natural light mixed with a subtle purple glow
accent, premium editorial photography style, no visible text or logos on
screen.
```

### نمونه برای بنر تبلیغاتی اپ (استور/وب)

```
Abstract app promo banner, dominant violet gradient background
(#A78BFA → #7C3AED), a single glowing checkmark-in-circle icon as the
hero element, soft amber accent dot, floating minimal UI card mockups
(calendar, clock, checklist) arranged with generous spacing, glassmorphism
cards with soft blur, clean premium SaaS marketing aesthetic, empty space
reserved on one side for headline text, 4k, ultra clean.
```

### نمونه استوری/ریلز (عمودی ۹:۱۶)

```
Vertical 9:16 mobile app teaser background, deep near-black (#0F0F0F)
backdrop with a soft glowing violet radial light (#8B5CF6) in the upper
third, minimal floating rounded UI elements (calendar dots, a soft
checkmark glyph) drifting in the glow, premium tech aesthetic, plenty of
empty space at bottom third for text overlay, no readable text baked in.
```

## ۸. چک‌لیست قبل از استفاده از خروجی AI

- [ ] رنگ غالب تصویر بنفش هست (نه آبی، نه قرمز، نه رنگین‌کمانی)؟
- [ ] حال‌وهوا آروم و پرمیوم است، نه شلوغ یا کارتونی؟
- [ ] اگه متنی روی تصویر هست، فارسی‌اش درست و خوانا نوشته شده (یا جای خالی برای اضافه‌کردن دستی گذاشته شده)؟
- [ ] از رنگ قرمز به‌عنوان رنگ اصلی استفاده نشده؟
- [ ] تصویر با فضای عمومی نوبت‌یار (اپ مدیریت نوبت/صف برای کسب‌وکار) مرتبطه، نه یک اپ عمومی بی‌ربط؟
