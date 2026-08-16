"""
business/tests_moderation_api.py

Owner-facing side of content moderation: what the API exposes, when an edit
sends a business back into the queue, and what the decision notices say.

Deliberately in its own module rather than business/tests.py — the decision
logic and the admin queue are tested separately, and mixing them makes it
unclear which layer a failure belongs to.
"""

from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import TestCase
from django.urls import reverse
from django.utils import timezone
from rest_framework.test import APIClient

from .models import Business
from .serializers import (
    BusinessSerializer,
    ClientBusinessSerializer,
    PublicBusinessSerializer,
)
from . import services, sms_moderation

User = get_user_model()

MODERATION_FIELDS = {
    'moderation_status',
    'moderation_status_display',
    'moderation_note',
    'moderation_submitted_at',
}


def make_business(user, **overrides):
    defaults = dict(
        user=user,
        title='سالن زیبایی نمونه',
        phone='02112345678',
        address='تهران، خیابان آزادی',
        bio='بهترین خدمات',
        default_service_duration=30,
        work_start_hour=9,
        work_end_hour=18,
    )
    defaults.update(overrides)
    return Business.objects.create(**defaults)


class ModerationSerializerExposureTests(TestCase):
    """Owner sees the review state; the public never does."""

    def setUp(self):
        self.user = User.objects.create_user(phone='09120000001', name='مالک')
        self.business = make_business(self.user)

    def test_owner_serializer_exposes_moderation_state(self):
        data = BusinessSerializer(self.business).data
        self.assertTrue(MODERATION_FIELDS.issubset(data.keys()))
        self.assertEqual(data['moderation_status'], Business.MODERATION_PENDING)
        self.assertEqual(data['moderation_status_display'], 'در انتظار بررسی')

    def test_owner_serializer_moderation_fields_are_read_only(self):
        serializer = BusinessSerializer(
            self.business,
            data={'moderation_status': Business.MODERATION_APPROVED,
                  'moderation_note': 'خودم تأیید کردم'},
            partial=True,
        )
        self.assertTrue(serializer.is_valid(), serializer.errors)
        serializer.save()
        self.business.refresh_from_db()
        # An owner must not be able to approve themselves past review.
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)
        self.assertEqual(self.business.moderation_note, '')

    def test_public_serializer_leaks_no_moderation_state(self):
        fields = set(PublicBusinessSerializer(self.business).data.keys())
        self.assertEqual(fields & MODERATION_FIELDS, set())

    def test_client_serializer_leaks_no_moderation_state(self):
        fields = set(ClientBusinessSerializer(self.business).data.keys())
        self.assertEqual(fields & MODERATION_FIELDS, set())


class ReSubmitOnEditTests(TestCase):
    """Editing what the public sees puts an approved business back in the queue."""

    def setUp(self):
        self.user = User.objects.create_user(phone='09120000002', name='مالک')
        self.business = make_business(
            self.user, moderation_status=Business.MODERATION_APPROVED,
        )

    def _edit(self, **changes):
        before = services.moderated_snapshot(self.business)
        for field, value in changes.items():
            setattr(self.business, field, value)
        self.business.save()
        requeued = services.resubmit_if_content_changed(self.business, before)
        self.business.refresh_from_db()
        return requeued

    def test_editing_title_returns_to_pending(self):
        self.assertTrue(self._edit(title='سالن زیبایی تازه'))
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)
        self.assertIsNotNone(self.business.moderation_submitted_at)
        self.assertFalse(self.business.is_publicly_visible)

    def test_editing_non_moderated_field_stays_approved(self):
        self.assertFalse(self._edit(work_start_hour=8))
        self.assertEqual(self.business.moderation_status, Business.MODERATION_APPROVED)

    def test_no_op_save_stays_approved(self):
        self.assertFalse(self._edit(title=self.business.title))
        self.assertEqual(self.business.moderation_status, Business.MODERATION_APPROVED)

    def test_unchanged_logo_does_not_requeue(self):
        # FieldFile identity, not value, is the trap here.
        self.business.logo = 'business_logos/x.png'
        self.business.save()
        self.assertFalse(self._edit(work_end_hour=19))
        self.assertEqual(self.business.moderation_status, Business.MODERATION_APPROVED)

    def test_rejected_business_re_enters_queue_once(self):
        self.business.moderation_status = Business.MODERATION_REJECTED
        self.business.moderation_note = 'عنوان نامناسب'
        self.business.save()

        self.assertTrue(self._edit(title='عنوان اصلاح‌شده'))
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)

        stamped = self.business.moderation_submitted_at
        # Already queued — a second edit must not shuffle the queue order.
        self.assertFalse(self._edit(title='باز هم عوض شد'))
        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_submitted_at, stamped)

    def test_suspended_business_cannot_self_lift(self):
        self.business.moderation_status = Business.MODERATION_SUSPENDED
        self.business.save()
        self.assertFalse(self._edit(title='دور زدن تعلیق'))
        self.assertEqual(self.business.moderation_status, Business.MODERATION_SUSPENDED)


