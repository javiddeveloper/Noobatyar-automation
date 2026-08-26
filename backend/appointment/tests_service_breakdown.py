"""
appointment/tests_service_breakdown.py

Coverage for ServiceBreakdownView (appointment/views/service_breakdown_view.py),
the aggregation endpoint behind the owner dashboard's per-service donut chart
(docs/OWNER_WEB_PLAN.md ۹.۱, gap #3).
"""

from datetime import datetime, timezone as dt_timezone

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from business.models import Business
from visitor.models import Visitor

from .models import Appointment

User = get_user_model()


def ms(year, month, day):
    return int(datetime(year, month, day, tzinfo=dt_timezone.utc).timestamp() * 1000)


class ServiceBreakdownTests(TestCase):
    URL = '/api/appointment/service-breakdown/'

    def setUp(self):
        self.owner = User.objects.create_user(phone='09120000011', password='x', name='مالک تفکیک')
        self.visitor = Visitor.objects.create(full_name='مشتری تفکیک', phone_number='09350000011')
        self.business = Business.objects.create(
            user=self.owner,
            title='آرایشگاه تست',
            phone='02100000000',
            address='تهران',
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=17,
            services=['کوتاهی مو', 'رنگ مو'],
        )
        self.client = APIClient()
        self.client.force_authenticate(user=self.owner)

    def _appointment(self, selected_services='', local_dt=None, status='WAITING'):
        return Appointment.objects.create(
            business=self.business,
            visitor=self.visitor,
            appointment_date=timezone.make_aware(local_dt or datetime(2025, 4, 15, 12, 0)),
            selected_services=selected_services,
            status=status,
        )

    def _get(self, **params):
        params.setdefault('business_id', self.business.id)
        return self.client.get(self.URL, params)

    # ── normal case ──────────────────────────────────────────────────────

    def test_normal_breakdown_counts_and_reconciles(self):
        self._appointment('کوتاهی مو')
        self._appointment('کوتاهی مو')
        self._appointment('رنگ مو')

        response = self._get()
        self.assertEqual(response.status_code, 200)
        data = response.json()['data']

        self.assertEqual(data['total_appointments'], 3)
        self.assertEqual(
            data['services'],
            [{'name': 'کوتاهی مو', 'count': 2}, {'name': 'رنگ مو', 'count': 1}],
        )
        self.assertEqual(data['no_service_count'], 0)
        self.assertEqual(data['removed_service_count'], 0)
        # The invariant the endpoint promises: nothing dropped, nothing double-counted.
        reconciled = sum(s['count'] for s in data['services']) + data['no_service_count'] + data['removed_service_count']
        self.assertEqual(reconciled, data['total_appointments'])

    def test_multi_service_appointment_counts_once_under_first_service(self):
        self._appointment('رنگ مو,کوتاهی مو')

        response = self._get()
        data = response.json()['data']

        self.assertEqual(data['total_appointments'], 1)
        self.assertEqual(data['services'], [{'name': 'رنگ مو', 'count': 1}])
        reconciled = sum(s['count'] for s in data['services']) + data['no_service_count'] + data['removed_service_count']
        self.assertEqual(reconciled, data['total_appointments'])

    # ── appointments with no service ────────────────────────────────────

    def test_appointment_with_no_service_is_counted_not_dropped(self):
        self._appointment('')
        self._appointment('کوتاهی مو')

        response = self._get()
        data = response.json()['data']

        self.assertEqual(data['total_appointments'], 2)
        self.assertEqual(data['no_service_count'], 1)
        self.assertEqual(data['services'], [{'name': 'کوتاهی مو', 'count': 1}])

    # ── service removed from the catalog since it was picked ───────────

    def test_service_no_longer_in_catalog_is_counted_not_dropped(self):
        self._appointment('اصلاح ابرو')  # never on this business's menu

        response = self._get()
        data = response.json()['data']

        self.assertEqual(data['total_appointments'], 1)
        self.assertEqual(data['removed_service_count'], 1)
        self.assertEqual(data['services'], [])

    # ── business with no appointments ───────────────────────────────────

    def test_business_with_no_appointments_returns_zeros(self):
        response = self._get()
        self.assertEqual(response.status_code, 200)
        data = response.json()['data']

        self.assertEqual(data['total_appointments'], 0)
        self.assertEqual(data['services'], [])
        self.assertEqual(data['no_service_count'], 0)
        self.assertEqual(data['removed_service_count'], 0)

    # ── ownership ────────────────────────────────────────────────────────

    def test_another_owners_business_is_not_visible(self):
        stranger = User.objects.create_user(phone='09120000012', password='x', name='غریبه')
        other_business = Business.objects.create(
            user=stranger,
            title='کسب‌وکار دیگری',
            phone='02100000001',
            address='تهران',
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=17,
            services=['ماساژ'],
        )
        Appointment.objects.create(
            business=other_business,
            visitor=self.visitor,
            appointment_date=timezone.make_aware(datetime(2025, 4, 15, 12, 0)),
            selected_services='ماساژ',
            status='WAITING',
        )

        response = self._get(business_id=other_business.id)
        self.assertEqual(response.status_code, 200)
        data = response.json()['data']
        self.assertEqual(data['total_appointments'], 0)
        self.assertEqual(data['services'], [])

    # ── date range ───────────────────────────────────────────────────────

    def test_date_range_scopes_the_breakdown(self):
        self._appointment('کوتاهی مو', local_dt=datetime(2025, 1, 1, 10, 0))
        self._appointment('رنگ مو', local_dt=datetime(2025, 4, 15, 10, 0))

        response = self._get(date_from=ms(2025, 4, 1), date_to=ms(2025, 4, 30))
        data = response.json()['data']

        self.assertEqual(data['total_appointments'], 1)
        self.assertEqual(data['services'], [{'name': 'رنگ مو', 'count': 1}])

    # ── validation ───────────────────────────────────────────────────────

    def test_missing_business_id_returns_400(self):
        response = self.client.get(self.URL)
        self.assertEqual(response.status_code, 400)

    def test_invalid_business_id_returns_400(self):
        response = self.client.get(self.URL, {'business_id': 'not-a-number'})
        self.assertEqual(response.status_code, 400)

    def test_malformed_date_from_returns_400(self):
        response = self._get(date_from='not-a-timestamp')
        self.assertEqual(response.status_code, 400)

    def test_malformed_date_to_returns_400(self):
        response = self._get(date_to='not-a-timestamp')
        self.assertEqual(response.status_code, 400)
