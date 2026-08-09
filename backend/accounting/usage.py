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

import logging
from datetime import datetime, timezone as dt_timezone

from django.core.cache import cache
from django.db import transaction

from . import entitlements

logger = logging.getLogger(__name__)

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


# ── Ledger (audit trail) ────────────────────────────────────────────────────
#
# CreditLedger (accounting/models.py) is the durable, append-only history
# underneath the Redis state this module manages. Writing a row is a side
# effect, never a gate: the Redis mutation always happens first — that is
# the thing actually governing behaviour — and if the ledger write then
# fails, every function below still returns exactly what it would have
# returned before this module knew the ledger existed. This mirrors the
# module's own "fail open" philosophy (see the module docstring), just
# applied to the DB side instead of the Redis side: a gap in the audit trail
# is a real problem worth logging loudly, but never one worth blocking a
# booking or an SMS send over.

# Reason codes, as constants rather than free-form strings at each call site
# so a typo can never silently mint an new, unqueryable reason.
REASON_BOOKING = "booking"
REASON_CANCELLATION_REFUND = "cancellation_refund"
REASON_SMS_SEND = "sms_send"
REASON_SMS_REFUND = "sms_refund"
REASON_WALLET_CREDIT = "wallet_credit"
REASON_WALLET_DEBIT = "wallet_debit"


def _write_ledger(user_id, metric, delta, balance_after, reason, ref_type="", ref_id=None):
    """
    Best-effort ledger write — never raises. Imported lazily so this module
    (imported early by async views, management commands, etc.) does not carry
    a module-level ORM dependency that needs the app registry ready.

    Some call sites (e.g. appointment cancellation in
    appointment/views/views.py) run inside an outer ``@transaction.atomic``
    block and write to the same row again right after this returns. On
    Postgres, a DB-level failure during the ``create()`` — a deadlock, a
    connection blip — aborts the *whole* transaction at the protocol level
    the instant it happens, regardless of whether Python catches the
    exception: the bare ``except Exception`` below stops the ledger error from
    propagating, but the connection itself is left unable to execute anything
    else until the transaction ends, so the very next statement in the outer
    block (the appointment save) would raise anyway and roll back a real
    change that Redis has no way to undo. Wrapping the write in its own nested
    ``atomic()`` makes Django open a SAVEPOINT for it; catching the exception
    while still inside that block rolls back only to the savepoint, leaving
    the outer transaction free to continue. Cheap and correct even when this
    function is called with no outer transaction at all — a top-level atomic()
    just behaves like a normal transaction.
    """
    try:
        with transaction.atomic():
            from .models import CreditLedger

            CreditLedger.objects.create(
                user_id=user_id,
                metric=metric,
                delta=delta,
                balance_after=balance_after,
                reason=reason,
                ref_type=ref_type or "",
                ref_id=ref_id,
            )
    except Exception:
        # A gap in the audit trail is a real problem to go investigate, but
        # never one worth failing the caller's actual booking/SMS operation
        # over — that operation already completed against Redis by the time
        # this runs (see each call site: Redis mutation first, ledger after).
        logger.exception(
            "CreditLedger write failed (user=%s metric=%s delta=%s reason=%s) "
            "— Redis state is unaffected, only the audit trail has a gap",
            user_id, metric, delta, reason,
        )


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


def add_wallet(user_or_id, amount, reason=None, ref_type="", ref_id=None):
    """
    Add (or with a negative amount, spend) persistent SMS credit.

    ``reason``/``ref_type``/``ref_id`` are optional so every existing caller
    keeps working unchanged; pass them when you have real context (e.g. an
    ``AddOnPurchase`` id) so the ledger row says something useful instead of
    a generic default. When omitted, the reason is inferred from the sign of
    ``amount`` (credit vs. debit) — accurate for *what* happened, just not
    *why*.
    """
    user_id = _uid(user_or_id)
    key = _wallet_key(user_id)
    try:
        if cache.add(key, amount, timeout=None):
            new_value = amount
        else:
            new_value = cache.incr(key, amount)
    except ValueError:
        cache.set(key, amount, timeout=None)
        new_value = amount

    _write_ledger(
        user_id, "sms_wallet", amount, new_value,
        reason or (REASON_WALLET_CREDIT if amount >= 0 else REASON_WALLET_DEBIT),
        ref_type=ref_type, ref_id=ref_id,
    )
    return new_value


# ── Appointment wallet (add-on credit) ────────────────────────────────────────

def get_appt_wallet(user_or_id):
    return int(cache.get(_appt_wallet_key(_uid(user_or_id))) or 0)


def add_appt_wallet(user_or_id, amount, reason=None, ref_type="", ref_id=None):
    """
    Add (or with a negative amount, spend) persistent appointment credit.

    See :func:`add_wallet` for the ``reason``/``ref_type``/``ref_id`` contract
    — identical here, just for the appointment wallet bucket.
    """
    user_id = _uid(user_or_id)
    key = _appt_wallet_key(user_id)
    try:
        if cache.add(key, amount, timeout=None):
            new_value = amount
        else:
            new_value = cache.incr(key, amount)
    except ValueError:
        cache.set(key, amount, timeout=None)
        new_value = amount

    _write_ledger(
        user_id, "appointment_wallet", amount, new_value,
        reason or (REASON_WALLET_CREDIT if amount >= 0 else REASON_WALLET_DEBIT),
        ref_type=ref_type, ref_id=ref_id,
    )
    return new_value


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
        new_value = add_usage(owner_or_id, METRIC_APPOINTMENTS, 1)
        _write_ledger(_uid(owner_or_id), "appointment_monthly", 1, new_value, REASON_BOOKING)
        return SOURCE_MONTHLY
    if get_appt_wallet(owner_or_id) > 0:
        add_appt_wallet(owner_or_id, -1, reason=REASON_BOOKING)
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
        add_appt_wallet(owner_or_id, 1, reason=REASON_CANCELLATION_REFUND)
        return True

    if source != SOURCE_MONTHLY:
        return False

    booked_period = (
        booked_at.astimezone(dt_timezone.utc).strftime("%Y-%m") if booked_at else _period()
    )
    if booked_period != _period():
        return False

    return _decrement_usage(
        _uid(owner_or_id), METRIC_APPOINTMENTS, booked_period,
        ledger_metric="appointment_monthly", reason=REASON_CANCELLATION_REFUND,
    )


