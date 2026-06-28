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
    STATUS_CHOICES = [
        ('WAITING', 'Waiting'),
        ('IN_PROGRESS', 'In Progress'),
        ('COMPLETED', 'Completed'),
        ('NO_SHOW', 'No Show'),
        ('CANCELLED', 'Cancelled'),
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
        max_length=20,
        choices=STATUS_CHOICES,
        default='WAITING',
        db_index=True
    )
    description = models.TextField(blank=True, null=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-appointment_date']
        indexes = [
            models.Index(fields=['business', 'appointment_date']),
            models.Index(fields=['visitor', 'appointment_date']),
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

    def save(self, *args, **kwargs):
        self.full_clean()
        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.visitor.full_name} - {self.business.title} ({self.appointment_date})"
