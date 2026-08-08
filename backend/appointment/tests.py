"""Public-visibility gate for the client-facing appointment endpoints.

Two things are gated on `Business.is_publicly_visible`: *discovery* (which slots
a business advertises) and *new bookings*. Deliberately not gated: a client
reading back an appointment they already hold. Those tests live here together
because the line between them is the easy thing to get wrong — suspending a
business must not erase a client's own booking history.
"""

from datetime import timedelta
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone

from business.models import Business
from visitor.auth import sign_visitor_token
from visitor.models import Visitor

from .models import Appointment

User = get_user_model()

NON_PUBLIC_STATUSES = (
    Business.MODERATION_PENDING,
    Business.MODERATION_REJECTED,
    Business.MODERATION_SUSPENDED,
)

# What a client is told when the business will not take the booking. One neutral
# sentence covers both an unpaid owner and a moderated-away listing: which of
# the two applies is not the client's business to learn.
BOOKING_REFUSED_MESSAGE = "این کسب‌وکار در حال حاضر نوبت جدید نمی‌پذیرد"


class ClientGateTestCaseMixin:
    def setUp(self):
        super().setUp()
        # The slot endpoints cache their payload and DRF keeps throttle history
        # in the same cache, so tests must not inherit either.
        cache.clear()
        self.owner = User.objects.create_user(
            phone='09120000002', password='x', name='مالک تست'
        )
        self.visitor = Visitor.objects.create(
            full_name='مشتری تست', phone_number='09350000002'
        )
        self.visitor_auth = f'Visitor {sign_visitor_token(self.visitor.id)}'

    def make_business(self, *, status=Business.MODERATION_APPROVED, is_locked=False):
        return Business.objects.create(
            user=self.owner,
            title='کسب‌وکار تست',
            phone='02100000000',
            address='تهران',
            default_service_duration=30,
            work_start_hour=9,
            work_end_hour=17,
            moderation_status=status,
            is_locked=is_locked,
        )


class AvailableSlotsGateTests(ClientGateTestCaseMixin, TestCase):
    """GET /api/client/appointments/<business_id>/available-slots/"""

    def slots(self, business_id):
        return self.client.get(
            reverse('client-available-slots', args=[business_id]),
            {'date': timezone.localdate().isoformat()},
        )

    def test_non_approved_business_slots_return_404(self):
        for status in NON_PUBLIC_STATUSES:
            with self.subTest(status=status):
                business = self.make_business(status=status)
                self.assertEqual(self.slots(business.id).status_code, 404)
                business.delete()

    def test_approved_but_locked_business_slots_return_404(self):
        business = self.make_business(is_locked=True)
        self.assertEqual(self.slots(business.id).status_code, 404)

    def test_approved_unlocked_business_slots_are_served(self):
        business = self.make_business()
        response = self.slots(business.id)
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json()['data']['slots'])

    def test_suspension_takes_effect_despite_a_warm_cache(self):
        # The cache key is business+date and carries no moderation state, so the
        # gate has to be checked ahead of the cache read — otherwise a
        # suspension would not bite until the entry expired.
        business = self.make_business()
        self.assertEqual(self.slots(business.id).status_code, 200)

        business.moderation_status = Business.MODERATION_SUSPENDED
        business.save(update_fields=['moderation_status'])

        self.assertEqual(self.slots(business.id).status_code, 404)


class PublicSlotsGateTests(ClientGateTestCaseMixin, TestCase):
    """GET /api/client/appointments/slots/<business_id>/ (occupied ranges only)."""

    def slots(self, business_id):
        return self.client.get(
            reverse('client-public-slots', args=[business_id]),
            {'date': timezone.localdate().isoformat()},
        )

    def test_non_approved_business_slots_return_404(self):
        for status in NON_PUBLIC_STATUSES:
            with self.subTest(status=status):
                business = self.make_business(status=status)
                self.assertEqual(self.slots(business.id).status_code, 404)
                business.delete()

    def test_approved_but_locked_business_slots_return_404(self):
        business = self.make_business(is_locked=True)
        self.assertEqual(self.slots(business.id).status_code, 404)

    def test_approved_unlocked_business_slots_are_served(self):
        business = self.make_business()
        response = self.slots(business.id)
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()['business_id'], business.id)

    def test_suspension_takes_effect_despite_a_warm_cache(self):
        business = self.make_business()
        self.assertEqual(self.slots(business.id).status_code, 200)

        business.moderation_status = Business.MODERATION_SUSPENDED
        business.save(update_fields=['moderation_status'])

        self.assertEqual(self.slots(business.id).status_code, 404)


