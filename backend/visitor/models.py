from django.db import models

# Create your models here.
from django.db import models
from django.contrib.auth import get_user_model

User = get_user_model()

class Visitor(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='visitors')
    full_name = models.CharField(max_length=255)
    phone_number = models.CharField(max_length=20)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'visitor'
        ordering = ['-created_at']
        constraints = [
            models.UniqueConstraint(
                fields=['user', 'phone_number'],
                name='unique_user_phone'
            )
        ]

    def __str__(self):
        return f"{self.full_name} ({self.phone_number})"


class SmsLog(models.Model):
    STATUS_CHOICES = [
        ('SENT', 'Sent'),
        ('FAILED', 'Failed'),
    ]
    
    business = models.ForeignKey('business.Business', on_delete=models.CASCADE, related_name='sms_logs')
    visitor = models.ForeignKey(Visitor, on_delete=models.CASCADE, related_name='sms_logs')
    message_text = models.TextField()
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='SENT')
    sent_at = models.DateTimeField(auto_now_add=True)
    
    class Meta:
        db_table = 'sms_log'
        ordering = ['-sent_at']

    def __str__(self):
        return f"SMS to {self.visitor.phone_number} at {self.sent_at}"
