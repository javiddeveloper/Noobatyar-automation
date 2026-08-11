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
    call instead of each remembering to send the SMS themselves. A rejection or
    suspension also auto-resolves any open content report against the business
    (:func:`_auto_resolve_open_reports`), linking it to the resulting
    BusinessModerationLog so "which report led to this decision" stays
    answerable without correlating timestamps by hand.
"""

import logging

from django.db import transaction
from django.utils import timezone

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

    A rejection or suspension also auto-resolves any content report still open
    (NEW/REVIEWING) against ``business``: see :func:`_auto_resolve_open_reports`.
    Approving does not — "it was fine" is not an action taken *on* a report, so
    reports stay open for a reviewer to dismiss or action explicitly.

    The SMS is fired after the decision is committed and can never undo it: a
    provider outage must not roll a rejection back. Returns whether the notice
    was actually accepted by the provider, so a caller that cares (e.g. an admin
    message) can say "تأیید شد، اما پیامک ارسال نشد".
    """
    from .models import Business
    from .moderation import apply_decision

    with transaction.atomic():
        log = apply_decision(business, to_status, actor, note=note)
        if to_status in (Business.MODERATION_REJECTED, Business.MODERATION_SUSPENDED):
            _auto_resolve_open_reports(business, log, actor)

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


def _auto_resolve_open_reports(business, log, actor):
    """Close out open :class:`ContentReport` rows against ``business`` and link
    them to the decision that resolved them.

    Without this, a reviewer who suspends a business from the moderation queue
    leaves any report that prompted the suspension sitting in the "NEW" queue
    forever, and nothing records that this particular log row is *why* the
    report was closed — "which report led to this suspension" becomes a manual
    correlation across two unrelated tables. Scoped to NEW/REVIEWING only, so a
    report someone already dismissed or actioned for an unrelated reason is
    left exactly as that reviewer left it.
    """
    from .models import ContentReport

    ContentReport.objects.filter(
        business=business,
        status__in=(ContentReport.STATUS_NEW, ContentReport.STATUS_REVIEWING),
    ).update(
        status=ContentReport.STATUS_ACTIONED,
        resolved_by=actor,
        resolved_at=timezone.now(),
        resulting_moderation_log=log,
    )


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


def stage_pending_moderated_fields(business, validated_data):
    """Redirect any changed ``Business.MODERATED_FIELDS`` key out of
    ``validated_data`` and into the matching ``pending_<field>`` name instead.

    Mutates and returns a dict of ``{'pending_<field>': new_value, ...}``
    suitable as ``serializer.save(**staged)`` kwargs; the popped keys are gone
    from ``validated_data`` so the plain ``serializer.save()`` call the caller
    makes afterwards never touches the live column. Only called once a business
    has been approved at least once (``first_approved_at`` is set) — before
    that there is no previously-approved, publicly-true copy to protect, so the
    field may as well save directly like it always has.
    """
    from .models import Business

    staged = {}
    for field in Business.MODERATED_FIELDS:
        if field not in validated_data:
            continue
        new_value = validated_data[field]
        current_value = getattr(business, field)
        # Compare by stored file name for logo, same as moderated_snapshot(),
        # so re-posting the same logo file is not mistaken for an edit.
        new_compare = new_value.name if hasattr(new_value, 'name') else new_value
        current_compare = current_value.name if hasattr(current_value, 'name') else current_value
        if new_compare == (current_compare or ''):
            continue
        staged['pending_' + field] = validated_data.pop(field)

    return staged


def save_with_moderation(serializer, business, before_snapshot):
    """``serializer.save()``, keeping the public copy stable while a content
    edit is under re-review.

    Once a business has cleared review at least once (``first_approved_at``
    set), any change to a MODERATED_FIELDS column is staged onto its
    ``pending_<field>`` counterpart (:func:`stage_pending_moderated_fields`)
    instead of overwriting the live column — so the booking page keeps
    showing the last-approved copy instead of the business disappearing while
    a moderator gets to it. A moderator's later APPROVED decision promotes the
    staged draft onto the live columns (business/moderation.py).

    Before a business's first approval there is nothing yet worth protecting,
    so this falls back to the original snapshot-diff behaviour
    (:func:`resubmit_if_content_changed`), which also still owns the one path
    a first-time REJECTED business has back into the queue.

    Runs as one transaction for the same reason :func:`resubmit_if_content_changed`
    always did: the owner's update view awaits this as a single
    ``sync_to_async`` hop, and a save whose re-queue half never ran would leave
    an edited business either publicly live with unreviewed copy (pre-staging)
    or with a draft stuck in limbo (post-staging) if a worker died in between.

    Returns True if the business now has an edit pending re-review.
    """
    from .models import Business

    with transaction.atomic():
        if not business.first_approved_at:
            serializer.save()
            return resubmit_if_content_changed(business, before_snapshot)

        staged = stage_pending_moderated_fields(business, serializer.validated_data)
        serializer.save(**staged)

        if not staged:
            return business.moderation_status == Business.MODERATION_PENDING

        if business.moderation_status in (
            Business.MODERATION_APPROVED, Business.MODERATION_REJECTED,
        ):
            from .moderation import submit_for_review

            changed_names = '، '.join(f[len('pending_'):] for f in staged)
            submit_for_review(
                business,
                reason='ویرایش اطلاعات نمایش‌داده‌شده: ' + changed_names,
            )
            logger.info(
                'Business %s re-queued for review after staging edits to %s',
                business.pk, changed_names,
            )

        # Already PENDING (a second edit landing while the first is still
        # under review) or SUSPENDED: the draft is saved either way, just
        # without re-stamping the queue position or lifting the suspension.
        return True