class BusinessApiModerationTests(TestCase):
    """The same rules through the actual owner endpoint."""

    def setUp(self):
        self.user = User.objects.create_user(phone='09120000003', name='مالک')
        self.client = APIClient()
        self.client.force_authenticate(user=self.user)
        self.business = make_business(
            self.user, moderation_status=Business.MODERATION_APPROVED,
        )
        self.url = reverse('business-detail', args=[self.business.id])

    def test_get_returns_moderation_state(self):
        response = self.client.get(self.url)
        self.assertEqual(response.status_code, 200)
        data = response.json()['data']
        self.assertEqual(data['moderation_status'], Business.MODERATION_APPROVED)
        self.assertEqual(data['moderation_status_display'], 'تأیید شده')

    def test_put_changing_title_requeues_and_response_shows_it(self):
        response = self.client.put(self.url, {'title': 'عنوان جدید'}, format='json')
        self.assertEqual(response.status_code, 200)
        payload = response.json()['data']
        # The response must carry post-save state, not the pre-requeue render.
        self.assertEqual(payload['moderation_status'], Business.MODERATION_PENDING)
        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)

    def test_put_changing_hours_keeps_approval(self):
        response = self.client.put(self.url, {'work_start_hour': 8}, format='json')
        self.assertEqual(response.status_code, 200)
        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_APPROVED)

    def test_put_resending_same_title_keeps_approval(self):
        response = self.client.put(
            self.url, {'title': self.business.title}, format='json',
        )
        self.assertEqual(response.status_code, 200)
        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_APPROVED)


class BusinessCreationModerationTests(TestCase):
    """Creation still works, and a new business enters the queue properly."""

    def setUp(self):
        self.user = User.objects.create_user(phone='09120000005', name='مالک')
        self.client = APIClient()
        self.client.force_authenticate(user=self.user)
        self.url = reverse('business-list-create')

    def _payload(self, title='کسب‌وکار تازه'):
        return {
            'title': title,
            'phone': '02133334444',
            'address': 'تهران',
            'default_service_duration': 30,
            'work_start_hour': 9,
            'work_end_hour': 17,
        }

    def test_created_business_is_stamped_into_the_queue(self):
        response = self.client.post(self.url, self._payload(), format='json')
        self.assertEqual(response.status_code, 201, response.content)
        created = Business.objects.get(id=response.json()['data']['id'])
        self.assertEqual(created.moderation_status, Business.MODERATION_PENDING)
        # Null here would leave new businesses unordered against resubmissions.
        self.assertIsNotNone(created.moderation_submitted_at)

    def test_pending_business_counts_against_the_plan_quota(self):
        first = self.client.post(self.url, self._payload(), format='json')
        self.assertEqual(first.status_code, 201, first.content)

        second = self.client.post(self.url, self._payload('دومی'), format='json')
        # Not counting businesses awaiting review would let an owner exceed
        # their plan indefinitely by parking listings in the queue.
        self.assertEqual(second.status_code, 403)
        self.assertEqual(Business.objects.filter(user=self.user).count(), 1)


