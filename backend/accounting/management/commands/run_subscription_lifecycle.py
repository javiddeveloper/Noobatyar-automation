"""
Subscription lifecycle job — intended to run periodically (e.g. hourly cron):

    python manage.py run_subscription_lifecycle

It performs three things:

  1. Renewal reminders — SMS to owners whose subscription ends within
     ``REMINDER_DAYS`` days and who haven't been reminded yet.
  2. Grace-period expiry — subscriptions past ``ends_at + GRACE_DAYS`` are marked
     ``expired``.
  3. Graceful downgrade — for each just-expired user, excess businesses are
     locked (kept, not deleted) to match the new (baseline) quota.

Tunable via env: SUBSCRIPTION_REMINDER_DAYS, SUBSCRIPTION_GRACE_DAYS.
"""

import os
from datetime import timedelta

from django.core.management.base import BaseCommand
from django.utils import timezone

from accounting.models import Subscription

REMINDER_DAYS = int(os.getenv("SUBSCRIPTION_REMINDER_DAYS", "3"))
GRACE_DAYS = int(os.getenv("SUBSCRIPTION_GRACE_DAYS", "3"))


class Command(BaseCommand):
    help = "ارسال یادآوری تمدید، انقضای اشتراک‌ها با مهلت، و قفل کسب‌وکارهای مازاد"

    def handle(self, *args, **options):
        now = timezone.now()
        self._send_renewal_reminders(now)
        self._expire_and_downgrade(now)
        self.stdout.write(self.style.SUCCESS("چرخه‌ی عمر اشتراک‌ها اجرا شد"))

    # ── 1. Renewal reminders ─────────────────────────────────────────────────
    def _send_renewal_reminders(self, now):
        from api.sms import send_sms

        window_end = now + timedelta(days=REMINDER_DAYS)
        due = Subscription.objects.filter(
            status="active",
            reminder_sent=False,
            ends_at__gt=now,
            ends_at__lte=window_end,
        ).select_related("user", "plan")

        for sub in due:
            phone = getattr(sub.user, "phone", None)
            if phone:
                days = max(1, sub.days_left())
                msg = (
                    f"نوبت‌یار ⏰\n"
                    f"اشتراک «{sub.plan.name}» شما تا {days} روز دیگر به پایان می‌رسد.\n"
                    f"برای جلوگیری از قطع خدمات، همین حالا تمدید کنید."
                )
                try:
                    send_sms(phone, msg)
                except Exception as exc:  # noqa: BLE001 — reminder must not crash the job
                    self.stderr.write(f"reminder SMS failed for {phone}: {exc}")
            sub.reminder_sent = True
            sub.save(update_fields=["reminder_sent"])

        self.stdout.write(f"یادآوری تمدید: {due.count() if hasattr(due, 'count') else 0} مورد")

    # ── 2 & 3. Expiry + graceful downgrade ───────────────────────────────────
    def _expire_and_downgrade(self, now):
        from business.services import sync_locks

        cutoff = now - timedelta(days=GRACE_DAYS)
        expired_subs = Subscription.objects.filter(
            status="active",
            ends_at__lte=cutoff,
        ).select_related("user")

        affected_user_ids = set()
        count = 0
        for sub in expired_subs:
            sub.status = "expired"
            sub.save(update_fields=["status"])
            affected_user_ids.add(sub.user_id)
            count += 1

        # Re-sync business locks now that these users have dropped to baseline.
        for user in _users_by_ids(affected_user_ids):
            sync_locks(user)

        self.stdout.write(f"اشتراک منقضی‌شده: {count} مورد، قفل کسب‌وکار برای {len(affected_user_ids)} کاربر")


def _users_by_ids(user_ids):
    if not user_ids:
        return []
    from django.contrib.auth import get_user_model
    User = get_user_model()
    return list(User.objects.filter(id__in=user_ids))
