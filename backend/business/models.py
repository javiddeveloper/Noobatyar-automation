from django.core.exceptions import ValidationError
from django.core.validators import RegexValidator
from django.db import models
from django.contrib.auth import get_user_model
from django.utils.crypto import get_random_string

User = get_user_model()

class Business(models.Model):
    """
    Represents a business profile for appointment management.
    Each user can have multiple businesses.
    """
    # Ordered by theme (health → beauty → professional → education/sport →
    # trades → other) rather than alphabetically: this list is rendered as a
    # flat <select> in several places, and grouping related trades together is
    # the only thing that keeps a ~35-entry dropdown scannable.
    #
    # Deliberately a flat list rather than Django's grouped-choices form
    # (`[('گروه', [(k, v), ...])]`): several consumers iterate this as
    # `for value, label in CATEGORY_CHOICES` — core/segments.py's
    # BUSINESS_CATEGORY_CHOICES, the segment builder and campaign templates,
    # ServiceCatalogItem.category — and grouped choices would hand them a list
    # where they expect a label.
    #
    # The four original keys (BEAUTY_SALON, DOCTOR, CONSULTANT, OTHER) keep
    # their exact values *and* labels. Renaming one would silently
    # re-categorize every existing business holding it, and re-labelling
    # BEAUTY_SALON as women-only would mis-describe the men's barbershops
    # already filed under it — BARBERSHOP is offered for new ones instead.
    CATEGORY_CHOICES = [
        # ── سلامت و درمان ──
        ('DOCTOR', 'پزشک و کلینیک'),
        ('DENTIST', 'دندان‌پزشکی'),
        ('PSYCHOLOGY', 'روان‌شناسی و روان‌پزشکی'),
        ('PHYSIOTHERAPY', 'فیزیوتراپی و توان‌بخشی'),
        ('NUTRITION', 'تغذیه و رژیم‌درمانی'),
        ('LABORATORY', 'آزمایشگاه و تصویربرداری'),
        ('OPTOMETRY', 'بینایی‌سنجی و عینک'),
        ('SPEECH_THERAPY', 'گفتاردرمانی'),
        ('MIDWIFERY', 'مامایی و سلامت بانوان'),
        ('VETERINARY', 'دامپزشکی'),

        # ── زیبایی و آرایش ──
        ('BEAUTY_SALON', 'آرایشگاه و سالن زیبایی'),
        ('BARBERSHOP', 'آرایشگاه مردانه'),
        ('NAIL_SALON', 'سالن ناخن'),
        ('SKIN_LASER', 'پوست، مو و لیزر'),
        ('TATTOO', 'تتو و میکروپیگمنتیشن'),
        ('MASSAGE_SPA', 'ماساژ و اسپا'),

        # ── خدمات حرفه‌ای ──
        ('CONSULTANT', 'مشاوره'),
        ('LAWYER', 'وکالت و مشاوره حقوقی'),
        ('ACCOUNTING', 'حسابداری و مالیات'),
        ('REAL_ESTATE', 'املاک و مستغلات'),
        ('INSURANCE', 'بیمه'),
        ('IMMIGRATION', 'مهاجرت و ویزا'),

        # ── آموزش و ورزش ──
        ('TUTORING', 'تدریس و کلاس خصوصی'),
        ('LANGUAGE_SCHOOL', 'آموزشگاه زبان'),
        ('MUSIC_SCHOOL', 'آموزش موسیقی'),
        ('GYM', 'باشگاه ورزشی و مربی شخصی'),
        ('YOGA_PILATES', 'یوگا و پیلاتس'),
        ('DRIVING_SCHOOL', 'آموزشگاه رانندگی'),

        # ── خدمات فنی و تعمیرات ──
        ('AUTO_SERVICE', 'تعمیرگاه و خدمات خودرو'),
        ('CAR_WASH', 'کارواش و دیتیلینگ'),
        ('HOME_SERVICE', 'خدمات و تعمیرات منزل'),
        ('DEVICE_REPAIR', 'تعمیر موبایل و لوازم برقی'),
        ('TAILORING', 'خیاطی و طراحی لباس'),

        # ── سایر ──
        ('PHOTOGRAPHY', 'عکاسی و آتلیه'),
        ('EVENT_SERVICES', 'تالار و خدمات مراسم'),
        ('PET_GROOMING', 'آرایش و نگهداری حیوانات'),
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
    # The public identifier: /b/Noobatyar-<unique_code> resolves to this row.
    # Left blank it is generated as CODE_LENGTH random characters, but the field
    # is deliberately writable from the admin so a business can be given a
    # vanity code. max_length is only the ceiling for those hand-written codes —
    # the generator always produces CODE_LENGTH characters, so every business
    # that already exists keeps the 8-character code it was created with.
    # Charset is restricted to what is safe in a URL path segment; every code
    # generated so far is a strict subset of it, so no existing row is affected.
    CODE_LENGTH = 8
    CODE_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
    CODE_MAX_LENGTH = 64

    unique_code = models.CharField(
        max_length=CODE_MAX_LENGTH,
        unique=True,
        db_index=True,
        blank=True,
        validators=[RegexValidator(
            regex=r'^[A-Za-z0-9_-]+$',
            message='کد فقط می‌تواند شامل حروف انگلیسی، رقم، خط تیره (-) و زیرخط (_) باشد.',
        )],
        help_text=(
            'کد یکتای عمومی کسب‌وکار؛ همان چیزی که در آدرس صفحهٔ رزرو می‌آید '
            '(‏/b/Noobatyar-<کد>). اگر خالی بماند یک کد ۸ کاراکتری خودکار ساخته '
            'می‌شود. برای کد دلخواه تا ۶۴ کاراکتر مجاز است. توجه: تغییر کد، '
            'لینک‌های قبلیِ همین کسب‌وکار را از کار می‌اندازد.'
        ),
    )
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
    # The notice ("پیام اضطراری") and the booking switch are deliberately
    # independent: the common case is a shop that is still taking bookings but
    # needs to warn today's clients about something ("آب قطع است، با تاخیر
    # بیایید"). Tying the notice to booking_enabled would have forced the owner
    # to close their calendar just to say a sentence.
    notice_enabled = models.BooleanField(
        default=False,
        help_text="If True, notice_message is shown to clients on the booking page"
    )
    # CharField, not TextField: this is rendered inside a fixed banner on the
    # booking page and an unbounded text field let an owner paste an essay that
    # pushed the booking button off-screen. 300 chars is enforced here and again
    # at the serializer layer so every write path (owner app, admin) is capped.
    notice_message = models.CharField(
        max_length=300,
        blank=True,
        default='',
        help_text="A short notice shown to clients on the booking page (max 300 chars)"
    )
    booking_enabled = models.BooleanField(
        default=True,
        help_text="If False, clients cannot create new appointments"
    )

    # ── Service menu ──────────────────────────────────────────────────────
    # The list of services THIS business actually offers ("لیست خدمات من"),
    # picked by the owner in the business-definition screen from the
    # category-wide ServiceCatalogItem chips (plus anything they add).
    #
    # Deliberately a JSON list of names on the business rather than a
    # many-to-many to ServiceCatalogItem: the catalog is a shared vocabulary,
    # not an inventory. What matters downstream (appointment.selected_services)
    # is the *name*, and copying it here means an owner's menu is unaffected
    # when some other business in the category renames or adds a chip.
    #
    # Read by the public booking page too: a client picking "رنگ مو" from the
    # owner's own menu is the whole point — it turns an unparseable free-text
    # note into something the owner can plan the slot length around.
    services = models.JSONField(
        default=list,
        blank=True,
        help_text="Service names this business offers, e.g. ['کوتاهی مو', 'رنگ مو']"
    )
    # Off by default: the owner's menu is what makes a client's answer usable,
    # and a free "+" for clients quietly reintroduces the free-text mess this
    # feature exists to remove. Owners who genuinely take custom requests can
    # turn it on. A name a client adds this way is stored on that appointment
    # only — it never edits the owner's menu.
    allow_client_add_service = models.BooleanField(
        default=False,
        help_text="If True, clients may add a service name that is not on the business's menu"
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
    notify_owner_by_sms = models.BooleanField(
        default=False,
        help_text=(
            "Text the owner when a client books. Off by default: the owner is "
            "meant to learn about a booking from the app's own notification, "
            "not from an SMS billed to their own quota. Left switchable for "
            "owners who explicitly want to pay for it."
        )
    )

    # ── Reminder delivery channel ─────────────────────────────────────────
    # Two very different cost models, so this is a server-side setting rather
    # than a client preference: MANUAL costs nothing (the owner app opens the
    # phone's own SMS composer and the message leaves the owner's SIM), while
    # PANEL bills every reminder to the owner's plan quota through Melipayamak.
    # PANEL is therefore gated behind FEATURE_AUTO_REMINDER_SMS
    # (accounting/permissions.py) and MANUAL is the default so that nobody is
    # silently charged. The reminder cron only ever looks at PANEL businesses;
    # MANUAL ones are driven entirely from the owner app.
    REMINDER_DELIVERY_CHOICES = [
        ('MANUAL', 'ارسال دستی از سیم‌کارت اونر'),
        ('PANEL', 'ارسال خودکار از پنل پیامکی'),
    ]
    reminder_delivery = models.CharField(
        max_length=10,
        choices=REMINDER_DELIVERY_CHOICES,
        default='MANUAL',
        help_text="How appointment reminders reach clients: owner's SIM (free) or SMS panel (paid)"
    )

    # ── Content moderation ────────────────────────────────────────────────
    # Deliberately separate from `is_locked`. That flag is *billing* state:
    # sync_locks() drives it from the owner's plan quota. This is *editorial*
    # state: whether a human reviewer has cleared the business to appear in
    # public listings. A business can be perfectly paid-up and still not
    # approved, or approved and locked for non-payment — collapsing the two
    # into one flag would let a plan renewal silently republish content a
    # moderator rejected.
    MODERATION_PENDING = 'PENDING'
    MODERATION_APPROVED = 'APPROVED'
    MODERATION_REJECTED = 'REJECTED'
    MODERATION_SUSPENDED = 'SUSPENDED'
    MODERATION_STATUS_CHOICES = [
        (MODERATION_PENDING, 'در انتظار بررسی'),
        (MODERATION_APPROVED, 'تأیید شده'),
        (MODERATION_REJECTED, 'رد شده'),
        (MODERATION_SUSPENDED, 'معلق شده'),
    ]

    moderation_status = models.CharField(
        max_length=20,
        choices=MODERATION_STATUS_CHOICES,
        default=MODERATION_PENDING,
        db_index=True,
        help_text="Editorial review state. Only APPROVED is publicly visible.",
    )
    moderation_note = models.TextField(
        blank=True,
        default='',
        help_text="Reviewer's reason for rejection/suspension. Shown to the owner.",
    )
    moderation_reviewed_by = models.ForeignKey(
        User,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='moderated_businesses',
        help_text="Staff member who last decided this business's status.",
    )
    moderation_reviewed_at = models.DateTimeField(null=True, blank=True)
    moderation_submitted_at = models.DateTimeField(
        null=True,
        blank=True,
        help_text="When this business last entered the review queue. Drives "
                  "queue ordering (oldest waiting first).",
    )
    # Set once, the first time this business is ever approved, and never
    # cleared afterwards. Distinguishes "still waiting for its first review"
    # (nothing publicly true yet — stay hidden) from "already live, an edit
    # just sent it back for re-review" (the old, approved copy is still true
    # and should keep showing — see pending_* below).
    first_approved_at = models.DateTimeField(null=True, blank=True)

    # ── Staged edits, pending re-review ─────────────────────────────────────
    # Once a business has been approved at least once, an edit to a
    # MODERATED_FIELDS column is written here instead of onto the live column,
    # so the public booking page keeps showing the last-approved copy while the
    # edit is under review — instead of the business vanishing until a
    # moderator gets to it. A moderator's APPROVED decision (moderation.py)
    # copies these onto the live fields and clears them; a REJECTED decision
    # leaves them so the owner's next edit still has something to start from.
    pending_title = models.CharField(max_length=255, null=True, blank=True)
    pending_bio = models.CharField(max_length=50, null=True, blank=True)
    pending_address = models.TextField(null=True, blank=True)
    pending_logo = models.ImageField(upload_to='business_logos/', null=True, blank=True)

    class Meta:
        db_table = 'business'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['user', '-created_at']),
            # Serves the public listing gate (is_locked + moderation_status)
            # and the moderation queue's "oldest pending first" ordering.
            models.Index(
                fields=['moderation_status', 'moderation_submitted_at'],
                name='biz_moderation_queue_idx',
            ),
        ]

    # Editing any of these changes what the public actually sees, so a change
    # after approval has to go back through review — otherwise an owner can be
    # approved with clean copy and then swap in anything they like.
    #
    # notice_message is deliberately NOT here: it's a time-sensitive, owner-
    # controlled announcement ("closed today"). Gating it on moderation took
    # the entire business offline while the notice sat in the review queue,
    # defeating the point of an emergency notice. It's still shown to
    # moderators via moderated_texts() so abuse is still visible, it just no
    # longer flips moderation_status or public visibility.
    MODERATED_FIELDS = ('title', 'bio', 'address', 'logo')

    @property
    def is_publicly_visible(self):
        """The single gate every public/client-facing query must pass through.

        Two independent reasons a business can be hidden — unpaid (`is_locked`)
        and not editorially cleared. The second one is not simply "APPROVED":
        a business that cleared review once and is now PENDING again because
        an edit staged a pending draft (see pending_* fields) is still showing
        its last-approved, live column values — those were true and cleared,
        so it stays visible. Only a business that has never once been approved
        (first_approved_at is still null) has nothing publicly true to show,
        so PENDING there means hidden. REJECTED/SUSPENDED are always hidden —
        those are real editorial/enforcement decisions, not just an edit
        waiting in the queue.
        """
        if self.is_locked:
            return False
        if self.moderation_status == self.MODERATION_APPROVED:
            return True
        return self.moderation_status == self.MODERATION_PENDING and self.first_approved_at is not None

    @staticmethod
    def public_filter():
        """Queryset equivalent of :attr:`is_publicly_visible`.

        Use as ``Business.objects.filter(Business.public_filter())`` so the
        listing and detail paths can never drift apart.
        """
        from django.db.models import Q
        return Q(is_locked=False) & (
            Q(moderation_status=Business.MODERATION_APPROVED)
            | Q(moderation_status=Business.MODERATION_PENDING, first_approved_at__isnull=False)
        )

    def clean(self):
        """Validate a hand-written `unique_code` before it can reach the table.

        `unique=True` is case-sensitive in PostgreSQL, but the public lookup is
        not (client_views searches `unique_code__iexact`), so "SalonA" and
        "salona" would both answer to the same URL. Reject that collision here
        rather than letting the admin create a pair that can never be told
        apart. Runs on the admin form (full_clean); the auto-generated path in
        save() does the same check against the same casing rule.
        """
        super().clean()
        code = (self.unique_code or '').strip()
        self.unique_code = code
        if not code:
            # Blank is legal — save() fills it in.
            return
        clash = Business.objects.filter(unique_code__iexact=code)
        if self.pk:
            clash = clash.exclude(pk=self.pk)
        if clash.exists():
            raise ValidationError({
                'unique_code': 'کسب‌وکار دیگری با همین کد (بدون در نظر گرفتن بزرگی و کوچکی حروف) وجود دارد.',
            })

    def save(self, *args, **kwargs):
        if not self.unique_code:
            # Generate a random CODE_LENGTH-character code of uppercase letters
            # and digits. Compared case-insensitively so a generated code can
            # never shadow a custom one that differs only in case.
            while True:
                code = get_random_string(
                    length=self.CODE_LENGTH, allowed_chars=self.CODE_ALPHABET,
                )
                if not Business.objects.filter(unique_code__iexact=code).exists():
                    self.unique_code = code
                    break
        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.title} ({self.user.phone})"


