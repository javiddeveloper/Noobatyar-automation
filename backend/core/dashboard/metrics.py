# core/dashboard/metrics.py
"""
The aggregate queries behind the admin dashboard.

Kept out of the view so the numbers can be tested directly
(``core/tests_dashboard.py``) without building a request, and so the view stays
a thin "collect → gate on permissions → render".

Three rules this module exists to enforce:

  * **Only ``status='success'`` counts as revenue.** A ``pending`` row is a
    payment somebody started and may never finish; including it inflates every
    headline number. Pending rows appear on this dashboard only in the alerts
    panel, as something to chase.

  * **``Transaction`` and ``AddOnPurchase`` are disjoint.** Subscriptions live
    in one table, SMS/appointment packs in the other; there is no row that
    exists in both. Platform revenue is therefore their plain sum, in Toman.

  * **Appointment deposits are not revenue and are not shown here.** See
    ``appointment/payment/zibal_deposit.py``: a deposit is requested against
    ``Business.merchant_id``, so the money lands in the *owner's* Zibal account
    and never passes through the platform's. It is also never written to the
    database — there is no row anywhere to sum, which is why no GMV figure
    appears below. If deposits are ever persisted, they belong under their own
    "گردش مالی" heading and must never be added into the revenue cards.

Everything is aggregated database-side. These queries run on every admin index
load (behind a short cache), so a Python loop over a queryset, or one query per
day of the 30-day chart, is not acceptable here — conditional aggregation gives
all four windows from a single scan per table.

Day bucketing is done in ``settings.TIME_ZONE`` (Asia/Tehran), never UTC:

  * "امروز" has to begin at local midnight. Bucketing in UTC would start the
    day at 03:30 Tehran time, filing everything booked or paid between midnight
    and 03:30 under yesterday.
  * Tehran's offset is a *half* hour (+03:30), so the naive shortcut of reading
    ``.date()`` off the stored UTC value is wrong for the whole 20:30–24:00
    local window every single day. ``TruncDate`` is given an explicit ``tzinfo``
    so the shift happens in SQL, and the window boundaries below are built from
    a local calendar date rather than by subtracting hours from ``now()``.
"""

from datetime import datetime, time, timedelta
from zoneinfo import ZoneInfo

from django.conf import settings
from django.db.models import Count, Q, Sum
from django.db.models.functions import Coalesce, TruncDate
from django.utils import timezone

from accounting.models import AddOnPack, AddOnPurchase, Subscription, Transaction
from api import jalali
from api.models import User
from appointment.models import Appointment
from business.models import Business
from visitor.models import SmsLog

# ── Tunables ──────────────────────────────────────────────────────────────────
CHART_DAYS = 30
EXPIRING_WITHIN_DAYS = 7
# A Zibal payment either verifies within seconds or the user walked away from
# the bank page. An hour is far past any legitimate round trip, so anything
# still `pending` after that is abandoned or broken and needs a human.
STUCK_PAYMENT_MINUTES = 60
SMS_FAILURE_WINDOW_HOURS = 24
# How many rows each alert list shows. The count next to it is the real total.
ALERT_ROWS = 8

WINDOWS = ('today', 'week', 'month', 'all')

# The kind lives on the pack, not the purchase — a purchase is split by
# `pack__kind`.
ADDON_KINDS = (
    AddOnPack.KIND_SMS,
    AddOnPack.KIND_APPOINTMENT,
    AddOnPack.KIND_FEATURE,
)


# ── Time helpers ──────────────────────────────────────────────────────────────

def project_tz():
    return ZoneInfo(settings.TIME_ZONE)


def local_day_start(now=None, days_ago=0):
    """Midnight of the local calendar day ``days_ago`` days before ``now``.

    Built with ``datetime.combine(date, time.min, tzinfo=tz)`` rather than
    ``aware.replace(hour=0)``: replace() keeps whatever UTC offset the original
    instant had, which is the wrong offset for the target wall time whenever the
    zone's rules differ between the two (Iran dropped DST in 2022, but this must
    not quietly depend on that).
    """
    tz = project_tz()
    local = timezone.localtime(now or timezone.now(), tz)
    return datetime.combine(local.date() - timedelta(days=days_ago), time.min, tzinfo=tz)