class BookingCreationGateTests(ClientGateTestCaseMixin, TestCase):
    """POST /api/client/appointments/ — a stale link must not create a booking."""

    def book(self, business_id):
        when = timezone.now() + timedelta(days=1)
        return self.client.post(
            reverse('client-appointment-list'),
            {
                'business_id': business_id,
                'appointment_date': int(when.timestamp() * 1000),
            },
            content_type='application/json',
            HTTP_AUTHORIZATION=self.visitor_auth,
        )

    def assertRefused(self, response):
        # 403, not 404: the client followed a link to a business that really
        # exists, so they are told the booking was refused rather than being
        # sent chasing a page that "never existed".
        self.assertEqual(response.status_code, 403)
        self.assertEqual(response.json()['message'], BOOKING_REFUSED_MESSAGE)
        self.assertEqual(Appointment.objects.count(), 0)

    def test_non_approved_business_refuses_booking(self):
        for status in NON_PUBLIC_STATUSES:
            with self.subTest(status=status):
                business = self.make_business(status=status)
                self.assertRefused(self.book(business.id))
                business.delete()

    def test_approved_but_locked_business_refuses_booking(self):
        business = self.make_business(is_locked=True)
        self.assertRefused(self.book(business.id))

    def test_refusal_message_is_identical_for_lock_and_moderation(self):
        # The whole point of the shared message: comparing the two responses
        # must not reveal which gate closed.
        locked = self.make_business(is_locked=True)
        rejected = self.make_business(status=Business.MODERATION_REJECTED)

        locked_response = self.book(locked.id)
        rejected_response = self.book(rejected.id)

        self.assertEqual(locked_response.status_code, rejected_response.status_code)
        self.assertEqual(
            locked_response.json()['message'], rejected_response.json()['message']
        )

    def test_unknown_business_still_returns_404(self):
        # The gate must not swallow the genuinely-missing case into a 403.
        response = self.book(999999)
        self.assertEqual(response.status_code, 404)


class ClientOwnAppointmentReadTests(ClientGateTestCaseMixin, TestCase):
    """A client keeps access to bookings they already hold.

    Moderation governs discovery and new bookings. It is not a reason to hide
    someone's own appointment from them: the business may be suspended, but the
    client still needs to know they have (or had) a slot there — not least to
    cancel it.
    """

    def make_appointment(self, business):
        return Appointment.objects.create(
            user=None,
            business=business,
            visitor=self.visitor,
            appointment_date=timezone.now() + timedelta(days=1),
            service_duration=30,
            status='WAITING',
        )

    def listed_ids(self):
        response = self.client.get(
            reverse('client-appointment-list'), HTTP_AUTHORIZATION=self.visitor_auth
        )
        self.assertEqual(response.status_code, 200)
        return [row['id'] for row in response.json()['data']['results']]

    def test_appointment_at_suspended_business_stays_readable(self):
        business = self.make_business()
        appointment = self.make_appointment(business)

        business.moderation_status = Business.MODERATION_SUSPENDED
        business.save(update_fields=['moderation_status'])

        self.assertIn(appointment.id, self.listed_ids())

    def test_appointment_at_locked_business_stays_readable(self):
        business = self.make_business()
        appointment = self.make_appointment(business)

        business.is_locked = True
        business.save(update_fields=['is_locked'])

        self.assertIn(appointment.id, self.listed_ids())

    def test_appointment_at_suspended_business_can_still_be_cancelled(self):
        # Cancelling is the one write a client must keep: leaving them holding a
        # slot they cannot release would be worse than hiding the business.
        business = self.make_business()
        appointment = self.make_appointment(business)

        business.moderation_status = Business.MODERATION_SUSPENDED
        business.save(update_fields=['moderation_status'])

        # Cancelling notifies the owner from a detached daemon thread with its
        # own DB connection, which cannot see this test's transaction — patched
        # out so the assertion below is about the cancellation, not about SMS.
        with patch('appointment.client_views._send_booking_sms'):
            response = self.client.post(
                reverse('client-appointment-cancel', args=[appointment.id]),
                HTTP_AUTHORIZATION=self.visitor_auth,
            )
        self.assertEqual(response.status_code, 200)
        appointment.refresh_from_db()
        self.assertEqual(appointment.status, 'CANCELLED')
