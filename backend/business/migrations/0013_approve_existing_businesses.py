"""Grandfather every pre-existing business into APPROVED.

`Business.moderation_status` defaults to PENDING, which is right for anything
created from now on. Applied to rows that already exist it would be a silent
outage: every business currently taking bookings would vanish from the public
listing, the slot endpoints and the booking page the moment this migration ran,
with no one having done anything wrong. Those businesses predate the review
process, so the only defensible starting state for them is "already cleared".

`moderation_submitted_at` is backfilled from `created_at` rather than left null
so the queue's oldest-first ordering has something real to sort by if one of
these is ever re-submitted for review.
"""

from django.db import migrations
from django.db.models import F


def approve_existing(apps, schema_editor):
    """Only ever touches rows still at the model default.

    Filtered on PENDING so this is safe to re-run after a rollback: on first
    application every row is PENDING (the field's default) and gets approved,
    exactly as intended. But a `migrate business 0012` rollback followed by a
    re-forward is a real operational sequence (bad deploy rolled back, fixed
    build redeployed), and by the time that happens some rows may have moved to
    REJECTED/SUSPENDED through real moderation decisions. An unfiltered update
    would silently republish every one of those on the redeploy, with no
    BusinessModerationLog entry to show it happened — the exact bypass this
    whole feature exists to prevent.
    """
    Business = apps.get_model('business', 'Business')
    Business.objects.filter(moderation_status='PENDING').update(
        moderation_status='APPROVED',
        moderation_submitted_at=F('created_at'),
    )


def unapprove(apps, schema_editor):
    """Reverse leaves the data alone.

    Setting these back to PENDING would take live businesses offline — the exact
    outage this migration exists to prevent. A no-op reverse is the safe choice:
    the forward migration only ever *grants* visibility, so nothing needs undoing.
    """
    pass


class Migration(migrations.Migration):

    dependencies = [
        ('business', '0012_bannedkeyword_businessmoderationlog_contentreport_and_more'),
    ]

    operations = [
        migrations.RunPython(approve_existing, unapprove),
    ]
