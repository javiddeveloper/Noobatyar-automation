# core/tests_segments.py
"""
The audience segment builder's query layer (core/segments.py) and the three
things this phase's brief called out as non-negotiable:

  * opted-out visitors excluded by default, with the exclusion count visible
    (never a number that silently shrank);
  * a segment count/export never costs a query per row;
  * every export is written to core.models.AudienceSegmentExport before the
    CSV goes out, with the real row count and who ran it.
"""

from datetime import timedelta

from django.contrib.auth import get_user_model
from django.contrib.auth.models import Permission
from django.core.cache import cache
from django.db import connection
from django.test import TestCase
from django.test.utils import CaptureQueriesContext
from django.urls import reverse
from django.utils import timezone

from accounting.models import Plan, Subscription
from accounting.usage import _wallet_key
from appointment.models import Appointment
from business.models import Business
from core import segments
from core.models import AudienceSegment, AudienceSegmentExport
from visitor.models import Visitor

User = get_user_model()


def make_user(phone, **kwargs):
    return User.objects.create_user(phone=phone, name=kwargs.pop('name', 'کاربر'), **kwargs)


def make_business(user, **overrides):
    defaults = dict(
        user=user, title='کسب‌وکار', phone='02112345678', address='تهران',
        default_service_duration=30, work_start_hour=9, work_end_hour=18,
        category='BEAUTY_SALON', moderation_status=Business.MODERATION_APPROVED,
    )
    defaults.update(overrides)
    return Business.objects.create(**defaults)


def make_visitor(phone, name='مراجع', opted_out=False):
    return Visitor.objects.create(full_name=name, phone_number=phone, marketing_opt_out=opted_out)


def make_appointment(business, visitor, status='COMPLETED', when=None):
    return Appointment.objects.create(
        business=business, visitor=visitor, status=status,
        appointment_date=when or timezone.now(),
    )


def grant(user, *labels):
    perms = []
    for label in labels:
        app_label, codename = label.split('.')
        perms.append(Permission.objects.get(content_type__app_label=app_label, codename=codename))
    user.user_permissions.add(*perms)


# ── Visitor-side filters ────────────────────────────────────────────────────

class VisitorFilterTests(TestCase):
    def setUp(self):
        self.owner = make_user('09130000001')
        self.biz_a = make_business(self.owner, title='الف', category='BEAUTY_SALON')
        self.biz_b = make_business(self.owner, title='ب', category='DOCTOR')

    def test_business_scope(self):
        v1 = make_visitor('09131110001')
        v2 = make_visitor('09131110002')
        make_appointment(self.biz_a, v1)
        make_appointment(self.biz_b, v2)

        qs = segments.visitor_queryset({'business_ids': [self.biz_a.id]})
        self.assertEqual(set(qs.values_list('id', flat=True)), {v1.id})

    def test_business_category_scope(self):
        v1 = make_visitor('09131110003')
        v2 = make_visitor('09131110004')
        make_appointment(self.biz_a, v1)
        make_appointment(self.biz_b, v2)

        qs = segments.visitor_queryset({'business_category': 'DOCTOR'})
        self.assertEqual(set(qs.values_list('id', flat=True)), {v2.id})

    def test_min_appointment_count(self):
        frequent = make_visitor('09131110005')
        rare = make_visitor('09131110006')
        make_appointment(self.biz_a, frequent)
        make_appointment(self.biz_a, frequent)
        make_appointment(self.biz_a, rare)

        qs = segments.visitor_queryset({'min_appointment_count': 2})
        self.assertEqual(set(qs.values_list('id', flat=True)), {frequent.id})

    def test_has_status_any_no_show(self):
        no_show_visitor = make_visitor('09131110007')
        clean_visitor = make_visitor('09131110008')
        make_appointment(self.biz_a, no_show_visitor, status='COMPLETED')
        make_appointment(self.biz_a, no_show_visitor, status='NO_SHOW')
        make_appointment(self.biz_a, clean_visitor, status='COMPLETED')

        qs = segments.visitor_queryset({'has_status': 'NO_SHOW'})
        self.assertEqual(set(qs.values_list('id', flat=True)), {no_show_visitor.id})

    def test_all_status_every_appointment_completed(self):
        all_completed = make_visitor('09131110009')
        mixed = make_visitor('09131110010')
        make_appointment(self.biz_a, all_completed, status='COMPLETED')
        make_appointment(self.biz_a, all_completed, status='COMPLETED')
        make_appointment(self.biz_a, mixed, status='COMPLETED')
        make_appointment(self.biz_a, mixed, status='NO_SHOW')

        qs = segments.visitor_queryset({'all_status': 'COMPLETED'})
        self.assertEqual(set(qs.values_list('id', flat=True)), {all_completed.id})

    def test_not_booked_in_n_days_requires_prior_history(self):
        gone_quiet = make_visitor('09131110011')
        never_booked = make_visitor('09131110012')
        recent = make_visitor('09131110013')
        make_appointment(self.biz_a, gone_quiet, when=timezone.now() - timedelta(days=90))
        make_appointment(self.biz_a, recent, when=timezone.now() - timedelta(days=1))

        qs = segments.visitor_queryset({'not_booked_days': 60})
        ids = set(qs.values_list('id', flat=True))
        self.assertIn(gone_quiet.id, ids)
        self.assertNotIn(recent.id, ids)
        self.assertNotIn(never_booked.id, ids)  # never booked != "gone quiet"

    def test_date_range_first_vs_last_appointment(self):
        visitor = make_visitor('09131110014')
        make_appointment(self.biz_a, visitor, when=timezone.now() - timedelta(days=100))
        make_appointment(self.biz_a, visitor, when=timezone.now() - timedelta(days=1))

        recent_cutoff = (timezone.now() - timedelta(days=10)).date()
        # Filtering on "first appointment" after a recent cutoff should
        # exclude this visitor (their first booking was 100 days ago).
        qs_first = segments.visitor_queryset({
            'appointment_date_field': 'first', 'appointment_date_from': recent_cutoff,
        })
        self.assertNotIn(visitor.id, set(qs_first.values_list('id', flat=True)))
        # ...but filtering on "last appointment" should include them.
        qs_last = segments.visitor_queryset({
            'appointment_date_field': 'last', 'appointment_date_from': recent_cutoff,
        })
        self.assertIn(visitor.id, set(qs_last.values_list('id', flat=True)))