def window_starts(now=None):
    """Lower bounds for the four reporting windows; ``None`` means unbounded.

    The windows are inclusive of today: "۷ روز" is today plus the six days
    before it, so it lines up with the right-hand end of the 30-day chart
    instead of being off by one against it.
    """
    now = now or timezone.now()
    today = local_day_start(now)
    return {
        'today': today,
        'week': today - timedelta(days=6),
        'month': today - timedelta(days=CHART_DAYS - 1),
        'all': None,
    }


def _windowed(agg, expr, field, starts, prefix='w_'):
    """One aggregate per window, so a single query answers all four.

    The alias is prefixed because ``all`` and ``today`` are poor bare names to
    hand Django as annotation aliases next to real field names.
    """
    out = {}
    for key, start in starts.items():
        if start is None:
            out[f'{prefix}{key}'] = agg(expr)
        else:
            out[f'{prefix}{key}'] = agg(expr, filter=Q(**{f'{field}__gte': start}))
    return out


def _unprefix(row, prefix='w_'):
    return {key: int(row.get(f'{prefix}{key}') or 0) for key in WINDOWS}


def _zeros():
    return {key: 0 for key in WINDOWS}


def _add(into, other):
    for key in WINDOWS:
        into[key] += other[key]


# ── Revenue ───────────────────────────────────────────────────────────────────

def revenue(starts):
    """Platform revenue in Toman, split by source, for all four windows.

    Two queries: one scan of ``Transaction``, one grouped scan of
    ``AddOnPurchase``.
    """
    subscription = _unprefix(
        Transaction.objects.filter(status='success').aggregate(
            **_windowed(Sum, 'amount', 'created_at', starts)
        )
    )

    # `.order_by()` on the values() call is load-bearing, not tidying:
    # AddOnPurchase.Meta.ordering is ['-created_at'], and Django appends any
    # ordering field to the GROUP BY of a values().annotate(). Without clearing
    # it the result is one row per (kind, timestamp) — the grouping silently
    # stops grouping.
    per_kind = {kind: _zeros() for kind in ADDON_KINDS}
    rows = (
        AddOnPurchase.objects.filter(status='success')
        .values('pack__kind')
        .order_by()
        .annotate(**_windowed(Sum, 'amount', 'created_at', starts))
    )
    for row in rows:
        per_kind.setdefault(row['pack__kind'], _zeros()).update(_unprefix(row))

    total = dict(subscription)
    for bucket in per_kind.values():
        _add(total, bucket)

    return {
        'total': total,
        'subscription': subscription,
        'sms_pack': per_kind[AddOnPack.KIND_SMS],
        'appointment_pack': per_kind[AddOnPack.KIND_APPOINTMENT],
        'feature_pack': per_kind[AddOnPack.KIND_FEATURE],
    }


# ── Volume counters ───────────────────────────────────────────────────────────

def counts(starts):
    """New users / businesses / appointments / SMS for all four windows."""
    users = _unprefix(User.objects.aggregate(
        **_windowed(Count, 'id', 'joined_at', starts)
    ))
    businesses = _unprefix(Business.objects.aggregate(
        **_windowed(Count, 'id', 'created_at', starts)
    ))
    # Appointments are counted by when they were *booked* (created_at), not by
    # when they are scheduled — this is a platform-activity metric, and
    # appointment_date is mostly in the future.
    appointments = _unprefix(Appointment.objects.aggregate(
        **_windowed(Count, 'id', 'created_at', starts)
    ))
    # SmsLog is the only historical record of SMS volume that exists. It does
    # not reconcile exactly with what was *billed*: quota is metered in Redis
    # per calendar month (accounting/usage.py) with no history at all, and a
    # send refunded after a provider failure still leaves a FAILED row here.
    # Phase 5 adds a database usage ledger — read billed usage from that when it
    # lands rather than trying to reconstruct it from this table.
    sms = _unprefix(SmsLog.objects.filter(status='SENT').aggregate(
        **_windowed(Count, 'id', 'sent_at', starts)
    ))
    return {
        'users': users,
        'businesses': businesses,
        'appointments': appointments,
        'sms': sms,
    }


