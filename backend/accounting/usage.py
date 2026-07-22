"""
accounting/usage.py

Usage metering for plan quotas, backed by the shared Redis cache.

Two kinds of state:

  * Monthly counters — appointments / SMS consumed in the current calendar month.
    Key: ``usage:{user_id}:{metric}:{YYYY-MM}`` (auto-resets each month via a new key).

  * SMS wallet — persistent, non-expiring credit bought via SMS add-on packs.
    Key: ``sms_wallet:{user_id}``. Consumed only after the monthly SMS allowance
    is used up.

All counters are keyed on the **business owner's** user id, because quotas belong
to the plan the owner pays for (a client booking consumes the owner's quota).

Everything fails open: if Redis is unavailable the cache returns falsy values and
callers are allowed through, so metering can never take the booking flow down.
"""

from datetime import datetime, timezone as dt_timezone

from django.core.cache import cache

from . import entitlements

# Metrics
METRIC_APPOINTMENTS = "appointments"
METRIC_SMS = "sms"

# A monthly key only needs to outlive its month; ~62 days covers any month safely.
_MONTHLY_TTL = 60 * 60 * 24 * 62


def _period():
    return datetime.now(dt_timezone.utc).strftime("%Y-%m")


def _uid(user_or_id):
    return user_or_id.id if hasattr(user_or_id, "id") else user_or_id


def _month_key(user_id, metric, period=None):
    return f"usage:{user_id}:{metric}:{period or _period()}"


def _wallet_key(user_id):
    return f"sms_wallet:{user_id}"


# ── Monthly counters ──────────────────────────────────────────────────────────

def get_usage(user_or_id, metric):
    """How much of ``metric`` has been consumed this month."""
    return int(cache.get(_month_key(_uid(user_or_id), metric)) or 0)


def add_usage(user_or_id, metric, amount=1):
    """Atomically increment this month's counter and return the new value."""
    key = _month_key(_uid(user_or_id), metric)
    try:
        if cache.add(key, amount, timeout=_MONTHLY_TTL):
            return amount
        return cache.incr(key, amount)
    except ValueError:
        # Key expired between add() and incr(); reset it.
        cache.set(key, amount, timeout=_MONTHLY_TTL)
        return amount


def within_quota(user_or_id, metric, quota):
    """True if there is still room this month (``quota`` = -1 means unlimited)."""
    if quota == entitlements.UNLIMITED:
        return True
    return get_usage(user_or_id, metric) < quota


def remaining(user_or_id, metric, quota):
    """Remaining monthly allowance; -1 for unlimited."""
    if quota == entitlements.UNLIMITED:
        return entitlements.UNLIMITED
    return max(0, quota - get_usage(user_or_id, metric))


# ── SMS wallet (add-on credit) ────────────────────────────────────────────────

def get_wallet(user_or_id):
    return int(cache.get(_wallet_key(_uid(user_or_id))) or 0)


def add_wallet(user_or_id, amount):
    """Add (or with a negative amount, spend) persistent SMS credit."""
    key = _wallet_key(_uid(user_or_id))
    try:
        if cache.add(key, amount, timeout=None):
            return amount
        return cache.incr(key, amount)
    except ValueError:
        cache.set(key, amount, timeout=None)
        return amount


# ── Appointment quota ─────────────────────────────────────────────────────────

def can_book_appointment(owner_or_id):
    """True if the owner's plan still allows another appointment this month."""
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_APPOINTMENTS)
    return within_quota(owner_or_id, METRIC_APPOINTMENTS, quota)


def record_appointment(owner_or_id):
    add_usage(owner_or_id, METRIC_APPOINTMENTS, 1)


# ── SMS quota (monthly allowance first, then wallet) ──────────────────────────

def consume_sms(owner_or_id, amount=1):
    """
    Try to consume ``amount`` SMS credits: first from this month's plan allowance,
    then from the persistent wallet. Returns True if fully consumed, False if the
    owner is out of credit (caller should skip sending).
    """
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_SMS)
    if quota == entitlements.UNLIMITED:
        add_usage(owner_or_id, METRIC_SMS, amount)
        return True

    remaining_month = remaining(owner_or_id, METRIC_SMS, quota)
    if remaining_month >= amount:
        add_usage(owner_or_id, METRIC_SMS, amount)
        return True

    # Draw the shortfall from the wallet.
    shortfall = amount - remaining_month
    if get_wallet(owner_or_id) >= shortfall:
        if remaining_month:
            add_usage(owner_or_id, METRIC_SMS, remaining_month)
        add_wallet(owner_or_id, -shortfall)
        return True

    return False


def sms_balance(owner_or_id):
    """Convenience for dashboards: {'monthly_remaining', 'wallet', 'quota'}."""
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_SMS)
    return {
        "quota": quota,
        "monthly_remaining": remaining(owner_or_id, METRIC_SMS, quota),
        "wallet": get_wallet(owner_or_id),
    }
