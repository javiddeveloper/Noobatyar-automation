# Generated manually (local toolchain can't run `makemigrations` — see
# noobatyar-local-toolchain memory).
"""One-time repair for the bug this feature (0012-0017) introduced: editing a
moderated field on an already-APPROVED business re-queued it to PENDING and,
until 0017 added pending_* staging, that took the whole business offline while
the edit sat in the queue — including for `notice_message`, which shouldn't
have required review at all (see the MODERATED_FIELDS comment in models.py).

This migration:
  1. Backfills `first_approved_at` on every currently-APPROVED business, from
     its earliest APPROVED BusinessModerationLog entry if one exists, else
     `moderation_reviewed_at`, else `created_at`. Needed for the
     is_publicly_visible/public_filter() PENDING-but-previously-approved case
     added in 0017 to have anything to check.
  2. Restores public visibility for businesses currently stuck at PENDING that
     show evidence of a prior approval (a BusinessModerationLog row with
     from_status or to_status == APPROVED) — i.e. this is a re-review, not a
     first-time submission. There is no way to recover the pre-edit snapshot
     (the bug overwrote it in place, before pending_* existed to catch it), so
     the pragmatic fix is to approve the business as it stands now — which is
     also exactly what the owner asked for: no business should be taken
     offline by editing its own content, full stop.

A first-time-PENDING business (never approved before) is correctly left
alone: it is genuinely awaiting its first review and was never live, so there
is nothing to "restore".
"""

from django.db import migrations
from django.db.models import Min, Q
from django.utils import timezone


def restore_and_backfill(apps, schema_editor):
    Business = apps.get_model('business', 'Business')
    BusinessModerationLog = apps.get_model('business', 'BusinessModerationLog')

    first_approval_by_business = dict(
        BusinessModerationLog.objects.filter(to_status='APPROVED')
        .values('business_id')
        .annotate(first_approved=Min('created_at'))
        .values_list('business_id', 'first_approved')
    )
    ever_approved_ids = set(
        BusinessModerationLog.objects.filter(
            Q(to_status='APPROVED') | Q(from_status='APPROVED')
        ).values_list('business_id', flat=True)
    )

    now = timezone.now()

    for business in Business.objects.filter(moderation_status__in=['APPROVED', 'PENDING']):
        first_approved = first_approval_by_business.get(business.id)

        if business.moderation_status == 'APPROVED':
            business.first_approved_at = (
                first_approved or business.moderation_reviewed_at or business.created_at
            )
            business.save(update_fields=['first_approved_at'])
            continue

        # PENDING from here down.
        if business.id not in ever_approved_ids:
            continue  # genuine first-time submission — leave hidden, untouched.

        business.moderation_status = 'APPROVED'
        business.first_approved_at = first_approved or business.moderation_reviewed_at or business.created_at
        business.save(update_fields=['moderation_status', 'first_approved_at'])

        BusinessModerationLog.objects.create(
            business=business,
            from_status='PENDING',
            to_status='APPROVED',
            note=(
                'بازیابی خودکار: این کسب‌وکار به‌دلیل ویرایش محتوا از حالت تأییدشده '
                'خارج و از دید مشتریان مخفی شده بود؛ طبق رویه‌ی جدید، ویرایش محتوا '
                'دیگر کل کسب‌وکار را غیرفعال نمی‌کند.'
            ),
            actor=None,
            snapshot={},
        )


def noop_reverse(apps, schema_editor):
    """Leaves data alone — see 0013's reverse for the same reasoning: undoing
    this would re-hide businesses that are legitimately live again."""
    pass


class Migration(migrations.Migration):

    dependencies = [
        ('business', '0017_business_first_approved_at_business_pending_address_and_more'),
    ]

    operations = [
        migrations.RunPython(restore_and_backfill, noop_reverse),
    ]
