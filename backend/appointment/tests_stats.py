"""
appointment/tests_stats.py

Coverage for the date_from/date_to range added to AppointmentStatsView
(appointment/views/appointment_stats_view.py), for the owner web dashboard's
"this month" KPI cards (docs/OWNER_WEB_PLAN.md ۹.۱).
"""

from datetime import datetime, timedelta, timezone as dt_timezone

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from business.models import Business
from visitor.models import Visitor

from .models import Appointment

User = get_user_model()


def ms(year, month, day):
    """Unix epoch milliseconds at UTC midnight of the given calendar day — the
    same unit appointment_query_view.py expects for date_from/date_to."""
    return int(datetime(year, month, day, tzinfo=dt_timezone.utc).timestamp() * 1000)


class AppointmentStatsDateRangeTests(TestCase):
    STATS_URL = '/api/appointment/stats/'

    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000010', password='x', name='مالک آمار')
        self.visitor = Visitor.objects.create(full_name='مشتری آمار', phone_number='09350000010')
        self.business = Business.objects.create(
            user=self.owner,
            title='کسب‌وکار آمار',
            phone='02100000000',
            address='تهران',
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=17,
        )
        self.client = APIClient()
        self.client.force_authenticate(user=self.owner)

    def _appointment(self, local_dt, status='WAITING'):
        return Appointment.objects.create(
            business=self.business,
            visitor=self.visitor,
            appointment_date=timezone.make_aware(local_dt),
            status=status,
        )

    def test_no_params_still_returns_todays_stats(self):
        """Backward compatibility: a caller sending only business_id — exactly
        what mobile's AppointmentApiService sends — must get exactly what it
        always got: today's window, nothing else."""
        local_today = timezone.localtime(timezone.now()).date()
        self._appointment(datetime.combine(local_today, datetime.min.time()).replace(hour=12))
        # Clearly outside today, in both directions.
        self._appointment(datetime.combine(local_today - timedelta(days=2), datetime.min.time()).replace(hour=12))
        self._appointment(datetime.combine(local_today + timedelta(days=2), datetime.min.time()).replace(hour=12))

        response = self.client.get(self.STATS_URL, {'business_id': self.business.id})
        self.assertEqual(response.status_code, 200)
        data = response.json()['data']
        self.assertEqual(data['total_appointments'], 1)

    def test_date_range_filters_to_the_requested_window(self):
        self._appointment(datetime(2025, 3, 1, 10, 0))   # before range
        self._appointment(datetime(2025, 3, 5, 9, 0))     # in range
        self._appointment(datetime(2025, 3, 10, 23, 0))   # in range
        self._appointment(datetime(2025, 3, 15, 10, 0))   # after range

        response = self.client.get(self.STATS_URL, {
            'business_id': self.business.id,
            'date_from': ms(2025, 3, 5),
            'date_to': ms(2025, 3, 10),
        })
        self.assertEqual(response.status_code, 200)
        data = response.json()['data']
        self.assertEqual(data['total_appointments'], 2)

    def test_date_to_is_inclusive_of_the_whole_day(self):
        """Same semantics as appointment_list: date_to's calendar day counts
        in full, not just up to its midnight."""
        self._appointment(datetime(2025, 3, 10, 23, 59))

        response = self.client.get(self.STATS_URL, {
            'business_id': self.business.id,
            'date_from': ms(2025, 3, 10),
            'date_to': ms(2025, 3, 10),
        })
        data = response.json()['data']
        self.assertEqual(data['total_appointments'], 1)

    def test_date_from_alone_is_open_ended(self):
        """date_from/date_to are independent, exactly like appointment_list —
        supplying only one must not silently reintroduce an upper/lower bound."""
        self._appointment(datetime(2025, 1, 1, 10, 0))
        self._appointment(datetime(2025, 6, 1, 10, 0))

        response = self.client.get(self.STATS_URL, {
            'business_id': self.business.id,
            'date_from': ms(2025, 3, 1),
        })
        data = response.json()['data']
        self.assertEqual(data['total_appointments'], 1)

    def test_malformed_date_from_returns_400(self):
        response = self.client.get(self.STATS_URL, {
            'business_id': self.business.id,
            'date_from': 'not-a-timestamp',
        })
        self.assertEqual(response.status_code, 400)

    def test_malformed_date_to_returns_400(self):
        response = self.client.get(self.STATS_URL, {
            'business_id': self.business.id,
            'date_to': 'not-a-timestamp',
        })
        self.assertEqual(response.status_code, 400)