class BusinessModerationLog(models.Model):
    """Append-only record of every moderation decision.

    Separate from `django.contrib.admin.models.LogEntry` because that only
    covers changes made through the admin change form, and records them as an
    opaque message string. A rejection needs to be answerable months later —
    who decided, on what grounds, and what the business looked like at the time.
    Nothing here is ever updated or deleted.
    """

    business = models.ForeignKey(
        Business, on_delete=models.CASCADE, related_name='moderation_logs',
    )
    from_status = models.CharField(max_length=20, blank=True, default='')
    to_status = models.CharField(max_length=20)
    note = models.TextField(blank=True, default='')
    # SET_NULL, not CASCADE: the decision has to outlive the staff account that
    # made it, otherwise offboarding a moderator erases the audit trail.
    actor = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='moderation_actions',
    )
    # Snapshot of MODERATED_FIELDS at decision time, so the log still explains
    # itself after the owner edits the business.
    snapshot = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'business_moderation_log'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['business', '-created_at'], name='biz_mod_log_recent_idx'),
        ]

    def __str__(self):
        return f"{self.business_id}: {self.from_status or '—'} → {self.to_status}"


class BannedKeyword(models.Model):
    """A term that flags a business for closer review.

    Explicitly advisory: matching a keyword never rejects, hides, or blocks
    anything on its own. It only highlights the match in the moderation queue so
    a human looks harder. Automated rejection was ruled out deliberately —
    Persian morphology and legitimate business names produce far too many false
    positives to act on without a person in the loop.
    """

    SEVERITY_LOW = 'LOW'
    SEVERITY_HIGH = 'HIGH'
    SEVERITY_CHOICES = [
        (SEVERITY_LOW, 'کم — فقط نشانه‌گذاری'),
        (SEVERITY_HIGH, 'زیاد — بررسی فوری'),
    ]

    term = models.CharField(max_length=100, unique=True, db_index=True)
    severity = models.CharField(max_length=10, choices=SEVERITY_CHOICES, default=SEVERITY_LOW)
    note = models.CharField(max_length=255, blank=True, default='', help_text="چرا این کلمه اضافه شده")
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'banned_keyword'
        ordering = ['term']

    def __str__(self):
        return self.term


