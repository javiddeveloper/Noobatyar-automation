# visitor/tests_reports.py
"""
The SMS operations report: the failure-rate/per-business aggregation, the
Jalali range parsing it borrows from accounting/reports.py, the PII
discipline (message_text never appears anywhere in the output), the
permission gate, the CSV export's encoding, and the cost of a page load.

Mirrors the structure of accounting/tests_reports.py and
core/tests_dashboard.py.
"""

from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group, Permission
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.urls import reverse

from business.models import Business
from visitor import reports
from visitor.models import SmsLog

User = get_user_model()
TEHRAN = ZoneInfo('Asia/Tehran')

# Mid-afternoon Tehran, far from both local and UTC midnight — same
# convention as core/tests_dashboard.py / accounting/tests_reports.py.
NOW = datetime(2025, 6, 15, 15, 0, tzinfo=TEHRAN)


def tehran(year, month, day, hour=0, minute=0):
    return datetime(year, month, day, hour, minute, tzinfo=TEHRAN)


def make_user(phone, **kwargs):
    return User.objects.create_user(phone=phone, name=kwargs.pop('name', 'کاربر'), **kwargs)


def make_business(user, **overrides):
    defaults = dict(
        user=user, title='کسب‌وکار', phone='02112345678', address='تهران',
        default_service_duration=30, work_start_hour=9, work_end_hour=18,
    )
    defaults.update(overrides)
    return Business.objects.create(**defaults)


def _backdate(instance, field, when):
    """auto_now_add ignores create()'s value, so a second UPDATE is the only
    way to place a row in the past — same helper as the other reporting test
    modules."""
    type(instance).objects.filter(pk=instance.pk).update(**{field: when})
    instance.refresh_from_db()
    return instance


class ReportTestData(TestCase):
    def setUp(self):
        self.owner = make_user('09120000001')
        self.biz_a = make_business(self.owner, title='آرایشگاه الف')
        self.biz_b = make_business(self.owner, title='آرایشگاه ب')

    def log(self, business, status, when, error='', text='پیامک آزمایشی'):
        row = SmsLog.objects.create(
            business=business, message_text=text, status=status, error_detail=error,
        )
        return _backdate(row, 'sent_at', when)


class RangeParsingTests(ReportTestData):
    """visitor/reports.py re-exports accounting.reports.parse_range verbatim
    — this just pins down that the wiring actually works end to end, not the
    parsing logic itself (already covered by accounting/tests_reports.py)."""

    def test_defaults_to_last_30_days_ending_today(self):
        start, end, from_day, to_day = reports.parse_range('', '', NOW)
        self.assertEqual(to_day, NOW.astimezone(TEHRAN).date())
        self.assertEqual((to_day - from_day).days, 29)

    def test_bad_date_string_is_rejected(self):
        with self.assertRaises(reports.ReportRangeError):
            reports.parse_range('garbage', '', NOW)


class SummaryTests(ReportTestData):

    def test_failure_rate_hand_computed(self):
        start, end, _, _ = reports.parse_range('1404/03/20', '1404/03/25', NOW)
        window = start + timedelta(hours=1)
        self.log(self.biz_a, 'SENT', window)
        self.log(self.biz_a, 'SENT', window)
        self.log(self.biz_a, 'FAILED', window)
        # Outside the range — must not be counted.
        self.log(self.biz_a, 'FAILED', start - timedelta(days=1))

        result = reports.summary(start, end)
        self.assertEqual(result['sent'], 2)
        self.assertEqual(result['failed'], 1)
        self.assertEqual(result['total'], 3)
        self.assertAlmostEqual(result['failure_rate'], 1 / 3)

    def test_no_traffic_gives_none_rate_not_a_divide_by_zero(self):
        start, end, _, _ = reports.parse_range('1404/03/20', '1404/03/25', NOW)
        result = reports.summary(start, end)
        self.assertEqual(result['total'], 0)
        self.assertIsNone(result['failure_rate'])

    def test_business_filter_scopes_the_summary(self):
        start, end, _, _ = reports.parse_range('1404/03/20', '1404/03/25', NOW)
        window = start + timedelta(hours=1)
        self.log(self.biz_a, 'FAILED', window)
        self.log(self.biz_b, 'FAILED', window)
        self.log(self.biz_b, 'FAILED', window)

        result = reports.summary(start, end, business_id=self.biz_b.id)
        self.assertEqual(result['failed'], 2)


