# core/tests_detail_views.py
"""
The two 360 pages: the query layer's numbers (core/detail_views.py) and the
permission gate that degrades per panel instead of 403ing the whole page —
same idiom core/tests_dashboard.py pins down for the dashboard.
"""

from datetime import timedelta

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Permission
from django.core.cache import cache
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from accounting.models import Plan, Subscription
from appointment.models import Appointment
from business.models import Business, BusinessModerationLog
from core import detail_views
from visitor.models import SmsLog, Visitor

User = get_user_model()


def make_user(phone, **kwargs):
    return User.objects.create_user(phone=phone, name=kwargs.pop('name', 'کاربر'), **kwargs)


def make_business(user, **overrides):
    defaults = dict(
        user=user, title='کسب‌وکار', phone='02112345678', address='تهران',
        default_service_duration=30, work_start_hour=9, work_end_hour=18,
        moderation_status=Business.MODERATION_APPROVED,
    )
    defaults.update(overrides)
    return Business.objects.create(**defaults)


def make_visitor(phone, name='مراجع'):
    return Visitor.objects.create(full_name=name, phone_number=phone)


def make_appointment(business, visitor, status='COMPLETED', when=None):
    return Appointment.objects.create(
        business=business, visitor=visitor, status=status,
        appointment_date=when or timezone.now(),
    )


def grant(user, *codenames_with_app):
    """codenames_with_app: e.g. 'api.view_user'."""
    perms = []
    for label in codenames_with_app:
        app_label, codename = label.split('.')
        perms.append(Permission.objects.get(content_type__app_label=app_label, codename=codename))
    user.user_permissions.add(*perms)


class BusinessAppointmentStatsTests(TestCase):
    def setUp(self):
        self.owner = make_user('09120000010')
        self.business = make_business(self.owner)
        self.visitor = make_visitor('09121110000')

    def test_counts_by_status_one_query(self):
        make_appointment(self.business, self.visitor, status='COMPLETED')
        make_appointment(self.business, self.visitor, status='COMPLETED')
        make_appointment(self.business, self.visitor, status='NO_SHOW')
        make_appointment(self.business, self.visitor, status='CANCELLED')

        from django.db import connection
        from django.test.utils import CaptureQueriesContext

        with CaptureQueriesContext(connection) as ctx:
            stats = detail_views.business_appointment_stats(self.business)
        self.assertEqual(len(ctx.captured_queries), 1)

        self.assertEqual(stats['total'], 4)
        # no-show rate is COMPLETED+NO_SHOW only: 1 / (2 + 1) = 1/3
        self.assertAlmostEqual(stats['no_show_rate'], 1 / 3)
        self.assertEqual(stats['no_show_eligible'], 3)

    def test_no_show_rate_none_when_nothing_eligible(self):
        make_appointment(self.business, self.visitor, status='CANCELLED')
        stats = detail_views.business_appointment_stats(self.business)
        self.assertIsNone(stats['no_show_rate'])


class BusinessCustomersQuerysetTests(TestCase):
    def setUp(self):
        self.owner = make_user('09120000011')
        self.business_a = make_business(self.owner, title='الف')
        self.business_b = make_business(self.owner, title='ب')
        self.v1 = make_visitor('09121110001')
        self.v2 = make_visitor('09121110002')

    def test_distinct_per_business_with_counts(self):
        make_appointment(self.business_a, self.v1, when=timezone.now() - timedelta(days=5))
        make_appointment(self.business_a, self.v1, when=timezone.now() - timedelta(days=1))
        make_appointment(self.business_a, self.v2)
        make_appointment(self.business_b, self.v2)  # different business, must not count for A

        qs = detail_views.business_customers_queryset(self.business_a)
        by_id = {row.id: row for row in qs}
        self.assertEqual(set(by_id), {self.v1.id, self.v2.id})
        self.assertEqual(by_id[self.v1.id].appointment_count, 2)
        self.assertEqual(by_id[self.v2.id].appointment_count, 1)

    def test_visitor_with_zero_appointments_excluded(self):
        make_visitor('09121119999')  # never booked anywhere
        qs = detail_views.business_customers_queryset(self.business_a)
        self.assertEqual(qs.count(), 0)