class ContentReport(models.Model):
    """A report of inappropriate content, filed against a business.

    Reporters are not required to have an account — most people who would
    notice a problem on a public booking page are visitors, not owners — so both
    `reporter_user` and `reporter_visitor` are optional and `reporter_phone`
    carries the contact for anonymous reports.
    """

    REASON_CHOICES = [
        ('INAPPROPRIATE', 'محتوای نامناسب'),
        ('MISLEADING', 'اطلاعات گمراه‌کننده'),
        ('IMPERSONATION', 'جعل هویت'),
        ('ILLEGAL', 'فعالیت غیرقانونی'),
        ('SPAM', 'اسپم'),
        ('OTHER', 'سایر'),
    ]

    STATUS_NEW = 'NEW'
    STATUS_REVIEWING = 'REVIEWING'
    STATUS_ACTIONED = 'ACTIONED'
    STATUS_DISMISSED = 'DISMISSED'
    STATUS_CHOICES = [
        (STATUS_NEW, 'جدید'),
        (STATUS_REVIEWING, 'در حال بررسی'),
        (STATUS_ACTIONED, 'اقدام شد'),
        (STATUS_DISMISSED, 'رد شد'),
    ]

    business = models.ForeignKey(
        Business, on_delete=models.CASCADE, related_name='content_reports',
    )
    reason = models.CharField(max_length=20, choices=REASON_CHOICES)
    detail = models.TextField(blank=True, default='')
    reporter_user = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='filed_reports',
    )
    reporter_visitor = models.ForeignKey(
        'visitor.Visitor', on_delete=models.SET_NULL, null=True, blank=True,
        related_name='filed_reports',
    )
    reporter_phone = models.CharField(max_length=20, blank=True, default='')
    status = models.CharField(
        max_length=20, choices=STATUS_CHOICES, default=STATUS_NEW, db_index=True,
    )
    resolution_note = models.TextField(blank=True, default='')
    resolved_by = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='resolved_reports',
    )
    resolved_at = models.DateTimeField(null=True, blank=True)
    # Set only when resolving this report was a side effect of a moderation
    # decision (suspending/rejecting the business it names) rather than some
    # other resolution — a warning email, a phone call, dismissal as
    # unfounded. SET_NULL, not CASCADE: the report should still show it *was*
    # linked to a decision even if that log row were ever removed, but nothing
    # here ever deletes a BusinessModerationLog in practice.
    resulting_moderation_log = models.ForeignKey(
        BusinessModerationLog, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='resolved_reports',
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'content_report'
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['status', '-created_at'], name='content_report_queue_idx'),
        ]

    def __str__(self):
        return f"گزارش {self.get_reason_display()} برای {self.business_id}"


