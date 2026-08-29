"""Public-visibility gate for the client-facing business endpoints.

A business is hidden from the public surface for two entirely independent
reasons — `is_locked` (the owner's plan lapsed) and `moderation_status` (a
reviewer has not cleared it). These tests exist because the two are easy to
confuse for one another: they pin down that *both* are enforced, and equally
that neither was quietly swapped in for the other.
"""

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.core.exceptions import ValidationError
from django.test import TestCase
from django.urls import reverse

from .models import Business

User = get_user_model()

# Everything except APPROVED must stay invisible — with one deliberate
# exception not covered by this tuple: PENDING *with* first_approved_at set
# (a business re-queued by its own edit, not a first-time submission) stays
# visible on its last-approved copy. See PendingButPreviouslyApprovedTests
# below and Business.is_publicly_visible. Kept as a tuple driving subTest
# loops so a new moderation state added to the model shows up here as a single
# line rather than as three near-identical test methods.
NON_PUBLIC_STATUSES = (
    Business.MODERATION_PENDING,
    Business.MODERATION_REJECTED,
    Business.MODERATION_SUSPENDED,
)


class PublicGateTestCaseMixin:
    """Shared owner/business fixtures for the public-surface tests."""

    def setUp(self):
        super().setUp()
        # Throttle history and the slot caches both live in the default cache,
        # so a shared cache leaks state between tests (and between a test run
        # and the dev server on LocMem).
        cache.clear()
        self.owner = User.objects.create_user(
            phone='09120000001', password='x', name='مالک تست'
        )

    def make_business(self, *, title='کسب‌وکار تست', status=Business.MODERATION_APPROVED,
                      is_locked=False, first_approved_at=None):
        return Business.objects.create(
            user=self.owner,
            title=title,
            phone='02100000000',
            address='تهران',
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=17,
            moderation_status=status,
            is_locked=is_locked,
            first_approved_at=first_approved_at,
        )


class ClientBusinessListGateTests(PublicGateTestCaseMixin, TestCase):
    """GET /api/client/business/ — unapproved businesses simply do not appear."""

    def listed_ids(self):
        response = self.client.get(reverse('client-business-list'))
        self.assertEqual(response.status_code, 200)
        return [row['id'] for row in response.json()['data']['results']]

    def test_non_approved_businesses_are_absent_from_listing(self):
        for status in NON_PUBLIC_STATUSES:
            with self.subTest(status=status):
                business = self.make_business(status=status)
                self.assertNotIn(business.id, self.listed_ids())
                business.delete()

    def test_approved_but_locked_business_is_absent_from_listing(self):
        # Regression guard: approval must be *added* to the billing lock, not
        # substituted for it. A business whose owner stopped paying stays hidden
        # however cleanly it passed review.
        business = self.make_business(is_locked=True)
        self.assertNotIn(business.id, self.listed_ids())

    def test_approved_unlocked_business_is_listed(self):
        # The other half of the guard: the gate must not be so wide that nothing
        # is publishable at all.
        business = self.make_business()
        self.assertIn(business.id, self.listed_ids())

    def test_search_does_not_bypass_the_gate(self):
        # Searching by unique_code is an exact-match lookup, i.e. the closest
        # thing the listing has to a direct fetch — the extra filters must
        # narrow the gated queryset, never replace it.
        hidden = self.make_business(
            title='کسب‌وکار پنهان', status=Business.MODERATION_PENDING
        )
        response = self.client.get(
            reverse('client-business-list'), {'search': hidden.unique_code}
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()['data']['results'], [])


class ClientBusinessDetailGateTests(PublicGateTestCaseMixin, TestCase):
    """GET /api/client/business/<id>/ — a hidden business is indistinguishable
    from one that never existed, which is the point: an anonymous caller holding
    a stale link must not learn that a reviewer rejected it."""

    def detail(self, business_id):
        return self.client.get(
            reverse('client-business-detail', args=[business_id])
        )

    def test_non_approved_business_detail_returns_404(self):
        for status in NON_PUBLIC_STATUSES:
            with self.subTest(status=status):
                business = self.make_business(status=status)
                response = self.detail(business.id)
                self.assertEqual(response.status_code, 404)
                # Same body as a genuinely missing id — nothing in the response
                # hints at moderation.
                self.assertIsNone(response.json()['data'])
                business.delete()

    def test_approved_but_locked_business_detail_returns_404(self):
        business = self.make_business(is_locked=True)
        self.assertEqual(self.detail(business.id).status_code, 404)

    def test_approved_unlocked_business_detail_is_served(self):
        business = self.make_business()
        response = self.detail(business.id)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()['data']['id'], business.id)


