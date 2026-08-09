# accounting/reports.py
"""
Query layer behind the admin financial reporting page
(``accounting/admin.py`` → ``TransactionAdmin.financial_report_view``).

Kept out of the view for the same reason ``core/dashboard/metrics.py`` is kept
out of its view: every number here can be unit tested directly against
hand-computed totals (``accounting/tests_reports.py``) without building a
request, and the view stays a thin "parse range → gate on permissions →
render/export".

What counts as revenue is identical to the dashboard's rule — imported from
there rather than restated, so the two surfaces can never quietly disagree on
what a "successful payment" is:

  * Only ``status='success'`` rows are revenue, anywhere. ``pending`` /
    ``failed`` / ``cancelled`` appear only in :func:`payment_conversion`.
  * ``Transaction`` (subscriptions) and ``AddOnPurchase`` (sms / appointment /
    feature packs) are disjoint tables; total revenue is their plain sum.
  * Appointment deposits are never included. See ``DEPOSIT_NOTE`` below: a
    deposit is requested against ``Business.merchant_id`` so the money lands
    in the *owner's* Zibal account, and while an appointment's status/
    ``payment_reference`` do reflect that a deposit was verified, the actual
    amount paid is never persisted anywhere — there is no exact figure to sum.

The dashboard's ``revenue()`` works over four fixed lookback windows anchored
on "now". This report works over one arbitrary ``[start, end)`` range chosen
by staff, so the aggregation is its own function here rather than a call into
``metrics.revenue()`` — but every query still follows the same shape it does
there: one grouped scan with ``TruncDate`` + conditional aggregation, explicit
Tehran ``tzinfo``, never a query per day or per row.
"""

from datetime import date, datetime, time, timedelta

from django.db.models import Count, Sum
from django.db.models.functions import TruncDate
from django.utils import timezone

from accounting.management.commands.run_subscription_lifecycle import (
    GRACE_DAYS as RENEWAL_GRACE_DAYS,
)
from accounting.models import AddOnPack, AddOnPurchase, Subscription, Transaction
from api import jalali
from core.dashboard.metrics import ADDON_KINDS, project_tz

DEFAULT_RANGE_DAYS = 30

# Precisely what is and isn't available, since overstating the gap is its own
# kind of wrong: appointment.payment_reference does get written with the
# Zibal track_id when a deposit is requested (appointment/client_views.py),
# and a verified deposit is what moves an appointment from LOCKED to WAITING —
# so a *count* of successfully-deposited appointments (and, combined with
# Business.deposit_amount, a rough GMV) is in fact derivable from existing
# fields. What genuinely does not exist is the exact amount actually paid per
# deposit: appointment/payment/zibal_deposit.py verifies the Zibal payment but
# never persists the paid amount anywhere, so an exact per-transaction GMV
# cannot be reconstructed even from those fields. This report computes
# neither the count nor the amount this phase — both are future work — but
# the note is careful not to claim more is missing than actually is.
DEPOSIT_NOTE = (
    'مبلغ دقیق بیعانه‌ها در این گزارش محاسبه نشده: بیعانهٔ نوبت مستقیماً به درگاه '
    'زیبالِ خودِ کسب‌وکار واریز می‌شود (نه حساب پلتفرم)، و مبلغ پرداخت‌شده در هیچ‌جای '
    'دیتابیس ذخیره نمی‌شود — appointment/payment/zibal_deposit.py مبلغ را تأیید '
    'می‌کند اما هرگز آن را ثبت نمی‌کند. (تعداد نوبت‌های دارای بیعانهٔ تأییدشده از روی '
    'وضعیت نوبت قابل استخراج است، اما در این نسخه گزارش نشده.) این بخش عمداً بدون '
    'هیچ عددی نمایش داده می‌شود: نمایش صفر به‌جای «داده‌ای در دسترس نیست» با فعالیت '
    'واقعیِ صفر اشتباه گرفته می‌شود، که نادرست‌تر از نبود عدد است.'
)


class ReportRangeError(ValueError):
    """The Jalali date range from the report form could not be parsed."""


# ── Jalali ⇄ Gregorian ────────────────────────────────────────────────────────
# api/jalali.py only converts Gregorian → Jalali (everything it renders starts
# from a stored, already-UTC datetime). The report form goes the other way —
# a staff member types a Jalali date and it has to become a query boundary —
# so the inverse conversion lives here rather than being bolted onto a module
# outside this phase's scope. Same well-known civil-calendar algorithm as
# jalali.to_jalali, just run backwards; round-trips against api/jalali.py's
# thirty lines were checked by hand (1400/01/01 ⇄ 2021-03-21).

