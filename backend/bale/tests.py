"""
bale/tests.py

Weighted towards the webhook's authentication and replay behaviour: it is the
only endpoint in this project that can change editorial state without a session
or a JWT, so the checks that keep it safe are the ones worth pinning down.

All outbound HTTP is stubbed at ``bale.client._call`` — the single funnel every
helper in that module goes through — so nothing here touches the network.
"""

import json
from unittest.mock import patch

from django.test import TestCase
from django.urls import reverse

from api.models import User
from bale.keyboards import parse_callback
from bale.models import BaleSettings, PendingReason
from business.models import Business, BusinessModerationLog

CHAT_ID = '4242424242'


def _ok(token, method, payload):
    return {'success': True, 'result': {'message_id': 1}}


class BaleTestBase(TestCase):
    """Configured bot, one reviewable business, and the two ways in."""

    def setUp(self):
        # The owner's SMS runs on a daemon thread in production. Under sqlite
        # that thread contends with the test transaction ("database table is
        # locked"), and what it does is already covered by the business app's
        # own tests — here we only care that a decision triggers it.
        sms_patcher = patch('bale.views.fire_owner_sms')
        self.fire_sms = sms_patcher.start()
        self.addCleanup(sms_patcher.stop)

        self.reviewer = User.objects.create(
            phone='09120000001', name='بازبین', is_staff=True, is_superuser=True,
        )
        self.owner = User.objects.create(phone='09120000002', name='مالک')

        self.config = BaleSettings.load()
        self.config.bot_token = 'test-token'
        self.config.chat_id = CHAT_ID
        self.config.is_enabled = True
        self.config.actor = self.reviewer
        self.config.save()

        self.business = self.make_business('کسب‌وکار تست')
        self.url = reverse('bale:webhook',
                           kwargs={'secret': self.config.webhook_secret})

    def make_business(self, title, status=Business.MODERATION_PENDING, **extra):
        business = Business.objects.create(
            user=self.owner, title=title, category='OTHER',
            phone='02100000000', address='آدرس تست',
            default_service_duration=30, work_start_hour=9, work_end_hour=18,
            **extra,
        )
        if status != Business.MODERATION_PENDING:
            # update() rather than the service layer: these are fixtures, not
            # decisions, and must not emit logs or notifications.
            Business.objects.filter(pk=business.pk).update(moderation_status=status)
            business.refresh_from_db()
        return business

    def tap(self, data, sender=CHAT_ID, url=None):
        return self.client.post(
            url or self.url,
            data=json.dumps({'callback_query': {
                'id': 'cbq', 'from': {'id': sender},
                'message': {'message_id': 7}, 'data': data,
            }}),
            content_type='application/json',
        )

    def say(self, text, sender=CHAT_ID):
        return self.client.post(
            self.url,
            data=json.dumps({'message': {
                'message_id': 8, 'from': {'id': sender},
                'chat': {'id': sender}, 'text': text,
            }}),
            content_type='application/json',
        )

    def status(self):
        self.business.refresh_from_db()
        return self.business.moderation_status

    @staticmethod
    def sent_texts(mock):
        """Every outbound message body, so assertions can look for wording."""
        return [
            call.args[2].get('text', '')
            for call in mock.call_args_list
            if len(call.args) > 2 and isinstance(call.args[2], dict)
        ]

    @staticmethod
    def sent_buttons(mock):
        return [
            button['callback_data']
            for call in mock.call_args_list
            if len(call.args) > 2 and isinstance(call.args[2], dict)
            for markup in [call.args[2].get('reply_markup')] if markup
            for row in markup['inline_keyboard'] for button in row
        ]