class MarketingOptOutTests(TestCase):
    def setUp(self):
        self.owner = make_user('09130000002')
        self.biz = make_business(self.owner)
        self.opted_out = make_visitor('09131110020', opted_out=True)
        self.opted_in = make_visitor('09131110021', opted_out=False)
        make_appointment(self.biz, self.opted_out)
        make_appointment(self.biz, self.opted_in)

    def test_excluded_by_default(self):
        qs = segments.visitor_queryset({})
        ids = set(qs.values_list('id', flat=True))
        self.assertIn(self.opted_in.id, ids)
        self.assertNotIn(self.opted_out.id, ids)

    def test_included_when_toggle_off(self):
        qs = segments.visitor_queryset({}, exclude_opted_out=False)
        ids = set(qs.values_list('id', flat=True))
        self.assertIn(self.opted_out.id, ids)

    def test_count_breakdown_shows_excluded_number_not_a_silent_shrink(self):
        counts = segments.count_visitor_segment({})
        self.assertEqual(counts['total_before_consent'], 2)
        self.assertEqual(counts['opted_out_excluded'], 1)
        self.assertEqual(counts['included'], 1)


# ── Owner-side filters ──────────────────────────────────────────────────────

class OwnerFilterTests(TestCase):
    def setUp(self):
        self.plan_active = Plan.objects.create(name='حرفه‌ای', price=300_000, duration_value=3, duration_unit='month')
        self.plan_other = Plan.objects.create(name='ویژه', price=600_000, duration_value=6, duration_unit='month')

    def test_never_purchased(self):
        no_business_user = make_user('09140000001')  # not an "owner" at all
        owner_no_sub = make_user('09140000002')
        make_business(owner_no_sub)

        ids = set(segments.owner_ids_for_segment({'subscription_status': 'never'}))
        self.assertIn(owner_no_sub.id, ids)
        self.assertNotIn(no_business_user.id, ids)  # never an owner in the first place

    def test_active_vs_expired(self):
        active_owner = make_user('09140000003')
        make_business(active_owner)
        Subscription.objects.create(
            user=active_owner, plan=self.plan_active, status='active',
            ends_at=timezone.now() + timedelta(days=10),
        )

        expired_owner = make_user('09140000004')
        make_business(expired_owner)
        Subscription.objects.create(
            user=expired_owner, plan=self.plan_active, status='expired',
            ends_at=timezone.now() - timedelta(days=5),
        )

        active_ids = set(segments.owner_ids_for_segment({'subscription_status': 'active'}))
        expired_ids = set(segments.owner_ids_for_segment({'subscription_status': 'expired'}))
        self.assertIn(active_owner.id, active_ids)
        self.assertNotIn(expired_owner.id, active_ids)
        self.assertIn(expired_owner.id, expired_ids)
        self.assertNotIn(active_owner.id, expired_ids)

    def test_plan_filter(self):
        owner = make_user('09140000005')
        make_business(owner)
        Subscription.objects.create(
            user=owner, plan=self.plan_active, status='active', ends_at=timezone.now() + timedelta(days=10),
        )
        ids_match = set(segments.owner_ids_for_segment({'plan_id': self.plan_active.id}))
        ids_no_match = set(segments.owner_ids_for_segment({'plan_id': self.plan_other.id}))
        self.assertIn(owner.id, ids_match)
        self.assertNotIn(owner.id, ids_no_match)

    def test_expiry_within_days(self):
        soon = make_user('09140000006')
        make_business(soon)
        Subscription.objects.create(
            user=soon, plan=self.plan_active, status='active', ends_at=timezone.now() + timedelta(days=5),
        )
        far = make_user('09140000007')
        make_business(far)
        Subscription.objects.create(
            user=far, plan=self.plan_active, status='active', ends_at=timezone.now() + timedelta(days=200),
        )

        ids = set(segments.owner_ids_for_segment({'expiry_within_days': 14}))
        self.assertIn(soon.id, ids)
        self.assertNotIn(far.id, ids)

    def test_min_businesses_and_category(self):
        multi = make_user('09140000008')
        make_business(multi, category='DOCTOR')
        make_business(multi, category='DOCTOR')
        single = make_user('09140000009')
        make_business(single, category='DOCTOR')

        ids = set(segments.owner_ids_for_segment({'min_businesses': 2, 'business_category': 'DOCTOR'}))
        self.assertIn(multi.id, ids)
        self.assertNotIn(single.id, ids)

    def test_low_wallet_filter_batches_a_single_redis_read(self):
        cache.clear()
        low = make_user('09140000010')
        make_business(low)
        high = make_user('09140000011')
        make_business(high)
        cache.set(_wallet_key(low.id), 2, timeout=None)
        cache.set(_wallet_key(high.id), 50, timeout=None)

        ids = set(segments.owner_ids_for_segment({'low_wallet_below': 5}))
        self.assertIn(low.id, ids)
        self.assertNotIn(high.id, ids)

    def test_low_wallet_filter_fails_closed_when_cache_is_unreachable(self):
        """Regression: cache.get_many() on a Redis outage returns {} — the
        exact same value a genuinely-empty batch of wallet keys produces
        (django-redis's IGNORE_EXCEPTIONS swallows the connection error).
        Reading that {} as "balance 0 for everyone" used to make a "low
        wallet" filter silently match the entire owner base during an
        outage, with a plausible row count and no warning in the export or
        its audit row. It must raise instead of guessing.
        """
        cache.clear()
        make_business(make_user('09140000013'))
        make_business(make_user('09140000014'))

        from unittest.mock import patch
        with patch.object(segments.cache, 'get', return_value=None):
            with self.assertRaises(segments.SegmentFilterError):
                segments.owner_ids_for_segment({'low_wallet_below': 5})

    def test_export_owner_rows_has_no_email_column(self):
        owner = make_user('09140000012', name='مالک تست')
        make_business(owner)
        header, rows, count = segments.export_rows('owner', {})
        self.assertEqual(header, ('نام', 'شماره تلفن'))
        rows = list(rows)
        self.assertEqual(count, len(rows))
        self.assertTrue(all(len(row) == 2 for row in rows))


