# ════════════════════════════════════════
# 🔐 AUTHENTICATION
# ════════════════════════════════════════

# 1. ثبت‌نام (Register)
curl -X POST http://127.0.0.1:8000/api/auth/register/ \
  -H "Content-Type: application/json" \
  -d '{"phone": "09178516035", "password": "password123", "name": "جاوید"}'
# ✅ Success (201 Created):
# {
#   "status": "success", "code": 201, "message": "ثبت‌نام موفق",
#   "data": {
#     "user": {"id": 1, "phone": "09178516035", "name": "جاوید", "user_type": "normal", "is_employee": false, "joined_at": "2024-05-07T..."},
#     "tokens": {"refresh": "eyJ...", "access": "eyJ..."}
#   }
# }
# ❌ Error (400 Bad Request):
# {"status": "error", "code": 400, "message": "این شماره قبلاً ثبت شده", "data": null}

# 2. ورود (Login)
curl -X POST http://127.0.0.1:8000/api/auth/login/ \
  -H "Content-Type: application/json" \
  -d '{"phone": "09178516035", "password": "password123"}'
# ✅ Success (200 OK):
# {
#   "status": "success", "code": 200, "message": "ورود موفق",
#   "data": {
#     "user": {"id": 1, "phone": "09178516035", "name": "جاوید", ...},
#     "tokens": {"refresh": "...", "access": "..."}
#   }
# }
# ❌ Error (401 Unauthorized):
# {"status": "unauthorized", "code": 401, "message": "شماره یا رمز اشتباه است", "data": null}

# 3. رفرش توکن (Token Refresh)
curl -X POST http://127.0.0.1:8000/api/auth/token/refresh/ \
  -H "Content-Type: application/json" \
  -d '{"refresh": "<refresh_token>"}'
# ✅ Success (200 OK):
# {"access": "eyJ...", "refresh": "eyJ..."}
# ❌ Error (401 Unauthorized):
# {"detail": "Token is invalid or expired", "code": "token_not_valid"}

# 4. خروج (Logout)
curl -X POST http://127.0.0.1:8000/api/auth/logout/ \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"refresh": "<refresh_token>"}'
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "message": "خروج موفق", "data": null}
# ❌ Error (400 Bad Request):
# {"status": "error", "code": 400, "message": "توکن نامعتبر است", "data": null}

# ════════════════════════════════════════
# 🔑 FORGOT PASSWORD (OTP-based)
# ════════════════════════════════════════

# 5. ارسال کد تایید (Send OTP)
curl -X POST http://127.0.0.1:8000/api/auth/forgot-password/send/ \
  -H "Content-Type: application/json" \
  -d '{"phone": "09178516035"}'
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "message": "کد تأیید ارسال شد", "data": {"expires_in": 120}}
# ❌ Error (400 Bad Request):
# {"status": "error", "code": 400, "message": "فرمت شماره: 09XXXXXXXXX", "data": null}

# 6. تأیید کد (Verify OTP)
curl -X POST http://127.0.0.1:8000/api/auth/forgot-password/verify/ \
  -H "Content-Type: application/json" \
  -d '{"phone": "09178516035", "code": "123456"}'
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "message": "کد تأیید شد", "data": {"reset_token": "abc...123", "expires_in": 300}}
# ❌ Error (400 Bad Request):
# {"status": "error", "code": 400, "message": "کد نامعتبر یا منقضی شده است", "data": null}

# 7. تغییر رمز عبور (Reset Password)
curl -X POST http://127.0.0.1:8000/api/auth/forgot-password/reset/ \
  -H "Content-Type: application/json" \
  -d '{"phone": "09178516035", "reset_token": "abc...123", "new_password": "NewSecurePassword123"}'
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "message": "رمز عبور با موفقیت تغییر کرد", "data": null}
# ❌ Error (400 Bad Request):
# {"status": "error", "code": 400, "message": "توکن نامعتبر یا منقضی شده است", "data": null}