class FailuresByBusinessTests(ReportTestData):

    def test_split_by_business_worst_first(self):
        start, end, _, _ = reports.parse_range('1404/03/20', '1404/03/25', NOW)
        window = start + timedelta(hours=1)
        self.log(self.biz_a, 'FAILED', window)
        self.log(self.biz_b, 'FAILED', window)
        self.log(self.biz_b, 'FAILED', window)
        self.log(self.biz_b, 'SENT', window)

        rows = reports.failures_by_business(start, end)
        self.assertEqual(rows[0]['business_id'], self.biz_b.id)
        self.assertEqual(rows[0]['failed'], 2)
        self.assertEqual(rows[0]['sent'], 1)
        self.assertAlmostEqual(rows[0]['failure_rate'], 2 / 3)
        self.assertEqual(rows[1]['business_id'], self.biz_a.id)
        self.assertEqual(rows[1]['failed'], 1)

    def test_business_with_no_logs_in_range_is_absent(self):
        start, end, _, _ = reports.parse_range('1404/03/20', '1404/03/25', NOW)
        self.log(self.biz_a, 'FAILED', start + timedelta(hours=1))
        rows = reports.failures_by_business(start, end)
        self.assertEqual({row['business_id'] for row in rows}, {self.biz_a.id})


class RecentLogsTests(ReportTestData):

    def test_defaults_to_failed_only_newest_first(self):
        start, end, _, _ = reports.parse_range('1404/03/20', '1404/03/25', NOW)
        older = self.log(self.biz_a, 'FAILED', start + timedelta(hours=1), error='e1')
        newer = self.log(self.biz_a, 'FAILED', start + timedelta(hours=5), error='e2')
        self.log(self.biz_a, 'SENT', start + timedelta(hours=3))

        rows = reports.recent_logs(start, end, business_id=None)
        self.assertEqual(len(rows), 2)
        self.assertEqual(rows[0]['error_detail'], 'e2')
        self.assertEqual(rows[1]['error_detail'], 'e1')

    def test_status_filter_can_widen_to_sent(self):
        start, end, _, _ = reports.parse_range('1404/03/20', '1404/03/25', NOW)
        self.log(self.biz_a, 'FAILED', start + timedelta(hours=1))
        self.log(self.biz_a, 'SENT', start + timedelta(hours=2))

        rows = reports.recent_logs(start, end, status='SENT')
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]['status'], 'SENT')

    def test_never_exposes_message_text(self):
        """PII discipline (visitor/reports.py's own docstring): the failure
        row dict must not contain the message body under any key."""
        start, end, _, _ = reports.parse_range('1404/03/20', '1404/03/25', NOW)
        self.log(self.biz_a, 'FAILED', start + timedelta(hours=1),
                  error='invalid number', text='سلام آقای رضایی، نوبت شما لغو شد')

        rows = reports.recent_logs(start, end)
        self.assertEqual(len(rows), 1)
        self.assertNotIn('message_text', rows[0])
        self.assertNotIn('text', rows[0])
        self.assertNotIn('رضایی', str(rows[0]))
        self.assertEqual(rows[0]['error_detail'], 'invalid number')


class BuildReportTests(ReportTestData):

    def test_status_outside_choices_is_ignored(self):
        """An unrecognised status query param must not become a filter that
        silently matches nothing — it degrades to 'show failures' (the
        report's own default), not a 500 or an empty page."""
        report = reports.build_report('1404/03/20', '1404/03/25', status='bogus', now=NOW)
        self.assertEqual(report['status'], '')


class QueryCostTests(ReportTestData):
    MAX_QUERIES = 15

    def test_build_report_query_count_is_bounded_and_stable(self):
        start, end, _, _ = reports.parse_range('1404/03/01', '1404/03/25', NOW)
        for day in range(5):
            when = start + timedelta(days=day)
            self.log(self.biz_a, 'FAILED', when, error=f'err{day}')
            self.log(self.biz_b, 'SENT', when)

        with CaptureQueriesContext(connection) as small:
            reports.build_report('1404/03/01', '1404/03/25', now=NOW)
        self.assertLessEqual(len(small), self.MAX_QUERIES, [q['sql'] for q in small])

        for day in range(25):
            when = start + timedelta(days=day)
            self.log(self.biz_a, 'FAILED', when, error=f'more{day}')
            self.log(self.biz_b, 'SENT', when)

        with CaptureQueriesContext(connection) as large:
            reports.build_report('1404/03/01', '1404/03/25', now=NOW)
        self.assertEqual(len(large), len(small))


