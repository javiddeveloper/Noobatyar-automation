# accounting/tests_reports.py
"""
The financial reporting page: date-range parsing, the revenue/plan/conversion
queries, the MRR/ARPU/churn formulas (hand-computed against fixed data), the
Tehran day-boundary rules, the permission gate, the CSV export's encoding, and
the cost of a page load.

Mirrors the structure and the two things worth pinning down from
core/tests_dashboard.py:

  * **Only status='success' rows are revenue** — pending/failed/cancelled
    must never leak into a total, only into the conversion-rate report.
  * **Tehran day boundaries, not UTC** — the same 20:30-24:00 local window
    that would land on the wrong UTC date is tested here again because this
    report builds its own range from user-typed Jalali dates rather than
    reusing metrics.window_starts().
"""

from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.urls import reverse

from accounting import reports
from accounting.models import AddOnPack, AddOnPurchase, Plan, Subscription, Transaction

User = get_user_model()
TEHRAN = ZoneInfo('Asia/Tehran')

# Same fixed "now" convention as core/tests_dashboard.py: mid-afternoon
# Tehran, far from both local and UTC midnight so nothing depends on the
# wall clock the suite happens to run at.
NOW = datetime(2025, 6, 15, 15, 0, tzinfo=TEHRAN)


def tehran(year, month, day, hour=0, minute=0):
    return datetime(year, month, day, hour, minute, tzinfo=TEHRAN)


def make_user(phone, **kwargs):
    return User.objects.create_user(phone=phone, name=kwargs.pop('name', 'کاربر'), **kwargs)


def _backdate(instance, field, when):
    """Force an auto_now_add timestamp to a chosen value via a second UPDATE,
    same helper as core/tests_dashboard.py — create() ignores any value
    passed for an auto_now_add field, and .save() would refire auto_now on
    sibling fields."""
    type(instance).objects.filter(pk=instance.pk).update(**{field: when})
    instance.refresh_from_db()
    return instance


class ReportTestData(TestCase):
    def setUp(self):
        self.user = make_user('09120000001')
        self.plan = Plan.objects.create(
            name='حرفه‌ای', price=300_000, duration_value=1, duration_unit='month')
        self.sms_pack = AddOnPack.objects.create(
            name='بسته پیامک', price=50_000, kind=AddOnPack.KIND_SMS, sms_amount=500)
        self.apt_pack = AddOnPack.objects.create(
            name='بسته نوبت', price=80_000, kind=AddOnPack.KIND_APPOINTMENT,
            appointment_amount=200)
        self._seq = 0

    def transaction(self, amount, when, status='success', plan=None, user=None):
        self._seq += 1
        row = Transaction.objects.create(
            user=user or self.user, plan=plan or self.plan, amount=amount, status=status,
            track_id=f'T{self._seq}', order_id=f'OT{self._seq}')
        return _backdate(row, 'created_at', when)

    def purchase(self, pack, amount, when, status='success', user=None):
        self._seq += 1
        row = AddOnPurchase.objects.create(
            user=user or self.user, pack=pack, amount=amount, status=status,
            track_id=f'A{self._seq}', order_id=f'OA{self._seq}')
        return _backdate(row, 'created_at', when)

    def subscription(self, plan, started_at, ends_at, status='active', user=None):
        row = Subscription.objects.create(
            user=user or self.user, plan=plan, status=status, ends_at=ends_at)
        return _backdate(row, 'started_at', started_at)


# ── Jalali <-> Gregorian ──────────────────────────────────────────────────────

class JalaliConversionTests(TestCase):

    def test_known_round_trip_date(self):
        # 1400/01/01 is the well-known reference date: 2021-03-21.
        gy, gm, gd = reports._jalali_to_gregorian(1400, 1, 1)
        self.assertEqual((gy, gm, gd), (2021, 3, 21))

    def test_round_trips_through_to_jalali(self):
        from api import jalali
        for jy, jm, jd in [(1404, 5, 18), (1403, 12, 30), (1400, 1, 1), (1390, 6, 31)]:
            gy, gm, gd = reports._jalali_to_gregorian(jy, jm, jd)
            back = jalali.to_jalali(gy, gm, gd)
            self.assertEqual(back, (jy, jm, jd))

    def test_parse_jalali_date_accepts_dash_or_slash(self):
        self.assertEqual(reports.parse_jalali_date('1400/01/01'), reports.parse_jalali_date('1400-01-01'))

    def test_parse_jalali_date_rejects_garbage(self):
        for bad in ('', 'abc', '1400/13/01', '1400/01', '1400/01/01/01'):
            with self.assertRaises(reports.ReportRangeError):
                reports.parse_jalali_date(bad)


