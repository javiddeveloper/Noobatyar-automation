"""
core/tests_dashboard.py

The admin dashboard: the arithmetic, the day bucketing, the permission gate and
the cost of a page load.

The two things most worth pinning down here are the ones that are wrong in a way
nobody notices for months:

  * **Day bucketing in Asia/Tehran, not UTC.** Tehran is +03:30, so every
    instant between local 20:30 and midnight belongs to a *different* UTC day.
    A naive implementation puts "today's revenue" 3.5 hours out and quietly
    files the last three and a half hours of every day under tomorrow. The
    boundary tests below place rows fifteen minutes either side of local
    midnight — both land on the same UTC date, so only a genuinely
    timezone-aware implementation separates them.

  * **Only successful payments are revenue.** A `pending` row is a bank page
    somebody closed. It belongs in the alerts, never in a headline figure.
"""

from datetime import datetime, time, timedelta
from unittest.mock import patch
from zoneinfo import ZoneInfo

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Group
from django.core.cache import cache
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.urls import reverse

from accounting.models import AddOnPack, AddOnPurchase, Plan, Subscription, Transaction
from business.models import Business
from core.dashboard import cache as dashboard_cache
from core.dashboard import metrics, panels
from visitor.models import SmsLog

User = get_user_model()
TEHRAN = ZoneInfo('Asia/Tehran')

# A fixed "now" so the windows are the same on every run and at every hour of
# the day the suite happens to be run at. Mid-afternoon Tehran, deliberately
# far from both local and UTC midnight so nothing depends on the wall clock.
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
    """Set an ``auto_now_add`` timestamp.

    ``auto_now_add`` overwrites whatever the caller passes to create(), so the
    only way to place a row in the past is a second UPDATE. Done through the
    queryset rather than instance.save() so ``auto_now`` on updated_at does not
    fire either.
    """
    type(instance).objects.filter(pk=instance.pk).update(**{field: when})
    instance.refresh_from_db()
    return instance


class DashboardTestData(TestCase):
    """Shared catalogue rows. No payments — each test creates its own."""

    def setUp(self):
        cache.clear()
        self.user = make_user('09120000001')
        self.plan = Plan.objects.create(
            name='حرفه‌ای', price=500_000, duration_value=1, duration_unit='month')
        self.sms_pack = AddOnPack.objects.create(
            name='بسته پیامک', price=50_000, kind=AddOnPack.KIND_SMS, sms_amount=500)
        self.apt_pack = AddOnPack.objects.create(
            name='بسته نوبت', price=80_000, kind=AddOnPack.KIND_APPOINTMENT,
            appointment_amount=200)
        self._seq = 0

    def transaction(self, amount, when, status='success'):
        self._seq += 1
        row = Transaction.objects.create(
            user=self.user, plan=self.plan, amount=amount, status=status,
            track_id=f'T{self._seq}', order_id=f'OT{self._seq}')
        return _backdate(row, 'created_at', when)

    def purchase(self, pack, amount, when, status='success'):
        self._seq += 1
        row = AddOnPurchase.objects.create(
            user=self.user, pack=pack, amount=amount, status=status,
            track_id=f'A{self._seq}', order_id=f'OA{self._seq}')
        return _backdate(row, 'created_at', when)