def _jalali_to_gregorian(jy: int, jm: int, jd: int):
    jy += 1595
    days = (
        -355668
        + (365 * jy)
        + ((jy // 33) * 8)
        + (((jy % 33) + 3) // 4)
        + jd
    )
    if jm < 7:
        days += (jm - 1) * 31
    else:
        days += ((jm - 7) * 30) + 186

    gy = 400 * (days // 146097)
    days %= 146097
    if days > 36524:
        days -= 1
        gy += 100 * (days // 36524)
        days %= 36524
        if days >= 365:
            days += 1

    gy += 4 * (days // 1461)
    days %= 1461
    if days > 365:
        gy += (days - 1) // 365
        days = (days - 1) % 365

    gd = days + 1
    month_days = [0, 31, 29 if (gy % 4 == 0 and (gy % 100 != 0 or gy % 400 == 0)) else 28,
                  31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    gm = 1
    for gm in range(1, 13):
        if gd <= month_days[gm]:
            break
        gd -= month_days[gm]
    return gy, gm, gd


def parse_jalali_date(value: str) -> date:
    """``'1404/05/18'`` (or ``-``-separated) → :class:`datetime.date`.

    Raises :class:`ReportRangeError` — never a bare exception — on anything
    that is not three integers or does not land on a real calendar day, since
    this is always fed by a query-string param a staff member typed by hand.
    """
    raw = (value or '').strip()
    parts = raw.replace('-', '/').split('/')
    if len(parts) != 3:
        raise ReportRangeError(f'تاریخ نامعتبر: «{value}»')
    try:
        jy, jm, jd = (int(part) for part in parts)
    except ValueError:
        raise ReportRangeError(f'تاریخ نامعتبر: «{value}»')
    if not (1 <= jm <= 12 and 1 <= jd <= 31):
        raise ReportRangeError(f'تاریخ نامعتبر: «{value}»')
    try:
        gy, gm, gd = _jalali_to_gregorian(jy, jm, jd)
        return date(gy, gm, gd)
    except ValueError:
        raise ReportRangeError(f'تاریخ نامعتبر: «{value}»')


def format_jalali_date(day: date) -> str:
    jy, jm, jd = jalali.to_jalali(day.year, day.month, day.day)
    return f'{jy}/{jm:02d}/{jd:02d}'


def local_midnight(day: date, tz=None) -> datetime:
    """Same construction as ``metrics.local_day_start``: ``combine(...,
    tzinfo=tz)`` rather than ``.replace(hour=0)``, so the wall-clock midnight
    is correct even where the zone's offset differs from another instant's."""
    return datetime.combine(day, time.min, tzinfo=tz or project_tz())


def parse_range(date_from: str, date_to: str, now=None):
    """Two Jalali date strings → ``(start, end, from_day, to_day)``.

    ``start``/``end`` are UTC-aware bounds for ``field__gte=start,
    field__lt=end`` — local midnight of ``from_day`` through local midnight of
    the day *after* ``to_day``, so the range is inclusive of both endpoints'
    entire local day (matching the Tehran-boundary rule ``metrics.py`` uses).

    Missing either side defaults to the last :data:`DEFAULT_RANGE_DAYS` days
    ending today — the report page must render something useful on first
    load, before a staff member has picked a range.
    """
    tz = project_tz()
    now = now or timezone.now()
    today = timezone.localtime(now, tz).date()

    to_day = parse_jalali_date(date_to) if date_to else today
    from_day = parse_jalali_date(date_from) if date_from else to_day - timedelta(days=DEFAULT_RANGE_DAYS - 1)

    if from_day > to_day:
        raise ReportRangeError('تاریخ شروع نمی‌تواند بعد از تاریخ پایان باشد.')

    start = local_midnight(from_day, tz)
    end = local_midnight(to_day + timedelta(days=1), tz)
    return start, end, from_day, to_day


def _day_range(start, end, tz):
    first = timezone.localtime(start, tz).date()
    last = timezone.localtime(end, tz).date() - timedelta(days=1)  # end is exclusive
    days = []
    day = first
    while day <= last:
        days.append(day)
        day += timedelta(days=1)
    return days


def _daily_sum(queryset, field_name, tz):
    """``{date: total}`` grouped by local calendar day, entirely in SQL."""
    rows = (
        queryset.annotate(day=TruncDate(field_name, tzinfo=tz))
        .values('day')
        .order_by()  # clears Meta.ordering — see metrics.revenue()'s note
        .annotate(value=Sum('amount'))
    )
    return {row['day']: int(row['value'] or 0) for row in rows}


# ── Report 1: revenue breakdown ────────────────────────────────────────────

def revenue_breakdown(start, end):
    """Revenue by source over ``[start, end)``, plus a dense daily series.

    Two grouped queries for the totals (one scan of ``Transaction``, one
    grouped scan of ``AddOnPurchase``), plus one ``TruncDate`` query per
    source for the chart data — the same shape as
    ``metrics.revenue()``/``metrics.daily_series()``, just bounded on both
    ends instead of only a lower one.
    """
    tz = project_tz()

    sub_qs = Transaction.objects.filter(status='success', created_at__gte=start, created_at__lt=end)
    subscription_total = int(sub_qs.aggregate(total=Sum('amount'))['total'] or 0)

    addon_qs = AddOnPurchase.objects.filter(status='success', created_at__gte=start, created_at__lt=end)
    per_kind = {kind: 0 for kind in ADDON_KINDS}
    for row in addon_qs.values('pack__kind').order_by().annotate(total=Sum('amount')):
        per_kind[row['pack__kind']] = int(row['total'] or 0)

    total = subscription_total + sum(per_kind.values())

    days = _day_range(start, end, tz)
    subs_daily = _daily_sum(sub_qs, 'created_at', tz)
    addon_daily = _daily_sum(addon_qs, 'created_at', tz)

    series = {
        'labels': [format_jalali_date(day)[5:] for day in days],  # MM/DD only
        'subscription': [subs_daily.get(day, 0) for day in days],
        'addon': [addon_daily.get(day, 0) for day in days],
        'total': [subs_daily.get(day, 0) + addon_daily.get(day, 0) for day in days],
        # Same three numbers as the parallel arrays above, pre-zipped into one
        # row per day — the arrays are what tests assert against (matching
        # metrics.daily_series()'s shape), the rows are what the template
        # loops over. The Django template language cannot zip three lists on
        # its own, and a template filter for exactly this would be more code
        # than just building both shapes here.
        'rows': [
            {
                'label': format_jalali_date(day)[5:],
                'subscription': subs_daily.get(day, 0),
                'addon': addon_daily.get(day, 0),
                'total': subs_daily.get(day, 0) + addon_daily.get(day, 0),
            }
            for day in days
        ],
    }

    return {
        'total': total,
        'subscription': subscription_total,
        'sms_pack': per_kind[AddOnPack.KIND_SMS],
        'appointment_pack': per_kind[AddOnPack.KIND_APPOINTMENT],
        'feature_pack': per_kind[AddOnPack.KIND_FEATURE],
        'series': series,
    }


# ── Report 2: per-plan sales ────────────────────────────────────────────────

def plan_sales(start, end):
    """Which plans actually sold: count + revenue per plan, successful
    ``Transaction`` rows only, one grouped query."""
    rows = (
        Transaction.objects.filter(status='success', created_at__gte=start, created_at__lt=end)
        .values('plan_id', 'plan__name')
        .order_by()  # clears Meta.ordering before the GROUP BY, same reason as metrics.revenue()
        .annotate(count=Count('id'), revenue=Sum('amount'))
        .order_by('-revenue')
    )
    return [
        {
            'plan_id': row['plan_id'],
            'plan': row['plan__name'],
            'count': row['count'],
            'revenue': int(row['revenue'] or 0),
        }
        for row in rows
    ]


# ── Report 3: payment conversion ────────────────────────────────────────────

def _status_counts(queryset, statuses, start, end):
    rows = (
        queryset.filter(created_at__gte=start, created_at__lt=end)
        .values('status')
        .order_by()
        .annotate(count=Count('id'))
    )
    counts = {status: 0 for status in statuses}
    for row in rows:
        counts[row['status']] = row['count']
    total = sum(counts.values())
    rate = (counts.get('success', 0) / total) if total else None
    return {'counts': counts, 'total': total, 'rate': rate}


def payment_conversion(start, end):
    """Success vs pending vs failed/cancelled, for ``Transaction`` and
    ``AddOnPurchase`` separately, over ``[start, end)``.

    Nobody currently has any visibility into the abandoned/broken-payment
    rate — this is the first place it is computed anywhere in the codebase.

    Conversion rate = successful rows / all rows created in the window. A
    ``pending`` row is counted in the denominator, not dropped from it: the
    question this rate answers is "of the payments we tried to start, how
    many actually landed", and quietly excluding abandoned attempts from both
    sides of the fraction would make the rate blind to exactly the problem it
    exists to surface.
    """
    return {
        'transaction': _status_counts(
            Transaction.objects.all(), [c for c, _ in Transaction.STATUS_CHOICES], start, end,
        ),
        'addon_purchase': _status_counts(
            AddOnPurchase.objects.all(), [c for c, _ in AddOnPurchase.STATUS_CHOICES], start, end,
        ),
    }


# ── Report 4: MRR / ARPU / churn ────────────────────────────────────────────

# Below this many days, "prorate to a monthly rate" stops normalizing and
# starts amplifying: dividing a 21,000-Toman/7-day plan's price by
# months=7/30≈0.23 reports 90,000 Toman of MRR — 4.3x the actual price —
# which only makes sense if the platform genuinely expects that subscriber to
# keep re-buying every 7 days forever. Nothing in this system's plan ladder
# (see PLANS.md — trial/۱/۳/۶/۱۲ ماهه) works that way: the only day-unit plan
# today is the 10-day trial, priced at zero. This floor exists so that if
# staff ever add a short *paid* promotional plan through PlanAdmin (nothing
# stops them — duration_value has no minimum), it is excluded from MRR
# outright rather than silently inflating the platform's headline number.
_MRR_MIN_DURATION_DAYS = 28


def mrr(now=None):
    """Monthly Recurring Revenue: currently active subscriptions' plan price,
    prorated to a 30-day month.

    Formula, per active subscription (``status='active'`` and ``ends_at >
    now`` — the same check ``Subscription.is_valid()``/``metrics.standing()``
    use, since the ``status`` column alone is not kept in sync at the instant
    a subscription lapses)::

        months = duration_value / 30   if duration_unit == 'day'
        months = duration_value        if duration_unit == 'month'
        monthly_price = plan.price / months

    This mirrors ``Plan.get_end_date()``'s own "one month = 30 days"
    convention exactly, so a 3-month plan and a 90-day plan priced the same
    contribute the same MRR — they are the same commitment length under this
    system's own accounting, and using calendar months instead would make the
    two disagree for no reason grounded in how the platform actually bills.

    Day-unit plans shorter than :data:`_MRR_MIN_DURATION_DAYS` are excluded
    entirely rather than prorated — see that constant's comment for why.

    One grouped query (active subscriptions per plan — a handful of rows,
    never one row per subscription); the per-plan monthly rate is arithmetic
    done once per plan in Python, not a query per subscription.
    """
    now = now or timezone.now()
    rows = (
        Subscription.objects.filter(status='active', ends_at__gt=now)
        .values('plan_id', 'plan__price', 'plan__duration_value', 'plan__duration_unit')
        .order_by()
        .annotate(active_count=Count('id'))
    )
    total = 0.0
    for row in rows:
        if row['plan__duration_unit'] == 'day':
            if row['plan__duration_value'] < _MRR_MIN_DURATION_DAYS:
                continue
            months = row['plan__duration_value'] / 30
        else:
            months = row['plan__duration_value']
        if months <= 0:
            continue
        total += (row['plan__price'] / months) * row['active_count']
    return round(total)


def arpu(start, end, revenue_total=None):
    """ARPU = platform revenue in ``[start, end)`` / distinct paying users in
    the same window. A user who bought both a subscription and an SMS pack in
    the window counts once, not twice.

    Two queries for the distinct user-id sets (bounded by however many
    payments happened in the window, fetched once — not a query per user),
    combined in Python with a set union.
    """
    if revenue_total is None:
        revenue_total = revenue_breakdown(start, end)['total']

    tx_users = set(
        Transaction.objects.filter(status='success', created_at__gte=start, created_at__lt=end)
        .values_list('user_id', flat=True)
    )
    addon_users = set(
        AddOnPurchase.objects.filter(status='success', created_at__gte=start, created_at__lt=end)
        .values_list('user_id', flat=True)
    )
    paying_users = tx_users | addon_users
    count = len(paying_users)
    return {
        'revenue': revenue_total,
        'paying_users': count,
        'arpu': (revenue_total / count) if count else None,
    }


def churn(start, end):
    """Churn rate for ``[start, end)``::

        churned = subscriptions whose ends_at fell inside the window AND were
                  not renewed
        active_at_start = subscriptions already running at the window's start
        rate = churned / active_at_start

    "Not renewed" = the same user has no *other* Subscription whose
    ``started_at`` falls within ``[ends_at, ends_at + RENEWAL_GRACE_DAYS]`` —
    the exact grace period ``run_subscription_lifecycle.py`` waits before it
    actually flips a lapsed subscription to ``expired``, so a renewal that
    arrives a day or two late (a manual bank transfer, a missed reminder SMS)
    is counted as retained, matching what the rest of the system already
    treats as "still the same subscription", not as churn.

    "Active at the window's start" = ``started_at <= start`` and ``ends_at >
    start``, regardless of the current ``status`` value — the lifecycle job
    runs on a cron and may not have flipped a just-lapsed row to ``expired``
    yet, so trusting ``status`` here would double-count some rows as both
    "active at start" and, days later, "churned in the window" is decided
    from ``ends_at`` alone either way.

    There is no single industry-standard churn definition; this is the one
    used here, spelled out so a different definition is a deliberate choice
    later, not a silent drift.

    Two queries total: the lapsed set (bounded by the window) and one broad
    query for every subscription that *could* be a renewal for one of those
    users. The per-subscription renewal match is plain Python over the
    already-fetched rows, not a query per lapsed subscription.
    """
    lapsed = list(Subscription.objects.filter(ends_at__gte=start, ends_at__lt=end))
    active_at_start = Subscription.objects.filter(started_at__lte=start, ends_at__gt=start).count()

    if not lapsed:
        return {
            'lapsed': 0, 'renewed': 0, 'churned': 0,
            'active_at_start': active_at_start,
            'rate': (0.0 if active_at_start else None),
        }

    grace = timedelta(days=RENEWAL_GRACE_DAYS)
    user_ids = {sub.user_id for sub in lapsed}
    earliest_end = min(sub.ends_at for sub in lapsed)
    latest_deadline = max(sub.ends_at for sub in lapsed) + grace
    lapsed_ids = [sub.pk for sub in lapsed]

    candidates = (
        Subscription.objects.filter(
            user_id__in=user_ids, started_at__gte=earliest_end, started_at__lte=latest_deadline,
        )
        .exclude(pk__in=lapsed_ids)
        .values('user_id', 'started_at')
    )
    renewals_by_user = {}
    for row in candidates:
        renewals_by_user.setdefault(row['user_id'], []).append(row['started_at'])

    churned = 0
    for sub in lapsed:
        renewals = renewals_by_user.get(sub.user_id, [])
        renewed = any(sub.ends_at <= started <= sub.ends_at + grace for started in renewals)
        if not renewed:
            churned += 1

    return {
        'lapsed': len(lapsed),
        'renewed': len(lapsed) - churned,
        'churned': churned,
        'active_at_start': active_at_start,
        'rate': (churned / active_at_start) if active_at_start else None,
    }


# ── Entry point ──────────────────────────────────────────────────────────────

def build_report(date_from, date_to, now=None):
    """The complete report payload for the page and for CSV export.

    Raises :class:`ReportRangeError` if the range cannot be parsed — the view
    turns that into a form error rather than a 500.
    """
    now = now or timezone.now()
    start, end, from_day, to_day = parse_range(date_from, date_to, now)

    revenue = revenue_breakdown(start, end)
    arpu_data = arpu(start, end, revenue_total=revenue['total'])

    return {
        'range': {
            'from': from_day,
            'to': to_day,
            'from_jalali': format_jalali_date(from_day),
            'to_jalali': format_jalali_date(to_day),
        },
        'revenue': revenue,
        'plans': plan_sales(start, end),
        'conversion': payment_conversion(start, end),
        'mrr': mrr(now),
        'arpu': arpu_data,
        'churn': churn(start, end),
        'deposit_note': DEPOSIT_NOTE,
        'generated_at': jalali.format_datetime(now),
    }