# ── Query cost ───────────────────────────────────────────────────────────────

class QueryCostTests(TestCase):
    """A segment count must not cost a query per row — see core/segments.py's
    module docstring, 'Correctness rule'."""

    def setUp(self):
        owner = make_user('09150000001')
        biz = make_business(owner)
        for i in range(40):
            v = make_visitor(f'0919{i:07d}')
            make_appointment(biz, v)

    def test_visitor_count_is_a_small_fixed_number_of_queries(self):
        with CaptureQueriesContext(connection) as ctx:
            counts = segments.count_visitor_segment({'min_appointment_count': 1})
        self.assertEqual(counts['included'], 40)
        # Two counts (with/without the opt-out filter) — not 40, not "a query
        # per visitor".
        self.assertLessEqual(len(ctx.captured_queries), 3)


# ── Form parsing ─────────────────────────────────────────────────────────────

class ParseFiltersTests(TestCase):
    def test_bad_jalali_date_raises_segment_filter_error(self):
        with self.assertRaises(segments.SegmentFilterError):
            segments.parse_visitor_filters({'appointment_date_from': 'not-a-date'})

    def test_bad_int_raises_segment_filter_error(self):
        with self.assertRaises(segments.SegmentFilterError):
            segments.parse_owner_filters({'min_businesses': 'lots'})

    def test_valid_params_round_trip(self):
        filters = segments.parse_visitor_filters({
            'business_ids': '1,2,3', 'min_appointment_count': '2', 'not_booked_days': '30',
        })
        self.assertEqual(filters['business_ids'], [1, 2, 3])
        self.assertEqual(filters['min_appointment_count'], 2)
        self.assertEqual(filters['not_booked_days'], 30)


