# core/detail_views.py
"""
Query layer behind the two "360" pages (user detail, business detail) and the
customers-per-business list — rendered by NobatyarAdminSite's
user_detail_view / business_detail_view / business_customers_view
(core/admin_site.py).

Same split as core/dashboard/metrics.py and accounting/reports.py: every
function here takes a model instance (or id) and returns plain dicts/querysets,
no request, no permission check, no HTML. The view's job is "fetch → gate on
permissions per panel (see `permissions_for_user`/`permissions_for_business`
below) → render". Testable directly (core/tests_detail_views.py) without
building a request.

Nothing here loops a queryset to build a per-row aggregate — every count/sum is
database-side (`annotate`/`aggregate`/`Count(..., filter=...)`), same rule
core/dashboard/metrics.py's docstring spells out. The one page-load query this
module intentionally cannot avoid is accounting.usage's Redis reads for wallet
balance: that state has no database row to aggregate at all (see
accounting/usage.py's module docstring — "fail open", no history), so it is a
handful of direct Redis round-trips, never a loop over rows.
"""

from django.db.models import Count, Max, Q

from accounting import usage as accounting_usage
from accounting.models import AddOnPurchase, Subscription, Transaction
from api import jalali
from appointment.models import Appointment
from business.models import Business, BusinessModerationLog
from visitor.models import SmsLog, Visitor

# How many rows of a long list (transactions, appointments, sms log) a 360
# page shows inline before pointing at the full changelist. Same idea as
# core/dashboard/metrics.ALERT_ROWS — a summary page, not a replacement for
# the changelist's own filtering/pagination.
DETAIL_ROWS = 15


# ── User 360 ────────────────────────────────────────────────────────────────

def permissions_for_user(viewer):
    """Per-panel visibility for the user detail page — same idiom as
    core/dashboard/panels.permissions(): a missing permission hides a panel,
    it never 403s the whole page (that gate is the caller's `api.view_user`
    check, done once before this is even called)."""
    return {
        'subscriptions': viewer.has_perm('accounting.view_subscription'),
        'transactions': viewer.has_perm('accounting.view_transaction'),
        'addon_purchases': viewer.has_perm('accounting.view_addonpurchase'),
        # Wallet/quota reads no database table (accounting/usage.py is pure
        # Redis), so it rides on whichever accounting permission the viewer
        # holds rather than needing a table of its own to gate on.
        'wallet': viewer.has_perm('accounting.view_subscription') or viewer.has_perm('accounting.view_addonpurchase'),
        'businesses': viewer.has_perm('business.view_business'),
        'activity': viewer.has_perm('visitor.view_visitoractivity'),
    }


def user_overview(user):
    return {
        'id': user.id,
        'name': user.name,
        'phone': user.phone,
        'role': user.get_role_display(),
        'is_staff': user.is_staff,
        'is_active': user.is_active,
        'joined_at': jalali.format_datetime(user.joined_at),
    }


def user_subscriptions(user):
    return (
        Subscription.objects.filter(user=user)
        .select_related('plan')
        .order_by('-started_at')[:DETAIL_ROWS]
    )


def user_transactions(user):
    return (
        Transaction.objects.filter(user=user)
        .select_related('plan')
        .order_by('-created_at')[:DETAIL_ROWS]
    )


def user_addon_purchases(user):
    return (
        AddOnPurchase.objects.filter(user=user)
        .select_related('pack')
        .order_by('-created_at')[:DETAIL_ROWS]
    )


def user_businesses(user):
    """Businesses this user owns, with their moderation/lock state — a single
    query, no per-business follow-up lookup."""
    return Business.objects.filter(user=user).order_by('-created_at')


def user_wallet(user):
    """Live Redis state (accounting/usage.py) — never historical, see that
    module's docstring. A handful of direct reads, not a loop over anything."""
    return {
        'sms': accounting_usage.sms_balance(user),
        'appointments': accounting_usage.appointment_balance(user),
    }


def user_activity(user):
    """The only owner-side activity trail that exists today.

    VisitorActivity (visitor/models.py) is fundamentally a *visitor's* history
    — appointments booked, status changes, profile edits — not an owner
    activity log (no logins, no owner-initiated settings changes are recorded
    anywhere). `actor_user` is set only for the subset of rows where this
    particular user acted *on a visitor* (e.g. archiving them, changing an
    appointment's status by hand), so this is a partial proxy at best. There is
    no general owner-side audit log in this codebase — surfaced here rather
    than invented, per this phase's brief.
    """
    from visitor.models import VisitorActivity

    return (
        VisitorActivity.objects.filter(actor_user=user)
        .select_related('visitor', 'business')
        .order_by('-created_at')[:DETAIL_ROWS]
    )


