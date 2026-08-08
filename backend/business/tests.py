"""Public-visibility gate for the client-facing business endpoints.

A business is hidden from the public surface for two entirely independent
reasons — `is_locked` (the owner's plan lapsed) and `moderation_status` (a
reviewer has not cleared it). These tests exist because the two are easy to
confuse for one another: they pin down that *both* are enforced, and equally
that neither was quietly swapped in for the other.
"""

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.urls import reverse

from .models import Business

User = get_user_model()

# Everything except APPROVED must stay invisible. Kept as a tuple driving
# subTest loops so a new moderation state added to the model shows up here as a
# single line rather than as three near-identical test methods.
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
                      is_locked=False):
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
        for status in (Business.MODERATION_APPROVED,) + NON_PUBLIC_STATUSES:
            for is_locked in (False, True):
                with self.subTest(status=status, is_locked=is_locked):
                    business = self.make_business(status=status, is_locked=is_locked)
                    via_filter = Business.objects.filter(
                        Business.public_filter(), pk=business.pk
                    ).exists()
                    self.assertEqual(via_filter, business.is_publicly_visible)
                    business.delete()