class DayBucketingTests(DashboardTestData):
    """Local midnight, not UTC midnight, separates one day from the next."""

    def test_just_after_local_midnight_counts_as_today(self):
        # 00:15 Tehran on the 15th is 20:45 UTC on the *14th*. Bucketing in UTC
        # would file this under yesterday and drop it from "امروز".
        self.transaction(100_000, tehran(2025, 6, 15, 0, 15))
        result = metrics.revenue(metrics.window_starts(NOW))
        self.assertEqual(result['total']['today'], 100_000)

    def test_just_before_local_midnight_counts_as_yesterday(self):
        # 23:45 Tehran on the 14th — thirty minutes earlier than the row above
        # and the same UTC date as it, but a different Tehran date.
        self.transaction(100_000, tehran(2025, 6, 14, 23, 45))
        result = metrics.revenue(metrics.window_starts(NOW))
        self.assertEqual(result['total']['today'], 0)
        self.assertEqual(result['total']['week'], 100_000)

    def test_the_two_boundary_rows_share_a_utc_date(self):
        """Guards the guard: if these ever stop sharing a UTC date the two
        tests above would pass against a naive UTC implementation too."""
        before = tehran(2025, 6, 14, 23, 45).astimezone(ZoneInfo('UTC')).date()
        after = tehran(2025, 6, 15, 0, 15).astimezone(ZoneInfo('UTC')).date()
        self.assertEqual(before, after)

    def test_window_starts_are_local_midnights(self):
        starts = metrics.window_starts(NOW)
        self.assertEqual(starts['today'], tehran(2025, 6, 15))
        # Inclusive of today: seven days is today plus the six before it.
        self.assertEqual(starts['week'], tehran(2025, 6, 9))
        self.assertEqual(starts['month'], tehran(2025, 5, 17))
        self.assertIsNone(starts['all'])
        for start in (starts['today'], starts['week'], starts['month']):
            self.assertEqual(start.timetz(), time(0, 0, tzinfo=TEHRAN))

    def test_chart_series_buckets_by_local_day(self):
        self.transaction(100_000, tehran(2025, 6, 15, 0, 15))
        self.transaction(70_000, tehran(2025, 6, 14, 23, 45))
        series = metrics.daily_series(metrics.window_starts(NOW), NOW)
        self.assertEqual(len(series['revenue']['total']), metrics.CHART_DAYS)
        self.assertEqual(series['revenue']['total'][-1], 100_000)   # today
        self.assertEqual(series['revenue']['total'][-2], 70_000)    # yesterday

    def test_chart_series_is_dense(self):
        """Days with no rows are zeros, not gaps — a sparse array would draw a
        line straight from one payment to the next."""
        self.transaction(100_000, tehran(2025, 6, 15, 12, 0))
        series = metrics.daily_series(metrics.window_starts(NOW), NOW)
        self.assertEqual(series['revenue']['total'][:-1], [0] * (metrics.CHART_DAYS - 1))
        self.assertEqual(len(series['labels']), metrics.CHART_DAYS)


class RevenueTests(DashboardTestData):

    def test_only_successful_payments_are_revenue(self):
        self.transaction(500_000, tehran(2025, 6, 15, 10), status='success')
        self.transaction(999_000, tehran(2025, 6, 15, 11), status='pending')
        self.transaction(888_000, tehran(2025, 6, 15, 12), status='failed')
        self.transaction(777_000, tehran(2025, 6, 15, 13), status='cancelled')
        self.purchase(self.sms_pack, 50_000, tehran(2025, 6, 15, 10), status='success')
        self.purchase(self.sms_pack, 666_000, tehran(2025, 6, 15, 11), status='pending')

        result = metrics.revenue(metrics.window_starts(NOW))
        self.assertEqual(result['total']['today'], 550_000)
        self.assertEqual(result['total']['all'], 550_000)

    def test_revenue_is_split_by_source_and_summed_once(self):
        self.transaction(500_000, tehran(2025, 6, 15, 10))
        self.purchase(self.sms_pack, 50_000, tehran(2025, 6, 15, 10))
        self.purchase(self.apt_pack, 80_000, tehran(2025, 6, 15, 10))

        result = metrics.revenue(metrics.window_starts(NOW))
        self.assertEqual(result['subscription']['today'], 500_000)
        self.assertEqual(result['sms_pack']['today'], 50_000)
        self.assertEqual(result['appointment_pack']['today'], 80_000)
        # Transaction and AddOnPurchase are disjoint tables: the total is their
        # plain sum, and nothing is counted in two buckets.
        self.assertEqual(result['total']['today'], 630_000)

    def test_windows_nest(self):
        self.transaction(100_000, tehran(2025, 6, 15, 10))    # today
        self.transaction(200_000, tehran(2025, 6, 12, 10))    # this week
        self.transaction(400_000, tehran(2025, 5, 20, 10))    # this month
        self.transaction(800_000, tehran(2025, 1, 5, 10))     # older

        total = metrics.revenue(metrics.window_starts(NOW))['total']
        self.assertEqual(total['today'], 100_000)
        self.assertEqual(total['week'], 300_000)
        self.assertEqual(total['month'], 700_000)
        self.assertEqual(total['all'], 1_500_000)

    def test_no_rows_gives_zero_not_none(self):
        """Sum() over an empty set is NULL; the cards must show ۰, not blank."""
        total = metrics.revenue(metrics.window_starts(NOW))['total']
        self.assertEqual(total, {'today': 0, 'week': 0, 'month': 0, 'all': 0})

    def test_add_on_purchases_group_by_kind(self):
        """Regression: AddOnPurchase.Meta.ordering is ['-created_at'], which
        Django appends to the GROUP BY of a values().annotate() unless the
        ordering is cleared — turning one row per kind into one row per
        purchase and losing all but the last."""
        for hour in (9, 10, 11):
            self.purchase(self.sms_pack, 50_000, tehran(2025, 6, 15, hour))
        result = metrics.revenue(metrics.window_starts(NOW))
        self.assertEqual(result['sms_pack']['today'], 150_000)


