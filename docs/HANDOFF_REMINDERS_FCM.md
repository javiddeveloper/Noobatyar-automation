# هندآف: یادآوری نوبت + FCM

**تاریخ:** ۲۵ اوت ۲۰۲۶ · **برنچ:** `feat/admin-dashboard` · **آخرین کامیت:** `f6375ac`

این سند برای این نوشته شده که در یک سشن دیگر — بدون هیچ حافظه‌ای از این
گفتگو — بشود کار را دقیقاً از همین‌جا ادامه داد.

- **اگر می‌خواهی بدانی سیستم اعلان‌ها چطور کار می‌کند** → [NOTIFICATIONS.md](NOTIFICATIONS.md)
- **اگر می‌خواهی FCM را روی سرور راه بیندازی** → [FCM_SETUP.md](FCM_SETUP.md)
- **اگر می‌خواهی بدانی چه شد و چه مانده** → همین سند

---

## خلاصه در سی ثانیه

سه چیز خواسته شد:

1. گزینه‌ی یادآوری در اپ اونر «سرویس را هم کال کند» تا برای مشتری پیامک برود
2. برای موبایل اونر از FCM استفاده شود
3. پیش‌فرض زمان یادآوری نیم‌ساعت شود (در اپ `0` بود)

هر سه انجام و کامیت و پوش شده. **کد کامل است.** تنها چیزی که مانده، سه دستور
روی سرور پروداکشن است (بخش «کار باقی‌مانده»).

سه باگ واقعی هم در مسیر پیدا شد که هیچ‌کدام بخشی از درخواست اولیه نبودند ولی
بدون آن‌ها فیچر روی دست کاربر می‌ترکید — مهم‌ترینشان یک باگ کانال اندروید بود
که باعث می‌شد **روی هر نصب تازه، تمام پوش‌ها بی‌صدا ناپدید شوند**.

---

## ۱. سه کامیت

```
f6375ac  docs: describe the whole notification system
50d5c77  fix(push): create the notification channel at startup, not on first use
a4eb206  feat(reminders): fix the 30-minute default, wire client SMS, add FCM push
```

جمعاً ۴۹ فایل، ۲۶۰۹ خط اضافه. درخت کاری تمیز است (به‌جز
`docs/OWNER_WEB_PLAN.md` و `mobile_client/` که از قبل untracked بودند و به این
کار ربطی ندارند — دست‌نخورده مانده‌اند).

---

## ۲. باگ اصلی: پیش‌فرض صفر

**نشانه:** در اپ اونر زمان یادآوری `0` نشان داده می‌شد.

**ریشه:** دو چیز با هم:

- `Business.notificationMinutesBefore` در مدل دامنه پیش‌فرض `= 0` داشت
- `CreateBusinessViewModel` این فیلد را اصلاً ست نمی‌کرد

پس هر کسب‌وکاری که از اپ ساخته می‌شد `notification_minutes_before=0` را به
سرور پوش می‌کرد و یادآوری‌اش سرِ ساعت خودِ نوبت شلیک می‌شد — که اصلاً یادآوری
نیست.

بدتر: سه مقدار متناقض وجود داشت — `0` در دامنه، `10` در سازنده‌ی
`PreferencesManager`، و `20` در گتر همان کلاس.

**راه‌حل:** یک ثابت واحد،
[`DEFAULT_REMINDER_MINUTES = 30`](../mobile_owner/composeApp/src/commonMain/kotlin/xyz/sattar/javid/proqueue/domain/model/business/ReminderDefaults.kt)،
هم‌تراز با پیش‌فرض سرور. همه‌ی آن سه جا حالا از همین می‌خوانند. کسب‌وکارهایی که
`0` ذخیره‌شده دارند موقع لود پیش‌فرض را نشان می‌دهند، و ویرایش کسب‌وکار دیگر
تنظیمی را که صفحه‌ی اعلان‌ها ذخیره کرده پاک نمی‌کند.

---

## ۳. صفحه‌ی اعلان‌ها حالا واقعاً به مشتری می‌رسد