# ════════════════════════════════════════
# 👤 USERS MANAGEMENT
# ════════════════════════════════════════

# 8. لیست کاربران (Admin Only)
curl http://127.0.0.1:8000/api/users/ \
  -H "Authorization: Bearer <access_token>"
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "message": null, "data": [{"id": 1, "phone": "09178516035", ...}, ...]}
# ❌ Error (403 Forbidden):
# {"detail": "You do not have permission to perform this action."}

# 9. مشاهده پروفایل (Self or Admin)
curl http://127.0.0.1:8000/api/users/1/ \
  -H "Authorization: Bearer <access_token>"
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "data": {"id": 1, "phone": "09178516035", "name": "جاوید", ...}}
# ❌ Error (404 Not Found):
# {"status": "error", "code": 404, "message": "کاربر پیدا نشد", "data": null}

# 10. ویرایش پروفایل
curl -X PATCH http://127.0.0.1:8000/api/users/1/ \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "جاوید جدید"}'
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "message": "پروفایل به‌روز شد", "data": {"id": 1, "name": "جاوید جدید", ...}}

# 11. بخش VIP (نیاز به اشتراک فعال + VIP)
# توجه: این سرویس نمونه‌ای از خدمات بیزینسی است که بدون اشتراک فعال (حتی آزمایشی) قابل دسترسی نیست.
curl http://127.0.0.1:8000/api/vip/ \
  -H "Authorization: Bearer <access_token>"
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "data": {"content": "سلام جاوید، به بخش VIP خوش اومدی"}}
# ❌ Error (403 Forbidden - اشتراک تمام شده یا ندارد):
# {
#   "status": "error", 
#   "code": 403, 
#   "message": "برای استفاده از خدمات نوبت‌یار، نیاز به اشتراک فعال دارید. لطفاً نسبت به تمدید یا خرید اشتراک اقدام کنید.", 
#   "data": null
# }
# ❌ Error (403 Forbidden - کاربر VIP نیست):
# {"status": "error", "code": 403, "message": "دسترسی ندارید", "data": null}

# ════════════════════════════════════════
# 💳 ACCOUNTING & SUBSCRIPTION
# ════════════════════════════════════════

# 12. لیست پلن‌های خرید
curl http://127.0.0.1:8000/api/accounting/plans/
# ✅ Success (200 OK):
# {
#   "status": "success", "code": 200, "data": [
#     {"id": 1, "name": "آزمایشی", "price": 0, "discount_price": null, "price_display": "رایگان", "duration_display": "10 روز", "description": ["سرویس ۱۰ روزه", "پشتیبانی"], "is_vip": false},
#     {"id": 2, "name": "یک ماهه", "price": 120000, "discount_price": 99000, "price_display": "99,000 تومان", "duration_display": "1 ماه", ...}
#   ]
# }

# 13. خرید مستقیم (فقط برای تست یا پنل مدیریت)
curl -X POST http://127.0.0.1:8000/api/accounting/plans/buy/ \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"plan_id": 2}'
# ✅ Success (201 Created):
# {"status": "success", "code": 201, "message": "پلن یک ماهه با موفقیت فعال شد", "data": {"id": 5, "plan": {...}, "status": "active", "ends_at": "2024-06-07T...", "is_valid": true}}

# 14. شروع فرآیند پرداخت آنلاین (Zibal)
curl -X POST http://127.0.0.1:8000/api/accounting/plans/payment/ \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"plan_id": 2}'
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "message": "درخواست پرداخت ایجاد شد", "data": {"payment_url": "https://gateway.zibal.ir/start/123456", "track_id": 123456}}
# ❌ Error (400 Bad Request - پلن رایگان):
# {"status": "error", "code": 400, "message": "پلن‌های رایگان را نمی‌توان از طریق درگاه خریداری کرد", "data": null}