class StandingAndAlertTests(DashboardTestData):

    def test_active_subscription_count_respects_ends_at(self):
        """A lapsed row keeps status='active' until something re-syncs it, so
        the count has to check ends_at the way Subscription.is_valid() does."""
        Subscription.objects.create(user=self.user, plan=self.plan,
                                    status='active', ends_at=NOW + timedelta(days=10))
        Subscription.objects.create(user=self.user, plan=self.plan,
                                    status='active', ends_at=NOW - timedelta(days=1))
        Subscription.objects.create(user=self.user, plan=self.plan,
                                    status='expired', ends_at=NOW + timedelta(days=10))
        self.assertEqual(metrics.standing(NOW)['active_subscriptions'], 1)

    def test_stuck_payments_are_pending_and_old(self):
        self.transaction(500_000, NOW - timedelta(hours=3), status='pending')
        self.purchase(self.sms_pack, 50_000, NOW - timedelta(hours=2), status='pending')
        # Too recent — the user may still be on the bank page.
        self.transaction(500_000, NOW - timedelta(minutes=5), status='pending')
        # Not pending.
        self.transaction(500_000, NOW - timedelta(hours=3), status='success')

        result = metrics.alerts(NOW)['stuck_payments']
        self.assertEqual(result['count'], 2)
        self.assertEqual({row['kind'] for row in result['items']},
                         {'اشتراک', 'بسته افزودنی'})

    def test_expiring_subscriptions_window(self):
        Subscription.objects.create(user=self.user, plan=self.plan, status='active',
                                    ends_at=NOW + timedelta(days=3))
        Subscription.objects.create(user=self.user, plan=self.plan, status='active',
                                    ends_at=NOW + timedelta(days=30))
        Subscription.objects.create(user=self.user, plan=self.plan, status='active',
                                    ends_at=NOW - timedelta(days=1))   # already gone
        self.assertEqual(metrics.alerts(NOW)['expiring']['count'], 1)

    def test_sms_failures_are_recent_only(self):
        business = make_business(self.user)
        recent = SmsLog.objects.create(business=business, message_text='x',
                                       status='FAILED', error_detail='boom')
        _backdate(recent, 'sent_at', NOW - timedelta(hours=2))
        old = SmsLog.objects.create(business=business, message_text='x',
                                    status='FAILED', error_detail='boom')
        _backdate(old, 'sent_at', NOW - timedelta(days=3))
        sent = SmsLog.objects.create(business=business, message_text='x', status='SENT')
        _backdate(sent, 'sent_at', NOW - timedelta(hours=1))

        self.assertEqual(metrics.alerts(NOW)['sms_failures']['count'], 1)

    def test_moderation_queue_orders_oldest_first_with_null_fallback(self):
        """A business submitted before the moderation fields existed has a null
        submitted_at; it must fall back to created_at rather than sorting to an
        arbitrary end of the queue."""
        old = make_business(self.user, title='قدیمی')
        _backdate(old, 'created_at', NOW - timedelta(days=20))
        recent = make_business(self.user, title='تازه')
        Business.objects.filter(pk=recent.pk).update(
            moderation_submitted_at=NOW - timedelta(days=1))

        queue = metrics.alerts(NOW)['moderation_queue']
        self.assertEqual(queue['count'], 2)
        self.assertEqual([row['title'] for row in queue['items']], ['قدیمی', 'تازه'])

    def test_alert_lists_are_capped_but_counts_are_true(self):
        for index in range(metrics.ALERT_ROWS + 3):
            self.transaction(1_000, NOW - timedelta(hours=3), status='pending')
        result = metrics.alerts(NOW)['stuck_payments']
        self.assertEqual(result['count'], metrics.ALERT_ROWS + 3)
        self.assertEqual(len(result['items']), metrics.ALERT_ROWS)