class PublicFilterConsistencyTests(PublicGateTestCaseMixin, TestCase):
    """`public_filter()` and `is_publicly_visible` must never disagree.

    Every gated call site picks whichever of the two fits its shape (queryset vs
    loaded instance), so a divergence between them would show up as one endpoint
    hiding a business while another serves it.
    """

    def test_filter_and_property_agree_for_every_combination(self):
        from django.utils import timezone

        for status in (Business.MODERATION_APPROVED,) + NON_PUBLIC_STATUSES:
            for is_locked in (False, True):
                for first_approved_at in (None, timezone.now()):
                    with self.subTest(
                        status=status, is_locked=is_locked,
                        first_approved_at=first_approved_at,
                    ):
                        business = self.make_business(
                            status=status, is_locked=is_locked,
                            first_approved_at=first_approved_at,
                        )
                        via_filter = Business.objects.filter(
                            Business.public_filter(), pk=business.pk
                        ).exists()
                        self.assertEqual(via_filter, business.is_publicly_visible)
                        business.delete()


class PendingButPreviouslyApprovedTests(PublicGateTestCaseMixin, TestCase):
    """A business re-queued by its own edit (business/services.py
    stage_pending_moderated_fields) is not the same as one still waiting for
    its first review — it has a last-approved copy that is still true, and
    stays visible on it. This is the fix for the bug where setting an
    emergency notice (or any moderated-field edit) took a live business
    offline until a moderator got to it."""

    def test_pending_with_prior_approval_stays_visible(self):
        from django.utils import timezone

        business = self.make_business(
            status=Business.MODERATION_PENDING, first_approved_at=timezone.now(),
        )
        self.assertTrue(business.is_publicly_visible)
        self.assertTrue(
            Business.objects.filter(Business.public_filter(), pk=business.pk).exists()
        )

    def test_pending_without_prior_approval_stays_hidden(self):
        business = self.make_business(
            status=Business.MODERATION_PENDING, first_approved_at=None,
        )
        self.assertFalse(business.is_publicly_visible)
        self.assertFalse(
            Business.objects.filter(Business.public_filter(), pk=business.pk).exists()
        )


