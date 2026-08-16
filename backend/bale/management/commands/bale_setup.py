"""
Register the webhook with Bale and prove the chat is reachable.

Run once after filling in the token and chat id in the admin panel, and again
any time SITE_URL or the secret changes:

    docker compose exec -T web python manage.py bale_setup
"""

from django.core.management.base import BaseCommand

from bale.client import get_me, send_message, set_webhook
from bale.models import BaleSettings
from bale.setup import build_webhook_url


class Command(BaseCommand):
    help = 'ثبت وبهوک ربات بله و ارسال یک پیام تست'

    def add_arguments(self, parser):
        parser.add_argument(
            '--skip-test-message',
            action='store_true',
            help='فقط وبهوک را ثبت کن و پیام تست نفرست',
        )

    def handle(self, *args, **options):
        config = BaleSettings.load()

        if not config.bot_token:
            self.stderr.write(self.style.ERROR(
                'توکن ربات خالی است — از پنل ادمین (تنظیمات ربات بله) واردش کن'
            ))
            return
        if not config.chat_id:
            self.stderr.write(self.style.ERROR(
                'chat id خالی است — از پنل ادمین واردش کن'
            ))
            return
        if not config.is_enabled:
            self.stdout.write(self.style.WARNING(
                'هشدار: ربات غیرفعال است؛ وبهوک ثبت می‌شود ولی هیچ پیامی '
                'ارسال و هیچ تصمیمی پذیرفته نمی‌شود تا وقتی فعالش کنی'
            ))

        # getMe first: an invalid token fails here with a clear message instead
        # of surfacing as a confusing setWebhook error.
        me = get_me(config.bot_token)
        if not me['success']:
            self.stderr.write(self.style.ERROR(f'توکن معتبر نیست: {me["error"]}'))
            return
        username = (me.get('result') or {}).get('username', '?')
        self.stdout.write(f'ربات: @{username}')

        url = build_webhook_url(config)
        if not url:
            self.stderr.write(self.style.ERROR(
                'آدرس وبهوک ساخته نشد — SITE_URL باید یک آدرس https عمومی باشد '
                f'(الان: {self._site_url()!r})'
            ))
            return

        result = set_webhook(config.bot_token, url)
        if not result['success']:
            self.stderr.write(self.style.ERROR(f'ثبت وبهوک ناموفق: {result["error"]}'))
            return
        self.stdout.write(self.style.SUCCESS(f'وبهوک ثبت شد: {url}'))

        if options['skip_test_message']:
            return

        sent = send_message(
            config.bot_token,
            config.chat_id,
            'ربات بررسی نوبت‌یار فعال شد ✅\n'
            'از این پس هر کسب‌وکار جدید یا ویرایش‌شده همین‌جا اعلام می‌شود',
        )
        if sent['success']:
            self.stdout.write(self.style.SUCCESS('پیام تست ارسال شد'))
        else:
            self.stderr.write(self.style.ERROR(
                f'پیام تست ارسال نشد (chat id را بررسی کن): {sent["error"]}'
            ))

    def _site_url(self):
        from django.conf import settings

        return getattr(settings, 'SITE_URL', '')