# ── Views: permission gate, audit log ────────────────────────────────────────

class SegmentBuilderViewTests(TestCase):
    def setUp(self):
        self.staff = make_user('09160000001', is_staff=True)
        self.client.force_login(self.staff)

    def test_403_with_no_relevant_permission(self):
        response = self.client.get(reverse('admin:core_segment_builder'), {'kind': 'visitor'})
        self.assertEqual(response.status_code, 403)

    def test_200_with_visitor_permission(self):
        grant(self.staff, 'visitor.view_visitor')
        response = self.client.get(reverse('admin:core_segment_builder'), {'kind': 'visitor'})
        self.assertEqual(response.status_code, 200)

    def test_owner_tab_needs_its_own_permission(self):
        grant(self.staff, 'visitor.view_visitor')
        response = self.client.get(reverse('admin:core_segment_builder'), {'kind': 'owner'})
        self.assertEqual(response.status_code, 403)

    def test_preview_rows_never_leak_pii_without_export_pii(self):
        """Regression: the live-count preview used to render real name+phone
        rows to anyone with plain visitor.view_visitor/api.view_user — the
        same permission Support holds per setup_admin_roles.py — completely
        bypassing core.export_pii and leaving no AudienceSegmentExport row.
        A staff member without export_pii could page through 20-row previews
        under different filters to reconstruct an arbitrarily large phone
        list, entirely outside the audited export path.
        """
        owner = make_user('09160000005')
        make_business(owner)
        visitor = make_visitor('09161110099', name='نام محرمانه')

        grant(self.staff, 'visitor.view_visitor')  # deliberately NOT core.export_pii
        response = self.client.get(
            reverse('admin:core_segment_builder'), {'kind': 'visitor'})
        self.assertEqual(response.status_code, 200)

        body = response.content.decode('utf-8')
        self.assertNotIn(visitor.phone_number, body)
        self.assertNotIn('نام محرمانه', body)
        self.assertEqual(list(response.context['preview_rows']), [])
        self.assertEqual(AudienceSegmentExport.objects.count(), 0)

    def test_preview_rows_render_for_a_viewer_with_export_pii(self):
        """The count-only restriction must not also hide rows from someone
        who legitimately holds export_pii — only from someone who doesn't."""
        owner = make_user('09160000006')
        make_business(owner)
        visitor = make_visitor('09161110098', name='مرئی برای این کاربر')

        grant(self.staff, 'visitor.view_visitor', 'core.export_pii')
        response = self.client.get(
            reverse('admin:core_segment_builder'), {'kind': 'visitor'})
        self.assertEqual(response.status_code, 200)
        self.assertIn(visitor.phone_number, response.content.decode('utf-8'))


