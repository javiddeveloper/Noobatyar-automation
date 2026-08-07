# لوکال در برابر production: هر پروژه چطور سوییچ می‌کنه

این ریپو سه بخش قابل‌اجرا داره — `backend`، `front_client`، `mobile_owner`
— و تا قبل از این، هرکدوم به‌صورت پیش‌فرض به **سرور واقعی
production** وصل می‌شدن، بدون هیچ راه پشتیبانی‌شده‌ای برای تست در برابر بک‌اند
لوکال، جز ادیت دستی سورس‌کد. این داکیومنت توضیح می‌ده چه کانفیگی الان برای
هرکدوم وجود داره، تا یک ایجنت (یا انسان) بتونه یک استک کاملاً لوکال (بک‌اند +
فرانت + اپ موبایل لوکال که با هم صحبت می‌کنن) رو بدون حدس زدن بالا بیاره، و
بتونه با یک نگاه بفهمه یک instance در حال اجرا توی کدوم حالته.

اگه یک ایجنت هستی که داری کار local-dev رو توی این ریپو ادامه می‌دی، اول این
فایل رو بخون — جای آزمون‌وخطا رو می‌گیره.

## مشکل اصلی: سه تا "localhost" متفاوت

هرچیزی که بک‌اند رو اجرا می‌کنه (`python manage.py runserver`) روی رابط
loopback همین ماشین بایند می‌شه. سه کلاینت مختلف برای رسیدن به همون loopback
به سه هاست‌نیم متفاوت نیاز دارن، چون "localhost" برای هرکدوم یعنی "خود این
دستگاه"، نه "ماشینی که بک‌اند روش اجرا می‌شه":

| کلاینت | هاست‌نیم برای رسیدن به ماشین میزبان |
|---|---|
| مرورگر روی همین ماشین (front_client) | `127.0.0.1` / `localhost` |
| Android Emulator | `10.0.2.2` |
| iOS Simulator | `127.0.0.1` (لوپ‌بک ماشین میزبان رو به اشتراک می‌ذاره) |
| دستگاه واقعی روی همون Wi-Fi/LAN | آی‌پی LAN همین ماشین (مثلاً `192.168.1.x`) |

هر کانفیگ حالت لوکال زیر، هاست درست رو از این جدول انتخاب می‌کنه.
`backend/core/settings.py` از قبل `127.0.0.1,10.0.2.2,localhost` رو توی
`ALLOWED_HOSTS` به‌صورت پیش‌فرض مجاز کرده، پس فقط باید طرف *هاست* هر URL رو
درست کنی، نه نگران reject شدن request توسط جنگو باشی.

## backend (Django)

**فایل:** `backend/core/settings.py` از قبل همه‌چیز رو از طریق
`os.getenv(...)` می‌خونه — این همیشه همین بوده. چیزی که کم بود، راهی برای ست
کردن این env varها بدون prefix کردن هر دستور، و راهی برای اینکه این تنظیمات
بعد از هر ری‌استارت سرور یا `pkill` از بین نره.

**الان:** `settings.py` هنگام import شدن، `load_dotenv(BASE_DIR / '.env.local')`
رو صدا می‌زنه (نگاه کن به `core/settings.py:9-14`)، با استفاده از
`python-dotenv` (که از قبل توی `requirements.txt` بود؛ الان واقعاً نصب و
استفاده می‌شه). پیش‌فرض `load_dotenv` (یعنی `override=False`) به این معنیه که
**یک env var واقعی همیشه روی فایل اولویت داره** — پس این هیچ تغییری توی نحوه
اجرای production ایجاد نمی‌کنه (docker-compose مستقیماً env varهای واقعی رو
پاس می‌ده؛ توی کانتینر هیچ `.env.local` وجود نداره، و حتی اگه هم بود، چیزی که
از قبل ست شده رو override نمی‌کرد).

- `backend/.env.local` — مقادیر واقعی لوکال تو. **gitignore شده**
  (`.gitignore` از قبل `.env` / `.env.*` / `!.env.example` رو داشت، از یک
  اتفاق قبلی که یک پسورد سرور از طریق اسکریپت‌های کامیت‌شده لو رفته بود — این
  فایل هم روی همون قانون سوار می‌شه). از قبل با مقادیر کارکرده ساخته شده
  (پایین‌تر ببین).
