"""
Appointment reminder job — intended to run every few minutes via cron:

    python manage.py send_appointment_reminders

``Business.enable_reminder_sms`` and ``Business.notification_minutes_before``
existed as settings for a long time but nothing ever acted on them, so clients
never received a reminder. This command closes that gap: for every upcoming
appointment whose reminder window has opened, it sends one SMS and stamps
``reminder_sent_at`` so the same appointment is never reminded twice.

Scope: only businesses with ``reminder_delivery='PANEL'`` — the paid, automatic
channel gated behind ``auto_reminder_sms``. ``MANUAL`` businesses (the default)
send their reminders from the owner's own SIM inside the owner app; this job
must send nothing and charge nothing for them.

Options:
    --dry-run   report what would be sent without sending or stamping anything.
"""

from datetime import timedelta

from django.core.management.base import BaseCommand
from django.db.models import Max
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

        sent = skipped = failed = 0
        for appointment in due:
            if dry_run:
                self.stdout.write(
                    f"[dry-run] #{appointment.id} → {appointment.visitor.phone_number} "
                    f"({appointment.appointment_date.isoformat()})"
                )
                sent += 1
                continue

            result = self._send_one(appointment, now)
            if result == 'sent':
                sent += 1
            elif result == 'skipped':
                skipped += 1
            else:
                failed += 1

        summary = f"یادآوری‌ها: {sent} ارسال، {skipped} رد شده، {failed} ناموفق"
        self.stdout.write(self.style.SUCCESS(summary))

    def _due_appointments(self, now):
        """
        Appointments whose reminder window has opened but that have not been
        reminded yet.

        The window opens at ``appointment_date - notification_minutes_before``,
        which varies per business. Rather than express that subtraction in SQL
        (duration arithmetic on mixed types behaves differently on SQLite and
        PostgreSQL, the two backends this project uses), the query narrows to
        the widest possible window and the exact per-business cutoff is applied
        in Python. The candidate set is only "appointments starting soon", so
        this stays small.

        ``reminder_delivery`` is part of the filter, not just a display setting:
        a MANUAL business sends its reminders from the owner's own SIM through
        the owner app, so anything this job did for one would be a duplicate
        message *and* a charge against a quota the owner deliberately chose not
        to spend. Only PANEL businesses are the panel's to send.
        """
        widest = (
            Business.objects
            .filter(
                enable_reminder_sms=True,
                notification_enabled=True,
                reminder_delivery='PANEL',
            )
            .aggregate(m=Max('notification_minutes_before'))['m']
        )
        if not widest:
            return []

        candidates = (
            Appointment.objects
            .filter(
                status__in=REMINDABLE_STATUSES,
                reminder_sent_at__isnull=True,
                appointment_date__gt=now,
                appointment_date__lte=now + timedelta(minutes=widest),
                business__enable_reminder_sms=True,
                business__notification_enabled=True,
                business__reminder_delivery='PANEL',
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

    def _send_one(self, appointment, now):
        from api.sms import send_sms, signed
        from visitor.models import SmsLog

        phone = appointment.visitor.phone_number
        if not phone:
            return 'skipped'

        owner_id = appointment.business.user_id
        receipt = usage.consume_sms(owner_id)
        if not receipt:
            self.stderr.write(
                f"#{appointment.id}: اعتبار پیامک کسب‌وکار {appointment.business_id} تمام شده است"
            )
            return 'skipped'

        message = signed(
            f"⏰ یادآوری نوبت شما در {appointment.business.title}\n"
            f"تاریخ: {format_datetime(appointment.appointment_date)}"
        )

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