# ── Daily series for the charts ───────────────────────────────────────────────

def _daily_map(queryset, field, alias_expr, start, tz):
    """``{date: value}`` grouped by local calendar day, entirely in SQL."""
    rows = (
        queryset.filter(**{f'{field}__gte': start})
        .annotate(day=TruncDate(field, tzinfo=tz))
        .values('day')
        .order_by()          # see the note in revenue(): clears Meta.ordering
        .annotate(value=alias_expr)
    )
    return {row['day']: int(row['value'] or 0) for row in rows}


def daily_series(starts, now=None):
    """Dense 30-day series for the revenue and growth charts.

    Four grouped queries in total. The densification loop below runs over the
    ≤30 rows those queries returned — it never touches the database.
    """
    tz = project_tz()
    start = starts['month']
    today = timezone.localtime(now or timezone.now(), tz).date()
    days = [today - timedelta(days=CHART_DAYS - 1 - offset) for offset in range(CHART_DAYS)]

    subs = _daily_map(
        Transaction.objects.filter(status='success'), 'created_at', Sum('amount'), start, tz
    )
    addons = _daily_map(
        AddOnPurchase.objects.filter(status='success'), 'created_at', Sum('amount'), start, tz
    )
    new_users = _daily_map(User.objects.all(), 'joined_at', Count('id'), start, tz)
    new_biz = _daily_map(Business.objects.all(), 'created_at', Count('id'), start, tz)

    labels = [_short_jalali(day) for day in days]
    return {
        'labels': labels,
        'revenue': {
            'subscription': [subs.get(day, 0) for day in days],
            'addon': [addons.get(day, 0) for day in days],
            'total': [subs.get(day, 0) + addons.get(day, 0) for day in days],
        },
        'growth': {
            'users': [new_users.get(day, 0) for day in days],
            'businesses': [new_biz.get(day, 0) for day in days],
        },
    }


def _short_jalali(day):
    """``05/18`` — month/day only; the axis has no room for the year."""
    _, jm, jd = jalali.to_jalali(day.year, day.month, day.day)
    return f'{jm:02d}/{jd:02d}'


# ── Standing totals ───────────────────────────────────────────────────────────

def standing(now=None):
    now = now or timezone.now()
    return {
        # `status='active'` alone is not enough: nothing flips the row to
        # 'expired' at the instant it lapses, so Subscription.is_valid() also
        # checks ends_at. Mirror that here or the count over-reports.
        'active_subscriptions': Subscription.objects.filter(
            status='active', ends_at__gt=now
        ).count(),
        'pending_moderation': Business.objects.filter(
            moderation_status=Business.MODERATION_PENDING
        ).count(),
    }


# ── Alerts ────────────────────────────────────────────────────────────────────

