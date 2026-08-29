# core/models.py
"""
Models owned directly by the `core` app: artefacts of the admin panel itself
(saved audience segments, the PII-export audit trail) rather than domain
models — those belong to `visitor`, `business`, `accounting`, etc.

`core` was installed only for management commands until this phase (see
core/apps.py's CoreConfig docstring) and had no models.py or migrations. This
is its first model, hence the first migration under core/migrations/.
"""

from django.conf import settings
from django.db import models


class AudienceSegment(models.Model):
    """A saved, named audience filter for the segment builder (core/segments.py).

    This is a **saved query, not a snapshot**. `definition` is the JSON filter
    spec core/segments.py knows how to evaluate; re-running a saved segment
    (see NobatyarAdminSite.segment_run_view) re-executes every filter against
    current data. A visitor who stops booking after the segment is saved
    silently drops out of a "hasn't booked in N days" segment the next time
    someone opens it, and a low-wallet owner filter drifts the moment the
    owner buys an SMS pack — see core/segments.py's module docstring for why
    this can't be a snapshot (the wallet balance it can read from lives only
    in Redis, with no history at all). Every template that renders a saved
    segment repeats this so nobody mistakes "saved" for "frozen".
    """

    KIND_VISITOR = 'visitor'
    KIND_OWNER = 'owner'
    KIND_CHOICES = [
        (KIND_VISITOR, 'مراجع'),
        (KIND_OWNER, 'صاحب کسب‌وکار'),
    ]

    name = models.CharField(max_length=200, verbose_name='نام')
    kind = models.CharField(max_length=10, choices=KIND_CHOICES, verbose_name='نوع مخاطب')
    # The filter spec core.segments.build_queryset()/count_segment() consume.
    # Shape documented in core/segments.py; not modelled as real columns
    # because the filter set is still evolving and a JSON blob lets this
    # model stay stable while core/segments.py grows new filter keys.
    definition = models.JSONField(default=dict, blank=True, verbose_name='فیلترها')
    created_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='audience_segments', verbose_name='ساخته‌شده توسط',
    )
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='تاریخ ساخت')
    last_run_at = models.DateTimeField(null=True, blank=True, verbose_name='آخرین اجرا')

    class Meta:
        db_table = 'core_audience_segment'
        ordering = ['-created_at']
        verbose_name = 'گروه مخاطب'
        verbose_name_plural = 'گروه‌های مخاطب'
        permissions = [
            # No existing role (Superadmin/Moderator/Support/Finance — see
            # core/management/commands/setup_admin_roles.py) is the right fit
            # for "may export a phone-number list for marketing": Support is
            # read-only on Visitor for support lookups, not bulk export;
            # Finance never touches user/visitor data at all; Moderator is
            # content-only. Rather than quietly overload one of those, this
            # is a standalone permission that starts granted to nobody but
            # Superadmin (which holds every permission that exists) until a
            # human decides who else should get it — see
            # NobatyarAdminSite.segment_export_view()'s comment for the gate
            # itself, and the phase report for why this was left unresolved
            # rather than picked for the requester.
            ('export_pii', 'می‌تواند خروجی شماره‌تلفن/فهرست مخاطب بگیرد'),
        ]

    def __str__(self):
        return self.name


class AudienceSegmentExport(models.Model):
    """Audit trail for every PII export the segment builder produces.

    Written unconditionally by NobatyarAdminSite.segment_export_view() before
    the CSV response is returned — a phone-number list is the single most
    sensitive artefact this admin panel can produce, and if one leaks this
    table is the only way to know who pulled it, on what filter, how many
    rows, and when. Deliberately its own queryable model rather than a log
    line: "queryable later" is the whole point of an audit trail.

    `definition` is copied from the segment (or the ad-hoc filter, if the
    export was run without saving first) at export time rather than only
    referenced via `segment`, so the record stays fully self-explaining even
    after a saved segment's filters are edited or the segment itself is
    deleted (hence SET_NULL, not CASCADE — the audit row must outlive the
    segment).
    """

    segment = models.ForeignKey(
        AudienceSegment, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='exports', verbose_name='گروه مخاطب',
    )
    kind = models.CharField(max_length=10, choices=AudienceSegment.KIND_CHOICES, verbose_name='نوع مخاطب')
    definition = models.JSONField(default=dict, blank=True, verbose_name='فیلترهای اجراشده')
    exported_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='audience_segment_exports', verbose_name='گرفته‌شده توسط',
    )
    row_count = models.PositiveIntegerField(verbose_name='تعداد ردیف')
    created_at = models.DateTimeField(auto_now_add=True, verbose_name='زمان خروجی')

    class Meta:
        db_table = 'core_audience_segment_export'
        ordering = ['-created_at']
        verbose_name = 'خروجی گروه مخاطب'
        verbose_name_plural = 'خروجی‌های گروه مخاطب'

    def __str__(self):
        who = self.exported_by.phone if self.exported_by_id else '—'
        return f'{self.get_kind_display()} — {self.row_count} ردیف — {who}'


