from django.utils import timezone
from django.db import models

# Create your models here.
from django.db import models
from django.core.exceptions import ValidationError

from business.models import Business
from visitor.models import Visitor
from django.contrib.auth import get_user_model

User = get_user_model()

class Appointment(models.Model):
    # ── Status choices ────────────────────────────────────────────────────
    # LOCKED              : slot is temporarily held (15 min) while client pays
    # PENDING_VERIFICATION: client submitted payment receipt; owner must verify
    # PENDING_APPROVAL    : (legacy) client booked; owner must approve (free flow)
    # CONFIRMED           : owner approved / payment verified / gateway succeeded
    # WAITING             : confirmed and waiting for the appointment time
    # IN_PROGRESS         : currently being served
    # COMPLETED           : done
    # NO_SHOW             : client did not show up
    # CANCELLED           : rejected or manually cancelled
    STATUS_CHOICES = [
        ('LOCKED',               'قفل شده (در حال پرداخت)'),
        ('PENDING_VERIFICATION', 'در انتظار تأیید فیش'),
        ('PENDING_APPROVAL',     'در انتظار تأیید مالک'),
        ('CONFIRMED',            'تأیید شده'),
        ('WAITING',              'در صف انتظار'),
        ('IN_PROGRESS',          'در حال سرویس'),
        ('COMPLETED',            'تکمیل شده'),
        ('NO_SHOW',              'غیبت'),
        ('CANCELLED',            'لغو شده'),
    ]

    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='appointments'
    )
    business = models.ForeignKey(
        Business,
        on_delete=models.CASCADE,
        related_name='appointments',
        db_index=True
    )
    visitor = models.ForeignKey(
        Visitor,
        on_delete=models.CASCADE,
        related_name='appointments',
        db_index=True
    )
    appointment_date = models.DateTimeField(db_index=True)
    service_duration = models.PositiveIntegerField(
        null=True,
        blank=True,
        help_text="Duration in minutes. If null, uses business default."
    )
    status = models.CharField(
        max_length=25,
        choices=STATUS_CHOICES,
        default='WAITING',
        db_index=True
    )
    description = models.TextField(blank=True, null=True)

    # ── Slot-lock fields (Red Line #2) ────────────────────────────────────
    locked_at = models.DateTimeField(
        null=True,
        blank=True,
        help_text="Timestamp when this slot was locked by a client"
    )
    expires_at = models.DateTimeField(
        null=True,
        blank=True,
        db_index=True,
        help_text="Lock expiry time (locked_at + 15 min). Slot is freed after this."
    )

    # ── Payment tracking fields ───────────────────────────────────────────
    tracking_code = models.CharField(
        max_length=20,
        blank=True,
        null=True,
        unique=True,
        help_text="Auto-generated code shown to client as their booking reference"
    )
    payment_reference = models.CharField(
        max_length=100,
        blank=True,
        null=True,
        help_text="Receipt / transaction number submitted by the client for card-to-card payment"
    )
    payment_receipt = models.ImageField(
        upload_to='payment_receipts/',
        blank=True,
        null=True,
        help_text="Uploaded receipt image for card-to-card payment"
    )

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)


    class Meta:
        ordering = ['-appointment_date']
        indexes = [
            models.Index(fields=['business', 'appointment_date']),
            models.Index(fields=['visitor', 'appointment_date']),
            # Serves the hot status-filtered range scans (capacity checks,
            # slot occupancy queries, appointment listing by status).
            models.Index(fields=['business', 'status', 'appointment_date']),
        ]

    # In appointment/models.py - Appointment.clean()
    def clean(self):
        pass
        # Keep only business logic validations:
        # if self.appointment_date and self.appointment_date < timezone.now():
        #     raise ValidationError({'appointment_date': 'تاریخ قرار ملاقات نمی‌تواند در گذشته باشد'})
        #
        # if self.service_duration and self.service_duration <= 0:
        #     raise ValidationError({'service_duration': 'مدت زمان سرویس باید مثبت باشد'})

    # NOTE: save() intentionally does NOT call full_clean(). Model validation
    # runs at the serializer/view layer (create & update paths call full_clean()
    # explicitly where needed). Calling full_clean() on every save added a
    # per-write uniqueness query for `tracking_code`, which hurts write
    # throughput on the hot booking path.

    def __str__(self):
        return f"{self.visitor.full_name} - {self.business.title} ({self.appointment_date})"
