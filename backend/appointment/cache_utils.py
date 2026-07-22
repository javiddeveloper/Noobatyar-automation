"""
appointment/cache_utils.py

Shared helpers for caching the (high-traffic, public) slot endpoints and for
invalidating those cache entries whenever an appointment for a given
business/date is created, updated, or deleted.

Two endpoints are cached with distinct key namespaces so their different
response shapes never collide:

    avail_slots:{business_id}:{YYYY-MM-DD}   → AvailableSlotsView (full grid)
    pub_slots:{business_id}:{YYYY-MM-DD}     → PublicAvailableSlotsView (occupied)
"""

from datetime import date as _date, datetime

from django.core.cache import cache

# Short TTL: slot availability changes often, but even 30s of caching absorbs
# the vast majority of read traffic during a booking rush.
SLOT_CACHE_TTL = 30

_AVAIL_PREFIX = "avail_slots"
_PUBLIC_PREFIX = "pub_slots"


def _normalize_date(value) -> str:
    """Accept a date, datetime, or 'YYYY-MM-DD' string and return 'YYYY-MM-DD'."""
    if isinstance(value, datetime):
        return value.date().isoformat()
    if isinstance(value, _date):
        return value.isoformat()
    return str(value)


def available_slots_key(business_id, date_value) -> str:
    return f"{_AVAIL_PREFIX}:{business_id}:{_normalize_date(date_value)}"


def public_slots_key(business_id, date_value) -> str:
    return f"{_PUBLIC_PREFIX}:{business_id}:{_normalize_date(date_value)}"


def invalidate_slots_cache(business_id, date_value) -> None:
    """
    Drop both cached slot representations for one business on one date.

    Call this after any write that changes slot occupancy (create / status
    change / reschedule / delete). ``date_value`` may be a date, a datetime
    (e.g. appointment.appointment_date), or a 'YYYY-MM-DD' string.
    """
    if business_id is None or date_value is None:
        return
    cache.delete_many([
        available_slots_key(business_id, date_value),
        public_slots_key(business_id, date_value),
    ])