**مشکل:** دو فیلدی که جاب یادآوری سرور می‌خواند — `enable_reminder_sms` و
`reminder_delivery` — از اپ اصلاً دست‌یافتنی نبودند. فقط در دیتابیس بودند، پس
هر کسب‌وکاری تا ابد روی پیش‌فرضش می‌ماند و هیچ اونری نمی‌توانست پیامک یادآوری
مشتری را روشن کند.

**راه‌حل:** `enableReminderSms` به کل زنجیره اضافه شد — دامنه، Room (نسخه‌ی
دیتابیس ۱۴ → ۱۵)، DTO، و بدنه‌ی multipart. صفحه‌ی
[اعلان‌ها](../mobile_owner/composeApp/src/commonMain/kotlin/xyz/sattar/javid/proqueue/feature/notifications/NotificationsScreen.kt)
یک کارت جدید گرفت: سوییچ «یادآوری برای مشتری» + انتخاب کانال دستی/پنل، با همان
قفل entitlement و بازگشت به `MANUAL` روی ۴۰۳ که صفحه‌ی پیام‌ها از قبل داشت.

---

## ۴. جاب یادآوری بازطراحی شد

**مشکل:** `send_appointment_reminders` فقط کسب‌وکارهای `reminder_delivery=PANEL`
را می‌دید. ولی پیش‌فرض `MANUAL` است — یعنی اونرِ حالت پیش‌فرض **هیچ‌وقت** خبردار
نمی‌شد که نوبتی نزدیک است و باید برای مشتری پیامک بفرستد.

**راه‌حل:** دو کانال کاملاً مستقل در یک جاب:

| کانال | گیرنده | شرط | مهر |
|---|---|---|---|
| پوش | اونر | هر کسب‌وکاری با `notification_enabled` | `reminder_push_sent_at` |
| پیامک | مشتری | `enable_reminder_sms` **و** `delivery=PANEL` | `reminder_sent_at` |

دو مهر جدا لازم بود چون دو کانال به دو نفر با شرط‌های متفاوت می‌روند؛ با یک مهر
مشترک ارسال موفق پیامک، پوش را خفه می‌کرد و برعکس.

توضیح کامل‌تر در [NOTIFICATIONS.md بخش ۲](NOTIFICATIONS.md).

---

## ۵. FCM از صفر

هیچ‌چیزی از FCM در پروژه وجود نداشت — نه مدل توکن دستگاه، نه کلید، نه مسیر
ارسال. کامنتی در `client_views.py` صریحاً می‌گفت «this backend cannot send
push yet». حالا می‌تواند.

**سرور:**

| فایل | نقش |
|---|---|
| `api/models.py` → `DeviceToken` | توکن دستگاه، یکتا در کل جدول |
| `api/device_views.py` | `POST /api/devices/register/` و `/unregister/` |
| `api/services/push.py` | فرستنده‌ی FCM با HTTP v1 |

احراز هویت با **service account** است، نه server key — گوگل کلیدهای قدیمی
(`/fcm/send`) را بسته. `google-auth` به `requirements_prod.txt` اضافه شد.

همه‌جا نرم شکست می‌خورد: نبودِ کلید فقط باعث می‌شود `is_configured()` مقدار
`False` بدهد و جاب بدون خطا ادامه دهد. پوش کانالِ کنارِ پیامک است، نباید
بتواند اجرای یادآوری را بترکاند.

**اپ:** `PushTokenProvider` (اندروید FCM / iOS عمداً `null`)،
`PushMessagingService`، ثبت توکن در هر استارتِ لاگین‌شده، حذف روی لاگ‌اوت.

**گریدل:** پلاگین `google-services` در `build.gradle.kts` ریشه با `apply false`
فقط روی classpath است، و `composeApp` **مشروط به وجود `google-services.json`**
اعمالش می‌کند — چون آن پلاگین در نبودِ فایل کل build را می‌شکند. با این ترتیب
کلونی که فایل را ندارد هم build می‌شود.

---

## ۶. سه باگی که در مسیر پیدا شد

### الف) کانال نوتیفیکیشن — جدی‌ترین

روی نصب تمیز، **همه‌ی پوش‌ها بی‌صدا دور ریخته می‌شدند.** FCM دویست
برمی‌گرداند، سوکت `:5228` وصل بود، `POST_NOTIFICATIONS` گرنت بود، لاگ‌کت خالی،
`dumpsys` خالی.

