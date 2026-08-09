# accounting/ledger_reports.py
"""
Historical usage reporting over CreditLedger.

Before this phase, "how did this user's SMS/appointment usage look last
month" was not answerable from anything in the database — Redis only ever
holds the *current* month's counter and the *current* wallet balance, with
no memory of what came before (see ``accounting/usage.py``'s module
docstring). CreditLedger is the first place that history exists, so this is
the first place it can be queried.

Kept out of ``accounting/usage.py`` for the same reason ``accounting/reports.py``
is kept out of ``core/dashboard/metrics.py`` (see that module's own docstring):
a query/render-layer split, so every number here is unit-testable against
hand-computed ledger rows without building a request or a view.

Deliberately modest — one query, grouped in SQL, no per-row Python loop —
this is a bonus reporting surface for this phase, not a new dashboard.
"""

from datetime import timedelta

from django.db.models import Q, Sum
from django.db.models.functions import TruncMonth
from django.utils import timezone

from accounting.models import CreditLedger

_POSITIVE_DELTA = Q(delta__gt=0)
_NEGATIVE_DELTA = Q(delta__lt=0)


def monthly_usage(user_id, months=6, now=None):
    """
    ``{month: {metric: {'granted': int, 'spent': int, 'net': int}}}`` for the
    last ``months`` calendar months (most recent first), from ``user_id``'s
    CreditLedger rows.

    "granted" sums positive deltas, "spent" sums the absolute value of
    negative deltas, for the SAME reason a bank statement shows deposits and
    withdrawals separately rather than netting them silently — "spent 40,
    refunded 10" and "spent 30" look identical net but are not the same
    story. ``net`` is granted - spent, for a single glance total.

    One grouped query (``TruncMonth`` + conditional aggregation via two
    ``Sum`` filters), never a query per month or per row.
    """
    now = now or timezone.now()
    # months=6 → look back ~6 calendar months; a day's slack on the boundary
    # doesn't matter since grouping is by TruncMonth, not by this cutoff.
    since = now - timedelta(days=31 * months)

    rows = (
        CreditLedger.objects.filter(user_id=user_id, created_at__gte=since)
        .annotate(month=TruncMonth('created_at'))
        .values('month', 'metric')
        .order_by()
        .annotate(
            granted=Sum('delta', filter=_POSITIVE_DELTA),
            spent=Sum('delta', filter=_NEGATIVE_DELTA),
        )
    )

    result = {}
    for row in rows:
        month_key = row['month'].strftime('%Y-%m')
        granted = int(row['granted'] or 0)
        spent = int(row['spent'] or 0)  # negative or zero
        bucket = result.setdefault(month_key, {})
        bucket[row['metric']] = {
            'granted': granted,
            'spent': -spent,  # report as a positive magnitude
            'net': granted + spent,
        }

    return dict(sorted(result.items(), reverse=True))


def recent_entries(user_id, limit=50):
    """The raw feed for an admin "activity" panel: newest rows first, already
    what ``CreditLedger.Meta.ordering`` gives, just capped."""
    return list(CreditLedger.objects.filter(user_id=user_id)[:limit])
