from django.db import models
from django.contrib.auth import get_user_model
from django.utils.crypto import get_random_string

User = get_user_model()

class Business(models.Model):
    """
    Represents a business profile for appointment management.
    Each user can have multiple businesses.
    """
    CATEGORY_CHOICES = [
        ('BEAUTY_SALON', 'آرایشگاه و سالن زیبایی'),
        ('DOCTOR', 'پزشک و کلینیک'),
        ('CONSULTANT', 'مشاوره'),
        ('OTHER', 'سایر'),
    ]

    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='businesses',
        help_text="Owner of this business"
    )
    title = models.CharField(max_length=255)
    category = models.CharField(max_length=50, choices=CATEGORY_CHOICES, default='OTHER', help_text="Business category")
    unique_code = models.CharField(max_length=8, unique=True, db_index=True, blank=True, help_text="Unique 8-character code")
    phone = models.CharField(max_length=20)
    address = models.TextField()
    bio = models.CharField(max_length=50, blank=True, null=True)
    logo = models.ImageField(upload_to='business_logos/', blank=True, null=True, help_text="Business logo image")
    default_service_duration = models.IntegerField(help_text="Default duration in minutes")
    work_start_hour = models.IntegerField(help_text="0-23")
    work_end_hour = models.IntegerField(help_text="0-23")
    notification_enabled = models.BooleanField(default=True)
    notification_types = models.CharField(
        max_length=100,
        default='SMS',
        help_text="Comma-separated: SMS,WHATSAPP,TELEGRAM"
    )
    notification_minutes_before = models.IntegerField(default=30)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    allow_anonymous_view = models.BooleanField(
        default=True,
        help_text="If True, guests can view contact details (phone, address, etc.)"
    )

    # ── Subscription-driven lock (graceful downgrade) ──────────────────────
    # When a subscription expires or is downgraded below the number of
    # businesses the user owns, excess businesses are locked (read-only, hidden
    # from public booking) instead of deleted. Renewing/upgrading unlocks them.
    is_locked = models.BooleanField(
        default=False,
        db_index=True,
        help_text="If True, this business is locked due to subscription limits (data kept, but hidden/read-only)."
    )

    # ── Client-facing notice & booking control ────────────────────────────
    notice_message = models.TextField(
        blank=True,
        default='',
        help_text="A short notice shown to clients on the booking page (e.g. vacation, holiday)"
    )
    booking_enabled = models.BooleanField(
        default=True,
        help_text="If False, clients cannot create new appointments"
    )

    # ── Payment configuration ─────────────────────────────────────────────
    PAYMENT_METHOD_CHOICES = [
        ('NONE',    'رایگان / بدون پیش‌پرداخت'),
        ('CARD',    'کارت به کارت'),
        ('GATEWAY', 'درگاه آنلاین (زیبال)'),
    ]
    payment_method = models.CharField(
        max_length=10,
        choices=PAYMENT_METHOD_CHOICES,
        default='NONE',
        help_text="How clients are charged when booking (Legacy)"
    )
    def _default_payment_methods():
        # Pay-at-location costs the owner nothing to offer and needs no setup
        # (no card number, no merchant id), so it is the one method every
        # business can accept from the moment it exists. An empty default here
        # left every business with zero working payment methods until the owner
        # found the advanced-settings screen and turned one on by hand —
        # checkout would offer nothing to a client at all.
        return ['CASH']

    accepted_payment_methods = models.JSONField(
        default=_default_payment_methods,
        blank=True,
        help_text="List of accepted payment methods e.g. ['ONLINE', 'CARD', 'CASH']"
    )

    # ── Advanced Capacity & Deposit Settings ───────────────────────────────
    max_appointments_per_hour = models.IntegerField(
        null=True,
        blank=True,
        help_text="Maximum concurrent appointments per hour. Null means unlimited."
    )
    
    DEPOSIT_MODE_CHOICES = [
        ('NONE', 'بدون بیعانه'),
        ('MANDATORY', 'بیعانه اجباری'),
        ('OPTIONAL', 'بیعانه اختیاری'),
    ]
    deposit_mode = models.CharField(
        max_length=20,
        choices=DEPOSIT_MODE_CHOICES,
        default='NONE',
        help_text="Deposit requirement mode"
    )
    deposit_amount = models.PositiveIntegerField(
        default=0,
        help_text="Deposit amount in Toman"
    )

    merchant_id = models.CharField(
        max_length=100,
        blank=True,
        default='',
        help_text="Zibal merchant ID — required when payment_method=GATEWAY"
    )
    payment_link = models.URLField(
        blank=True,
        default='',
        help_text="Direct payment link (e.g. zarinpal.com/pay/...)"
    )
    card_number = models.CharField(
        max_length=19,
        blank=True,
        default='',
        help_text="Owner's card number shown to clients — required when payment_method=CARD"
    )
    card_owner_name = models.CharField(
        max_length=100,
        blank=True,
        default='',
        help_text="Name on the card, displayed alongside card_number"
    )

    # ── SMS preferences ───────────────────────────────────────────────────
    enable_reminder_sms = models.BooleanField(
        default=True,
        help_text="Send appointment reminder SMS to clients"
    )
    enable_promotional_sms = models.BooleanField(
        default=False,
        help_text="Allow sending promotional/marketing SMS to clients"
    )

    class Meta:
        db_table = 'business'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['user', '-created_at']),
        ]

    def save(self, *args, **kwargs):
        if not self.unique_code:
            # Generate a random 8-character code consisting of uppercase letters and digits
            while True:
                code = get_random_string(length=8, allowed_chars='ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789')
                if not Business.objects.filter(unique_code=code).exists():
                    self.unique_code = code
                    break
        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.title} ({self.user.phone})"
