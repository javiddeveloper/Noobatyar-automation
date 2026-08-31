"""
Appointment reminder job — intended to run every few minutes via cron:

    python manage.py send_appointment_reminders

``Business.enable_reminder_sms`` and ``Business.notification_minutes_before``
existed as settings for a long time but nothing ever acted on them, so clients
never received a reminder. This command closes that gap: for every upcoming
appointment whose reminder window has opened, it sends one SMS and stamps
``reminder_sent_at`` so the same appointment is never reminded twice.

Three channels, deliberately independent:

* **The client's SMS** — only for businesses on ``reminder_delivery='PANEL'``,
  the paid automatic channel gated behind ``auto_reminder_sms``. ``MANUAL``
  businesses (the default) send their reminders from the owner's own SIM inside
  the owner app; this job must send nothing and charge nothing for them.
  Stamped with ``reminder_sent_at``.
* **The owner's push** — free, so it goes out for *every* business with
  notifications on, whichever way the client is reminded. Stamped with
  ``reminder_push_sent_at``. A business on MANUAL depends on this push: it is
  the prompt to go and text the client.
* **The client's push** — also free, but a plan-tier perk: only businesses
  whose plan already includes ``auto_reminder_sms`` (the same entitlement
  that unlocks PANEL client SMS) get it, independent of which
  ``reminder_delivery`` they actually chose — same "free, so it goes out
  regardless of the SMS channel" reasoning as the owner's push above.
  Stamped with ``reminder_visitor_push_sent_at``.

Options:
    --dry-run   report what would be sent without sending or stamping anything.
"""

from datetime import timedelta

from django.core.management.base import BaseCommand
from django.db.models import Max, Q
from django.utils import timezone

from accounting import usage
from api.jalali import format_datetime
from appointment.models import Appointment
from business.models import Business

# Statuses worth reminding about: the appointment is live and still expected.
REMINDABLE_STATUSES = ('WAITING', 'CONFIRMED')

# Ignore appointments whose reminder window opened long ago (e.g. after an
# outage) so a restarted job does not blast out a backlog of stale reminders.
MAX_LATENESS = timedelta(hours=2)