class BaleWebhookTests(BaleTestBase):

    # ── authentication ──────────────────────────────────────────────────────

    @patch('bale.client._call', side_effect=_ok)
    def test_wrong_secret_is_404_and_changes_nothing(self, _call):
        bad = reverse('bale:webhook', kwargs={'secret': 'x' * 48})
        response = self.tap(f'm:d:{self.business.pk}:A', url=bad)

        self.assertEqual(response.status_code, 404)
        self.assertEqual(self.status(), Business.MODERATION_PENDING)

    @patch('bale.client._call', side_effect=_ok)
    def test_unauthorised_sender_cannot_decide(self, _call):
        """The check that still holds after the webhook URL leaks."""
        response = self.tap(f'm:d:{self.business.pk}:A', sender='9999999999')

        self.assertEqual(response.status_code, 200)
        self.assertEqual(self.status(), Business.MODERATION_PENDING)
        self.assertFalse(BusinessModerationLog.objects.exists())

    @patch('bale.client._call', side_effect=_ok)
    def test_disabled_bot_refuses_everything(self, _call):
        self.config.is_enabled = False
        self.config.save()

        response = self.tap(f'm:d:{self.business.pk}:A')

        self.assertEqual(response.status_code, 404)
        self.assertEqual(self.status(), Business.MODERATION_PENDING)

    # ── decisions ───────────────────────────────────────────────────────────

    @patch('bale.client._call', side_effect=_ok)
    def test_approve_records_the_configured_actor(self, _call):
        response = self.tap(f'm:d:{self.business.pk}:A')

        self.assertEqual(response.status_code, 200)
        self.assertEqual(self.status(), Business.MODERATION_APPROVED)
        self.assertIsNotNone(self.business.first_approved_at)

        log = BusinessModerationLog.objects.get(business=self.business)
        self.assertEqual(log.to_status, Business.MODERATION_APPROVED)
        # Without this the bot's decisions are indistinguishable from an
        # owner-initiated resubmission, which also logs with actor=None.
        self.assertEqual(log.actor, self.reviewer)

    @patch('bale.client._call', side_effect=_ok)
    def test_approved_business_becomes_publicly_visible(self, _call):
        self.tap(f'm:d:{self.business.pk}:A')

        self.assertTrue(
            Business.objects.filter(
                Business.public_filter(), pk=self.business.pk
            ).exists()
        )

    @patch('bale.client._call', side_effect=_ok)
    def test_reject_stores_the_canned_reason_as_the_note(self, _call):
        """The note is what the owner is told by SMS, so it must not be blank."""
        self.tap(f'm:d:{self.business.pk}:R:0')

        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status,
                         Business.MODERATION_REJECTED)
        self.assertTrue(self.business.moderation_note)
        self.assertEqual(
            BusinessModerationLog.objects.get(business=self.business).note,
            self.business.moderation_note,
        )

    @patch('bale.client._call', side_effect=_ok)
    def test_opening_the_reason_menu_decides_nothing(self, _call):
        self.tap(f'm:q:{self.business.pk}:R')

        self.assertEqual(self.status(), Business.MODERATION_PENDING)
        self.assertFalse(BusinessModerationLog.objects.exists())

    @patch('bale.client._call', side_effect=_ok)
    def test_repeated_tap_does_not_write_a_second_log(self, _call):
        """Bale retries a webhook it thinks failed, and a chat message stays
        tappable until it is edited."""
        self.tap(f'm:d:{self.business.pk}:A')
        self.tap(f'm:d:{self.business.pk}:A')

        self.assertEqual(
            BusinessModerationLog.objects.filter(business=self.business).count(), 1
        )

    @patch('bale.client._call', side_effect=_ok)
    def test_unknown_business_is_answered_not_crashed(self, _call):
        response = self.tap('m:d:99999999:A')

        self.assertEqual(response.status_code, 200)

    @patch('bale.client._call', side_effect=_ok)
    def test_malformed_payloads_change_nothing(self, _call):
        for data in ('garbage', 'm:d:abc:A', f'm:d:{self.business.pk}:Z',
                     f'm:d:{self.business.pk}:R:99', f'm:d:{self.business.pk}:R'):
            with self.subTest(data=data):
                response = self.tap(data)
                self.assertEqual(response.status_code, 200)
                self.assertEqual(self.status(), Business.MODERATION_PENDING)

    @patch('bale.client._call', side_effect=_ok)
    def test_non_callback_updates_are_ignored(self, _call):
        response = self.client.post(
            self.url,
            data=json.dumps({'message': {'text': 'سلام', 'chat': {'id': CHAT_ID}}}),
            content_type='application/json',
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(self.status(), Business.MODERATION_PENDING)


class BaleTypedReasonTests(BaleTestBase):
    """The '✍️ نوشتن دلیل دلخواه' flow: tap, then type the note."""

    @patch('bale.client._call', side_effect=_ok)
    def test_asking_for_a_reason_decides_nothing_yet(self, _call):
        self.tap(f'm:w:{self.business.pk}:R')

        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)
        self.assertEqual(PendingReason.objects.count(), 1)

    @patch('bale.client._call', side_effect=_ok)
    def test_typed_reason_becomes_the_note(self, _call):
        self.tap(f'm:w:{self.business.pk}:R')
        self.say('عکس پروفایل با موضوع کسب‌وکار هم‌خوانی ندارد')

        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_REJECTED)
        self.assertEqual(self.business.moderation_note,
                         'عکس پروفایل با موضوع کسب‌وکار هم‌خوانی ندارد')
        self.assertEqual(
            BusinessModerationLog.objects.get(business=self.business).actor,
            self.reviewer,
        )
        # The prompt is consumed, so the next thing typed is not swallowed.
        self.assertEqual(PendingReason.objects.count(), 0)

    @patch('bale.client._call', side_effect=_ok)
    def test_typed_reason_works_for_suspension_too(self, _call):
        self.tap(f'm:w:{self.business.pk}:S')
        self.say('گزارش تخلف تأیید شد')

        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_SUSPENDED)
        self.assertEqual(self.business.moderation_note, 'گزارش تخلف تأیید شد')

    @patch('bale.client._call', side_effect=_ok)
    def test_message_without_a_prompt_decides_nothing(self, _call):
        self.say('سلام')

        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)
        self.assertFalse(BusinessModerationLog.objects.exists())

    @patch('bale.client._call', side_effect=_ok)
    def test_cancel_abandons_the_prompt(self, _call):
        self.tap(f'm:w:{self.business.pk}:R')
        self.say('/cancel')
        self.say('این نباید دلیل حساب شود')

        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)
        self.assertEqual(PendingReason.objects.count(), 0)

    @patch('bale.client._call', side_effect=_ok)
    def test_backing_out_abandons_the_prompt(self, _call):
        self.tap(f'm:w:{self.business.pk}:R')
        self.tap(f'm:b:{self.business.pk}')
        self.say('این نباید دلیل حساب شود')

        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)

    @patch('bale.client._call', side_effect=_ok)
    def test_only_one_prompt_can_be_outstanding(self, _call):
        other = Business.objects.create(
            user=self.owner, title='دومی', category='OTHER', phone='02100000001',
            address='آدرس', default_service_duration=30,
            work_start_hour=9, work_end_hour=18,
        )
        self.tap(f'm:w:{self.business.pk}:R')
        self.tap(f'm:w:{other.pk}:R')
        self.say('دلیل')

        self.business.refresh_from_db()
        other.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)
        self.assertEqual(other.moderation_status, Business.MODERATION_REJECTED)

    @patch('bale.client._call', side_effect=_ok)
    def test_unauthorised_sender_cannot_supply_a_reason(self, _call):
        self.tap(f'm:w:{self.business.pk}:R')
        self.say('دلیل جعلی', sender='9999999999')

        self.business.refresh_from_db()
        self.assertEqual(self.business.moderation_status, Business.MODERATION_PENDING)
        self.assertEqual(PendingReason.objects.count(), 1)