# ── Range parsing / Tehran boundaries ─────────────────────────────────────────

class RangeParsingTests(TestCase):

    def test_defaults_to_last_30_days_ending_today(self):
        start, end, from_day, to_day = reports.parse_range('', '', NOW)
        self.assertEqual(to_day, tehran(2025, 6, 15).date())
        self.assertEqual(from_day, tehran(2025, 5, 17).date())
        self.assertEqual(start, tehran(2025, 5, 17))
        self.assertEqual(end, tehran(2025, 6, 16))  # exclusive: local midnight the day AFTER `to`

    def test_explicit_range_is_inclusive_of_both_local_days(self):
        # 1404/03/25 -> 2025-06-15 ; 1404/03/20 -> 2025-06-10 (Jalali dates
        # picked so the round trip is easy to sanity check independently).
        start, end, from_day, to_day = reports.parse_range('1404/03/20', '1404/03/25', NOW)
        self.assertEqual(from_day, tehran(2025, 6, 10).date())
        self.assertEqual(to_day, tehran(2025, 6, 15).date())
        self.assertEqual(start, tehran(2025, 6, 10))
        self.assertEqual(end, tehran(2025, 6, 16))

    def test_start_after_end_is_rejected(self):
        with self.assertRaises(reports.ReportRangeError):
            reports.parse_range('1404/03/25', '1404/03/20', NOW)

    def test_bad_date_string_is_rejected(self):
        with self.assertRaises(reports.ReportRangeError):
            reports.parse_range('not-a-date', '', NOW)


class DayBucketingTests(ReportTestData):
    """Same boundary rows as core/tests_dashboard.py's DayBucketingTests: two
    payments 30 minutes apart in wall-clock time but landing on the same UTC
    date, which only a genuinely tz-aware bucketing tells apart."""

    def test_just_after_local_midnight_is_inside_a_single_day_range(self):
        self.transaction(100_000, tehran(2025, 6, 15, 0, 15))
        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.revenue_breakdown(start, end)
        self.assertEqual(result['total'], 100_000)

    def test_just_before_local_midnight_falls_outside_the_next_days_range(self):
        self.transaction(100_000, tehran(2025, 6, 14, 23, 45))
        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)  # the 15th only
        result = reports.revenue_breakdown(start, end)
        self.assertEqual(result['total'], 0)

    def test_daily_series_buckets_by_local_day(self):
        self.transaction(100_000, tehran(2025, 6, 15, 0, 15))
        self.transaction(70_000, tehran(2025, 6, 14, 23, 45))
        start, end, _, _ = reports.parse_range('1404/03/24', '1404/03/25', NOW)
        result = reports.revenue_breakdown(start, end)
        self.assertEqual(result['series']['total'], [70_000, 100_000])


# ── Revenue breakdown ──────────────────────────────────────────────────────────

class RevenueBreakdownTests(ReportTestData):

    def test_only_successful_payments_are_revenue(self):
        self.transaction(500_000, tehran(2025, 6, 15, 10), status='success')
        self.transaction(999_000, tehran(2025, 6, 15, 11), status='pending')
        self.transaction(888_000, tehran(2025, 6, 15, 12), status='failed')
        self.transaction(777_000, tehran(2025, 6, 15, 13), status='cancelled')
        self.purchase(self.sms_pack, 50_000, tehran(2025, 6, 15, 10), status='success')
        self.purchase(self.sms_pack, 666_000, tehran(2025, 6, 15, 11), status='pending')

        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.revenue_breakdown(start, end)
        self.assertEqual(result['total'], 550_000)
        self.assertEqual(result['subscription'], 500_000)
        self.assertEqual(result['sms_pack'], 50_000)

    def test_split_by_source_sums_once(self):
        self.transaction(500_000, tehran(2025, 6, 15, 10))
        self.purchase(self.sms_pack, 50_000, tehran(2025, 6, 15, 10))
        self.purchase(self.apt_pack, 80_000, tehran(2025, 6, 15, 10))

        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.revenue_breakdown(start, end)
        self.assertEqual(result['subscription'], 500_000)
        self.assertEqual(result['sms_pack'], 50_000)
        self.assertEqual(result['appointment_pack'], 80_000)
        self.assertEqual(result['total'], 630_000)

    def test_rows_outside_the_range_are_excluded(self):
        self.transaction(500_000, tehran(2025, 5, 1, 10))  # well before the range
        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.revenue_breakdown(start, end)
        self.assertEqual(result['total'], 0)