class ClientContentReportSubmissionTests(PublicGateTestCaseMixin, TestCase):
    """POST /api/client/business/<id>/report/ — phase 6's abuse-report intake.

    Covers the three reporter-identity outcomes client_views.py's
    ClientContentReportView docstring describes (visitor token / anonymous
    with a phone / fully anonymous), the public-visibility gate reusing
    Business.public_filter() the same way the detail endpoint does, and the
    per-IP throttle (ContentReportRateThrottle, scope 'content_report').
    """

    def setUp(self):
        super().setUp()
        from visitor.auth import sign_visitor_token
        from visitor.models import Visitor

        self.visitor = Visitor.objects.create(
            full_name='مراجع تست', phone_number='09350000009',
        )
        self.visitor_auth = f'Visitor {sign_visitor_token(self.visitor.id)}'

    def report(self, business_id, data=None, auth=None):
        kwargs = {}
        if auth:
            kwargs['HTTP_AUTHORIZATION'] = auth
        return self.client.post(
            reverse('client-business-report', args=[business_id]),
            data or {'reason': 'SPAM', 'detail': 'توضیح تست'},
            content_type='application/json',
            **kwargs,
        )

    def test_anonymous_submission_with_no_identity_at_all(self):
        from business.models import ContentReport

        business = self.make_business()
        response = self.report(business.id)
        self.assertEqual(response.status_code, 201)

        report = ContentReport.objects.get(business=business)
        self.assertIsNone(report.reporter_visitor_id)
        self.assertIsNone(report.reporter_user_id)
        self.assertEqual(report.reporter_phone, '')
        self.assertEqual(report.status, ContentReport.STATUS_NEW)

    def test_anonymous_submission_with_a_contact_phone(self):
        from business.models import ContentReport

        business = self.make_business()
        response = self.report(business.id, {
            'reason': 'MISLEADING', 'detail': '', 'reporter_phone': '09121234567',
        })
        self.assertEqual(response.status_code, 201)
        report = ContentReport.objects.get(business=business)
        self.assertEqual(report.reporter_phone, '09121234567')
        self.assertIsNone(report.reporter_visitor_id)

    def test_invalid_contact_phone_is_rejected(self):
        business = self.make_business()
        response = self.report(business.id, {
            'reason': 'SPAM', 'reporter_phone': 'not-a-phone',
        })
        self.assertEqual(response.status_code, 400)

    def test_authenticated_visitor_submission_sets_reporter_visitor(self):
        from business.models import ContentReport

        business = self.make_business()
        response = self.report(business.id, auth=self.visitor_auth)
        self.assertEqual(response.status_code, 201)

        report = ContentReport.objects.get(business=business)
        self.assertEqual(report.reporter_visitor_id, self.visitor.id)
        # A signed-in visitor doesn't need to also type their phone in — the
        # identity already carries it.
        self.assertEqual(report.reporter_phone, '')

    def test_invalid_reason_is_rejected(self):
        business = self.make_business()
        response = self.report(business.id, {'reason': 'NOT_A_REAL_REASON'})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(business.content_reports.count(), 0)

    def test_non_public_business_returns_404_not_403(self):
        # Same reasoning as ClientBusinessDetailGateTests: a stale/guessed id
        # for a rejected or locked listing must not confirm it exists.
        for status in NON_PUBLIC_STATUSES:
            with self.subTest(status=status):
                business = self.make_business(status=status)
                response = self.report(business.id)
                self.assertEqual(response.status_code, 404)
                business.delete()

        locked = self.make_business(is_locked=True)
        self.assertEqual(self.report(locked.id).status_code, 404)

    def test_unknown_business_returns_404(self):
        self.assertEqual(self.report(999999).status_code, 404)

    def test_throttle_engages_after_the_configured_rate(self):
        """THROTTLE_RATES['content_report'] defaults to 5/hour
        (core/settings.py) — the 6th request from the same client within the
        window must be refused, proving this endpoint is not silently riding
        on the much more generous shared 'anon' bucket (60/min)."""
        from django.conf import settings

        rate = settings.REST_FRAMEWORK['DEFAULT_THROTTLE_RATES']['content_report']
        limit = int(rate.split('/')[0])

        business = self.make_business()
        for _ in range(limit):
            response = self.report(business.id)
            self.assertEqual(response.status_code, 201)

        blocked = self.report(business.id)
        self.assertEqual(blocked.status_code, 429)

    def test_throttle_also_engages_for_an_authenticated_visitor(self):
        """Regression: plain AnonRateThrottle exempts any request whose
        request.user.is_authenticated is True, and Visitor.is_authenticated is
        hard-coded True (visitor/models.py) — so a signed-in visitor's
        requests silently bypassed this throttle entirely until
        ContentReportRateThrottle.get_cache_key() was overridden to always key
        by IP. Caught by hand-testing against a running server; pinned down
        here so it can't regress silently."""
        from django.conf import settings

        rate = settings.REST_FRAMEWORK['DEFAULT_THROTTLE_RATES']['content_report']
        limit = int(rate.split('/')[0])

        business = self.make_business()
        for _ in range(limit):
            response = self.report(business.id, auth=self.visitor_auth)
            self.assertEqual(response.status_code, 201)

        blocked = self.report(business.id, auth=self.visitor_auth)
        self.assertEqual(blocked.status_code, 429)


