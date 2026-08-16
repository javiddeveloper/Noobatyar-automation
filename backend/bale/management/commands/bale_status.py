"""
Show what Bale thinks the webhook is, and what this server thinks it is.

The two disagreeing is the usual cause of "the buttons do nothing": Bale
delivers taps to whatever URL was last registered, which is not necessarily the
one this deployment would build today.

    docker compose exec -T web python manage.py bale_status
"""

from django.core.management.base import BaseCommand

from bale.client import get_me, get_webhook_info
from bale.models import BaleSettings, PendingReason
from bale.setup import build_webhook_url
from business.models import Business


class Command(BaseCommand):
    help = 'وضعیت ربات بله و وبهوک ثبت‌شده'

    def handle(self, *args, **options):
        config = BaleSettings.load()

        self.stdout.write('── تنظیمات محلی ──')
        self.stdout.write(f'فعال: {"بله" if config.is_enabled else "خیر"}')
        self.stdout.write(f'توکن: {"ثبت شده" if config.bot_token else "خالی"}')
        self.stdout.write(f'chat id: {config.chat_id or "خالی"}')
        self.stdout.write(f'کاربر ثبت‌کننده تصمیم: {config.actor or "تنظیم نشده"}')
        expected = build_webhook_url(config)
        self.stdout.write(f'آدرس مورد انتظار: {expected or "ساخته نشد (SITE_URL؟)"}')

        pending_count = Business.objects.filter(
            moderation_status=Business.MODERATION_PENDING).count()
        self.stdout.write(f'در انتظار بررسی: {pending_count}')
        self.stdout.write(f'دلیل نیمه‌تمام: {PendingReason.objects.count()}')

        if not config.bot_token:
            self.stderr.write(self.style.ERROR(
                '\nتوکن خالی است — از پنل ادمین واردش کن'
            ))
            return

        me = get_me(config.bot_token)
        if not me['success']:
            self.stderr.write(self.style.ERROR(f'\nتوکن معتبر نیست: {me["error"]}'))
            return
        self.stdout.write(f'\nربات: @{(me.get("result") or {}).get("username", "?")}')

        info = get_webhook_info(config.bot_token)
        if not info['success']:
            self.stderr.write(self.style.ERROR(
                f'getWebhookInfo ناموفق: {info["error"]}'
            ))
            return

        data = info.get('result') or {}
        registered = data.get('url') or ''

        self.stdout.write('\n── وضعیت وبهوک در بله ──')
        self.stdout.write(f'آدرس ثبت‌شده: {registered or "(هیچ)"}')
        self.stdout.write(f'در صف تحویل: {data.get("pending_update_count", 0)}')
        if data.get('last_error_message'):
            self.stderr.write(self.style.ERROR(
                f'آخرین خطای تحویل: {data["last_error_message"]} '
                f'({data.get("last_error_date")})'
            ))

        self.stdout.write('')
        if not registered:
            self.stderr.write(self.style.ERROR(
                'هیچ وبهوکی ثبت نشده — دکمه‌ها هیچ‌وقت به سرور نمی‌رسند. '
                'دستور bale_setup را اجرا کن'
            ))
        elif expected and registered != expected:
            self.stderr.write(self.style.ERROR(
                'آدرس ثبت‌شده با آدرس این سرور فرق دارد — bale_setup را دوباره اجرا کن'
            ))
        else:
            self.stdout.write(self.style.SUCCESS('وبهوک درست ثبت شده است'))