- `backend/.env.example` — template کامیت‌شده، بدون secret، همون کلیدها رو با
  کامنت مستند می‌کنه. توی یک checkout تازه، این رو کپی کن به `.env.local`.

دقیقاً مثل قبل اجراش کن، بدون نیاز به prefix کردن env:

```bash
cd backend && source .venv/bin/activate && python manage.py runserver 0.0.0.0:8000
```

(`0.0.0.0` به‌جای `127.0.0.1` چون هم مسیر `10.0.2.2` امولاتور رو قبول می‌کنه
هم اتصال‌های LAN از یک دستگاه واقعی رو — بایند روی `127.0.0.1` هم اتفاقاً برای
حالت امولاتور کار می‌کنه چون NAT اون traffic لوپ‌بک رو ریدایرکت می‌کنه، ولی
`0.0.0.0` نسخه‌ایه که حالت دستگاه واقعی روی LAN رو هم پوشش می‌ده.)

### توی `.env.local` چیه و چرا

| کلید | مقدار لوکال | چرا |
|---|---|---|
| `DEBUG` | `True` | prod با `SECRET_KEY` پیش‌فرض بدون `DEBUG=True` بالا نمیاد (`settings.py:14`) — همین باعث می‌شه اجرای لوکال بدون هیچ `SECRET_KEY` ست‌شده‌ای کار کنه. |
| `ZIBAL_MERCHANT_ID` | `zibal` | مرچنت sandbox عمومی زیبال. هر درخواست پرداخت موفق می‌شه و هیچ پول واقعی جابه‌جا نمی‌شه. مرچنت واقعی production به‌صورت fallback توی settings.py هاردکد شده (`6a0d8775dc2e6664d8adf3fd`) و هروقت این env var نباشه (یعنی توی کانتینر دیپلوی‌شده) خودکار استفاده می‌شه. |
| `CLIENT_WEB_URL` | `http://10.0.2.2:3000` | جایی که بعد از پرداخت، Zibal مرورگر پرداخت‌کننده رو بهش ریدایرکت می‌کنه، و لینک‌های پرداخت ودیعه هم بهش اشاره می‌کنن. باید از هرجایی که اون مرورگر واقعاً اجرا می‌شه قابل‌دسترس باشه — جدول کلاینت‌ها بالا رو ببین. **این چیزیه که همه یادشون می‌ره عوضش کنن.** اگه هنوز روی `https://app.noobatyar.ir` (پیش‌فرض prod) باشه، Zibal پرداخت‌کننده رو مستقیم می‌بره توی *production*، و صفحه `payment-result` که قراره verify endpoint بک‌اند لوکال تو رو صدا بزنه هیچوقت اجرا نمی‌شه — تراکنش لوکال برای همیشه `pending` می‌مونه و اپ می‌گه "پرداخت ناموفق". این دقیقاً همون چیزی بود که توی این سشن قبل از وایر شدن درست env var باهاش مواجه شدیم. |
| `SITE_URL` | `http://127.0.0.1:8000` | تنظیمات base-URL تزئینی، اهمیت کمی داره. |
| `OTP_DEV_CODE` | `123456` | ارسال/تایید واقعی OTP پیامکی رو دور می‌زنه. هروقت `DEBUG=False` باشه، اجباراً خالی می‌شه، پس حتی اگه این var یه‌جوری توی prod ست بشه هم نمی‌تونه نشت کنه. |
| `SMS_DEV_MODE` | `True` | به‌جای ارسال واقعی پیامک‌ها، فقط لاگشون می‌کنه — پیامک‌های رزرو/یادآوری/اشتراک به شماره واقعی نمی‌رن. این هم خارج از `DEBUG` خاموش می‌شه. |

دو چیز دیگه هم هست که روی یک دیتابیس لوکال تازه باهاشون مواجه می‌شی و ربطی به
`.env` ندارن:

- **هشر Argon2**: `PASSWORD_HASHERS` از Argon2 استفاده می‌کنه
  (`settings.py:123-126`) ولی `argon2-cffi` واقعاً توی `.venv` نصب نبود، با
  اینکه توی `requirements.txt` لیست شده (venv از قبل از اضافه شدن اون خط
  ساخته شده). لاگین با خطای
  `ValueError: Couldn't load 'Argon2PasswordHasher'` fail می‌شه تا وقتی که
  `pip install argon2-cffi==23.1.0 argon2-cffi-bindings==25.1.0` رو بزنی (یا
  مسیر wheel محلی خراب `altgraph` توی `requirements.txt` رو درست کنی و کل
  `pip install -r requirements.txt` رو اجرا کنی).
- **Migration های عقب‌افتاده**: سوییچ کردن بین برنچ‌ها می‌تونه دیتابیس sqlite
  لوکال رو عقب‌تر از کد نگه داره (مثلاً `business.0012_...`، `0013_...` هنوز
  اعمال نشده) — بعد از هر سوییچ برنچ، `python manage.py migrate` رو بزن، قبل
  از اینکه فکر کنی یک خطای 500 یک باگ واقعیه.
- **داده seed**: `backend/seed_test_data.py` یک اونر تستی می‌سازه
  (`09100000001` / `testpass123`) و پنج کسب‌وکار نمونه. با
  `python manage.py shell < seed_test_data.py` اجراش کن.

### تست یک پرداخت واقعی sandbox زیبال، سر تا ته

صدا زدن مستقیم verify endpoint (`/api/accounting/payment-result`) با یک
`trackId` تازه‌ساخته، `"transaction failed"` برمی‌گردونه — این درسته، باگ
نیست. sandbox زیبال فقط وقتی یک `trackId` رو پرداخت‌شده علامت می‌زنه که واقعاً
از `payment_url` (`https://gateway.zibal.ir/start/<trackId>`) بازدید کرده
باشی و روی دکمه موفقیت fake-checkout زیبال کلیک کرده باشی. مسیر واقعی:

1. خرید رو توی اپ trigger کن → `payment_url` رو توی یک مرورگر خارجی باز
   می‌کنه (`HomeViewModel` یک `HomeEvent.OpenUrl(...)` می‌فرسته، نه WebView،
   پس خود اپ هیچوقت زنجیره ریدایرکت رو نمی‌بینه).
2. روی گزینه موفقیت sandbox توی اون صفحه کلیک کن.
3. زیبال به `CLIENT_WEB_URL + /home/payment-result?trackId=...` ریدایرکت
   می‌کنه.
4. اون صفحه (`front_client/app/home/payment-result/page.tsx`)
   `NEXT_PUBLIC_API_URL` رو می‌خونه (از قبل `http://127.0.0.1:8000` توی
   `front_client/.env.local`) و خودش verify endpoint بک‌اند رو صدا می‌زنه.
5. بک‌اند تراکنش رو `success` علامت می‌زنه و اشتراک رو فعال می‌کنه.
6. صفحه سعی می‌کنه با یک deep-link به اپ برگرده
   (`noobatyar://payment/result?...`).

اگه مرحله ۳ به‌جای فرانت لوکال تو، روی `app.noobatyar.ir` فرود بیاد، برگرد و
`CLIENT_WEB_URL` رو درست کن.

## front_client (Next.js)

قبل از این سشن هم env-driven بود — چیز جدیدی اینجا نیست، فقط برای یکدستی
مستندش می‌کنم:

- `front_client/.env.local` (gitignore شده) — برای لوکال
  `NEXT_PUBLIC_API_URL=http://127.0.0.1:8000`. مقدار production
  (`https://api.noobatyar.ir`) مستقیم توی بلاک `environment:` فایل
  `docker-compose.yml` برای کانتینر دیپلوی‌شده ست شده، نه از یک فایل خونده
  بشه.
- `npm --prefix front_client run dev` (یا ورودی `front_client` توی
  `.claude/launch.json`) خودکار این رو می‌خونه — Next.js خودش `.env.local`
  رو لود می‌کنه، نیازی به وایر کردن اضافه نیست.

## mobile_owner (Kotlin Multiplatform — اندروید + iOS)

