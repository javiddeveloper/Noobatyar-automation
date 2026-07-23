"""
business/services.py

Keeps a user's set of active/locked businesses in sync with the number allowed
by their current plan (``max_businesses`` quota).

Graceful downgrade: when the allowance shrinks below what the user owns, the
*newest* businesses are locked (kept, but hidden from public booking and
read-only) rather than deleted. Renewing/upgrading unlocks them again — oldest
first, up to the new allowance.
"""

from accounting import entitlements


def sync_locks(user):
    """
    Reconcile ``Business.is_locked`` for ``user`` with their current quota.
    Returns the number of businesses left locked.
    """
    from .models import Business

    quota = entitlements.get_quota(user, entitlements.QUOTA_MAX_BUSINESSES)

    # Oldest first — the earliest-created businesses keep priority.
    businesses = list(Business.objects.filter(user=user).order_by("created_at", "id"))

    if entitlements.is_unlimited(quota):
        allowed = len(businesses)
    else:
        allowed = max(0, quota)

    locked_count = 0
    for index, biz in enumerate(businesses):
        should_lock = index >= allowed
        if biz.is_locked != should_lock:
            biz.is_locked = should_lock
            biz.save(update_fields=["is_locked"])
        if should_lock:
            locked_count += 1

    return locked_count