# 15. وضعیت اشتراک من
curl http://127.0.0.1:8000/api/accounting/my-subscription/ \
  -H "Authorization: Bearer <access_token>"
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "data": {"id": 5, "plan": {"name": "یک ماهه", ...}, "status": "active", "ends_at": "2024-06-07...", "is_valid": true}}
# ❌ Success (200 OK - بدون اشتراک):
# {"status": "success", "code": 200, "message": "اشتراک فعالی ندارید", "data": {"is_vip": false}}

# ════════════════════════════════════════
# 📱 VERSION MANAGEMENT
# ════════════════════════════════════════

# 16. بررسی آپدیت نسخه
curl -X POST http://127.0.0.1:8000/api/version/check/ \
  -H "Content-Type: application/json" \
  -d '{"version_code": 100}'
# ✅ Success (200 OK):
# {
#   "status": "success", "code": 200, "message": "بررسی نسخه موفق",
#   "data": {
#     "is_outdated": true,
#     "force_update": false,
#     "latest_version": {"version_name": "1.1.0", "version_code": 110, "force_update": false, "changelog": {"changes": ["بهبود عملکرد", "رفع باگ پرداخت"]}}
#   }
# }

# 17. لیست تغییرات (Changelog)
curl http://127.0.0.1:8000/api/version/changelog/
# ✅ Success (200 OK):
# {"status": "success", "code": 200, "message": "لیست تغییرات", "data": [{"version_name": "1.1.0", "version_code": 110, "changes": [...]}, ...]}

# ════════════════════════════════════════
# 📅 APPOINTMENTS (Business Features)
# ════════════════════════════════════════

# 18. لیست نوبت‌ها (نیاز به اشتراک فعال)
curl http://127.0.0.1:8000/api/appointments/list/ \
  -H "Authorization: Bearer <access_token>"
# ✅ Success (200 OK):
# {
#   "status": "success", "code": 200, "message": "لیست نوبت‌ها با موفقیت دریافت شد",
#   "data": [
#     {"id": 1, "customer_name": "علی علوی", "customer_phone": "09120000000", "service_name": "ویزیت", "appointment_date": "2024-05-10", "start_time": "10:00:00", "status": "pending", "status_display": "در انتظار"}
#   ]
# }
# ❌ Error (403 Forbidden - بدون اشتراک):
# {"status": "error", "code": 403, "message": "برای استفاده از خدمات نوبت‌یار، نیاز به اشتراک فعال دارید...", "data": null}

# ════════════════════════════════════════
# 📱 Business
# ════════════════════════════════════════

# 18. ایجاد کسب‌وکار جدید
curl -X POST http://127.0.0.1:8000/api/business/ \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>
  -d '{
  "title": "آرایشگاه زنانه پارسیان",
  "phone": "09123456789",
  "address": "تهران، خیابان انقلاب، کوچه شهید فلاحی، پلاک ۴۵، واحد ۳",
  "default_service_duration": 45,
  "work_start_hour": 9,
  "work_end_hour": 21,
  "notification_enabled": true,
  "notification_types": "SMS,WHATSAPP",
  "notification_minutes_before": 60
}'

# 19. گرفتن کسب‌وکارهای کاربر
curl -X GET  'http://127.0.0.1:8000/api/business?page=2&page_size=2' \
  -H "Authorization: Bearer <access_token>

# 20. بروزرساتی کسب‌وکار
curl -X PUT "http://127.0.0.1:8000/api/business/3/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>
  -d '{
    "title": "آرایشگاه زنانه یان",
    "phone": "09123456789",
    "address": "تهران، خیابان انقلاب، کوچه شهید فلاحی، پلاک ۴۵، واحد ۳",
    "default_service_duration": 45,
    "work_start_hour": 9,
    "work_end_hour": 21,
    "notification_enabled": true,
    "notification_types": "SMS,WHATSAPP",
    "notification_minutes_before": 60
    }'

# 21. گرفتن اظلاعات کسب‌وکار
curl -X GET "http://127.0.0.1:8000/api/business/4/" \
  -H "Authorization: Bearer <access_token>