class QueryCostTests(DashboardTestData):
    """The whole point of aggregating in SQL: the cost must not follow the data.

    An exact number would be a tripwire on every unrelated refactor, so this
    asserts a ceiling and — more importantly — that ten times the data costs
    exactly the same.
    """

    MAX_QUERIES = 30

    def _bulk(self, days):
        business = make_business(self.user)
        for day in range(days):
            when = NOW - timedelta(days=day)
            self.transaction(10_000, when)
            self.purchase(self.sms_pack, 5_000, when)
            log = SmsLog.objects.create(business=business, message_text='x', status='SENT')
            _backdate(log, 'sent_at', when)

    def test_collect_query_count_is_bounded(self):
        self._bulk(3)
        with CaptureQueriesContext(connection) as small:
            metrics.collect(NOW)
        self.assertLessEqual(len(small), self.MAX_QUERIES, [q['sql'] for q in small])

    def test_collect_query_count_does_not_grow_with_data(self):
        self._bulk(2)
        with CaptureQueriesContext(connection) as small:
            metrics.collect(NOW)

        self._bulk(30)
        with CaptureQueriesContext(connection) as large:
            metrics.collect(NOW)

        # Same number of queries against ~16x the rows and a full 30 days of
        # chart buckets: no per-day query, no per-object query.
        self.assertEqual(len(large), len(small))


class CacheTests(DashboardTestData):

    def test_second_call_is_served_from_cache(self):
        first, cached = dashboard_cache.get_payload()
        self.assertFalse(cached)
        second, cached = dashboard_cache.get_payload()
        self.assertTrue(cached)
        self.assertEqual(first, second)

    def test_force_refresh_recomputes(self):
        dashboard_cache.get_payload()
        _, cached = dashboard_cache.get_payload(force_refresh=True)
        self.assertFalse(cached)

    def test_invalidate_drops_the_entry(self):
        dashboard_cache.get_payload()
        dashboard_cache.invalidate()
        _, cached = dashboard_cache.get_payload()
        self.assertFalse(cached)

    def test_cache_outage_degrades_to_a_live_query(self):
        """django_redis runs with IGNORE_EXCEPTIONS, but a backend that raises
        anyway must cost the admin its numbers, not its index page."""
        with patch('core.dashboard.cache.cache.get', side_effect=RuntimeError('down')), \
             patch('core.dashboard.cache.cache.set', side_effect=RuntimeError('down')):
            payload, cached = dashboard_cache.get_payload()
        self.assertFalse(cached)
        self.assertIn('revenue', payload)

    def test_payload_holds_no_model_instances(self):
        """Model instances in the cache come back as stale objects minutes
        later. Everything must already be flattened to plain data."""
        self.transaction(500_000, NOW - timedelta(hours=3), status='pending')
        payload, _ = dashboard_cache.get_payload()
        for row in payload['alerts']['stuck_payments']['items']:
            self.assertIsInstance(row, dict)


class PermissionGateTests(DashboardTestData):
    """A role sees the panels its permissions already allow, and nothing else.

    Roles come from `setup_admin_roles`, so these run the real command rather
    than hand-assembling a permission set — a change to a role's table there
    should show up here.
    """

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
        # Permission results are cached on the instance after the first check.
        return User.objects.get(pk=user.pk)

    def _context(self, user):
        payload, _ = dashboard_cache.get_payload()
        return panels.build(payload, user)

    def test_superuser_sees_everything(self):
        boss = make_user('09120000009')
        boss.is_staff = boss.is_superuser = True
        boss.save()
        context = self._context(User.objects.get(pk=boss.pk))
        self.assertTrue(context['dashboard_revenue_cards'])
        self.assertTrue(context['dashboard_volume_cards'])
        self.assertIsNotNone(context['dashboard_charts']['revenue'])
        self.assertEqual(len(context['dashboard_alerts']), 4)

    def test_moderator_sees_no_money_anywhere(self):
        context = self._context(self._staff('09120000010', 'Moderator'))

        self.assertEqual(context['dashboard_revenue_cards'], [])
        self.assertIsNone(context['dashboard_charts']['revenue'])
        self.assertFalse(context['dashboard_can']['finance'])
        self.assertFalse(context['dashboard_can']['payments'])
        self.assertNotIn('stuck', [panel['key'] for panel in context['dashboard_alerts']])
        self.assertNotIn('expiring', [panel['key'] for panel in context['dashboard_alerts']])
        # …but still lands on a useful page.
        self.assertTrue(context['dashboard_any'])
        self.assertIn('queue', [panel['key'] for panel in context['dashboard_alerts']])

    def test_finance_sees_money_and_no_people(self):
        context = self._context(self._staff('09120000011', 'Finance'))
        self.assertTrue(context['dashboard_revenue_cards'])
        self.assertIsNotNone(context['dashboard_charts']['revenue'])
        # Finance has no api/business/appointment/visitor permissions at all.
        self.assertEqual(context['dashboard_volume_cards'], [])
        self.assertIsNone(context['dashboard_charts']['growth'])
        self.assertNotIn('queue', [panel['key'] for panel in context['dashboard_alerts']])

    def test_support_sees_no_subscription_revenue(self):
        context = self._context(self._staff('09120000012', 'Support'))
        self.assertFalse(context['dashboard_can']['finance'])
        self.assertEqual(context['dashboard_revenue_cards'], [])
        # It does hold view on AddOnPurchase, so the stuck-payment chase is
        # part of its job and the panel stays.
        self.assertIn('stuck', [panel['key'] for panel in context['dashboard_alerts']])

    def test_chart_payload_omits_series_the_viewer_may_not_see(self):
        """Gating is server side: a hidden series must be absent from the JSON,
        not merely undrawn."""
        context = self._context(self._staff('09120000013', 'Moderator'))
        self.assertIsNone(context['dashboard_charts']['revenue'])
        self.assertNotIn('users', context['dashboard_charts']['growth'])
        self.assertIn('businesses', context['dashboard_charts']['growth'])


