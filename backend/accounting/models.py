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


class CreditLedger(models.Model):
    """
    Append-only audit trail underneath ``accounting/usage.py``'s Redis-backed
    wallets/counters.

    Redis (via ``usage.py``) remains the *fast path* that actually gates
    booking/SMS behaviour — this table never gates anything, matching the
    fail-open philosophy of the system it backs (see ``usage.py``'s module
    docstring). What this table adds is history: Redis has no memory of how a
    balance got to its current value, and a no-TTL wallet key that is bought
    with real money has no way to be reconstructed if it is ever lost (a
    botched migration, a ``FLUSHDB``, a misconfigured replica promotion). Every
    row here is one state-changing event against one of the four buckets
    ``usage.py`` tracks, so ``rebuild_wallets_from_ledger`` can replay them
    back into Redis and an operator can answer "how did this balance get to
    zero" without guessing.

    ``metric`` covers both monthly counters (which reset every calendar month
    in Redis, but are never rewritten in the ledger — the ledger is the one
    place a past month's activity is still visible) and the two persistent,
    no-TTL wallets bought via add-on packs.

    ``delta`` / ``balance_after`` convention — read carefully, it is *not*
    "positive always means the user gained credit":

      * For the two wallet metrics (``sms_wallet`` / ``appointment_wallet``),
        the Redis key IS the balance (credit remaining), so delta and
        balance_after behave exactly as intuition suggests: positive delta =
        credit granted, negative = credit spent, balance_after = wallet
        balance after the event.

      * For the two monthly metrics (``sms_monthly`` / ``appointment_monthly``),
        the underlying Redis key is a *consumed-this-month counter*, not a
        remaining balance (see ``usage.py``'s ``get_usage``/``add_usage``). To
        keep ``balance_after`` a literal, trustworthy mirror of "what belongs
        in that Redis key right now" — which is exactly what lets
        ``rebuild_wallets_from_ledger`` restore Redis by writing the most
        recent row's ``balance_after`` straight back, with no arithmetic and
        no risk of silently drifting from cache reality — ``delta`` for these
        two metrics mirrors the counter's own movement: a booking/SMS send
        (consumption) increments the counter, so it is recorded as a
        *positive* delta; a cancellation/refund decrements it, so it is a
        *negative* delta. This is the one place "positive/negative" flips
        from the intuitive credit-granted/credit-spent reading — documented
        here so it is never mistaken for a bug.

    ``ref_type`` / ``ref_id`` are a plain, ungated pointer to whatever caused
    the event (an ``AddOnPurchase`` id, an ``Appointment`` id, a ``SmsLog`` id,
    an admin username for a manual grant) — not a real ``GenericForeignKey``,
    since nothing here ever needs to traverse back into the referenced object,
    only display "what caused this" next to the row.
    """

    METRIC_SMS_MONTHLY = 'sms_monthly'
    METRIC_SMS_WALLET = 'sms_wallet'
    METRIC_APPOINTMENT_MONTHLY = 'appointment_monthly'
    METRIC_APPOINTMENT_WALLET = 'appointment_wallet'
    METRIC_CHOICES = [
        (METRIC_SMS_MONTHLY, 'پیامک — سهمیه ماهانه'),
        (METRIC_SMS_WALLET, 'پیامک — کیف‌پول'),
        (METRIC_APPOINTMENT_MONTHLY, 'نوبت — سهمیه ماهانه'),
        (METRIC_APPOINTMENT_WALLET, 'نوبت — کیف‌پول'),
    ]

    user = models.ForeignKey(get_user_model(), on_delete=models.CASCADE, related_name='credit_ledger_entries')
    metric = models.CharField(max_length=20, choices=METRIC_CHOICES)
    delta = models.IntegerField(help_text="تغییر (می‌تواند منفی باشد) — به کنوانسیون در docstring مدل توجه کنید")
    balance_after = models.IntegerField(help_text="مقدار دقیقی که باید بلافاصله پس از این رویداد در Redis باشد")
    reason = models.CharField(max_length=50, help_text="کد ماشین‌خوان، مثل booking / sms_send / addon_purchase")
    ref_type = models.CharField(max_length=50, blank=True, default='')
    ref_id = models.IntegerField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['user', 'metric', 'created_at']),
            models.Index(fields=['user', 'created_at']),
        ]

    def __str__(self):
        return f"{self.user_id} {self.metric} {self.delta:+d} ({self.reason})"