def build_user_detail(user, viewer):
    """Full 360 payload for `user`, already filtered to what `viewer` may see."""
    can = permissions_for_user(viewer)
    data = {'can': can, 'overview': user_overview(user)}
    if can['subscriptions']:
        data['subscriptions'] = user_subscriptions(user)
    if can['transactions']:
        data['transactions'] = user_transactions(user)
    if can['addon_purchases']:
        data['addon_purchases'] = user_addon_purchases(user)
    if can['wallet']:
        data['wallet'] = user_wallet(user)
    if can['businesses']:
        data['businesses'] = user_businesses(user)
    if can['activity']:
        data['activity'] = user_activity(user)
    return data


# ── Business 360 ──────────────────────────────────────────────────────────────

def permissions_for_business(viewer):
    return {
        'appointments': viewer.has_perm('appointment.view_appointment'),
        'sms': viewer.has_perm('visitor.view_smslog'),
        'customers': viewer.has_perm('visitor.view_visitor'),
        'moderation': viewer.has_perm('business.view_businessmoderationlog'),
    }


def business_appointment_stats(business):
    """Count per status plus the no-show rate, one grouped query and one
    small aggregate — never a query per status.

    "No-show rate" only makes sense over appointments that were actually due
    to happen — COMPLETED or NO_SHOW. A LOCKED slot that expired unpaid, or a
    CANCELLED booking, was never really kept, so counting either one in the
    denominator would understate a business's real attendance record.
    """
    rows = (
        Appointment.objects.filter(business=business)
        .values('status')
        .order_by()
        .annotate(count=Count('id'))
    )
    by_status = {code: 0 for code, _ in Appointment.STATUS_CHOICES}
    for row in rows:
        by_status[row['status']] = row['count']
    total = sum(by_status.values())

    completed, no_show = by_status.get('COMPLETED', 0), by_status.get('NO_SHOW', 0)
    eligible = completed + no_show
    no_show_rate = (no_show / eligible) if eligible else None

    return {
        'total': total,
        'by_status': [
            {'code': code, 'label': label, 'count': by_status[code]}
            for code, label in Appointment.STATUS_CHOICES
        ],
        'no_show_rate': no_show_rate,
        'no_show_eligible': eligible,
    }


def business_sms_usage(business):
    """SmsLog counts by outcome — the only historical SMS record that exists
    (see core/dashboard/metrics.py's note on SmsLog vs billed usage)."""
    rows = (
        SmsLog.objects.filter(business=business)
        .values('status')
        .order_by()
        .annotate(count=Count('id'))
    )
    counts = {code: 0 for code, _ in SmsLog.STATUS_CHOICES}
    for row in rows:
        counts[row['status']] = row['count']
    return {
        'sent': counts.get('SENT', 0),
        'failed': counts.get('FAILED', 0),
        'total': sum(counts.values()),
    }


def business_moderation_history(business):
    return (
        BusinessModerationLog.objects.filter(business=business)
        .select_related('actor')
        .order_by('-created_at')
    )


def business_customers_queryset(business):
    """Distinct visitors with >=1 appointment at `business`.

    Visitor is global (visitor/models.py's docstring: one row per phone
    number, shared across every business) and only connects to a specific
    business through Appointment, so this is a join + GROUP BY, never a
    stored relation. `Count`/`Max` with `filter=` reuse the same join Django
    builds for the `appointments` relation itself (the standard "conditional
    aggregation" pattern — see Django's docs on `Count(..., filter=Q(...))`),
    so this is one query no matter how many appointments the business has.
    """
    scope = Q(appointments__business=business)
    return (
        Visitor.objects.annotate(
            appointment_count=Count('appointments', filter=scope),
            last_appointment_at=Max('appointments__appointment_date', filter=scope),
        )
        .filter(appointment_count__gt=0)
        .order_by('-last_appointment_at')
    )


def build_business_detail(business, viewer):
    can = permissions_for_business(viewer)
    data = {'can': can, 'business': business}
    if can['appointments']:
        data['appointment_stats'] = business_appointment_stats(business)
    if can['sms']:
        data['sms_usage'] = business_sms_usage(business)
    if can['customers']:
        data['customers_preview'] = business_customers_queryset(business)[:DETAIL_ROWS]
        data['customers_total'] = business_customers_queryset(business).count()
    if can['moderation']:
        data['moderation_history'] = business_moderation_history(business)
    return data