class BalePendingListTests(BaleTestBase):
    def setUp(self):
        super().setUp()
        # These tests assert on exact queue contents, so start from an empty
        # queue rather than the base class's one pending business.
        self.business.delete()

    @patch('bale.client._call', side_effect=_ok)
    def test_empty_queue_says_so(self, _call):
        self.make_business('تأییدشده', Business.MODERATION_APPROVED)

        self.say('/pending')

        self.assertTrue(any('هیچ کسب‌وکاری در انتظار بررسی نیست' in t
                            for t in self.sent_texts(_call)))

    @patch('bale.client._call', side_effect=_ok)
    def test_lists_only_pending_businesses(self, _call):
        self.make_business('در انتظار الف')
        self.make_business('در انتظار ب')
        self.make_business('تأییدشده', Business.MODERATION_APPROVED)
        self.make_business('ردشده', Business.MODERATION_REJECTED)

        self.say('/pending')

        texts = self.sent_texts(_call)
        joined = '\n'.join(texts)
        self.assertIn('2 کسب‌وکار در انتظار بررسی', joined)
        self.assertIn('در انتظار الف', joined)
        self.assertIn('در انتظار ب', joined)
        self.assertNotIn('تأییدشده', joined)
        self.assertNotIn('ردشده', joined)

    @patch('bale.client._call', side_effect=_ok)
    def test_listed_cards_carry_decision_buttons(self, _call):
        biz = self.make_business('در انتظار')

        self.say('/pending')

        buttons = self.sent_buttons(_call)
        self.assertIn(f'm:d:{biz.pk}:A', buttons)
        self.assertIn(f'm:q:{biz.pk}:R', buttons)

    @patch('bale.client._call', side_effect=_ok)
    def test_help_is_offered_for_unknown_commands(self, _call):
        self.say('/help')

        self.assertTrue(any('/pending' in t for t in self.sent_texts(_call)))

    @patch('bale.client._call', side_effect=_ok)
    def test_unauthorised_sender_gets_no_listing(self, _call):
        self.make_business('محرمانه')

        self.say('/pending', sender='9999999999')

        self.assertNotIn('محرمانه', '\n'.join(self.sent_texts(_call)))