class ModerationSmsTests(TestCase):
    """Wording, and the rule that these are never billed to the owner."""

    def setUp(self):
        self.user = User.objects.create_user(phone='09120000004', name='مالک')
        self.business = make_business(self.user, title='سالن آفتاب')

    def test_texts_are_persian_and_carry_the_opt_out(self):
        self.business.moderation_note = 'تصویر نامناسب است'
        approved = sms_moderation.approved_text(self.business)
        rejected = sms_moderation.rejected_text(self.business)
        suspended = sms_moderation.suspended_text(self.business)

        for text in (approved, rejected, suspended):
            self.assertIn('سالن آفتاب', text)
            self.assertIn('نوبت‌یار', text)
            self.assertIn('لغو11', text)
            # The advertising line reads a full stop as a link and drops it.
            self.assertNotIn('.', text)

        self.assertIn('تصویر نامناسب است', rejected)
        self.assertIn('اصلاح', rejected)
        self.assertIn('تصویر نامناسب است', suspended)

    def test_long_rejection_note_is_trimmed(self):
        self.business.moderation_note = 'الف' * 300
        text = sms_moderation.rejected_text(self.business)
        self.assertIn('…', text)
        self.assertLess(len(text), 400)

    def test_sender_never_touches_the_owners_sms_quota(self):
        with patch('business.sms_moderation.send_sms', return_value=(True, '')) as send, \
                patch('business.sms_moderation.within_send_window', return_value=True), \
                patch('accounting.usage.consume_sms') as consume:
            ok = sms_moderation.notify_moderation_decision(
                self.business, Business.MODERATION_APPROVED,
            )

        self.assertTrue(ok)
        # Goes to the account phone, not the public business number.
        self.assertEqual(send.call_args[0][0], self.user.phone)
        consume.assert_not_called()

    def test_pending_is_not_notified(self):
        with patch('business.sms_moderation.send_sms') as send:
            sent = sms_moderation.notify_moderation_decision(
                self.business, Business.MODERATION_PENDING,
            )
        self.assertFalse(sent)
        send.assert_not_called()

    def test_provider_failure_never_breaks_the_decision(self):
        staff = User.objects.create_user(
            phone='09120000009', name='ناظر', is_staff=True,
        )
        with patch('business.sms_moderation.send_sms', side_effect=RuntimeError('boom')), \
                patch('accounting.usage.consume_sms') as consume, \
                self.assertLogs('business.sms_moderation', level='ERROR'):
            notified = services.apply_moderation_decision(
                self.business, Business.MODERATION_REJECTED, staff, note='محتوای نامناسب',
            )

        self.assertFalse(notified)
        consume.assert_not_called()
        self.business.refresh_from_db()
        # The decision stands even though the notice never left.
        self.assertEqual(self.business.moderation_status, Business.MODERATION_REJECTED)
        self.assertEqual(self.business.moderation_note, 'محتوای نامناسب')
        self.assertTrue(self.business.moderation_logs.exists())

    def test_apply_decision_sends_the_matching_notice(self):
        staff = User.objects.create_user(
            phone='09120000010', name='ناظر', is_staff=True,
        )
        with patch('business.sms_moderation.send_sms', return_value=(True, '')) as send, \
                patch('business.sms_moderation.within_send_window', return_value=True):
            notified = services.apply_moderation_decision(
                self.business, Business.MODERATION_APPROVED, staff,
            )

        self.assertTrue(notified)
        self.assertIn('تأیید شد', send.call_args[0][1])


class PendingEditStagingTests(TestCase):
    """Once a business has cleared review once (first_approved_at is set), an
    edit to a moderated field must stage instead of overwriting what customers
    currently see — the bug report this exists for: an owner's edit (in
    particular, the emergency notice) took the entire business offline."""

    def setUp(self):
        self.user = User.objects.create_user(phone='09120000020', name='مالک')
        self.business = make_business(
            self.user,
            moderation_status=Business.MODERATION_APPROVED,
            first_approved_at=timezone.now(),
        )

    def _put(self, **data):
        serializer = BusinessSerializer(self.business, data=data, partial=True)
        self.assertTrue(serializer.is_valid(), serializer.errors)
        before = services.moderated_snapshot(self.business)
        return services.save_with_moderation(serializer, self.business, before)

    def test_editing_title_stages_instead_of_overwriting(self):
        original_title = self.business.title

        requeued = self._put(title='عنوان جدید')
        self.assertTrue(requeued)

        self.business.refresh_from_db()
        self.assertEqual(self.business.title, original_title)
        self.assertEqual(self.business.pending_title, 'عنوان جدید')
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)
        # The whole point: still live, on the strength of the old, approved copy.
        self.assertTrue(self.business.is_publicly_visible)

    def test_public_serializer_still_shows_old_title_while_pending(self):
        self._put(title='عنوان جدید')
        self.business.refresh_from_db()

        data = PublicBusinessSerializer(self.business).data
        self.assertNotEqual(data['title'], 'عنوان جدید')

    def test_second_edit_while_pending_replaces_the_draft_without_reshuffling_queue(self):
        self._put(title='عنوان جدید')
        self.business.refresh_from_db()
        stamped = self.business.moderation_submitted_at

        requeued = self._put(title='باز هم عوض شد')
        self.assertTrue(requeued)

        self.business.refresh_from_db()
        self.assertEqual(self.business.pending_title, 'باز هم عوض شد')
        self.assertEqual(self.business.moderation_submitted_at, stamped)

    def test_approval_promotes_the_staged_edit(self):
        self._put(title='عنوان جدید', bio='معرفی جدید')
        self.business.refresh_from_db()

        services.apply_moderation_decision(
            self.business, Business.MODERATION_APPROVED, actor=None, notify=False,
        )
        self.business.refresh_from_db()

        self.assertEqual(self.business.title, 'عنوان جدید')
        self.assertEqual(self.business.bio, 'معرفی جدید')
        self.assertIsNone(self.business.pending_title)
        self.assertIsNone(self.business.pending_bio)
        self.assertEqual(self.business.moderation_status, Business.MODERATION_APPROVED)
        self.assertIsNotNone(self.business.first_approved_at)

    def test_editing_notice_message_no_longer_requeues(self):
        """The bug report this whole feature exists for."""
        requeued = self._put(notice_message='امروز تعطیلیم', notice_enabled=True)
        self.assertFalse(requeued)

        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_APPROVED)
        self.assertTrue(self.business.is_publicly_visible)
        self.assertEqual(self.business.notice_message, 'امروز تعطیلیم')