class ContentReportAdminResolutionActionTests(PublicGateTestCaseMixin, TestCase):
    """ContentReportAdmin.mark_actioned / mark_dismissed require a reason.

    business/admin.py:600 (_set_status) routes both through the same
    confirmation-page pattern as BusinessAdmin's bulk reject/suspend
    (moderation_bulk.html): resolving a report without recording why is a
    triage decision nobody can explain later. mark_reviewing is exempt since
    it's not a resolution.
    """

    def setUp(self):
        super().setUp()
        from business.models import ContentReport

        self.staff = User.objects.create_user(
            phone='09120000099', password='x', name='ناظر', is_staff=True, is_superuser=True,
        )
        self.client.force_login(self.staff)
        self.business = self.make_business()
        self.report = ContentReport.objects.create(
            business=self.business, reason='SPAM', detail='توضیح تست',
        )

    def post_action(self, action_name, apply=False, note=None):
        data = {
            'action': action_name,
            '_selected_action': [str(self.report.pk)],
        }
        if apply:
            data['apply'] = '1'
        if note is not None:
            data['resolution_note'] = note
        return self.client.post(
            reverse('admin:business_contentreport_changelist'), data,
        )

    def test_mark_actioned_without_a_note_does_not_resolve(self):
        response = self.post_action('mark_actioned')
        self.assertEqual(response.status_code, 200)
        self.report.refresh_from_db()
        self.assertEqual(self.report.status, self.report.__class__.STATUS_NEW)
        self.assertEqual(self.report.resolution_note, '')
        self.assertIsNone(self.report.resolved_by_id)
        self.assertIsNone(self.report.resolved_at)

    def test_mark_actioned_with_empty_note_on_apply_is_refused(self):
        response = self.post_action('mark_actioned', apply=True, note='   ')
        self.assertEqual(response.status_code, 200)
        self.report.refresh_from_db()
        self.assertEqual(self.report.status, self.report.__class__.STATUS_NEW)

    def test_mark_actioned_with_a_note_resolves_and_records_it(self):
        from business.models import ContentReport

        response = self.post_action('mark_actioned', apply=True, note='بررسی شد و حذف شد.')
        self.assertEqual(response.status_code, 302)
        self.report.refresh_from_db()
        self.assertEqual(self.report.status, ContentReport.STATUS_ACTIONED)
        self.assertEqual(self.report.resolution_note, 'بررسی شد و حذف شد.')
        self.assertEqual(self.report.resolved_by_id, self.staff.id)
        self.assertIsNotNone(self.report.resolved_at)

    def test_mark_dismissed_without_a_note_does_not_resolve(self):
        response = self.post_action('mark_dismissed')
        self.assertEqual(response.status_code, 200)
        self.report.refresh_from_db()
        self.assertEqual(self.report.status, self.report.__class__.STATUS_NEW)

    def test_mark_dismissed_with_a_note_resolves_and_records_it(self):
        from business.models import ContentReport

        response = self.post_action('mark_dismissed', apply=True, note='گزارش نامرتبط بود.')
        self.assertEqual(response.status_code, 302)
        self.report.refresh_from_db()
        self.assertEqual(self.report.status, ContentReport.STATUS_DISMISSED)
        self.assertEqual(self.report.resolution_note, 'گزارش نامرتبط بود.')
        self.assertEqual(self.report.resolved_by_id, self.staff.id)
        self.assertIsNotNone(self.report.resolved_at)

    def test_mark_reviewing_does_not_require_a_note(self):
        from business.models import ContentReport

        response = self.post_action('mark_reviewing')
        self.assertEqual(response.status_code, 302)
        self.report.refresh_from_db()
        self.assertEqual(self.report.status, ContentReport.STATUS_REVIEWING)
        self.assertEqual(self.report.resolution_note, '')
        self.assertIsNone(self.report.resolved_by_id)
        self.assertIsNone(self.report.resolved_at)


class UniqueCodeTests(PublicGateTestCaseMixin, TestCase):
    """`unique_code` is auto-generated by default but writable by an admin.

    The point of these is the "by default" half: making the field customisable
    must not change anything for a business nobody touches, which is every
    business that exists today.
    """

    def test_generated_code_is_eight_uppercase_chars(self):
        biz = self.make_business()
        self.assertEqual(len(biz.unique_code), Business.CODE_LENGTH)
        self.assertEqual(len(biz.unique_code), 8)
        self.assertTrue(set(biz.unique_code) <= set(Business.CODE_ALPHABET))

    def test_custom_code_longer_than_eight_is_accepted(self):
        biz = self.make_business()
        biz.unique_code = 'SALON-TEHRAN-VANAK-2026'
        biz.full_clean()
        biz.save()
        biz.refresh_from_db()
        self.assertEqual(biz.unique_code, 'SALON-TEHRAN-VANAK-2026')

    def test_custom_code_survives_a_later_save(self):
        # save() only generates when the field is empty, so re-saving an edited
        # business must not overwrite the code an admin typed.
        biz = self.make_business()
        biz.unique_code = 'my-salon'
        biz.save()
        biz.title = 'عنوان تازه'
        biz.save()
        biz.refresh_from_db()
        self.assertEqual(biz.unique_code, 'my-salon')

    def test_code_differing_only_in_case_is_rejected(self):
        first = self.make_business(title='اولی')
        first.unique_code = 'SalonA'
        first.save()
        second = self.make_business(title='دومی')
        second.unique_code = 'salona'
        with self.assertRaises(ValidationError) as ctx:
            second.full_clean()
        self.assertIn('unique_code', ctx.exception.message_dict)

    def test_unchanged_business_validates_against_itself(self):
        biz = self.make_business()
        biz.unique_code = 'VANAK'
        biz.save()
        biz.full_clean()  # its own row must not count as a collision

    def test_code_with_url_unsafe_characters_is_rejected(self):
        biz = self.make_business()
        biz.unique_code = 'salon tehran/2'
        with self.assertRaises(ValidationError) as ctx:
            biz.full_clean()
        self.assertIn('unique_code', ctx.exception.message_dict)

    def test_existing_generated_codes_still_validate(self):
        # The new charset validator is a superset of what the generator emits,
        # so every code created before this change must still pass full_clean().
        biz = self.make_business()
        biz.full_clean()
