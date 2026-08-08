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
    Business = apps.get_model('business', 'Business')
    Business.objects.update(
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