def alerts(now=None):
    """Things a human should act on today.

    Every list is capped at ALERT_ROWS with the true total alongside it, and
    every row is flattened to a plain dict — model instances would be pickled
    into the cache and served back as stale objects.
    """
    now = now or timezone.now()
    stuck_before = now - timedelta(minutes=STUCK_PAYMENT_MINUTES)
    sms_since = now - timedelta(hours=SMS_FAILURE_WINDOW_HOURS)
    expiring_before = now + timedelta(days=EXPIRING_WITHIN_DAYS)

    expiring_qs = Subscription.objects.filter(
        status='active', ends_at__gt=now, ends_at__lte=expiring_before
    ).select_related('user', 'plan').order_by('ends_at')
    expiring = [
        {
            'user': sub.user.name or sub.user.phone,
            'phone': sub.user.phone,
            'plan': sub.plan.name,
            'ends_at': jalali.format_date(sub.ends_at),
            'days_left': max(0, (sub.ends_at - now).days),
        }
        for sub in expiring_qs[:ALERT_ROWS]
    ]

    # Two tables, one problem: an abandoned or half-finished Zibal payment.
    # Nothing else in the admin surfaces these at all today.
    stuck_tx_qs = Transaction.objects.filter(
        status='pending', created_at__lt=stuck_before
    ).select_related('user', 'plan').order_by('-created_at')
    stuck_addon_qs = AddOnPurchase.objects.filter(
        status='pending', created_at__lt=stuck_before
    ).select_related('user', 'pack').order_by('-created_at')
    stuck = [
        {
            'kind': 'اشتراک',
            'label': row.plan.name,
            'user': row.user.name or row.user.phone,
            'amount': row.amount,
            'order_id': row.order_id,
            'created_at': jalali.format_datetime(row.created_at),
        }
        for row in stuck_tx_qs[:ALERT_ROWS]
    ] + [
        {
            'kind': 'بسته افزودنی',
            'label': row.pack.name,
            'user': row.user.name or row.user.phone,
            'amount': row.amount,
            'order_id': row.order_id,
            'created_at': jalali.format_datetime(row.created_at),
        }
        for row in stuck_addon_qs[:ALERT_ROWS]
    ]
    stuck.sort(key=lambda row: row['created_at'], reverse=True)
    stuck_tx_count = stuck_tx_qs.count()
    stuck_addon_count = stuck_addon_qs.count()

    sms_fail_qs = SmsLog.objects.filter(
        status='FAILED', sent_at__gte=sms_since
    ).select_related('business').order_by('-sent_at')
    sms_failures = [
        {
            'business': log.business.title,
            'error': (log.error_detail or '')[:120],
            'sent_at': jalali.format_datetime(log.sent_at),
        }
        for log in sms_fail_qs[:ALERT_ROWS]
    ]

    # Oldest first — the queue's own ordering. Businesses submitted before the
    # moderation fields existed have a null submitted_at, so fall back to
    # created_at instead of sorting them to one arbitrary end.
    queue_qs = Business.objects.filter(
        moderation_status=Business.MODERATION_PENDING
    ).select_related('user').annotate(
        waiting_since=Coalesce('moderation_submitted_at', 'created_at')
    ).order_by('waiting_since')
    queue = [
        {
            'id': biz.id,
            'title': biz.title,
            'owner': biz.user.name or biz.user.phone,
            'waiting_since': jalali.format_date(biz.waiting_since),
            'waiting_days': (now - biz.waiting_since).days,
        }
        for biz in queue_qs[:ALERT_ROWS]
    ]

    return {
        'expiring': {'count': expiring_qs.count(), 'items': expiring},
        'stuck_payments': {
            # Split by source table, not just summed, because the two halves
            # are gated by two different permissions downstream (panels.py):
            # view_transaction unlocks the subscription rows, view_addonpurchase
            # unlocks the pack rows, and a viewer can hold only one of them. A
            # single combined count/items list would force an all-or-nothing
            # gate on a panel that mixes data from both tables.
            'count': stuck_tx_count + stuck_addon_count,
            'count_transaction': stuck_tx_count,
            'count_addonpurchase': stuck_addon_count,
            'items': stuck[:ALERT_ROWS],
            'minutes': STUCK_PAYMENT_MINUTES,
        },
        'sms_failures': {
            'count': sms_fail_qs.count(),
            'items': sms_failures,
            'hours': SMS_FAILURE_WINDOW_HOURS,
        },
        'moderation_queue': {'count': queue_qs.count(), 'items': queue},
        'expiring_days': EXPIRING_WITHIN_DAYS,
    }


# ── Entry point ───────────────────────────────────────────────────────────────

def collect(now=None):
    """The complete, ungated dashboard payload.

    Deliberately computes everything regardless of who is asking: the result is
    cached once for all staff, and the permission filtering happens per-request
    at render time (see core/dashboard/panels.py). Caching a per-user subset
    instead would multiply the cache entries by the number of roles for no gain.
    """
    now = now or timezone.now()
    starts = window_starts(now)
    payload = {
        'revenue': revenue(starts),
        'counts': counts(starts),
        'charts': daily_series(starts, now),
        'alerts': alerts(now),
        'generated_at': jalali.format_datetime(now),
        'chart_days': CHART_DAYS,
    }
    payload.update(standing(now))
    return payload