# ── Per-plan sales ──────────────────────────────────────────────────────────────

class PlanSalesTests(ReportTestData):

    def test_count_and_revenue_per_plan(self):
        other_plan = Plan.objects.create(
            name='شروع', price=100_000, duration_value=1, duration_unit='month')
        self.transaction(300_000, tehran(2025, 6, 15, 10), plan=self.plan)
        self.transaction(300_000, tehran(2025, 6, 15, 11), plan=self.plan)
        self.transaction(100_000, tehran(2025, 6, 15, 12), plan=other_plan)
        # Pending — must not count anywhere in this report.
        self.transaction(300_000, tehran(2025, 6, 15, 13), plan=self.plan, status='pending')

        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        rows = {row['plan']: row for row in reports.plan_sales(start, end)}
        self.assertEqual(rows['حرفه‌ای']['count'], 2)
        self.assertEqual(rows['حرفه‌ای']['revenue'], 600_000)
        self.assertEqual(rows['شروع']['count'], 1)
        self.assertEqual(rows['شروع']['revenue'], 100_000)

    def test_plans_with_no_sales_in_range_are_absent(self):
        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        self.assertEqual(reports.plan_sales(start, end), [])


# ── Payment conversion ──────────────────────────────────────────────────────────

class ConversionTests(ReportTestData):

    def test_transaction_conversion_counts_and_rate(self):
        self.transaction(100_000, tehran(2025, 6, 15, 9), status='success')
        self.transaction(100_000, tehran(2025, 6, 15, 10), status='success')
        self.transaction(100_000, tehran(2025, 6, 15, 11), status='pending')
        self.transaction(100_000, tehran(2025, 6, 15, 12), status='failed')

        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.payment_conversion(start, end)['transaction']
        self.assertEqual(result['counts'], {'pending': 1, 'success': 2, 'failed': 1, 'cancelled': 0})
        self.assertEqual(result['total'], 4)
        # 2 successes out of 4 attempts, pending counted in the denominator.
        self.assertAlmostEqual(result['rate'], 0.5)

    def test_addon_purchase_conversion_is_separate_from_transaction(self):
        self.purchase(self.sms_pack, 50_000, tehran(2025, 6, 15, 9), status='success')
        self.purchase(self.sms_pack, 50_000, tehran(2025, 6, 15, 10), status='failed')

        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.payment_conversion(start, end)['addon_purchase']
        self.assertEqual(result['total'], 2)
        self.assertAlmostEqual(result['rate'], 0.5)

    def test_no_attempts_gives_none_rate_not_a_divide_by_zero(self):
        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.payment_conversion(start, end)['transaction']
        self.assertEqual(result['total'], 0)
        self.assertIsNone(result['rate'])


# ── MRR ─────────────────────────────────────────────────────────────────────────