class AdminIndexViewTests(DashboardTestData):
    """The page itself: it renders, it keeps the app list, and it never 403s."""

    @classmethod
    def setUpTestData(cls):
        from django.core.management import call_command
        call_command('setup_admin_roles', verbosity=0)

    def setUp(self):
        super().setUp()
        self.boss = make_user('09120000020')
        self.boss.is_staff = self.boss.is_superuser = True
        self.boss.save()
        self.transaction(500_000, NOW - timedelta(days=1))

    def test_index_renders_the_dashboard_above_the_app_list(self):
        self.client.force_login(self.boss)
        response = self.client.get(reverse('admin:index'))
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, 'نمای کلی سامانه')
        self.assertContains(response, 'درآمد کل پلتفرم')
        # The stock app list is still there — staff navigate by model.
        self.assertContains(response, 'app-accounting')

    def test_index_loads_chart_js_locally(self):
        """Never from a CDN: the production server is in Iran."""
        self.client.force_login(self.boss)
        response = self.client.get(reverse('admin:index'))
        self.assertContains(response, 'admin_custom/js/chart.umd.js')
        self.assertNotContains(response, 'cdn.jsdelivr.net')

    def test_moderator_gets_the_index_without_revenue(self):
        moderator = make_user('09120000021')
        moderator.is_staff = True
        moderator.save()
        moderator.groups.add(Group.objects.get(name='Moderator'))

        self.client.force_login(moderator)
        response = self.client.get(reverse('admin:index'))
        self.assertEqual(response.status_code, 200)
        self.assertNotContains(response, 'درآمد کل پلتفرم')
        self.assertNotContains(response, 'درآمد روزانه')
        self.assertContains(response, 'نمای کلی سامانه')

    def test_metric_failure_still_serves_the_app_list(self):
        """The index is the entry point to everything else; a broken aggregate
        must cost the numbers, not the page."""
        self.client.force_login(self.boss)
        with patch('core.dashboard.context_for', side_effect=RuntimeError('boom')):
            response = self.client.get(reverse('admin:index'))
        self.assertEqual(response.status_code, 200)
        self.assertContains(response, 'app-accounting')
        self.assertNotContains(response, 'درآمد کل پلتفرم')

    def test_refresh_view_clears_the_cache_and_redirects(self):
        self.client.force_login(self.boss)
        dashboard_cache.get_payload()
        response = self.client.get(reverse('admin:dashboard_refresh'))
        # Do not follow: rendering the index repopulates the cache and the
        # assertion below would pass for the wrong reason.
        self.assertRedirects(response, reverse('admin:index'),
                             fetch_redirect_response=False)
        _, cached = dashboard_cache.get_payload()
        self.assertFalse(cached)

    def test_refresh_view_is_staff_only(self):
        outsider = make_user('09120000022')
        self.client.force_login(outsider)
        response = self.client.get(reverse('admin:dashboard_refresh'))
        # admin_view() bounces a non-staff user to the admin login.
        self.assertEqual(response.status_code, 302)
        self.assertIn('login', response['Location'])
