# accounting/management/commands/seed_plans.py
from django.core.management.base import BaseCommand
from accounting.models import Plan, AddOnPack
from accounting import entitlements as ent


class Command(BaseCommand):
    help = 'پلن‌های پیش‌فرض و بسته‌های افزودنی رو میسازه'

    def handle(self, *args, **kwargs):
        plans = [
            {
                'name': 'آزمایشی',
                'price': 0,
                'discount_price': None,
                'duration_value': 10,
                'duration_unit': 'day',
                'is_vip': False,
                'description': ['سرویس ۱۰ روزه', 'پشتیبانی'],
                'features': ent.BUNDLE_TRIAL,
            },
            {
                'name': 'یک ماهه',
                'price': 120000,
                'discount_price': 50000,
                'duration_value': 1,
                'duration_unit': 'month',
                'is_vip': True,
                'description': ['۱ کسب‌وکار', 'تا ۱۵۰ نوبت در ماه', '۵۰ پیامک', 'پشتیبانی'],
                'features': ent.BUNDLE_1M,
            },
            {
                'name': 'سه ماهه',
                'price': 350000,
                'discount_price': None,
                'duration_value': 3,
                'duration_unit': 'month',
                'is_vip': True,
                'description': ['تا ۳ کسب‌وکار', 'نوبت نامحدود', 'درگاه آنلاین + بیعانه', 'گزارش پیشرفته', '۳۰۰ پیامک'],
                'features': ent.BUNDLE_3M,
            },
            {
                'name': 'شش ماهه',
                'price': 700000,
                'discount_price': None,
                'duration_value': 6,
                'duration_unit': 'month',
                'is_vip': True,
                'description': ['کسب‌وکار نامحدود', 'همه‌ی قابلیت‌ها', 'واتساپ/تلگرام', '۱۰۰۰ پیامک', 'پشتیبانی ویژه'],
                'features': ent.BUNDLE_6M,
            },
            {
                'name': 'یک ساله',
                'price': 1500000,
                'discount_price': None,
                'duration_value': 12,
                'duration_unit': 'month',
                'is_vip': True,
                'description': ['کسب‌وکار نامحدود', 'همه‌ی قابلیت‌ها', '۲۵۰۰ پیامک', 'پشتیبانی ویژه'],
                'features': ent.BUNDLE_12M,
            },
        ]

        for p in plans:
            plan, created = Plan.objects.get_or_create(name=p['name'], defaults=p)
            if not created:
                plan.price = p['price']
                plan.discount_price = p['discount_price']
                plan.duration_value = p['duration_value']
                plan.duration_unit = p['duration_unit']
                plan.description = p['description']
                plan.is_vip = p['is_vip']
                plan.features = p['features']
                plan.save()
            self.stdout.write(f"✓ پلن {p['name']}")

        # ── Add-on packs ──────────────────────────────────────────────
        addons = [
            {
                'name': 'بسته ۲۰۰ پیامک',
                'price': 60000,
                'kind': AddOnPack.KIND_SMS,
                'sms_amount': 200,
            },
            {
                'name': 'بسته ۵۰۰ پیامک',
                'price': 130000,
                'kind': AddOnPack.KIND_SMS,
                'sms_amount': 500,
            },
            {
                'name': 'درگاه آنلاین (۱ ماه)',
                'price': 90000,
                'kind': AddOnPack.KIND_FEATURE,
                'feature_key': ent.FEATURE_ONLINE_GATEWAY,
                'duration_days': 30,
            },
        ]

        for a in addons:
            pack, created = AddOnPack.objects.get_or_create(name=a['name'], defaults=a)
            if not created:
                for key, value in a.items():
                    setattr(pack, key, value)
                pack.save()
            self.stdout.write(f"✓ بسته {a['name']}")

        self.stdout.write(self.style.SUCCESS('پلن‌ها و بسته‌های افزودنی ساخته/به‌روزرسانی شدند'))