class MrrTests(ReportTestData):

    def test_hand_computed_mrr_across_duration_units(self):
        """
        Four active subscriptions, on plans with different duration units,
        priced so the arithmetic is easy to check by hand:

          - Plan A: 1-month, 300,000 -> monthly price 300,000 (x2 subs = 600,000)
          - Plan B: 3-month, 900,000 -> monthly price 300,000
          - Plan C: 90-day,  900,000 -> months = 90/30 = 3 -> monthly 300,000
          - Plan D: 30-day,  150,000 -> months = 1        -> monthly 150,000

        Expected MRR = 600,000 + 300,000 + 300,000 + 150,000 = 1,350,000.
        A fifth, expired subscription on plan A must not contribute anything.
        """
        plan_a = self.plan  # 1-month, 300,000
        plan_b = Plan.objects.create(name='B', price=900_000, duration_value=3, duration_unit='month')
        plan_c = Plan.objects.create(name='C', price=900_000, duration_value=90, duration_unit='day')
        plan_d = Plan.objects.create(name='D', price=150_000, duration_value=30, duration_unit='day')

        u2 = make_user('09120000002')
        u3 = make_user('09120000003')
        u4 = make_user('09120000004')
        u5 = make_user('09120000005')
        u_expired = make_user('09120000006')

        self.subscription(plan_a, NOW - timedelta(days=5), NOW + timedelta(days=25))
        self.subscription(plan_a, NOW - timedelta(days=5), NOW + timedelta(days=25), user=u2)
        self.subscription(plan_b, NOW - timedelta(days=5), NOW + timedelta(days=85), user=u3)
        self.subscription(plan_c, NOW - timedelta(days=5), NOW + timedelta(days=85), user=u4)
        self.subscription(plan_d, NOW - timedelta(days=5), NOW + timedelta(days=25), user=u5)
        # Already lapsed — excluded regardless of the status column.
        self.subscription(plan_a, NOW - timedelta(days=40), NOW - timedelta(days=1),
                           status='expired', user=u_expired)

        self.assertEqual(reports.mrr(NOW), 1_350_000)

    def test_no_active_subscriptions_gives_zero(self):
        self.assertEqual(reports.mrr(NOW), 0)


# ── ARPU ────────────────────────────────────────────────────────────────────────

class ArpuTests(ReportTestData):

    def test_hand_computed_arpu_dedupes_users_across_tables(self):
        """
        Three distinct paying users in the window:
          - user 1 (self.user): one Transaction (300,000) AND one AddOnPurchase
            (50,000) -> counted once, contributes 350,000 to revenue.
          - user 2: one AddOnPurchase (80,000).
          - user 3: one Transaction (100,000), but status='pending' -> not a
            paying user and contributes nothing.

        Total revenue = 300,000 + 50,000 + 80,000 = 430,000.
        Distinct paying users = 2 (user 1 and user 2 — user 3 never paid).
        ARPU = 430,000 / 2 = 215,000.
        """
        u2 = make_user('09120000002')
        u3 = make_user('09120000003')

        self.transaction(300_000, tehran(2025, 6, 15, 9), status='success')
        self.purchase(self.sms_pack, 50_000, tehran(2025, 6, 15, 10), status='success')
        self.purchase(self.apt_pack, 80_000, tehran(2025, 6, 15, 11), status='success', user=u2)
        self.transaction(100_000, tehran(2025, 6, 15, 12), status='pending', user=u3)

        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.arpu(start, end)
        self.assertEqual(result['revenue'], 430_000)
        self.assertEqual(result['paying_users'], 2)
        self.assertAlmostEqual(result['arpu'], 215_000)

    def test_no_paying_users_gives_none_not_a_divide_by_zero(self):
        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.arpu(start, end)
        self.assertEqual(result['paying_users'], 0)
        self.assertIsNone(result['arpu'])


# ── Churn ───────────────────────────────────────────────────────────────────────

