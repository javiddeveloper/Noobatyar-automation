from django.db import models

# Create your models here.
from django.db import models
from django.contrib.auth import get_user_model

User = get_user_model()

class Visitor(models.Model):
    # Optional: set only when an owner manually adds this person as a contact
    # (VisitorView.post). Anyone who books an appointment — through the client
    # web app or otherwise — is a Visitor first and never needs this set, since
    # a Visitor no longer requires an account of any kind to exist. A visitor
    # is identified by phone_number alone (globally, across the whole
    # platform), not scoped per-owner, so the same person booking at two
    # different businesses is still a single Visitor row.
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='visitors', null=True, blank=True)
    full_name = models.CharField(max_length=255)
    phone_number = models.CharField(max_length=20, unique=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'visitor'
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.full_name} ({self.phone_number})"

    @property
    def is_authenticated(self):
        """Duck-typed like Django's User/AnonymousUser: DRF's default
        UserRateThrottle (and anything else that treats request.user
        generically) checks this attribute, and a Visitor set as request.user
        by VisitorTokenAuthentication has no other reason to carry it."""
        return True


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