علت: کانال `appointment_reminders` فقط داخل `showNotification()` ساخته
می‌شد. ولی وقتی اپ در پس‌زمینه است نوتیفیکیشن را **SDK فایربیس** خودش پست
می‌کند با `default_notification_channel_id` منیفست — کد ما اجرا نمی‌شود. و
اندروید نوتیفیکیشنی که کانالش وجود ندارد را بی‌صدا دور می‌ریزد.

چرا زودتر نگرفتیمش: تست‌های اول با `adb install -r` روی نصب قدیمی بودند و
دیتای اپ کانال را از قبل داشت. اولین uninstall/install تمیز لوش داد.

حل: [`NotificationChannels`](../mobile_owner/composeApp/src/androidMain/kotlin/xyz/sattar/javid/proqueue/core/notifications/NotificationChannels.kt)
مالک شناسه است و کانال را در `ProQueueApp.onCreate` می‌سازد.

### ب) خلاصه‌ی `--dry-run` دروغ می‌گفت

به‌ازای هر نوبتِ سررسیده یک پیامک می‌شمرد، حتی برای کسب‌وکار `MANUAL` که اجرای
واقعی درست از فرستادنش امتناع می‌کند.

### ج) ویرایش کسب‌وکار تنظیمات یادآوری را پاک می‌کرد

`CreateBusinessViewModel` کل آبجکت `Business` را از فرم بازمی‌سازد، و آن فرم
فیلدی برای این دو ندارد. حالا از `uiState.business` حمل می‌شوند.

---

## ۷. چه چیزی واقعاً تست شد

### روی گوشی واقعی (Galaxy S23 FE، نصب تمیز)

```
14:27:39  POST /api/auth/login/       → 200
14:27:39  POST /api/devices/register/ → 200   ← خودکار، یک ثانیه بعد از لاگین
14:28     manage.py send_appointment_reminders
          → «0 پیامک ارسال، 1 اعلان به اونر»
          → نوتیفیکیشن روی گوشی نشست
```

آن «۰ پیامک» درست است: کسب‌وکار روی `MANUAL` بود، پس هیچ سهمیه‌ای خرج نشد ولی
اونر پوش گرفت.

تأیید نهایی از `dumpsys`:

```
tag=FCM-Notification:413526685   channel=appointment_reminders
```

پیشوند `FCM-Notification:` یعنی این را خودِ SDK پست کرده — همان مسیر پس‌زمینه
که خراب بود.

### بقیه

- **۲۸۴ تست بک‌اند** سبز، شامل ۷ تست جدید در `appointment/tests_reminders.py`
  که تفکیک دو کانال را پوشش می‌دهند
- کامپایل اندروید (`prod` و `local`) و iOS
- احراز هویت FCM با کلید واقعی مقابل سرور گوگل
- بادی دقیقی که Chucker از گوشی گرفته بود، عیناً روی کد جدید ریپلی شد → ۲۰۰

### چه چیزی تست **نشد**

- iOS (عمداً پوش ندارد — بخش ۹)
- مسیر `PANEL` روی گوشی واقعی؛ فقط با تست خودکار پوشش دارد
- هیچ‌چیز روی پروداکشن، چون هنوز دیپلوی نشده

---

## ۸. کار باقی‌مانده — فقط سرور

من به سرور دسترسی ندارم. تا این انجام نشود، `/api/devices/register/` روی
`api.noobatyar.ir` **۴۰۴** می‌دهد و هیچ پوشی فرستاده نمی‌شود. بقیه‌ی سیستم
کاملاً سالم کار می‌کند.

**۱. کلید service account روی سرور**

فایل JSON از Firebase console → پروژه‌ی `nobatyar-79c53` → ⚙️ Project settings
→ تب Service accounts → Generate new private key.

> این فایل برخلاف `google-services.json` واقعاً محرمانه است — کلید خصوصی دارد
> و نباید داخل ریپو یا APK برود.

```bash
sudo mkdir -p /etc/nobatyar
sudo mv nobatyar-79c53-firebase-adminsdk-*.json /etc/nobatyar/fcm.json
sudo chown www-data:www-data /etc/nobatyar/fcm.json
sudo chmod 400 /etc/nobatyar/fcm.json
```