class ChurnTests(ReportTestData):

    def test_hand_computed_churn_with_and_without_renewal(self):
        """
        Window: 1404/03/20..1404/03/25 == 2025-06-10 00:00 .. 2025-06-16 00:00 Tehran.

        Four subscriptions active at the window's start (started before the
        window, ends_at after the window's start):
          - sub 1 (user A): ends_at inside the window, user A starts a NEW
            subscription 2 days later (within the grace window) -> renewed.
          - sub 2 (user B): ends_at inside the window, no new subscription
            for user B at all -> churned.
          - sub 3 (user C): ends_at inside the window, user C's new
            subscription starts 10 days later (outside the grace window)
            -> churned.
          - sub 4 (user D): ends_at AFTER the window -> not lapsed in this
            window at all, still counts toward active_at_start.

        active_at_start = 4 (all four were running at 2025-06-10 00:00).
        lapsed in window = 3 (subs 1, 2, 3).
        churned = 2 (subs 2, 3) ; renewed = 1 (sub 1).
        rate = 2 / 4 = 0.5.
        """
        u_a = make_user('09120000010')
        u_b = make_user('09120000011')
        u_c = make_user('09120000012')
        u_d = make_user('09120000013')

        window_start = tehran(2025, 6, 10, 0, 0)

        sub1_end = tehran(2025, 6, 12, 10, 0)
        self.subscription(self.plan, window_start - timedelta(days=20), sub1_end, user=u_a)
        self.subscription(self.plan, sub1_end + timedelta(days=2), sub1_end + timedelta(days=32), user=u_a)

        sub2_end = tehran(2025, 6, 13, 10, 0)
        self.subscription(self.plan, window_start - timedelta(days=20), sub2_end, user=u_b)
        # No renewal for user B at all.

        sub3_end = tehran(2025, 6, 14, 10, 0)
        self.subscription(self.plan, window_start - timedelta(days=20), sub3_end, user=u_c)
        self.subscription(self.plan, sub3_end + timedelta(days=10), sub3_end + timedelta(days=40), user=u_c)

        self.subscription(self.plan, window_start - timedelta(days=20),
                           tehran(2025, 6, 20, 10, 0), user=u_d)  # ends after the window

        start, end, _, _ = reports.parse_range('1404/03/20', '1404/03/25', NOW)
        result = reports.churn(start, end)
        self.assertEqual(result['active_at_start'], 4)
        self.assertEqual(result['lapsed'], 3)
        self.assertEqual(result['churned'], 2)
        self.assertEqual(result['renewed'], 1)
        self.assertAlmostEqual(result['rate'], 0.5)

    def test_no_lapsed_subscriptions_and_no_active_gives_none_rate(self):
        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.churn(start, end)
        self.assertEqual(result['lapsed'], 0)
        self.assertIsNone(result['rate'])

    def test_no_lapsed_but_some_active_gives_zero_rate(self):
        self.subscription(self.plan, NOW - timedelta(days=5), NOW + timedelta(days=25))
        start, end, _, _ = reports.parse_range('1404/03/25', '1404/03/25', NOW)
        result = reports.churn(start, end)
        self.assertEqual(result['lapsed'], 0)
        self.assertEqual(result['rate'], 0.0)


# ── Deposit note ──────────────────────────────────────────────────────────────

class DepositNoteTests(ReportTestData):

    def test_build_report_includes_the_deposit_disclosure_in_persian(self):
        report = reports.build_report('', '', NOW)
        self.assertIn('بیعانه', report['deposit_note'])
        self.assertIn('appointment/payment/zibal_deposit.py', report['deposit_note'])


# ── Query cost ──────────────────────────────────────────────────────────────────

class QueryCostTests(ReportTestData):
    MAX_QUERIES = 20

    def test_build_report_query_count_is_bounded_and_stable(self):
        start, end, _, _ = reports.parse_range('1404/03/01', '1404/03/25', NOW)
        for day in range(5):
            when = start + timedelta(days=day)
            self.transaction(10_000, when)
            self.purchase(self.sms_pack, 5_000, when)

        with CaptureQueriesContext(connection) as small:
            reports.build_report('1404/03/01', '1404/03/25', NOW)
        self.assertLessEqual(len(small), self.MAX_QUERIES, [q['sql'] for q in small])

        for day in range(20):
            when = start + timedelta(days=day % 25)
            self.transaction(10_000, when)
            self.purchase(self.sms_pack, 5_000, when)

        with CaptureQueriesContext(connection) as large:
            reports.build_report('1404/03/01', '1404/03/25', NOW)
        # More rows and a wider date range must not cost more queries: no
        # per-day loop, no per-row loop.
        self.assertEqual(len(large), len(small))


# ── Permission gate + the page itself ────────────────────────────────────────

