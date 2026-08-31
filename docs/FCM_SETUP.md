# راه‌اندازی FCM (اعلان پوش برای اپ اونر)

> این سند فقط **راه‌اندازی** FCM است. برای اینکه کل سیستم اعلان‌ها
> (پوش، پیامک پنل، پیامک دستی، آلارم محلی) چطور کنار هم کار می‌کنند،
> [NOTIFICATIONS.md](NOTIFICATIONS.md) را بخوان.

کد هر دو طرف نوشته و مرج شده. چیزی که باقی مانده کارهای کنسول است — کلیدها
داخل ریپو نیستند و نباید باشند. تا وقتی این مراحل انجام نشده، همه‌چیز سالم
build و اجرا می‌شود و فقط پوش ارسال نمی‌شود:
`push.is_configured()` روی `False` می‌ماند و اپ هیچ توکنی نمی‌گیرد.

---

## ۱. پروژه‌ی Firebase (یک‌بار)

پروژه ساخته شده و هر دو اپ اندروید ثبت شده‌اند:

| اپ فایربیس | فلیور | سرور |
|---|---|---|
| `xyz.sattar.javid.proqueue` | `prod` | `https://api.noobatyar.ir` |
| `xyz.sattar.javid.proqueue.local` | `local` | `http://10.0.2.2:8000` |

دو تا لازم است چون اندروید هر اپ را با `applicationId` می‌شناسد و فلیور لوکال
`applicationIdSuffix = ".local"` دارد
([build.gradle.kts:164](../mobile_owner/composeApp/build.gradle.kts))؛ از دید
فایربیس این یک اپ کاملاً دیگر است. یک `google-services.json` هر دو کلاینت را
با هم دارد. اگر روزی فلیور سومی اضافه شد، اپش را هم باید در کنسول ثبت کرد وگرنه
پلاگین google-services با `No matching client found for package name ...`
بیلد آن فلیور را می‌شکند.

SHA-1 لازم نیست — FCM به آن نیاز ندارد (فقط Google Sign-In دارد).

## ۲. سمت اپ: `google-services.json`

فایل را از همان صفحه دانلود کن و بگذار در:

```
mobile_owner/composeApp/google-services.json
```

فایل سر جایش هست. نکته‌ی بیلد: پلاگین `com.google.gms.google-services` در
`build.gradle.kts` ریشه با `apply false` فقط روی classpath گذاشته می‌شود، و
`composeApp` **مشروط به وجود همین فایل** اعمالش می‌کند. دلیلش این است که آن
پلاگین در نبودِ فایل کل build را می‌شکند — با این ترتیب، کلونی از ریپو که
`google-services.json` ندارد هم build می‌شود (فقط پوش نمی‌گیرد).

نسخه‌ها همان‌هایی است که کنسول گوگل داد: پلاگین `4.5.0`، BoM `34.18.0`.
از BoM فقط `firebase-messaging` برداشته شده؛ `firebase-analytics` که در
اسنیپت نمونه بود اضافه نشده چون اینجا مصرفی ندارد.

این فایل را به `.gitignore` اضافه کن یا نه — تصمیم خودت است؛ محتوایش
«راز» به‌معنای واقعی نیست (داخل APK هم هست) ولی مخصوص پروژه‌ی توست.

## ۳. سمت سرور: service account

FCM دیگر server key قدیمی (`/fcm/send`) را قبول نمی‌کند، پس احراز هویت با
service account است:

1. Firebase console → ⚙️ **Project settings** → تب **Service accounts**
   → **Generate new private key** → یک فایل JSON دانلود می‌شود.
2. روی سرور بگذارش جایی خارج از ریپو و فقط خواندنی برای یوزر جنگو:

   ```bash
   sudo mkdir -p /etc/nobatyar
   sudo mv ~/nobatyar-firebase-adminsdk-xxxxx.json /etc/nobatyar/fcm.json
   sudo chown www-data:www-data /etc/nobatyar/fcm.json
   sudo chmod 400 /etc/nobatyar/fcm.json
   ```

3. در `.env` سرور:

   ```
   FCM_CREDENTIALS_FILE=/etc/nobatyar/fcm.json
   ```

   `FCM_PROJECT_ID` لازم نیست — از داخل همان JSON خوانده می‌شود.

4. نصب وابستگی و ری‌استارت:

   ```bash
   pip install -r requirements_prod.txt && systemctl restart nobatyar
   ```

   (`google-auth` به `requirements_prod.txt` اضافه شده.)

## ۴. کرون‌جاب یادآوری

اگر هنوز ست نشده، این همان جابی است که هم پیامک مشتری و هم پوش اونر را
می‌فرستد. هر ۵ دقیقه کافی است:

```bash
*/5 * * * * cd /srv/nobatyar && /srv/nobatyar/.venv/bin/python manage.py send_appointment_reminders >> /var/log/nobatyar/reminders.log 2>&1
```

## ۵. تست

```bash
python manage.py send_appointment_reminders --dry-run
```

برای تست واقعی پوش، بعد از اینکه یک‌بار با اپ لاگین کردی (اپ خودش توکن را
ثبت می‌کند):

```bash
python manage.py shell -c "from api.services import push; print(push.send_to_user(<USER_ID>, 'تست', 'سلام'))"
```

خروجی = تعداد دستگاه‌هایی که پیام را گرفتند. اگر `0` بود:

- `python manage.py shell -c "from api.models import DeviceToken; print(DeviceToken.objects.filter(user_id=<USER_ID>).values())"`
  — اگر خالی است، اپ توکن نفرستاده (یعنی `google-services.json` سر جایش نیست
  یا اپ لاگین نکرده).
- اگر ردیف هست ولی `is_active=False` شده، FCM آن توکن را `UNREGISTERED` اعلام
  کرده؛ اپ را دوباره باز کن تا توکن تازه ثبت شود.
- لاگ جنگو دلیل دقیق خطای FCM را می‌نویسد.

## ۶. iOS

فعلاً ثبت نمی‌شود و عمداً هم همین‌طور است:
`PushTokenProvider.ios.kt` مقدار `null` برمی‌گرداند. FCM روی iOS سوار APNs
است و به یک APNs Auth Key از Apple Developer، انتیتلمنت `aps-environment` و
لینک‌شدن SDK آی‌اواس فایربیس در پروژه‌ی Xcode نیاز دارد. تا آن موقع، اپ iOS
یادآوری‌ها را از همان آلارم محلی خودش می‌گیرد.

---

## چه چیزی از کجا فرستاده می‌شود

| رویداد | گیرنده | کانال | جای کد |
|---|---|---|---|
| نوبت جدید از سمت مشتری | اونر | FCM (رایگان) | `appointment/client_views.py` → `_send_booking_sms` |
| نوبت جدید از سمت مشتری | اونر | پیامک (فقط اگر `notify_owner_by_sms` روشن باشد — از سهمیه کم می‌شود) | همان‌جا |
| یادآوری نزدیک‌شدن نوبت | اونر | FCM (رایگان، برای همه‌ی کسب‌وکارها) | `send_appointment_reminders._push_owner` |
| یادآوری نزدیک‌شدن نوبت | مشتری | پیامک (فقط `reminder_delivery=PANEL`) | `send_appointment_reminders._send_one` |
