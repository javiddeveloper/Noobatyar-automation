# visitor/reports.py
"""
Query layer behind the SMS operations report
(``visitor/admin.py`` -> ``SmsLogAdmin.sms_report_view`` / ``sms_report_csv_view``).

Same split as ``accounting/reports.py`` and ``core/segments.py``: parsing,
querysets and aggregation live here so every number is unit-testable directly
against hand-built fixtures (``visitor/tests_reports.py``), and the admin view
stays a thin "parse range -> gate on permission -> render/export".

Date-range parsing, Tehran-midnight boundaries and Jalali round-tripping are
not reimplemented here. ``accounting.reports.parse_range`` /
``parse_jalali_date`` / ``local_midnight`` / ``project_tz`` are generic —
nothing about them is accounting-specific, they just happen to live in the
file the first reporting phase wrote — and ``core/segments.py`` already
imports them directly rather than duplicating the Jalali-parsing logic. This
module does the same.

── PII discipline ──────────────────────────────────────────────────────────

``SmsLog.message_text`` is the actual message body, and for the notifications
this platform sends (booking confirmations, reminders, moderation decisions —
see ``business/services.py``/``appointment`` send sites) that body routinely
contains the visitor's name and appointment details: exactly the kind of
"message content, not just metadata" that phase 4 (``core/segments.py``,
``core.export_pii``) drew a hard line around after the stuck-payments panel
leaked ``Transaction`` data across a role boundary. This report never reads
or exposes ``message_text`` — not on screen, not in the CSV, not even
truncated.

Deliberate reasoning, not a default: a troubleshooting view of *why
deliveries are failing* does not need the payload. ``error_detail`` (the
provider's own failure reason — "شماره نامعتبر", "اعتبار پیامک تمام شد" — is
provider/account-side, never the content sent to the visitor) already tells
staff what they need to act on. The failure list also does not surface the
visitor's identity (phone number / name): ``visitor.view_smslog`` is the only
permission gating this report, and unlike ``core.export_pii`` it is not a
deliberately-scarce grant in ``setup_admin_roles.py`` — a role could hold
``view_smslog`` without holding ``export_pii``, and this report must not
become a second, unguarded route to the same contact data phase 4 gated so
carefully. Business *name* is not personal data (it is already public on the
booking page) and is shown throughout.

If a future need genuinely requires reading message content from this report
(e.g. spotting a templating bug that garbles every message to one business),
that should be a deliberate, separately-gated addition on top of this module
— not something it falls into by exposing a field because it happened to be
there.

── Correctness ──────────────────────────────────────────────────────────────

Every aggregate below is one grouped query with conditional aggregation
(``Count(..., filter=Q(...))``), the same shape ``core/dashboard/metrics.py``
and ``accounting/reports.py`` use — never a query per business or per row.
"""

from django.db.models import Count, Q
from django.utils import timezone

from accounting.reports import (  # noqa: F401 -- re-exported for visitor/admin.py
    ReportRangeError,
    format_jalali_date,
    local_midnight,
    parse_jalali_date,
    parse_range,
    project_tz,
)
from api import jalali
from business.models import Business

from .models import SmsLog

RECENT_LOGS_LIMIT = 200
STATUS_CHOICES = ('SENT', 'FAILED')


# ── Report 1: overall status split / failure rate ──────────────────────────

def summary(start, end, business_id=None):
    """Status counts + failure rate over ``[start, end)``, one grouped query."""
    qs = SmsLog.objects.filter(sent_at__gte=start, sent_at__lt=end)
    if business_id:
        qs = qs.filter(business_id=business_id)

    rows = qs.values('status').order_by().annotate(count=Count('id'))
    counts = {status: 0 for status in STATUS_CHOICES}
    for row in rows:
        counts[row['status']] = row['count']

    total = sum(counts.values())
    failed = counts['FAILED']
    return {
        'counts': counts,
        'total': total,
        'sent': counts['SENT'],
        'failed': failed,
        # None (not 0) when nothing was sent at all — a report with zero
        # traffic in the window is not the same claim as "0% failure rate",
        # same reason accounting.reports.payment_conversion returns None.
        'failure_rate': (failed / total) if total else None,
    }