class ContentReportAutoResolveTests(TestCase):
    """A rejection/suspension auto-closes open reports against the business and
    links them to the BusinessModerationLog that caused it (business/services.py
    :func:`_auto_resolve_open_reports`)."""

    def setUp(self):
        from .models import ContentReport

        self.ContentReport = ContentReport
        self.user = User.objects.create_user(phone='09120000011', name='مالک')
        self.staff = User.objects.create_user(
            phone='09120000012', name='ناظر', is_staff=True,
        )
        self.business = make_business(self.user, title='سالن شب')

    def _report(self, **overrides):
        defaults = dict(business=self.business, reason='SPAM')
        defaults.update(overrides)
        return self.ContentReport.objects.create(**defaults)

    def _decide(self, to_status, note='محتوای نامناسب'):
        with patch('business.sms_moderation.send_sms', return_value=(True, '')), \
                patch('business.sms_moderation.within_send_window', return_value=True):
            return services.apply_moderation_decision(
                self.business, to_status, self.staff, note=note,
            )

    def test_suspend_resolves_open_reports_and_links_the_log(self):
        report = self._report(status=self.ContentReport.STATUS_NEW)

        self._decide(Business.MODERATION_SUSPENDED)

        report.refresh_from_db()
        self.assertEqual(report.status, self.ContentReport.STATUS_ACTIONED)
        self.assertEqual(report.resolved_by, self.staff)
        self.assertIsNotNone(report.resolved_at)
        log = self.business.moderation_logs.latest('created_at')
        self.assertEqual(report.resulting_moderation_log_id, log.pk)
        self.assertEqual(log.to_status, Business.MODERATION_SUSPENDED)

    def test_reject_resolves_reviewing_reports_too(self):
        report = self._report(status=self.ContentReport.STATUS_REVIEWING)

        self._decide(Business.MODERATION_REJECTED)

        report.refresh_from_db()
        self.assertEqual(report.status, self.ContentReport.STATUS_ACTIONED)
        self.assertIsNotNone(report.resulting_moderation_log_id)

    def test_approval_does_not_touch_open_reports(self):
        report = self._report(status=self.ContentReport.STATUS_NEW)

        self._decide(Business.MODERATION_APPROVED)

        report.refresh_from_db()
        self.assertEqual(report.status, self.ContentReport.STATUS_NEW)
        self.assertIsNone(report.resulting_moderation_log_id)

    def test_already_resolved_reports_are_left_alone(self):
        other_staff = User.objects.create_user(phone='09120000013', name='ناظر۲')
        report = self._report(
            status=self.ContentReport.STATUS_DISMISSED,
            resolved_by=other_staff,
        )

        self._decide(Business.MODERATION_SUSPENDED)

        report.refresh_from_db()
        self.assertEqual(report.status, self.ContentReport.STATUS_DISMISSED)
        self.assertEqual(report.resolved_by, other_staff)
        self.assertIsNone(report.resulting_moderation_log_id)