**قبل از این سشن:** بلاک `buildkonfig` توی `composeApp/build.gradle.kts`
برای هر build، روی هر پلتفرم، `BASE_URL = "https://api.noobatyar.ir"` رو
هاردکد کرده بود. تست لوکال یعنی ادیت دستی همون خط و یادت باشه قبل از کامیت
برگردونیش — راحت یادت می‌ره، و راهی نبود که هم نسخه "لوکال" هم "prod" همزمان
نصب باشن که مقایسه کنی.

**الان (فقط اندروید):** دو تا Gradle product flavor، `local` و `prod`،
هرکدوم با `BuildKonfig.BASE_URL` / `BOOKING_BASE_URL` مخصوص خودشون:

```kotlin
// composeApp/build.gradle.kts
buildkonfig {
    packageName = "xyz.sattar.javid.proqueue"
    defaultConfigs {                    // برای "prod" و برای iOS استفاده می‌شه (اونجا مفهوم flavor وجود نداره)
        buildConfigField(STRING, "BASE_URL", "https://api.noobatyar.ir")
        buildConfigField(STRING, "BOOKING_BASE_URL", "https://app.noobatyar.ir")
    }
    defaultConfigs("local") {           // اسمش با flavor اندروید "local" یکیه، ولی خودکار بهش وصل نمی‌شه — پایین رو ببین
        buildConfigField(STRING, "BASE_URL", "http://10.0.2.2:8000")
        buildConfigField(STRING, "BOOKING_BASE_URL", "http://10.0.2.2:3000")
    }
}

android {
    flavorDimensions += "env"
    productFlavors {
        create("prod") { dimension = "env" }
        create("local") {
            dimension = "env"
            applicationIdSuffix = ".local"   // کنار prod نصب می‌شه، جایگزینش نمی‌کنه
            versionNameSuffix = "-local"
        }
    }
}
```

⚠️ **تله‌ی مهم:** `defaultConfigs("local")` توی پلاگین buildkonfig هیچ ربطی به
product flavor اندروید نداره، حتی با اینکه هم‌اسمن. buildkonfig مقدارش رو از
یک پرچم کاملاً جدا و سراسری می‌گیره: property به اسم `buildkonfig.flavor`
(`-Pbuildkonfig.flavor=local` روی خط فرمان). این پرچم یک‌بار در کل اجرای
Gradle خونده می‌شه و **مستقل از این‌که کدوم AGP task/flavor رو صدا می‌زنی**
تعیین می‌کنه کدوم `BuildKonfig.kt` (تنها نسخه‌ی موجود، مشترک بین همه‌ی
targetها) تولید بشه. اگه این پرچم رو ندی، even با build کردن
`assembleLocalDebug`/`installLocalDebug`، مقدار `BASE_URL` بی‌سروصدا همون
prod (`https://api.noobatyar.ir`) می‌مونه و اپ ساکت به سرور واقعی می‌زنه —
بدون هیچ خطا یا هشداری. همیشه این پرچم رو کنار flavor اندروید بده:

```bash
# لوکال (به 10.0.2.2:8000 وصل می‌شه — نام مستعار امولاتور برای این ماشین)
./gradlew :composeApp:assembleLocalDebug -Pbuildkonfig.flavor=local
adb install -r composeApp/build/outputs/apk/local/debug/composeApp-local-universal-debug.apk
adb shell monkey -p xyz.sattar.javid.proqueue.local -c android.intent.category.LAUNCHER 1

# یا مستقیم نصب (assemble + install با هم)
./gradlew :composeApp:installLocalDebug -Pbuildkonfig.flavor=local

# Production (بدون پرچم هم کار می‌کنه چون defaultConfigs بدون اسم = prod)
./gradlew :composeApp:assembleProdDebug
adb install -r composeApp/build/outputs/apk/prod/debug/composeApp-prod-universal-debug.apk
adb shell monkey -p xyz.sattar.javid.proqueue -c android.intent.category.LAUNCHER 1
```

بعد از build، اگه شک داری کدوم `BASE_URL` واقعاً تولید شده، مستقیم چکش کن:

```bash
cat composeApp/build/buildkonfig/commonMain/xyz/sattar/javid/proqueue/BuildKonfig.kt
```

