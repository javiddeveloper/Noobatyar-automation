from django.db import models
from django.utils import timezone
from datetime import timedelta
from api.models import User
from django.db import models
from django.contrib.auth import get_user_model

class Plan(models.Model):
    """پلن‌های اشتراک — داده‌ها از اینجا میان"""

    DURATION_UNIT = [
        ('day', 'روز'),
        ('month', 'ماه'),
    ]

    name = models.CharField(max_length=100)           # نام پلن
    price = models.PositiveIntegerField()              # قیمت اصلی به تومان
    discount_price = models.PositiveIntegerField(null=True, blank=True)  # قیمت با تخفیف
    description = models.JSONField(default=list)       # لیست توضیحات پلن
    duration_value = models.PositiveIntegerField()     # عدد مدت
    duration_unit = models.CharField(max_length=10, choices=DURATION_UNIT)
    is_vip = models.BooleanField(default=False)        # آیا VIP میشه؟
    is_active = models.BooleanField(default=True)      # نمایش داده بشه؟

    # قابلیت‌ها و سقف‌هایی که این پلن باز می‌کند (نردبان تعهد).
    # کلیدها در accounting/entitlements.py تعریف شده‌اند. مثال:
    #   {"max_businesses": 3, "monthly_appointments": -1, "online_gateway": true}
    # مقدار -1 در سقف‌ها یعنی «نامحدود». هرچه خالی بماند از مقدار پایه پر می‌شود.
    features = models.JSONField(default=dict, blank=True)

    def __str__(self):
        return f"{self.name} - {self.price:,} تومان"

    def get_end_date(self, start_date=None):
        """تاریخ پایان اشتراک رو حساب میکنه"""
        if start_date is None:
            start_date = timezone.now()

        if self.duration_unit == 'day':
            return start_date + timedelta(days=self.duration_value)
        elif self.duration_unit == 'month':
            # هر ماه = 30 روز
            return start_date + timedelta(days=self.duration_value * 30)


class Subscription(models.Model):
    """اشتراک فعال هر کاربر"""

    STATUS = [
        ('active', 'فعال'),
        ('expired', 'منقضی'),
        ('cancelled', 'لغو شده'),
    ]

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='subscriptions')
    plan = models.ForeignKey(Plan, on_delete=models.PROTECT)
    status = models.CharField(max_length=20, choices=STATUS, default='active')
    started_at = models.DateTimeField(auto_now_add=True)
    ends_at = models.DateTimeField()                   # تاریخ پایان
    # آیا پیامک یادآوری تمدید برای این اشتراک ارسال شده؟ (چرخه‌ی عمر پلن)
    reminder_sent = models.BooleanField(default=False)

    class Meta:
        ordering = ['-started_at']

    def __str__(self):
        return f"{self.user.name} → {self.plan.name}"

    def is_valid(self):
        """چک میکنه اشتراک هنوز معتبره یا نه"""
        return self.status == 'active' and self.ends_at > timezone.now()

    def days_left(self):
        """تعداد روز باقی‌مانده تا انقضا (می‌تواند منفی باشد اگر گذشته باشد)."""
        delta = self.ends_at - timezone.now()
        return delta.days

class Transaction(models.Model):
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('success', 'Success'),
        ('failed', 'Failed'),
        ('cancelled', 'Cancelled'),
    ]

    user = models.ForeignKey(get_user_model(), on_delete=models.CASCADE, related_name='transactions')
    plan = models.ForeignKey(Plan, on_delete=models.PROTECT, related_name='transactions')  # Changed from plan_id
    amount = models.PositiveIntegerField()
    track_id = models.CharField(max_length=100, unique=True, db_index=True)
    order_id = models.CharField(max_length=100, unique=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    zibal_response = models.JSONField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['track_id']),
            models.Index(fields=['order_id']),
            models.Index(fields=['status']),
        ]

    def __str__(self):
        return f"Transaction {self.order_id} - {self.status}"


class AddOnPack(models.Model):
    """
    بسته‌ی افزودنی — خرید تکی روی هر پلن، بدون ارتقای کل اشتراک.
    دو نوع دارد:
      - sms_pack         : اعتبار پیامک به کیف‌پول کاربر اضافه می‌کند (sms_amount)
      - appointment_pack : اعتبار نوبت به کیف‌پول کاربر اضافه می‌کند (appointment_amount)

    نوع «feature» (قابلیت موقت) دیگر برای فروش ساخته نمی‌شود؛ فقط برای سازگاری با
    خریدهای قدیمی باقی مانده است.
    """
    KIND_SMS = 'sms_pack'
    KIND_APPOINTMENT = 'appointment_pack'
    KIND_FEATURE = 'feature'
    KIND_CHOICES = [
        (KIND_SMS, 'بسته پیامک'),
        (KIND_APPOINTMENT, 'بسته نوبت'),
        (KIND_FEATURE, 'قابلیت موقت'),
    ]

    name = models.CharField(max_length=100)
    price = models.PositiveIntegerField(help_text="قیمت به تومان")
    kind = models.CharField(max_length=20, choices=KIND_CHOICES)
    sms_amount = models.PositiveIntegerField(default=0, help_text="تعداد پیامک برای بسته‌ی پیامکی")
    appointment_amount = models.PositiveIntegerField(default=0, help_text="تعداد نوبت برای بسته‌ی نوبت")
    feature_key = models.CharField(max_length=50, blank=True, default='', help_text="کلید قابلیت برای بسته‌ی قابلیتی")
    duration_days = models.PositiveIntegerField(default=30, help_text="مدت اعتبار بسته‌ی قابلیتی (روز)")
    is_active = models.BooleanField(default=True)

    def __str__(self):
        return f"{self.name} - {self.price:,} تومان"


class AddOnPurchase(models.Model):
    """خرید یک بسته‌ی افزودنی توسط کاربر (فلوی پرداخت مشابه اشتراک، از طریق زیبال)."""
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('success', 'Success'),
        ('failed', 'Failed'),
    ]

    user = models.ForeignKey(get_user_model(), on_delete=models.CASCADE, related_name='addon_purchases')
    pack = models.ForeignKey(AddOnPack, on_delete=models.PROTECT, related_name='purchases')
    amount = models.PositiveIntegerField()
    track_id = models.CharField(max_length=100, unique=True, db_index=True)
    order_id = models.CharField(max_length=100, unique=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    zibal_response = models.JSONField(null=True, blank=True)
    # برای بسته‌های قابلیتی: بازه‌ی اعتبار پس از پرداخت موفق
    activated_at = models.DateTimeField(null=True, blank=True)
    expires_at = models.DateTimeField(null=True, blank=True, db_index=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['track_id']),
            models.Index(fields=['status']),
            models.Index(fields=['user', 'status']),
        ]

    def __str__(self):
        return f"AddOn {self.order_id} - {self.status}"