class ParseCallbackTests(TestCase):
    def test_decode_round_trip(self):
        self.assertEqual(
            parse_callback('m:d:12:A'),
            {'action': 'decide', 'business_id': 12,
             'status': Business.MODERATION_APPROVED, 'note': ''},
        )
        self.assertEqual(parse_callback('m:b:12'),
                         {'action': 'back', 'business_id': 12})
        self.assertEqual(parse_callback('m:q:12:S'),
                         {'action': 'menu', 'business_id': 12, 'letter': 'S'})
        self.assertEqual(
            parse_callback('m:w:12:R'),
            {'action': 'ask_reason', 'business_id': 12, 'letter': 'R',
             'status': Business.MODERATION_REJECTED},
        )

    def test_approval_can_never_ask_for_a_reason(self):
        """An approval has no note to give, so 'A' must not reach the prompt."""
        self.assertIsNone(parse_callback('m:w:12:A'))

    def test_reject_always_carries_a_note(self):
        parsed = parse_callback('m:d:12:R:0')
        self.assertEqual(parsed['status'], Business.MODERATION_REJECTED)
        self.assertTrue(parsed['note'])

    def test_hostile_input_returns_none(self):
        for data in (None, '', 'm', 'x:d:1:A', 'm:d:one:A', 'm:d:1:R:404',
                     'm:q:1:A', 'm:zzz:1'):
            with self.subTest(data=data):
                self.assertIsNone(parse_callback(data))


class BaleSettingsTests(TestCase):
    def test_is_a_singleton_with_a_generated_secret(self):
        first = BaleSettings.load()
        self.assertTrue(first.webhook_secret)

        second = BaleSettings(bot_token='other')
        second.save()

        self.assertEqual(BaleSettings.objects.count(), 1)
        self.assertEqual(second.pk, first.pk)

    def test_is_configured_requires_all_three(self):
        config = BaleSettings.load()
        self.assertFalse(config.is_configured)

        config.bot_token = 't'
        config.chat_id = 'c'
        self.assertFalse(config.is_configured)

        config.is_enabled = True
        self.assertTrue(config.is_configured)