**۲. متغیر محیطی**

```
FCM_CREDENTIALS_FILE=/etc/nobatyar/fcm.json
```

`FCM_PROJECT_ID` لازم نیست — از داخل خود فایل خوانده می‌شود.

**۳. دیپلوی**

```bash
cd /srv/nobatyar && git pull && pip install -r requirements_prod.txt && python manage.py migrate && systemctl restart nobatyar
```

`migrate` را جا نینداز — دو مایگریشن جدید هست:
`api.0003_devicetoken` و `appointment.0011_appointment_reminder_push_sent_at`.

**۴. کرون‌جاب** (اگر هنوز ست نیست)

```bash
*/5 * * * * cd /srv/nobatyar && /srv/nobatyar/.venv/bin/python manage.py send_appointment_reminders >> /var/log/nobatyar/reminders.log 2>&1
```

**۵. تأیید**

```bash
python manage.py shell -c "from api.services import push; print(push.is_configured())"
python manage.py send_appointment_reminders --dry-run
```

بعد اپ را باز کن (خودش توکن را ثبت می‌کند) و:

```bash
python manage.py shell -c "from api.services import push; print(push.send_to_user(<USER_ID>,'تست','سلام'))"
```

خروجی باید بزرگ‌تر از صفر باشد. اگر `0` بود → [NOTIFICATIONS.md بخش ۱۰](NOTIFICATIONS.md).

---

## ۹. تصمیم‌هایی که گرفته شد و دلیلشان

اگر در سشن بعد یکی از این‌ها عجیب به نظر رسید، عمدی بوده:

**iOS پوش ندارد.** `PushTokenProvider.ios.kt` مقدار `null` می‌دهد. FCM روی iOS
سوار APNs است و به APNs Auth Key از Apple Developer، انتیتلمنت
`aps-environment` و لینک‌شدن SDK آی‌اواس فایربیس در Xcode نیاز دارد — هیچ‌کدام
وجود ندارد. اپ iOS فعلاً فقط آلارم محلی دارد.

**`firebase-analytics` اضافه نشد** با اینکه در اسنیپت نمونه‌ی گوگل بود. مصرفی
ندارد و فقط حجم و یک SDK ردیابی اضافه می‌کند. فقط `firebase-messaging`.

**`google-services.json` کامیت شد.** ریپو private است (بدون لاگین ۴۰۴ می‌دهد) و
آن API key داخل خود APK هم می‌رود، پس راز واقعی نیست. مزیتش این است که هر
کلونی مستقیم با پوش build می‌شود. اگر نظرت عوض شد:
`git rm --cached mobile_owner/composeApp/google-services.json` و اضافه‌کردن به
`.gitignore` — بیلد نمی‌شکند چون پلاگین شرطی است.

**دو اپ در فایربیس ثبت شده،** چون فلیور `local` پسوند `.local` دارد و از دید
فایربیس یک اپ کاملاً دیگر است. اگر روزی فلیور سومی اضافه شد، اپش را هم باید
ثبت کرد وگرنه `No matching client found for package name ...`.

**پیامک اونر موقع رزرو، پیش‌فرض خاموش ماند** (`notify_owner_by_sms`). حالا که
پوش هست، اونر نباید از سهمیه‌ی خودش پول بدهد تا چیزی را بشنود که اپش نشان
می‌دهد.

**یک کامیت برای فیچر، نه سه‌تا.** تغییرات در `KoinModule.kt` و در جاب یادآوری
بین هر سه بخش درهم بود؛ جداکردنشان کامیت‌های نیمه‌کاره می‌ساخت.

---

## ۱۰. تله‌ای که وقت می‌گیرد: فلیور `local`

اگر خواستی دوباره با بک‌اند لوکال تست کنی، این را بخوان وگرنه یک ساعت سرگردان
می‌شوی. در [ENVIRONMENTS.md](ENVIRONMENTS.md) هم مستند است:

`defaultConfigs("local")` در buildkonfig **هیچ ربطی** به product flavor اندروید
ندارد، با اینکه هم‌اسم‌اند. باید پرچم جدا بدهی:

```bash
./gradlew :composeApp:assembleLocalDebug -Pbuildkonfig.flavor=local
```

بدون این پرچم، حتی با build کردن `assembleLocalDebug`، مقدار `BASE_URL`
بی‌سروصدا همان prod می‌ماند و اپ به سرور واقعی می‌زند. برای اطمینان:

```bash
cat mobile_owner/composeApp/build/buildkonfig/commonMain/xyz/sattar/javid/proqueue/BuildKonfig.kt
```

### دستور کامل تست روی گوشی واقعی

روی **دستگاه فیزیکی** آدرس `10.0.2.2` (نام مستعار امولاتور) resolve نمی‌شود، و
اگر گوشی روی دیتای موبایل باشد IP لن هم به دردت نمی‌خورد. راه‌حلی که جواب داد:
تونل USB.

۱. موقتاً در `composeApp/build.gradle.kts` بلوک `defaultConfigs("local")` را به
   `http://127.0.0.1:8000` تغییر بده
۲. موقتاً `127.0.0.1` را به `network_security_config.xml` اضافه کن (فقط
   `10.0.2.2` مجاز است و بدون این، اندروید cleartext را بلاک می‌کند)
۳. `adb reverse tcp:8000 tcp:8000`
۴. `manage.py runserver 0.0.0.0:8000` با `FCM_CREDENTIALS_FILE` ست‌شده
۵. build با پرچم بالا، نصب، `adb shell pm grant <pkg> android.permission.POST_NOTIFICATIONS`
۶. **هر دو تغییر موقت را برگردان** (هر دو در این سشن برگردانده شدند)

برای لاگین روی بک‌اند لوکال، کاربر را مستقیم بساز — نیازی به OTP نیست:

```python
u = User.objects.create_user(phone='09178516035', password='Test@12345',
                             name='جاوید', role='BUSINESS_OWNER')
```

> در دیتابیس لوکال (`backend/db.sqlite3`) همین حالا کاربر `09178516035` با رمز
> `Test@12345` و کسب‌وکار «آرایشگاه جاوید» (id=69) ساخته شده، به‌علاوه‌ی
> `09121112233` با همان رمز. اینها فقط دیتای تست لوکال‌اند.

---

## ۱۱. وضعیت محیط در پایان این سشن

- سرور جنگوی لوکال ممکن است هنوز در حال اجرا باشد (`runserver 0.0.0.0:8000`)
- `adb reverse` برداشته شد
- کپی کلید service account از پوشه‌ی موقت پاک شد؛ اصلش روی Desktop است
- اپ فلیور `local` روی گوشی نصب مانده و به `127.0.0.1:8000` اشاره می‌کند —
  حالا که تونل قطع است به جایی وصل نمی‌شود.
  پاک‌کردنش: `adb uninstall xyz.sattar.javid.proqueue.local`
- تغییرات موقتِ تست (URL فلیور لوکال و `network_security_config.xml`)
  برگردانده شده‌اند؛ `git status` تمیز است

---

## ۱۲. اگر خواستی ادامه بدهی

کارهای طبیعی بعدی، به ترتیب ارزش:

1. **دیپلوی روی پروداکشن** (بخش ۸) — تا این نشود، هیچ‌کدام از این‌ها به دست
   کاربر نمی‌رسد
2. **تپ روی نوتیفیکیشن به جای درست ببرد.** الان `data` شناسه‌ها را حمل می‌کند
   (`appointment_id`, `business_id`, `visitor_id`) و
   `PushMessagingService` آن‌ها را در اکسترای اینتنت می‌گذارد، ولی ناوبری
   داخل اپ هنوز آن‌ها را نمی‌خواند
3. **`onNewToken` وقتی کاربر لاگین نیست هم ثبت می‌زند** و ۴۰۱ می‌گیرد. بی‌ضرر
   است (نرم شکست می‌خورد) ولی نویز است — می‌شود در `SyncPushTokenUseCase`
   اول وجود توکن احراز هویت را چک کرد
4. **iOS** (بخش ۹) — کار مستقل و بزرگ‌تر
5. **تست `PANEL` روی دستگاه واقعی** — الان فقط تست خودکار داردش
