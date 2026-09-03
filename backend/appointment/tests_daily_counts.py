"""
appointment/tests_daily_counts.py

The window `daily-counts` reports over.

`days` counts backwards from today; `days_ahead` extends the window forward.
The forward half exists for the owner home screen's month card — "next month"
is a question about bookings that have not happened yet, and the endpoint used
to drop every future day during gap-fill even though the query had already
fetched them.
"""

from datetime import timedelta

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from business.models import Business
from visitor.models import Visitor

from .models import Appointment

User = get_user_model()


class DailyCountsWindowTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user(
            phone='09120000020', password='x', name='مالک',
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
        self.visitor = Visitor.objects.create(
            full_name='مشتری', phone_number='09390000020',
        )
        self.client = APIClient()
        self.client.force_authenticate(user=self.owner)

    def _appointment(self, days_from_today):
        when = timezone.now() + timedelta(days=days_from_today)
        return Appointment.objects.create(
            business=self.business,
            visitor=self.visitor,
            appointment_date=when.replace(hour=12, minute=0),
        )

    def _counts(self, **params):
        response = self.client.get(
            reverse('appointments:appointment-daily-counts'),
            {'business_id': self.business.id, **params},
        )
        self.assertEqual(response.status_code, 200)
        return {row['date']: row['count'] for row in response.json()['data']}

    def test_days_alone_covers_the_past_window_only(self):
        """Default behaviour, unchanged: the 7-day trend chart's contract."""
        self._appointment(-2)
        self._appointment(+3)  # future, must not appear

        counts = self._counts(days=7)

        self.assertEqual(len(counts), 7)
        today = timezone.localdate()
        self.assertIn((today - timedelta(days=2)).isoformat(), counts)
        self.assertEqual(counts[(today - timedelta(days=2)).isoformat()], 1)
        self.assertNotIn((today + timedelta(days=3)).isoformat(), counts)

    def test_days_ahead_extends_the_window_into_the_future(self):
        """The month card's case: a booking next week has to be reported."""
        self._appointment(+3)

        counts = self._counts(days=7, days_ahead=10)

        today = timezone.localdate()
        future = (today + timedelta(days=3)).isoformat()
        self.assertEqual(len(counts), 17)
        self.assertIn(future, counts)
        self.assertEqual(counts[future], 1)

    def test_window_is_bounded_at_both_ends(self):
        """Widening forward must not turn into "every booking ever"."""
        self._appointment(+40)   # beyond the requested forward window
        self._appointment(-40)   # beyond the requested backward window

        counts = self._counts(days=7, days_ahead=5)

        self.assertEqual(len(counts), 12)
        self.assertEqual(sum(counts.values()), 0)

    def test_non_numeric_params_fall_back_instead_of_erroring(self):
        counts = self._counts(days='abc', days_ahead='xyz')
        self.assertEqual(len(counts), 7)