# ── Report 2: failures broken out by business ───────────────────────────────

def failures_by_business(start, end):
    """Which businesses are seeing the most delivery problems.

    One grouped query over the whole range (conditional aggregation shares
    the scan between the FAILED and SENT counts, same pattern as
    ``core/dashboard/metrics.revenue()``) — never one query per business.
    Sorted by failure count, worst first, since that is the order a
    troubleshooting reviewer actually cares about.
    """
    rows = (
        SmsLog.objects.filter(sent_at__gte=start, sent_at__lt=end)
        .values('business_id', 'business__title')
        .order_by()  # clears Meta.ordering before the GROUP BY
        .annotate(
            failed=Count('id', filter=Q(status='FAILED')),
            sent=Count('id', filter=Q(status='SENT')),
        )
    )
    out = []
    for row in rows:
        total = row['failed'] + row['sent']
        out.append({
            'business_id': row['business_id'],
            'business': row['business__title'],
            'failed': row['failed'],
            'sent': row['sent'],
            'total': total,
            'failure_rate': (row['failed'] / total) if total else None,
        })
    out.sort(key=lambda r: (-r['failed'], -(r['failure_rate'] or 0)))
    return out


# ── Report 3: recent log rows for troubleshooting ───────────────────────────

def recent_logs(start, end, business_id=None, status='FAILED', limit=RECENT_LOGS_LIMIT):
    """Most recent rows in ``[start, end)``, newest first.

    Defaults to ``status='FAILED'`` — that is what a troubleshooting list is
    for — but accepts ``'SENT'`` or ``''``/``None`` (both statuses) so staff
    can cross-check "did this business's SMS traffic drop off entirely, or is
    it specifically failing". Capped at ``limit``; the range/business/status
    filters are how a reviewer narrows further, not infinite scroll.

    Never selects ``message_text`` — see this module's docstring, "PII
    discipline".
    """
    qs = SmsLog.objects.filter(sent_at__gte=start, sent_at__lt=end)
    if business_id:
        qs = qs.filter(business_id=business_id)
    if status:
        qs = qs.filter(status=status)

    qs = qs.select_related('business').order_by('-sent_at')[:limit]
    return [
        {
            'business_id': log.business_id,
            'business': log.business.title,
            'status': log.status,
            'error_detail': log.error_detail or '',
            'sent_at': jalali.format_datetime(log.sent_at),
        }
        for log in qs
    ]


def business_choices():
    """Businesses with at least one SmsLog row, for the filter dropdown —
    not every business on the platform, so the picker isn't a scroll through
    hundreds of listings with no SMS history at all."""
    return list(
        Business.objects.filter(sms_logs__isnull=False)
        .distinct().order_by('title').values('id', 'title')
    )


# ── Entry point ──────────────────────────────────────────────────────────────

def build_report(date_from, date_to, business_id=None, status=None, now=None):
    """The complete report payload for the page and for CSV export.

    Raises :class:`ReportRangeError` if the range cannot be parsed — the view
    turns that into a form error rather than a 500 (same contract as
    ``accounting.reports.build_report``).
    """
    now = now or timezone.now()
    start, end, from_day, to_day = parse_range(date_from, date_to, now)

    biz_id = int(business_id) if business_id else None
    status = status if status in STATUS_CHOICES else ''

    return {
        'range': {
            'from': from_day,
            'to': to_day,
            'from_jalali': format_jalali_date(from_day),
            'to_jalali': format_jalali_date(to_day),
        },
        'business_id': biz_id,
        'status': status,
        'summary': summary(start, end, biz_id),
        'by_business': failures_by_business(start, end),
        'recent_logs': recent_logs(start, end, biz_id, status or 'FAILED'),
        'businesses': business_choices(),
        'generated_at': jalali.format_datetime(now),
    }
