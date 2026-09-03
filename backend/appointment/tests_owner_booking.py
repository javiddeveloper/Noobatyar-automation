"""
appointment/tests_owner_booking.py

Who an owner is allowed to book an appointment *for*.

A Visitor row is global and identified by phone number, and `Visitor.user` is
only set when an owner adds the contact by hand. Someone who booked through the
client web app therefore has no `user` link to the business owner at all — so
the owner's create-appointment path, which filtered on `user=owner`, refused to
book a second appointment for a customer the owner could already see in their
own visitor list and had already served once.
"""

from datetime import timedelta

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from accounting.models import Plan, Subscription
from business.models import Business
from visitor.models import Visitor

from .models import Appointment

User = get_user_model()


class OwnerBookingForWebVisitorTests(TestCase):
    """An owner may book for anyone who has booked at their business before."""

    def setUp(self):
        self.owner = User.objects.create_user(
            phone='09120000010', password='x', name='مالک',
        )
        self.business = Business.objects.create(
            user=self.owner,
            title='کسب‌وکار تست',
            phone='02100000000',
            address='تهران',
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=17,
            moderation_status=Business.MODERATION_APPROVED,
        )
        # An active plan with unlimited monthly appointments, so the booking
        # quota gate never masks the authorization behaviour under test.
        plan = Plan.objects.create(
            name='تست', price=0, duration_value=1, duration_unit='month',
            features={'monthly_appointments': -1},
        )
        Subscription.objects.create(
            user=self.owner, plan=plan, status='active',
            ends_at=timezone.now() + timedelta(days=30),
        )
        # Booked online: no `user` link to the owner, which is the normal state
        # for every self-booked customer.
        self.web_visitor = Visitor.objects.create(
            full_name='مشتری وب', phone_number='09390000010',
        )
        self.client = APIClient()
        self.client.force_authenticate(user=self.owner)

    def _book(self, visitor, when=None):
        when = when or (timezone.now() + timedelta(days=1)).replace(
            hour=10, minute=0, second=0, microsecond=0,
        )
        return self.client.post(
            reverse('appointments:appointment-create'),
            data={
                'business_id': self.business.id,
                'visitor_id': visitor.id,
                # The endpoint takes Unix epoch milliseconds, not ISO-8601.
                'appointment_date': int(when.timestamp() * 1000),
            },
            format='json',
        )

    def test_owner_can_rebook_a_customer_who_first_booked_on_the_web(self):
        """The bug this file exists for.

        The customer books online, is served, and the owner then tries to book
        their next appointment from the app.
        """
        past = timezone.now() - timedelta(days=7)
        Appointment.objects.create(
            business=self.business,
            visitor=self.web_visitor,
            appointment_date=past,
            status='COMPLETED',
        )

        response = self._book(self.web_visitor)

        self.assertNotEqual(
            response.status_code, 404,
            'owner was told the customer does not exist while re-booking '
            'someone who already has a completed appointment with them',
        )
        self.assertIn(response.status_code, (200, 201))
        self.assertTrue(
            Appointment.objects.filter(
                business=self.business, visitor=self.web_visitor,
            ).count() == 2
        )

    def test_owner_can_still_book_a_contact_they_created_themselves(self):
        """The pre-existing path must keep working."""
        own_contact = Visitor.objects.create(
            user=self.owner, full_name='مشتری دستی', phone_number='09390000011',
        )
        response = self._book(own_contact)
        self.assertIn(response.status_code, (200, 201))

    def test_owner_cannot_book_for_a_stranger(self):
        """Widening the rule must not make every visitor on the platform bookable.

        Someone with no appointment at this owner's business and no contact
        link to them stays invisible — otherwise an owner could attach bookings
        to arbitrary phone numbers and probe who exists on the platform.
        """
        stranger = Visitor.objects.create(
            full_name='غریبه', phone_number='09390000012',
        )
        response = self._book(stranger)
        self.assertEqual(response.status_code, 404)