class AdminMessageLog(models.Model):
    """
    Audit trail for a platform-initiated message to a business owner (renewal
    reminders, general ops notices — NobatyarAdminSite.business_message_view).

    Deliberately its own table, not a row in visitor.SmsLog: that log is the
    owner's *own paid* SMS ledger (business/sms_views.py bills it to their
    monthly quota/wallet), and mixing in messages the owner never paid for —
    initiated by staff, funded by the platform — would make their own usage
    report lie about what they were actually charged for. See
    core/messaging.py's module docstring for the funding-source reasoning.

    One row per (business, channel) attempt, not per compose action, so a
    message sent as both push and SMS shows two independently-succeeded/
    failed rows rather than one row whose status is ambiguous about which
    channel actually got through.
    """

    CHANNEL_CHOICES = [('PUSH', 'اعلان'), ('SMS', 'پیامک')]
    STATUS_CHOICES = [('SENT', 'ارسال‌شده'), ('FAILED', 'ناموفق')]

    business = models.ForeignKey(
        'business.Business', on_delete=models.CASCADE,
        related_name='admin_message_logs', verbose_name='کسب‌وکار',
    )
    sent_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='sent_admin_messages', verbose_name='ارسال‌شده توسط',
    )
    channel = models.CharField(max_length=10, choices=CHANNEL_CHOICES, verbose_name='کانال')
    title = models.CharField(max_length=255, blank=True, verbose_name='عنوان')
    body = models.TextField(verbose_name='متن')
    status = models.CharField(max_length=10, choices=STATUS_CHOICES, verbose_name='وضعیت')
    error_detail = models.TextField(blank=True, verbose_name='جزئیات خطا')
    sent_at = models.DateTimeField(auto_now_add=True, verbose_name='زمان ارسال')

    class Meta:
        db_table = 'core_admin_message_log'
        ordering = ['-sent_at']
        verbose_name = 'پیام ادمین به کسب‌وکار'
        verbose_name_plural = 'پیام‌های ادمین به کسب‌وکارها'
        permissions = [
            # Same reasoning as AudienceSegment.export_pii above: a standalone
            # permission starting granted to nobody but Superadmin, since real
            # money (platform SMS budget) and a real notification reach a real
            # owner — not something to fold into business.change_business.
            ('send_business_message', 'می‌تواند به کسب‌وکارها پیام/اعلان بفرستد'),
        ]

    def __str__(self):
        return f'{self.get_channel_display()} به {self.business_id} — {self.get_status_display()}'


class MarketingPushLog(models.Model):
    """
    Audit trail for one promotional push campaign sent to a visitor segment
    (NobatyarAdminSite.segment_notify_view).

    One row per *campaign*, not per recipient — unlike AdminMessageLog (one
    business per send) or PushLog (one appointment reminder per visitor),
    a marketing blast can reach thousands of visitors, and a queryable
    per-recipient row for each would turn one click into a write storm this
    audit trail has no actual use for. `definition` is the filter snapshot
    (same JSON shape core.segments.raw_params produces) so the campaign
    stays self-explaining even after the underlying segment/filters change.
    """

    definition = models.JSONField(default=dict, blank=True, verbose_name='فیلترهای اجراشده')
    title = models.CharField(max_length=255, blank=True, verbose_name='عنوان')
    body = models.TextField(verbose_name='متن')
    recipient_count = models.PositiveIntegerField(verbose_name='تعداد مخاطب واجد شرایط')
    delivered_count = models.PositiveIntegerField(verbose_name='تعداد تحویل‌شده')
    sent_by = models.ForeignKey(
        settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='sent_marketing_pushes', verbose_name='ارسال‌شده توسط',
    )
    sent_at = models.DateTimeField(auto_now_add=True, verbose_name='زمان ارسال')

    class Meta:
        db_table = 'core_marketing_push_log'
        ordering = ['-sent_at']
        verbose_name = 'کمپین پوش تبلیغاتی'
        verbose_name_plural = 'کمپین‌های پوش تبلیغاتی'
        permissions = [
            # Same standalone-permission reasoning as AudienceSegment.export_pii
            # and AdminMessageLog.send_business_message — a real notification
            # reaching potentially thousands of real customers is not something
            # to fold into visitor.view_visitor (which Support already holds
            # for one-at-a-time lookups, per setup_admin_roles.py).
            ('send_marketing_push', 'می‌تواند پوش تبلیغاتی برای گروه مخاطب ارسال کند'),
        ]

    def __str__(self):
        return f'{self.title or self.body[:30]} — {self.delivered_count}/{self.recipient_count}'
