"""
business/services.py

Two concerns, both "policy that has side effects", kept out of the views:

  * Subscription locks — keeps a user's set of active/locked businesses in sync
    with the number allowed by their current plan (``max_businesses`` quota).
    Graceful downgrade: when the allowance shrinks below what the user owns, the
    *newest* businesses are locked (kept, but hidden from public booking and
    read-only) rather than deleted. Renewing/upgrading unlocks them again —
    oldest first, up to the new allowance.

  * Moderation decisions — :func:`apply_moderation_decision` is the single entry
    point that changes editorial state *and* tells the owner about it.
    ``business.moderation.apply_decision`` stays deliberately side-effect-free
    (status + audit log, atomic); the notification lives here so the admin UI,
    a future staff API, and management commands all get both halves from one
    call instead of each remembering to send the SMS themselves.
"""

import logging

from django.db import transaction

from accounting import entitlements

logger = logging.getLogger(__name__)


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


# ── Moderation ────────────────────────────────────────────────────────────────

def moderated_snapshot(business):
    """Current value of every field in ``Business.MODERATED_FIELDS``.

    Take one of these *before* the serializer saves and compare it with one
    taken after, to find out whether the owner actually changed anything the
    public sees.

    Delegates to the moderation module rather than reimplementing the flattening
    — the audit log and this comparison must agree on what a field's value *is*,
    or a change could be logged as one thing and compared as another. It matters
    most for ``logo``: reduced to its stored file name because the field's value
    is a FieldFile, and two of those never compare equal even when they point at
    the same upload, which would send every business back to review on a PATCH
    that never touched the logo.
    """
    from .moderation import moderated_snapshot as _snapshot

    return _snapshot(business)


def changed_moderated_fields(before, after):
    """Names of moderated fields whose value actually differs between snapshots."""
    return [key for key, old in before.items() if after.get(key) != old]


def apply_moderation_decision(business, to_status, actor, note='', notify=True):
    """Record a moderation decision **and** notify the owner.

    This is the function every caller should use — the admin action, any staff
    API, and management commands. ``business.moderation.apply_decision`` alone
    changes the status without telling anyone, which for a rejection means the
    owner is left staring at a business that is offline for no stated reason.

    The SMS is fired after the decision is committed and can never undo it: a
    provider outage must not roll a rejection back. Returns whether the notice
    was actually accepted by the provider, so a caller that cares (e.g. an admin
    message) can say "تأیید شد، اما پیامک ارسال نشد".
    """
    from .moderation import apply_decision

    apply_decision(business, to_status, actor, note=note)

    if not notify:
        return False

    from .sms_moderation import notify_moderation_decision

    # notify_moderation_decision swallows its own failures; the extra guard is
    # for an import-time or programming error, which still must not surface as a
    # 500 on a decision that already landed.
    try:
        return notify_moderation_decision(business, to_status)
    except Exception:
        logger.exception(
            'Moderation notification failed for business %s (%s)', business.pk, to_status
        )
        return False


def resubmit_if_content_changed(business, before_snapshot):
    """Send an edited business back into the review queue when it needs it.

    Called from the owner's update path with the snapshot taken before the save.
    Returns True if the business was re-queued.

    Which states re-enter the queue, and why:

      * APPROVED — must. This is the case that makes moderation mean anything:
        without it an owner gets cleared with tame copy and then swaps in
        whatever they like, and the review was theatre.
      * REJECTED — yes, and exactly once. Editing the offending copy *is* the
        resubmission; there is no separate "resubmit" endpoint, so without this
        a rejected owner has no route back into the queue at all. The next save
        finds the business already PENDING and falls through to the case below.
      * PENDING — no. It is already queued, and ``moderation_submitted_at``
        drives queue order (oldest waiting first). Re-stamping on every save
        would shove owners who are still polishing their copy to the back of
        the queue, punishing them for editing.
      * SUSPENDED — no, deliberately. Suspension is an enforcement action; an
        owner must not be able to lift it by retyping their own title. Only a
        reviewer moves a business out of SUSPENDED.
    """
    from .models import Business

    if business.moderation_status not in (
        Business.MODERATION_APPROVED, Business.MODERATION_REJECTED,
    ):
        return False

    changed = changed_moderated_fields(before_snapshot, moderated_snapshot(business))
    if not changed:
        # A PATCH that re-sends the same title unchanged is not an edit. Treating
        # it as one would drop a business out of the public listing every time
        # the owner adjusted their opening hours from a form that posts the whole
        # object back.
        return False

    from .moderation import submit_for_review

    submit_for_review(
        business,
        reason='ویرایش اطلاعات نمایش‌داده‌شده: ' + '، '.join(changed),
    )
    logger.info(
        'Business %s re-queued for review after editing %s', business.pk, changed
    )
    return True


def save_and_maybe_requeue(serializer, business, before_snapshot):
    """``serializer.save()`` and :func:`resubmit_if_content_changed`, atomically.

    The owner's update view runs each of these as a separate ``sync_to_async``
    hop, which means a separate database round trip. Between them, an edited but
    still-``APPROVED`` business is genuinely live on the public booking page
    with its new, unreviewed copy — a worker kill or a raised exception in that
    window leaves it there permanently, since nothing rolls the save back. This
    wraps both writes in one transaction so they commit — or fail — together;
    call it as a single ``sync_to_async`` unit from the view rather than calling
    the two pieces separately.

    Returns whatever :func:`resubmit_if_content_changed` returns.
    """
    with transaction.atomic():
        serializer.save()
        return resubmit_if_content_changed(business, before_snapshot)