# ── Permission gate + CSV export ────────────────────────────────────────────

class PermissionGateTests(ReportTestData):

    def _staff(self, phone, with_view_smslog=False):
        user = make_user(phone)
        user.is_staff = True
        user.save(update_fields=['is_staff'])
        if with_view_smslog:
            user.user_permissions.add(Permission.objects.get(
                content_type__app_label='visitor', codename='view_smslog'))
        return User.objects.get(pk=user.pk)

    def test_holder_of_view_smslog_gets_200(self):
        staff = self._staff('09120000030', with_view_smslog=True)
        self.client.force_login(staff)
        response = self.client.get(reverse('admin:visitor_smslog_report'))
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, 'گزارش عملیات پیامک')

    def test_superadmin_gets_200(self):
        boss = make_user('09120000031')
        boss.is_staff = boss.is_superuser = True
        boss.save()
        self.client.force_login(User.objects.get(pk=boss.pk))
        response = self.client.get(reverse('admin:visitor_smslog_report'))
        self.assertEqual(response.status_code, 200)

    def test_staff_without_the_permission_gets_403(self):
        staff = self._staff('09120000032', with_view_smslog=False)
        self.client.force_login(staff)
        response = self.client.get(reverse('admin:visitor_smslog_report'))
        self.assertEqual(response.status_code, 403)

    def test_non_staff_is_redirected_to_login_not_403(self):
        outsider = make_user('09120000033')
        self.client.force_login(outsider)
        response = self.client.get(reverse('admin:visitor_smslog_report'))
        self.assertEqual(response.status_code, 302)
        self.assertIn('login', response['Location'])

    def test_csv_export_is_gated_the_same_way(self):
        staff = self._staff('09120000034', with_view_smslog=False)
        self.client.force_login(staff)
        response = self.client.get(reverse('admin:visitor_smslog_report_csv'))
        self.assertEqual(response.status_code, 403)


class CsvExportTests(ReportTestData):

    def setUp(self):
        super().setUp()
        self.staff = make_user('09120000040')
        self.staff.is_staff = True
        self.staff.save(update_fields=['is_staff'])
        self.staff.user_permissions.add(Permission.objects.get(
            content_type__app_label='visitor', codename='view_smslog'))
        self.staff = User.objects.get(pk=self.staff.pk)
        self.log(self.biz_a, 'FAILED', tehran(2025, 6, 15, 10), error='شماره نامعتبر است')

    def test_csv_response_starts_with_utf8_bom(self):
        self.client.force_login(self.staff)
        response = self.client.get(
            reverse('admin:visitor_smslog_report_csv'),
            {'from': '1404/03/25', 'to': '1404/03/25'},
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.content[:3], b'\xef\xbb\xbf')

    def test_csv_content_has_error_detail_not_message_text(self):
        self.client.force_login(self.staff)
        response = self.client.get(
            reverse('admin:visitor_smslog_report_csv'),
            {'from': '1404/03/25', 'to': '1404/03/25'},
        )
        decoded = response.content.decode('utf-8-sig')
        self.assertIn('شماره نامعتبر است', decoded)
        self.assertIn('آرایشگاه الف', decoded)
        self.assertNotIn('پیامک آزمایشی', decoded)  # the message body — must never leak

    def test_csv_content_type_and_filename(self):
        self.client.force_login(self.staff)
        response = self.client.get(
            reverse('admin:visitor_smslog_report_csv'),
            {'from': '1404/03/25', 'to': '1404/03/25'},
        )
        self.assertIn('text/csv', response['Content-Type'])
        self.assertIn('attachment', response['Content-Disposition'])

    def test_invalid_range_returns_400_not_500(self):
        self.client.force_login(self.staff)
        response = self.client.get(
            reverse('admin:visitor_smslog_report_csv'),
            {'from': 'garbage', 'to': ''},
        )
        self.assertEqual(response.status_code, 400)