class Command(BaseCommand):
    help = "ارسال پیامک یادآوری برای نوبت‌های نزدیک"

    def add_arguments(self, parser):
        parser.add_argument(
            '--dry-run',
            action='store_true',
            help="فقط گزارش بده، پیامکی ارسال نکن",
        )

    def handle(self, *args, **options):
        dry_run = options['dry_run']
        now = timezone.now()

        due = self._due_appointments(now)

        sent = skipped = failed = pushed = visitor_pushed = 0
        for appointment in due:
            if dry_run:
                self.stdout.write(
                    f"[dry-run] #{appointment.id} → {appointment.visitor.phone_number} "
                    f"({appointment.appointment_date.isoformat()}) "
                    f"sms={'yes' if self._sms_due(appointment) else 'no'} "
                    f"push={'yes' if appointment.reminder_push_sent_at is None else 'no'} "
                    f"visitor_push={'yes' if self._visitor_push_due(appointment) else 'no'}"
                )
                # Count what would *actually* happen, not one-per-appointment:
                # the summary used to report an SMS for a MANUAL business that
                # the real run correctly refuses to send.
                if self._sms_due(appointment):
                    sent += 1
                if appointment.reminder_push_sent_at is None:
                    pushed += 1
                if self._visitor_push_due(appointment):
                    visitor_pushed += 1
                continue

            if appointment.reminder_push_sent_at is None:
                pushed += self._push_owner(appointment, now)

            if self._visitor_push_due(appointment):
                visitor_pushed += self._push_visitor(appointment, now)

            if not self._sms_due(appointment):
                continue

            result = self._send_one(appointment, now)
            if result == 'sent':
                sent += 1
            elif result == 'skipped':
                skipped += 1
            else:
                failed += 1

        summary = (
            f"یادآوری‌ها: {sent} پیامک ارسال، {skipped} رد شده، {failed} ناموفق، "
            f"{pushed} اعلان به اونر، {visitor_pushed} اعلان به مشتری"
        )
        self.stdout.write(self.style.SUCCESS(summary))

    @staticmethod
    def _sms_due(appointment):
        """Whether this appointment still owes the *client* an SMS.

        MANUAL businesses never do — their reminder leaves the owner's own SIM
        from inside the app, so sending here would both duplicate the message
        and spend quota the owner deliberately chose not to spend.
        """
        business = appointment.business
        return (
            appointment.reminder_sent_at is None
            and business.enable_reminder_sms
            and business.reminder_delivery == 'PANEL'
        )

    @staticmethod
    def _visitor_push_due(appointment):
        """
        Whether this appointment still owes the *client* a push reminder.

        Gated on the business's plan already including auto_reminder_sms
        (پرو/پرو پلاس and up) — the same entitlement that unlocks PANEL SMS —
        rather than on reminder_delivery itself: push is free, so a MANUAL
        business on that plan tier still gets it, same reasoning as the
        owner's own push.
        """
        from accounting.entitlements import FEATURE_AUTO_REMINDER_SMS, has_feature

        business = appointment.business
        return (
            appointment.reminder_visitor_push_sent_at is None
            and business.notification_enabled
            and has_feature(business.user_id, FEATURE_AUTO_REMINDER_SMS)
        )

    def _due_appointments(self, now):
        """
        Appointments whose reminder window has opened but that have not been
        reminded yet — on either channel.

        The window opens at ``appointment_date - notification_minutes_before``,
        which varies per business. Rather than express that subtraction in SQL
        (duration arithmetic on mixed types behaves differently on SQLite and
        PostgreSQL, the two backends this project uses), the query narrows to
        the widest possible window and the exact per-business cutoff is applied
        in Python. The candidate set is only "appointments starting soon", so
        this stays small.

        Unlike the first version of this job the filter is no longer restricted
        to PANEL businesses: a MANUAL one still needs the owner's push, which is
        free. Whether the *client* also gets an SMS is decided per appointment
        by :meth:`_sms_due`.
        """
        widest = (
            Business.objects
            .filter(notification_enabled=True)
            .aggregate(m=Max('notification_minutes_before'))['m']
        )
        if not widest:
            return []

        candidates = (
            Appointment.objects
            .filter(
                status__in=REMINDABLE_STATUSES,
                appointment_date__gt=now,
                appointment_date__lte=now + timedelta(minutes=widest),
                business__notification_enabled=True,
            )
            .filter(
                Q(reminder_sent_at__isnull=True)
                | Q(reminder_push_sent_at__isnull=True)
                | Q(reminder_visitor_push_sent_at__isnull=True)
            )
            .select_related('business', 'visitor')
            .order_by('appointment_date')
        )

        window_floor = now - MAX_LATENESS
        due = []
        for appointment in candidates:
            lead = timedelta(minutes=appointment.business.notification_minutes_before or 0)
            reminder_due = appointment.appointment_date - lead
            if window_floor <= reminder_due <= now:
                due.append(appointment)

        return due

    def _push_owner(self, appointment, now):
        """
        Notify the owner's phone through FCM. Returns 1 if any device took it.

        Stamped even when nothing was delivered — an owner with no registered
        device (push not set up, or app not installed) must not make this job
        re-check the same appointment every few minutes until it starts.
        """
        from accounting.entitlements import FEATURE_PUSH_NOTIFICATIONS, has_feature
        from api.services import push

        business = appointment.business
        delivered = 0
        if push.is_configured() and has_feature(business.user_id, FEATURE_PUSH_NOTIFICATIONS):
            body = (
                f"{appointment.visitor.full_name} — "
                f"{format_datetime(appointment.appointment_date)}"
            )
            if business.reminder_delivery != 'PANEL':
                body += "\nیادآوری را برای مشتری بفرستید"
            try:
                delivered = push.send_to_user(
                    business.user_id,
                    title=f"یادآوری نوبت · {business.title}",
                    body=body,
                    data={
                        'type': 'APPOINTMENT_REMINDER',
                        'appointment_id': appointment.id,
                        'business_id': business.id,
                        'visitor_id': appointment.visitor_id,
                    },
                )
            except Exception as exc:
                # Push is the convenience channel; the SMS below is what
                # actually carries the message. Never let it break the run.
                self.stderr.write(f"#{appointment.id}: ارسال اعلان ناموفق: {exc}")

        appointment.reminder_push_sent_at = now
        appointment.save(update_fields=['reminder_push_sent_at', 'updated_at'])
        return 1 if delivered else 0

    def _push_visitor(self, appointment, now):
        """
        Notify the client's own device through FCM. Returns 1 if any device
        took it. Stamped even when nothing was delivered, and always logged
        to PushLog (unlike the owner's push, which only stamps a timestamp) —
        this is the record a future report screen reads to show "was this
        reminder delivered by push".
        """
        from api.services import push
        from visitor.models import PushLog

        business = appointment.business
        visitor = appointment.visitor
        delivered = 0
        title = f"یادآوری نوبت · {business.title}"
        body = f"سلام {visitor.full_name}، نوبت شما در {business.title} نزدیک است."

        if push.is_configured():
            try:
                delivered = push.send_to_visitor(
                    visitor.id,
                    title=title,
                    body=body,
                    data={
                        'type': 'APPOINTMENT_REMINDER',
                        'appointment_id': appointment.id,
                        'business_id': business.id,
                    },
                )
                PushLog.objects.create(
                    business=business,
                    visitor=visitor,
                    appointment=appointment,
                    title=title,
                    body=body,
                    status='SENT' if delivered else 'FAILED',
                    error_detail='' if delivered else 'دستگاه فعالی برای این مراجع ثبت نشده است',
                )
            except Exception as exc:
                # Push is a convenience channel; the SMS above (if any) is
                # what actually carries the message. Never let it break the run.
                self.stderr.write(f"#{appointment.id}: ارسال اعلان به مشتری ناموفق: {exc}")
                PushLog.objects.create(
                    business=business,
                    visitor=visitor,
                    appointment=appointment,
                    title=title,
                    body=body,
                    status='FAILED',
                    error_detail=str(exc),
                )

        appointment.reminder_visitor_push_sent_at = now
        appointment.save(update_fields=['reminder_visitor_push_sent_at', 'updated_at'])
        return 1 if delivered else 0

    def _send_one(self, appointment, now):
        from api.sms import send_sms, signed
        from visitor.models import SmsLog

        phone = appointment.visitor.phone_number
        if not phone:
            return 'skipped'

        owner_id = appointment.business.user_id
        message = signed(
            f"⏰ یادآوری نوبت شما در {appointment.business.title}\n"
            f"تاریخ: {format_datetime(appointment.appointment_date)}"
        )

        receipt = usage.consume_sms(owner_id)
        if not receipt:
            self.stderr.write(
                f"#{appointment.id}: اعتبار پیامک کسب‌وکار {appointment.business_id} تمام شده است"
            )
            SmsLog.objects.create(
                business_id=appointment.business_id,
                visitor_id=appointment.visitor_id,
                message_text=message,
                status='SKIPPED_QUOTA',
                error_detail="اعتبار پیامک این ماه تمام شده است",
            )
            return 'skipped'

        ok, err = send_sms(phone, message)
        if not ok:
            # Failed sends are not billable — return the credit to its bucket.
            usage.refund_sms(receipt)

        SmsLog.objects.create(
            business_id=appointment.business_id,
            visitor_id=appointment.visitor_id,
            message_text=message,
            status='SENT' if ok else 'FAILED',
            error_detail=err if not ok else ""
        )

        # Stamp even on failure: the provider may still have delivered it, and
        # retrying every few minutes would be worse than missing one reminder.
        appointment.reminder_sent_at = now
        appointment.save(update_fields=['reminder_sent_at', 'updated_at'])

        return 'sent' if ok else 'failed'
