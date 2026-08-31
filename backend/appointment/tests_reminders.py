"""
Coverage for `manage.py send_appointment_reminders`.

The point of these tests is the split the command makes between two audiences:
the *client* is texted only when the business pays for the panel channel, while
the *owner* is pushed for free regardless. Getting that wrong is expensive in
both directions — a MANUAL business billed for reminders it never asked for, or
a MANUAL owner never told to go and send one.
"""

from datetime import timedelta
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.management import call_command
from django.test import TestCase
from django.utils import timezone

from api.models import DeviceToken
from business.models import Business
from visitor.models import Visitor

from .models import Appointment

User = get_user_model()


class ReminderCommandTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(
            phone='09120000009', password='x', name='مالک تست'
        )
        self.visitor = Visitor.objects.create(
            full_name='مشتری تست', phone_number='09350000009'
        )
        DeviceToken.objects.create(user=self.owner, token='tok-1', platform='ANDROID')

    def make_business(self, *, delivery='PANEL', enable_sms=True, minutes=30):
        return Business.objects.create(
            user=self.owner,
            title='کسب‌وکار تست',
            phone='02100000000',
            address='تهران',
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=17,
            notification_enabled=True,
            notification_minutes_before=minutes,
            enable_reminder_sms=enable_sms,
            reminder_delivery=delivery,
        )

    def make_appointment(self, business, *, minutes_away=10):
        return Appointment.objects.create(
            business=business,
            visitor=self.visitor,
            appointment_date=timezone.now() + timedelta(minutes=minutes_away),
            status='WAITING',
        )

    def run_command(self, *, sms_ok=True, push_configured=True, delivered=1, push_entitled=True):
        """Run the job with both outbound channels stubbed.

        push_entitled defaults True: this file's scope is the PANEL/MANUAL
        delivery split, not plan entitlements, so the owner push here is
        exercised as if always plan-entitled unless a test explicitly wants
        to check the entitlement gate itself.
        """
        with patch('api.sms.send_sms', return_value=(sms_ok, '')) as send_sms, \
                patch('api.services.push.is_configured', return_value=push_configured), \
                patch('api.services.push.send_to_user', return_value=delivered) as send_push, \
                patch('accounting.entitlements.has_feature', return_value=push_entitled), \
                patch('accounting.usage.consume_sms', return_value={'source': 'monthly'}), \
                patch('accounting.usage.refund_sms'):
            call_command('send_appointment_reminders')
        return send_sms, send_push

    def test_panel_business_texts_client_and_pushes_owner(self):
        business = self.make_business(delivery='PANEL')
        appointment = self.make_appointment(business)

        send_sms, send_push = self.run_command()

        self.assertEqual(send_sms.call_count, 1)
        self.assertEqual(send_push.call_count, 1)
        self.assertEqual(send_sms.call_args[0][0], self.visitor.phone_number)

        appointment.refresh_from_db()
        self.assertIsNotNone(appointment.reminder_sent_at)
        self.assertIsNotNone(appointment.reminder_push_sent_at)

    def test_manual_business_pushes_owner_without_texting_client(self):
        """The regression this job used to have: MANUAL businesses were skipped
        wholesale, so the owner was never prompted to send the reminder they had
        chosen to send themselves."""
        business = self.make_business(delivery='MANUAL')
        appointment = self.make_appointment(business)

        send_sms, send_push = self.run_command()

        self.assertEqual(send_sms.call_count, 0)
        self.assertEqual(send_push.call_count, 1)

        appointment.refresh_from_db()
        self.assertIsNone(appointment.reminder_sent_at)
        self.assertIsNotNone(appointment.reminder_push_sent_at)

    def test_reminder_sms_switch_off_still_pushes_owner(self):
        business = self.make_business(delivery='PANEL', enable_sms=False)
        self.make_appointment(business)

        send_sms, send_push = self.run_command()

        self.assertEqual(send_sms.call_count, 0)
        self.assertEqual(send_push.call_count, 1)

    def test_nothing_is_sent_twice(self):
        business = self.make_business(delivery='PANEL')
        self.make_appointment(business)

        self.run_command()
        send_sms, send_push = self.run_command()

        self.assertEqual(send_sms.call_count, 0)
        self.assertEqual(send_push.call_count, 0)

    def test_appointment_outside_the_lead_window_is_left_alone(self):
        """30-minute lead, appointment two hours out: not due yet."""
        business = self.make_business(delivery='PANEL', minutes=30)
        self.make_appointment(business, minutes_away=120)

        send_sms, send_push = self.run_command()

        self.assertEqual(send_sms.call_count, 0)
        self.assertEqual(send_push.call_count, 0)

    def test_notifications_disabled_business_is_skipped_entirely(self):
        business = self.make_business(delivery='PANEL')
        business.notification_enabled = False
        business.save(update_fields=['notification_enabled'])
        self.make_appointment(business)

        send_sms, send_push = self.run_command()

        self.assertEqual(send_sms.call_count, 0)
        self.assertEqual(send_push.call_count, 0)

    def test_push_is_stamped_even_when_fcm_is_not_configured(self):
        """Otherwise every run re-checks the same appointment until it starts."""
        business = self.make_business(delivery='MANUAL')
        appointment = self.make_appointment(business)

        _, send_push = self.run_command(push_configured=False)

        self.assertEqual(send_push.call_count, 0)
        appointment.refresh_from_db()
        self.assertIsNotNone(appointment.reminder_push_sent_at)
