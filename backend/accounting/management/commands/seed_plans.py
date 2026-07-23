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
                'description': ['تا ۲ کسب‌وکار', 'تا ۳۰۰ نوبت در ماه', 'درگاه آنلاین + بیعانه', 'گزارش پیشرفته', '۱۵۰ پیامک در ماه'],
                'features': ent.BUNDLE_3M,
            },
            {
                'name': 'شش ماهه',
                'price': 700000,
                'discount_price': None,
                'duration_value': 6,
                'duration_unit': 'month',
                'is_vip': True,
                'description': ['تا ۳ کسب‌وکار', 'تا ۶۰۰ نوبت در ماه', 'واتساپ/تلگرام', '۳۰۰ پیامک در ماه', 'پشتیبانی ویژه'],
                'features': ent.BUNDLE_6M,
            },
            {
                'name': 'یک ساله',
                'price': 1500000,
                'discount_price': None,
                'duration_value': 12,
                'duration_unit': 'month',
                'is_vip': True,
                'description': ['تا ۵ کسب‌وکار', 'تا ۱۰۰۰ نوبت در ماه', 'همه‌ی قابلیت‌ها', '۵۰۰ پیامک در ماه', 'پشتیبانی ویژه'],
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
        # فقط دو نوع بسته وجود دارد: پیامک و نوبت.
        #   • پیامک: هر عدد ۳۲۰ تومان (قیمت تمام‌شده ۱۶۰ × ۲)
        #   • نوبت:  هر عدد ۵۰ تومان
        SMS_UNIT_PRICE = 320
        APPT_UNIT_PRICE = 50
        _fa = str.maketrans('0123456789', '۰۱۲۳۴۵۶۷۸۹')

        addons = []
        for count in (50, 100, 200, 300):
            addons.append({
                'name': f'بسته {str(count).translate(_fa)} پیامک',
                'price': count * SMS_UNIT_PRICE,
                'kind': AddOnPack.KIND_SMS,
                'sms_amount': count,
                'appointment_amount': 0,
                'is_active': True,
            })
        for count in (50, 100, 150, 200, 300, 500):
            addons.append({
                'name': f'بسته {str(count).translate(_fa)} نوبت',
                'price': count * APPT_UNIT_PRICE,
                'kind': AddOnPack.KIND_APPOINTMENT,
                'appointment_amount': count,
                'sms_amount': 0,
                'is_active': True,
            })

        # بسته‌های قدیمی (مثل درگاه آنلاین یا بسته‌های پیامکی قبلی) را غیرفعال کن تا
        # فقط بسته‌های جدید در لیست نمایش داده شوند.
        AddOnPack.objects.exclude(name__in=[a['name'] for a in addons]).update(is_active=False)

        for a in addons:
            pack, created = AddOnPack.objects.get_or_create(name=a['name'], defaults=a)
            if not created:
                for key, value in a.items():
                    setattr(pack, key, value)
                pack.save()
            self.stdout.write(f"✓ بسته {a['name']}")

        self.stdout.write(self.style.SUCCESS('پلن‌ها و بسته‌های افزودنی ساخته/به‌روزرسانی شدند'))
