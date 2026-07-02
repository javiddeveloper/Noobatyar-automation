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