# 22. حذف کسب‌وکار
curl -X DELETE "http://127.0.0.1:8000/api/business/4/" \
  -H "Authorization: Bearer <access_token>

# ════════════════════════════════════════
# 📱 Visitor
# ════════════════════════════════════════
# 23. ایجاد مشتری جدید
curl -X POST "http://127.0.0.1:8000/api/visitor/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>
  -d '{
"full_name": "احمد کاوه",
"phone_number": "09123456783"
  }'

# 25. دریافت لیست مشتریان
curl -X GET 'http://127.0.0.1:8000/api/visitor?page=1&page_size=20' \
  -H "Authorization: Bearer <access_token>

# 26. گرفتن اطلاعات مشتری
curl -X GET "http://127.0.0.1:8000/api/visitor/3/" \
  -H "Authorization: Bearer <access_token>

# 27. حذف مشتری
curl -X DELETE "http://127.0.0.1:8000/api/visitor/3/" \
  -H "Authorization: Bearer <access_token>

# 28. ویرایش اطلاعات مشتری
curl -X PUT "http://127.0.0.1:8000/api/visitor/3/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>
  -d '{
    "full_name": "احمدی",
    "phone_number": "09123456779"
  }'

# ════════════════════════════════════════
# 📱 Appointment
# ════════════════════════════════════════

# 29. ایجاد نوبت جدید
curl -X POST "http://127.0.0.1:8000/api/appointment/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>
  -d '{
    "business_id": 1,
    "visitor_id": 2,
    "appointment_date": 1778198400000,
    "service_duration": 60,
    "description": "قرار ملاقات مشاوره"
  }'

# 30. ویرایش نوبت
curl -X PATCH "http://127.0.0.1:8000/api/appointment/7/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <access_token>
  -d '{
    "status": "COMPLETED",
    "appointment_date": 1778198400000,
    "service_duration": 60,
    "description": "جلسه مشاوره به‌روزرسانی شده"
  }'

# 31. حذف نوبت
curl -X DELETE "http://127.0.0.1:8000/api/appointment/1/" \
  -H "Authorization: Bearer <access_token>

# 32. دریافت جزئیات نوبت
curl -X GET 'http://127.0.0.1:8000/api/appointment/7/' \
  -H "Authorization: Bearer <access_token>

# 33. دریافت آمار نوبت‌ها
curl -X GET 'http://127.0.0.1:8000/api/appointment/stats?business_id=1' \
  -H "Authorization: Bearer <access_token>

# 34. جستجوی پیشرفته نوبت‌ها
curl -X GET 'http://127.0.0.1:8000/api/appointment/query?business_id=1&status=COMPLETED&ordering=-appointment_date&page=1&page_size=20&date=1793368800' \
  -H "Authorization: Bearer <access_token>

مستندات پارامترهای جستجوی نوبت‌ها***
پارامترهای /api/appointment/query:
    business_id (عدد صحیح، الزامی): شناسه کسب‌وکار
    visitor_id (عدد صحیح، اختیاری): فیلتر بر اساس مشتری خاص
    status (رشته، اختیاری): فیلتر وضعیت (WAITING, COMPLETED, CANCELLED, NO_SHOW)
    date (timestamp Unix به ثانیه، اختیاری): نوبت‌های یک روز خاص
    date_from (timestamp Unix به ثانیه، اختیاری): از تاریخ (شامل)
    date_to (timestamp Unix به ثانیه، اختیاری): تا تاریخ (شامل)
    ordering (رشته، اختیاری، پیش‌فرض: -appointment_date): مرتب‌سازی (appointment_date, -appointment_date, created_at, -created_at)
    page (عدد صحیح، اختیاری، پیش‌فرض: 1): شماره صفحه
    page_size (عدد صحیح، اختیاری، پیش‌فرض: 10، حداکثر: 100): تعداد نتایج در هر صفحه