class ServiceCatalogItem(models.Model):
    """
    A pickable "service received" name, scoped to a business CATEGORY rather
    than to a single Business.

    The whole point of this table is cross-business sharing within a
    category: when a hairdresser adds "رنگ ابرو" while booking a client, that
    name becomes a selectable chip for every other BEAUTY_SALON business too
    — not just the one that typed it. Owners were previously typing the same
    handful of service names into a free-text field over and over, with every
    typo and phrasing variant treated as a different service.

    Deliberately not gated/moderated: get_or_create on (category, name) is
    the whole write path (see ServiceCatalogView.post). A bad or duplicate-ish
    entry costs nothing to leave in place, and blocking on review would have
    defeated the "just add it inline while booking" UX this exists for.
    """
    category = models.CharField(
        max_length=50,
        choices=Business.CATEGORY_CHOICES,
        db_index=True,
        help_text="Shared across every business in this category"
    )
    name = models.CharField(max_length=100)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        db_table = 'business_service_catalog_item'
        ordering = ['name']
        constraints = [
            models.UniqueConstraint(
                fields=['category', 'name'],
                name='uniq_service_catalog_category_name'
            )
        ]
        indexes = [
            models.Index(fields=['category', 'name']),
        ]

    def __str__(self):
        return f"{self.name} ({self.category})"
