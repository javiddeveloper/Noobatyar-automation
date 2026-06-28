from django.db import models

# Create your models here.
from django.db import models
from django.contrib.auth import get_user_model

User = get_user_model()

class Business(models.Model):
    """
    Represents a business profile for appointment management.
    Each user can have multiple businesses.
    """
    user = models.ForeignKey(
        User,
        on_delete=models.CASCADE,
        related_name='businesses',
        help_text="Owner of this business"
    )
    title = models.CharField(max_length=255)
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

    class Meta:
        db_table = 'business'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['user', '-created_at']),
        ]

    def __str__(self):
        return f"{self.title} ({self.user.username})"