class SegmentExportPermissionAndAuditTests(TestCase):
    def setUp(self):
        self.staff = make_user('09160000002', is_staff=True)
        self.client.force_login(self.staff)
        owner = make_user('09160000003')
        biz = make_business(owner)
        self.v1 = make_visitor('09161110001')
        self.v2 = make_visitor('09161110002', opted_out=True)
        make_appointment(biz, self.v1)
        make_appointment(biz, self.v2)

    def test_export_requires_dedicated_pii_permission_not_just_view_visitor(self):
        grant(self.staff, 'visitor.view_visitor')
        response = self.client.get(reverse('admin:core_segment_export'), {'kind': 'visitor'})
        self.assertEqual(response.status_code, 403)

    def test_export_writes_audit_row_with_real_row_count(self):
        grant(self.staff, 'visitor.view_visitor', 'core.export_pii')
        self.assertEqual(AudienceSegmentExport.objects.count(), 0)

        response = self.client.get(reverse('admin:core_segment_export'), {'kind': 'visitor'})
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response['Content-Disposition'].startswith('attachment;'))
        # BOM present so Excel opens Persian text correctly.
        self.assertTrue(response.content.startswith(b'\xef\xbb\xbf'))

        export = AudienceSegmentExport.objects.get()
        self.assertEqual(export.exported_by_id, self.staff.id)
        self.assertEqual(export.kind, 'visitor')
        # v2 is opted out and excluded by default -> only v1 in the export.
        self.assertEqual(export.row_count, 1)

        body = response.content.decode('utf-8-sig')
        self.assertIn(self.v1.phone_number, body)
        self.assertNotIn(self.v2.phone_number, body)

    def test_export_fails_closed_not_500_when_wallet_cache_unreachable(self):
        """The low-wallet filter's cache.get() raises SegmentFilterError on an
        unreachable cache (see tests_segments' low-wallet fail-closed test);
        the export view has to catch it and return a clean 400 with no
        AudienceSegmentExport row, not let it become an unhandled 500 with a
        stack trace in the response.
        """
        from unittest.mock import patch
        grant(self.staff, 'api.view_user', 'core.export_pii')
        with patch.object(segments.cache, 'get', return_value=None):
            response = self.client.get(
                reverse('admin:core_segment_export'),
                {'kind': 'owner', 'low_wallet_below': '5'},
            )
        self.assertEqual(response.status_code, 400)
        self.assertEqual(AudienceSegmentExport.objects.count(), 0)


class SegmentSaveAndRunTests(TestCase):
    def setUp(self):
        self.staff = make_user('09160000004', is_staff=True)
        self.client.force_login(self.staff)
        grant(self.staff, 'visitor.view_visitor', 'core.add_audiencesegment')

    def test_save_then_run_round_trips_filters(self):
        response = self.client.post(reverse('admin:core_segment_save'), {
            'kind': 'visitor', 'name': 'گروه آزمایشی', 'min_appointment_count': '2',
            'exclude_opted_out': '1', 'return_qs': '',
        })
        self.assertEqual(response.status_code, 302)
        segment = AudienceSegment.objects.get(name='گروه آزمایشی')
        self.assertEqual(segment.kind, 'visitor')
        self.assertEqual(segment.definition['min_appointment_count'], '2')
        self.assertIsNone(segment.last_run_at)

        run_response = self.client.get(reverse('admin:core_segment_run', args=[segment.id]))
        self.assertEqual(run_response.status_code, 302)
        self.assertIn('min_appointment_count=2', run_response.url)
        segment.refresh_from_db()
        self.assertIsNotNone(segment.last_run_at)

    def test_save_owner_kind_is_not_silently_downgraded_to_visitor(self):
        """Regression test: the save form's `kind` field is only ever submitted
        as POST data (the form action carries no querystring — see
        templates/admin/core/segment_builder.html), so NobatyarAdminSite.
        _segment_kind() must read request.POST, not just request.GET, or every
        'owner' save silently falls through to the 'visitor' default and the
        owner-only filters (e.g. low_wallet_below) get parsed as visitor
        filters and dropped instead of saved."""
        grant(self.staff, 'api.view_user')
        response = self.client.post(reverse('admin:core_segment_save'), {
            'kind': 'owner', 'name': 'مالکان کیف‌پول کم', 'low_wallet_below': '5', 'return_qs': '',
        })
        self.assertEqual(response.status_code, 302)
        segment = AudienceSegment.objects.get(name='مالکان کیف‌پول کم')
        self.assertEqual(segment.kind, 'owner')
        self.assertEqual(segment.definition, {'low_wallet_below': '5'})
