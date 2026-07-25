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

# Which bucket an appointment credit was drawn from, so a cancellation can put
# it back where it came from instead of guessing.
SOURCE_MONTHLY = "monthly"
SOURCE_WALLET = "wallet"

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


def _appt_wallet_key(user_id):
    return f"appt_wallet:{user_id}"


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


# ── Appointment wallet (add-on credit) ────────────────────────────────────────

def get_appt_wallet(user_or_id):
    return int(cache.get(_appt_wallet_key(_uid(user_or_id))) or 0)


def add_appt_wallet(user_or_id, amount):
    """Add (or with a negative amount, spend) persistent appointment credit."""
    key = _appt_wallet_key(_uid(user_or_id))
    try:
        if cache.add(key, amount, timeout=None):
            return amount
        return cache.incr(key, amount)
    except ValueError:
        cache.set(key, amount, timeout=None)
        return amount


# ── Appointment quota (monthly allowance first, then wallet) ──────────────────

def can_book_appointment(owner_or_id):
    """
    True if the owner can book another appointment: either the plan's monthly
    allowance still has room, or the persistent appointment wallet has credit.
    """
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_APPOINTMENTS)
    if within_quota(owner_or_id, METRIC_APPOINTMENTS, quota):
        return True
    return get_appt_wallet(owner_or_id) > 0


def record_appointment(owner_or_id):
    """
    Consume one appointment credit: from this month's plan allowance first, then
    from the persistent wallet once the monthly allowance is exhausted.

    Returns which bucket paid for it (:data:`SOURCE_MONTHLY` /
    :data:`SOURCE_WALLET`, or ``""`` when nothing was charged). Callers should
    keep that value so a later cancellation can refund the right bucket — see
    :func:`release_appointment`.
    """
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_APPOINTMENTS)
    if within_quota(owner_or_id, METRIC_APPOINTMENTS, quota):
        add_usage(owner_or_id, METRIC_APPOINTMENTS, 1)
        return SOURCE_MONTHLY
    if get_appt_wallet(owner_or_id) > 0:
        add_appt_wallet(owner_or_id, -1)
        return SOURCE_WALLET
    return ""


def release_appointment(owner_or_id, source, booked_at=None):
    """
    Give back the credit a cancelled appointment consumed.

    ``source`` is the value :func:`record_appointment` returned for it, and
    ``booked_at`` is when the booking was made. Returns True if a credit was
    actually returned.

    Wallet credit is bought with money and never expires, so it is always
    refundable. The monthly counter is keyed per calendar month: refunding a
    booking made in an earlier month would hand back an allowance that month
    never spent, so those are left alone (the counter has already reset).
    """
    if source == SOURCE_WALLET:
        add_appt_wallet(owner_or_id, 1)
        return True

    if source != SOURCE_MONTHLY:
        return False

    booked_period = (
        booked_at.astimezone(dt_timezone.utc).strftime("%Y-%m") if booked_at else _period()
    )
    if booked_period != _period():
        return False

    return _decrement_usage(_uid(owner_or_id), METRIC_APPOINTMENTS, booked_period)


def _decrement_usage(user_id, metric, period):
    """Give back one unit of this month's counter, never dropping below zero."""
    key = _month_key(user_id, metric, period)
    try:
        if int(cache.get(key) or 0) <= 0:
            return False
        cache.decr(key, 1)
        return True
    except ValueError:
        # Key vanished between the read and the decrement — nothing to refund.
        return False


def appointment_balance(owner_or_id):
    """Convenience for dashboards: {'quota', 'monthly_remaining', 'wallet', 'used'}."""
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_APPOINTMENTS)
    return {
        "quota": quota,
        "used": get_usage(owner_or_id, METRIC_APPOINTMENTS),
        "monthly_remaining": remaining(owner_or_id, METRIC_APPOINTMENTS, quota),
        "wallet": get_appt_wallet(owner_or_id),
    }


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