class PermissionDegradeTests(TestCase):
    """Missing one accounting permission hides one panel, not the page."""

    def setUp(self):
        self.owner = make_user('09120000012')
        self.viewer = make_user('09120000013', is_staff=True)
        self.plan = Plan.objects.create(name='پایه', price=100_000, duration_value=1, duration_unit='month')
        Subscription.objects.create(user=self.owner, plan=self.plan, ends_at=timezone.now() + timedelta(days=30))

    def test_no_accounting_permission_hides_subscriptions_and_wallet(self):
        can = detail_views.permissions_for_user(self.viewer)
        self.assertFalse(can['subscriptions'])
        self.assertFalse(can['wallet'])

        detail = detail_views.build_user_detail(self.owner, self.viewer)
        self.assertNotIn('subscriptions', detail)
        self.assertNotIn('wallet', detail)
        # overview always present regardless of permissions
        self.assertIn('overview', detail)

    def test_granting_subscription_view_unlocks_the_panel(self):
        grant(self.viewer, 'accounting.view_subscription')
        detail = detail_views.build_user_detail(self.owner, self.viewer)
        self.assertIn('subscriptions', detail)
        self.assertEqual(len(detail['subscriptions']), 1)


class UserDetailViewTests(TestCase):
    def setUp(self):
        self.owner = make_user('09120000014')
        self.staff = make_user('09120000015', is_staff=True)
        self.client.force_login(self.staff)

    def test_403_without_view_user_permission(self):
        response = self.client.get(reverse('admin:core_user_detail', args=[self.owner.id]))
        self.assertEqual(response.status_code, 403)

    def test_200_with_permission(self):
        grant(self.staff, 'api.view_user')
        response = self.client.get(reverse('admin:core_user_detail', args=[self.owner.id]))
        self.assertEqual(response.status_code, 200)


class BusinessDetailViewTests(TestCase):
    def setUp(self):
        self.owner = make_user('09120000016')
        self.business = make_business(self.owner)
        self.staff = make_user('09120000017', is_staff=True)
        self.client.force_login(self.staff)

    def test_403_without_view_business_permission(self):
        response = self.client.get(reverse('admin:core_business_detail', args=[self.business.id]))
        self.assertEqual(response.status_code, 403)

    def test_200_with_permission_and_panels_gated(self):
        grant(self.staff, 'business.view_business')
        response = self.client.get(reverse('admin:core_business_detail', args=[self.business.id]))
        self.assertEqual(response.status_code, 200)
        # No appointment/sms/customers/moderation permission granted, so those
        # panels are absent from the payload the view built.
        detail = response.context['detail']
        self.assertNotIn('appointment_stats', detail)
        self.assertNotIn('sms_usage', detail)

    def test_customers_page_needs_both_permissions(self):
        grant(self.staff, 'business.view_business')
        response = self.client.get(reverse('admin:core_business_customers', args=[self.business.id]))
        self.assertEqual(response.status_code, 403)
        grant(self.staff, 'visitor.view_visitor')
        response = self.client.get(reverse('admin:core_business_customers', args=[self.business.id]))
        self.assertEqual(response.status_code, 200)


class UserActivityIsOwnerSideOnlyTests(TestCase):
    """VisitorActivity filtered to actor_user=this owner — not a full log."""

    def test_only_rows_this_user_acted_on_are_returned(self):
        from visitor.models import VisitorActivity

        owner = make_user('09120000018')
        other_owner = make_user('09120000019')
        visitor = make_visitor('09121110003')
        VisitorActivity.objects.create(
            visitor=visitor, action='ARCHIVED_BY_OWNER', actor_type='OWNER', actor_user=owner,
        )
        VisitorActivity.objects.create(
            visitor=visitor, action='ARCHIVED_BY_OWNER', actor_type='OWNER', actor_user=other_owner,
        )
        rows = list(detail_views.user_activity(owner))
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0].actor_user_id, owner.id)