`xyz.sattar.javid.proqueue` (prod) و `xyz.sattar.javid.proqueue.local`
(local) دو applicationId متفاوتن، پس هردو می‌تونن همزمان روی یک
امولاتور/دستگاه نصب باشن بدون اینکه یکی اون یکی رو overwrite کنه.

روی یک **دستگاه واقعی** توی همون Wi-Fi (نه امولاتور)، `10.0.2.2` resolve
نمی‌شه — موقتاً `BASE_URL`/`BOOKING_BASE_URL` رو توی بلاک flavor `local` با
آی‌پی LAN همین ماشین جایگزین کن.

**iOS توی این ستاپ اصلاً مفهوم flavor نداره** — `defaultConfigs` (مقادیر
prod) برای هر build اندروید فارغ از هرچیزی اعمال می‌شه. هنوز هیچ
`.xcconfig` جدا بر اساس محیط برای `BASE_URL` وایر نشده
(`iosApp/Configuration/Config.xcconfig` فقط `TEAM_ID` / bundle id / نسخه رو
داره، نه هاست API رو). فعلاً، تست شبیه‌ساز iOS در برابر بک‌اند لوکال یعنی
ادیت موقت `defaultConfigs` توی `build.gradle.kts` به روش قدیمی (روی iOS هم
اثر می‌ذاره چون iOS همیشه همون default بدون flavor رو می‌خونه) و برگردوندنش
قبل از کامیت. اگه این یک نیاز تکراری بشه، راه‌حلش شبیه همون flavor اندروید
خواهد بود، فقط با استفاده از per-KMP-target config خود buildkonfig به‌جای
product flavor اندروید.

## اپ موبایل مشتری

وجود نداره. یک پروژهٔ KMP به اسم `mobile_client` توی ریپو بود که هیچ‌وقت به
محصول نرسید و حذف شد؛ نوبت‌گیری مشتری فقط از طریق وب `front_client` انجام
می‌شه. (اگه لازم شد، توی تاریخچهٔ گیت هست.)

## مرجع سریع: توی حالت لوکال چی به چی وصله

```
Android Emulator (mobile_owner فلیور "local")
        │  BASE_URL = http://10.0.2.2:8000
        ▼
سرور dev جنگو — 0.0.0.0:8000  (backend/.env.local)
        │  CLIENT_WEB_URL = http://10.0.2.2:3000  (زیبال بعد از checkout به اینجا ریدایرکت می‌کنه)
        ▼
سرور dev Next.js — front_client، پورت 3000  (NEXT_PUBLIC_API_URL = http://127.0.0.1:8000)
        │  fetch سمت مرورگر به بک‌اند
        ▼
سرور dev جنگو (همون instance بالا)
```

## چک‌لیست بالا آوردن یک استک کاملاً لوکال

1. مطمئن شو `backend/.env.local` وجود داره (اگه نداره، از `.env.example` کپی کن).
2. اگه لاگین خطای Argon2 `ValueError` داد:
   `cd backend && source .venv/bin/activate && pip install argon2-cffi==23.1.0 argon2-cffi-bindings==25.1.0`
3. `python manage.py migrate` بزن (سوییچ برنچ ممکنه migration عقب‌افتاده جا بذاره).
4. `python manage.py runserver 0.0.0.0:8000`
5. `npm --prefix front_client run dev` (خودکار `front_client/.env.local` رو می‌خونه).
6. اگه داری اپ اندروید رو تست می‌کنی، فلیور `local` پروژه `mobile_owner` رو
   **با پرچم `-Pbuildkonfig.flavor=local`** build/نصب کن (بالا رو ببین) —
   بدون این پرچم، ساکت به prod وصل می‌شه.
7. فقط اگه داری یک پرداخت واقعی رو تست می‌کنی: مطمئن شو `CLIENT_WEB_URL` توی `.env.local` با مرورگر کلاینتی که واقعاً checkout رو اجرا می‌کنه هم‌خونی داره (امولاتور در برابر شبیه‌ساز در برابر دستگاه LAN — جدول هاست‌نیم بالای صفحه رو ببین).
