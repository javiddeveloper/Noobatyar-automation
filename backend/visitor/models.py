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
    # Platform-wide marketing consent — distinct from Business.enable_promotional_sms,
    # which only governs whether *a given business* may send promotional SMS to its
    # own clients. This flag is the one that matters to core/segments.py: the audience
    # segment builder queries Visitor rows across every business on the platform, and
    # until this field existed nothing let a visitor opt out of *that*. Defaults to
    # False (opted in) to match today's actual behaviour — this field only makes an
    # already-existing gap opt-out-able, it does not change who could be contacted
    # before it shipped.
    marketing_opt_out = models.BooleanField(
        default=False,
        db_index=True,
        help_text="اگر فعال باشد، این مراجع در فهرست‌های بازاریابی پلتفرم (خارج از پیامک‌های "
                  "خودِ کسب‌وکار) قرار نمی‌گیرد.",
    )
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

    @property
    def is_anonymous(self):
        """The other half of the User/AnonymousUser duck type.

        BusinessSerializer.to_representation masks contact fields based on this
        (business/serializers.py), which made /api/client/appointments/ raise
        AttributeError for every signed-in visitor. A visitor holding a valid
        token is by definition not anonymous.
        """
        return False


class SmsLog(models.Model):
    STATUS_CHOICES = [
        ('SENT', 'Sent'),
        ('FAILED', 'Failed'),
        # Never attempted — the owner's SMS quota (monthly allowance + wallet)
        # was already exhausted when this message would have gone out. Logged
        # instead of just dropped so it's visible in the SMS report and
        # countable for the Home-screen quota warning.
        ('SKIPPED_QUOTA', 'Skipped (quota exhausted)'),
    ]
    
    business = models.ForeignKey('business.Business', on_delete=models.CASCADE, related_name='sms_logs')
    # Null when the recipient is the owner rather than a client (e.g. the
    # "new booking" notification). Those are billed to the owner's quota too, so
    # they belong in this log — leaving them out made the SMS report undercount
    # every booking by one and never reconcile against what was charged.
    visitor = models.ForeignKey(
        Visitor, on_delete=models.CASCADE, related_name='sms_logs',
        null=True, blank=True,
    )
    message_text = models.TextField()
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='SENT')
    error_detail = models.TextField(blank=True, null=True)
    sent_at = models.DateTimeField(auto_now_add=True)
    
    class Meta:
        db_table = 'sms_log'
        ordering = ['-sent_at']

    def __str__(self):
        recipient = self.visitor.phone_number if self.visitor_id else "owner"
        return f"SMS to {recipient} at {self.sent_at}"


class VisitorArchive(models.Model):
    """An owner hiding a visitor from their own contact list.

    This exists because deleting a Visitor row is never a safe thing for an
    owner to do. A Visitor is identified by phone_number alone and that field is
    unique platform-wide, so there is exactly *one* row per person, shared by
    every business they ever book at. Appointment.visitor and SmsLog.visitor
    both cascade, and VisitorTokenAuthentication resolves the visitor by id — so
    a delete would wipe another owner's appointments and permanently lock the
    person out of their own account.

    Archiving is per-owner (matching how the visitor list is scoped) and fully
    reversible: nothing leaves the database.
    """

    owner = models.ForeignKey(User, on_delete=models.CASCADE, related_name='archived_visitors')
    visitor = models.ForeignKey(Visitor, on_delete=models.CASCADE, related_name='archives')
    archived_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'visitor_archive'
        ordering = ['-archived_at']
        constraints = [
            models.UniqueConstraint(fields=['owner', 'visitor'], name='unique_owner_visitor_archive'),
        ]

    def __str__(self):
        return f"{self.visitor.phone_number} archived by user {self.owner_id}"


class VisitorActivity(models.Model):
    """Append-only record of things that happened to a visitor.

    Written from the mutation sites themselves (this project deliberately uses
    no signals — side effects are called inline), and surfaced to the visitor on
    their own profile page so that owner-side actions are never invisible to the
    person they affect.
    """

    ACTION_CHOICES = [
        ('APPOINTMENT_BOOKED', 'نوبت گرفته شد'),
        ('APPOINTMENT_STATUS_CHANGED', 'وضعیت نوبت تغییر کرد'),
        ('APPOINTMENT_CANCELLED', 'نوبت لغو شد'),
        ('PROFILE_UPDATED', 'اطلاعات مراجع ویرایش شد'),
        ('ARCHIVED_BY_OWNER', 'از لیست کسب‌وکار بایگانی شد'),
        ('RESTORED_BY_OWNER', 'به لیست کسب‌وکار بازگردانده شد'),
    ]

    ACTOR_VISITOR = 'VISITOR'
    ACTOR_OWNER = 'OWNER'
    ACTOR_SYSTEM = 'SYSTEM'
    ACTOR_CHOICES = [
        (ACTOR_VISITOR, 'مراجع'),
        (ACTOR_OWNER, 'کسب‌وکار'),
        (ACTOR_SYSTEM, 'سیستم'),
    ]

    visitor = models.ForeignKey(Visitor, on_delete=models.CASCADE, related_name='activities')
    business = models.ForeignKey(
        'business.Business', on_delete=models.SET_NULL, null=True, blank=True,
        related_name='visitor_activities',
    )
    # SET_NULL, not CASCADE: the log has to outlive the appointment it describes,
    # otherwise cancelling or removing a booking erases its own history.
    appointment = models.ForeignKey(
        'appointment.Appointment', on_delete=models.SET_NULL, null=True, blank=True,
        related_name='visitor_activities',
    )
    action = models.CharField(max_length=32, choices=ACTION_CHOICES)
    actor_type = models.CharField(max_length=10, choices=ACTOR_CHOICES)
    # The owner behind an ACTOR_OWNER action; null for visitor/system actions.
    actor_user = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='visitor_activities',
    )
    detail = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'visitor_activity'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['visitor', '-created_at'], name='visitor_activity_recent_idx'),
        ]

    def __str__(self):
        return f"{self.action} on visitor {self.visitor_id} at {self.created_at}"
