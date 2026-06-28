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

    class Meta:
        ordering = ['-started_at']

    def __str__(self):
        return f"{self.user.name} → {self.plan.name}"

    def is_valid(self):
        """چک میکنه اشتراک هنوز معتبره یا نه"""
        return self.status == 'active' and self.ends_at > timezone.now()

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