def _decrement_usage(user_id, metric, period, amount=1, ledger_metric=None, reason=None):
    """
    Give back units of this month's counter, never dropping below zero.

    ``ledger_metric``/``reason`` are optional so any future internal caller
    that doesn't care about the audit trail keeps working; both current
    callers (:func:`release_appointment`, :func:`refund_sms`) pass them.
    """
    key = _month_key(user_id, metric, period)
    try:
        current = int(cache.get(key) or 0)
        if current <= 0:
            return False
        actual = min(amount, current)
        new_value = cache.decr(key, actual)
        if ledger_metric:
            _write_ledger(user_id, ledger_metric, -actual, new_value, reason or "")
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

def _sms_receipt(owner_or_id, monthly, wallet):
    return {
        "owner": _uid(owner_or_id),
        SOURCE_MONTHLY: monthly,
        SOURCE_WALLET: wallet,
        "period": _period(),
    }


def consume_sms(owner_or_id, amount=1):
    """
    Try to consume ``amount`` SMS credits: first from this month's plan allowance,
    then from the persistent wallet.

    Returns a receipt recording which buckets paid for it, or ``None`` when the
    owner is out of credit (caller should skip sending). The receipt is always
    truthy on success, so ``if not consume_sms(...)`` still reads naturally.

    Hold on to the receipt: if the send then fails, hand it to :func:`refund_sms`
    so a message the provider never accepted costs the owner nothing. Unlike an
    appointment, one SMS charge can straddle *both* buckets (a partial monthly
    remainder topped up from the wallet), so the split has to be recorded here
    rather than re-derived at refund time.
    """
    owner_id = _uid(owner_or_id)
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_SMS)
    if quota == entitlements.UNLIMITED:
        new_value = add_usage(owner_or_id, METRIC_SMS, amount)
        _write_ledger(owner_id, "sms_monthly", amount, new_value, REASON_SMS_SEND)
        return _sms_receipt(owner_or_id, monthly=amount, wallet=0)

    remaining_month = remaining(owner_or_id, METRIC_SMS, quota)
    if remaining_month >= amount:
        new_value = add_usage(owner_or_id, METRIC_SMS, amount)
        _write_ledger(owner_id, "sms_monthly", amount, new_value, REASON_SMS_SEND)
        return _sms_receipt(owner_or_id, monthly=amount, wallet=0)

    # Draw the shortfall from the wallet. Two buckets can pay for one charge,
    # so this can write up to two ledger rows for a single consume_sms() call
    # — one per bucket, never a single row with an ambiguous combined delta.
    shortfall = amount - remaining_month
    if get_wallet(owner_or_id) >= shortfall:
        if remaining_month:
            new_value = add_usage(owner_or_id, METRIC_SMS, remaining_month)
            _write_ledger(owner_id, "sms_monthly", remaining_month, new_value, REASON_SMS_SEND)
        add_wallet(owner_or_id, -shortfall, reason=REASON_SMS_SEND)
        return _sms_receipt(owner_or_id, monthly=remaining_month, wallet=shortfall)

    return None


def refund_sms(receipt):
    """
    Give back the credit a failed SMS consumed, using the receipt
    :func:`consume_sms` returned. Returns True if anything was actually returned.

    Same month-boundary rule as :func:`release_appointment`: wallet credit is
    bought with money and never expires, so it is always refundable, but the
    monthly counter is keyed per calendar month. If the charge happened in an
    earlier month that counter has already reset, and refunding it now would hand
    back an allowance the current month never spent.
    """
    if not receipt:
        return False

    owner = receipt.get("owner")
    monthly = int(receipt.get(SOURCE_MONTHLY) or 0)
    wallet = int(receipt.get(SOURCE_WALLET) or 0)
    refunded = False

    # Same two-bucket split as consume_sms(), refunded independently — each
    # bucket that was actually charged gets its own ledger row.
    if wallet:
        add_wallet(owner, wallet, reason=REASON_SMS_REFUND)
        refunded = True

    period = receipt.get("period") or _period()
    if monthly and period == _period():
        refunded = _decrement_usage(
            owner, METRIC_SMS, period, monthly,
            ledger_metric="sms_monthly", reason=REASON_SMS_REFUND,
        ) or refunded

    return refunded


def sms_balance(owner_or_id):
    """Convenience for dashboards: {'monthly_remaining', 'wallet', 'quota'}."""
    quota = entitlements.get_quota(owner_or_id, entitlements.QUOTA_MONTHLY_SMS)
    return {
        "quota": quota,
        "monthly_remaining": remaining(owner_or_id, METRIC_SMS, quota),
        "wallet": get_wallet(owner_or_id),
    }