class PermissionGateTests(ReportTestData):

    @classmethod
    def setUpTestData(cls):
        from django.core.management import call_command
        call_command('setup_admin_roles', verbosity=0)

    def _staff(self, phone, group=None):
        user = make_user(phone)
        user.is_staff = True
        user.save(update_fields=['is_staff'])
        if group:
            user.groups.add(Group.objects.get(name=group))
        return User.objects.get(pk=user.pk)

    def test_finance_gets_200(self):
        finance = self._staff('09120000030', 'Finance')
        self.client.force_login(finance)
        response = self.client.get(reverse('admin:accounting_financial_report'))
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, 'گزارش مالی')

    def test_superadmin_gets_200(self):
        boss = make_user('09120000031')
        boss.is_staff = boss.is_superuser = True
        boss.save()
        self.client.force_login(User.objects.get(pk=boss.pk))
        response = self.client.get(reverse('admin:accounting_financial_report'))
        self.assertEqual(response.status_code, 200)

    def test_moderator_gets_403(self):
        moderator = self._staff('09120000032', 'Moderator')
        self.client.force_login(moderator)
        response = self.client.get(reverse('admin:accounting_financial_report'))
        self.assertEqual(response.status_code, 403)

    def test_support_gets_403(self):
        """Support has view on Subscription/AddOnPurchase but NOT Transaction —
        the report is gated on view_transaction specifically, so Support must
        still be refused even though it can see some accounting models."""
        support = self._staff('09120000033', 'Support')
        self.client.force_login(support)
        response = self.client.get(reverse('admin:accounting_financial_report'))
        self.assertEqual(response.status_code, 403)

    def test_non_staff_is_redirected_to_login_not_403(self):
        outsider = make_user('09120000034')
        self.client.force_login(outsider)
        response = self.client.get(reverse('admin:accounting_financial_report'))
        self.assertEqual(response.status_code, 302)
        self.assertIn('login', response['Location'])

    def test_csv_export_is_gated_the_same_way(self):
        moderator = self._staff('09120000035', 'Moderator')
        self.client.force_login(moderator)
        response = self.client.get(reverse('admin:accounting_financial_report_csv'))
        self.assertEqual(response.status_code, 403)


class CsvExportTests(ReportTestData):

    @classmethod
    def setUpTestData(cls):
        from django.core.management import call_command
        call_command('setup_admin_roles', verbosity=0)

    def setUp(self):
        super().setUp()
        self.finance = make_user('09120000040')
        self.finance.is_staff = True
        self.finance.save(update_fields=['is_staff'])
        self.finance.groups.add(Group.objects.get(name='Finance'))
        self.finance = User.objects.get(pk=self.finance.pk)
        self.transaction(300_000, tehran(2025, 6, 15, 10))

    def test_csv_response_starts_with_utf8_bom(self):
        self.client.force_login(self.finance)
        response = self.client.get(
            reverse('admin:accounting_financial_report_csv'),
            {'from': '1404/03/25', 'to': '1404/03/25'},
        )
        self.assertEqual(response.status_code, 200)
        content = response.content
        # The actual byte sequence Excel checks for before trusting a CSV as
        # UTF-8 rather than the system codepage — checked as bytes, not by
        # eyeballing the file in an editor that auto-detects encoding.
        self.assertEqual(content[:3], b'\xef\xbb\xbf')

    def test_csv_persian_text_round_trips_after_stripping_the_bom(self):
        self.client.force_login(self.finance)
        response = self.client.get(
            reverse('admin:accounting_financial_report_csv'),
            {'from': '1404/03/25', 'to': '1404/03/25'},
        )
        decoded = response.content.decode('utf-8-sig')
        self.assertIn('گزارش مالی', decoded)
        self.assertIn('اشتراک‌ها', decoded)
        self.assertIn('حرفه‌ای', decoded)  # the plan name, from plan_sales rows

    def test_csv_content_type_and_filename(self):
        self.client.force_login(self.finance)
        response = self.client.get(
            reverse('admin:accounting_financial_report_csv'),
            {'from': '1404/03/25', 'to': '1404/03/25'},
        )
        self.assertIn('text/csv', response['Content-Type'])
        self.assertIn('attachment', response['Content-Disposition'])

    def test_invalid_range_returns_400_not_500(self):
        self.client.force_login(self.finance)
        response = self.client.get(
            reverse('admin:accounting_financial_report_csv'),
            {'from': 'garbage', 'to': ''},
        )
        self.assertEqual(response.status_code, 400)
