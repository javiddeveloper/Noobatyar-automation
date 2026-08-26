"""
api/tests_push.py

Coverage for the webpush block api/services/push.py adds to the FCM payload,
for the owner web panel (docs/OWNER_WEB_PLAN.md ۱۰.۱). Credentials and the
network call are stubbed out — this checks the payload shape, not real
delivery.
"""

from unittest.mock import MagicMock, patch

from django.test import TestCase, override_settings

from api.services import push


class WebpushBlockTests(TestCase):
    def _send(self):
        """Send one token through send_to_token with FCM plumbing stubbed, and
        return the ``message`` dict that would have gone to FCM."""
        fake_response = MagicMock(status_code=200)
        with patch.object(push, '_project_id', return_value='test-project'), \
                patch.object(push, '_access_token', return_value='fake-token'), \
                patch('api.services.push.requests.post', return_value=fake_response) as post:
            ok, detail = push.send_to_token('tok-1', 'عنوان', 'متن', {'appointment_id': 5})
        self.assertTrue(ok, detail)
        return post.call_args.kwargs['json']['message']

    def test_webpush_block_is_present_and_well_formed(self):
        message = self._send()
        self.assertIn('webpush', message)
        webpush = message['webpush']

        self.assertIn('notification', webpush)
        self.assertTrue(webpush['notification'].get('icon'))
        self.assertTrue(webpush['notification'].get('badge'))

        self.assertIn('fcm_options', webpush)
        link = webpush['fcm_options'].get('link')
        self.assertTrue(link and link.startswith('http'))

    @override_settings(FCM_WEBPUSH_CLICK_URL='https://panel.example.test/owner')
    def test_webpush_link_is_read_from_settings(self):
        """The click URL has to be configurable — the panel's real domain is
        not decided yet (docs/OWNER_WEB_PLAN.md بخش ۱۴)."""
        message = self._send()
        self.assertEqual(
            message['webpush']['fcm_options']['link'],
            'https://panel.example.test/owner',
        )

    def test_android_channel_id_is_not_reused_for_webpush(self):
        """FCM_ANDROID_CHANNEL_ID names an Android notification channel
        (NOTIFICATIONS.md ۴); webpush has no channel concept, so it must not
        leak into this block."""
        message = self._send()
        self.assertNotIn('channel_id', message['webpush']['notification'])

    def test_other_blocks_are_unaffected(self):
        """Adding webpush must not disturb the android/apns blocks already
        relied on by the native owner app."""
        message = self._send()
        self.assertEqual(
            message['android']['notification']['channel_id'],
            'appointment_reminders',
        )
        self.assertEqual(message['apns']['payload']['aps']['sound'], 'default')
