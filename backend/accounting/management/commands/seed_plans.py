# accounting/management/commands/seed_plans.py
from django.core.management.base import BaseCommand
from accounting.models import Plan


class Command(BaseCommand):
    help = 'پلن‌های پیش‌فرض رو میسازه'

    def handle(self, *args, **kwargs):
        plans = [
            {
                'name': 'آزمایشی', 
                'price': 0, 
                'discount_price': None,
                'duration_value': 10, 
                'duration_unit': 'day', 
                'is_vip': False,
                'description': ['سرویس ۱۰ روزه', 'پشتیبانی']
            },
            {
                'name': 'یک ماهه', 
                'price': 120000, 
                'discount_price': 50000,
                'duration_value': 1, 
                'duration_unit': 'month', 
                'is_vip': True,
                'description': ['سرویس ۱ ماهه', 'پشتیبانی']
            },
            {
                'name': 'سه ماهه', 
                'price': 350000, 
                'discount_price': None,
                'duration_value': 3, 
                'duration_unit': 'month', 
                'is_vip': True,
                'description': ['سرویس ۳ ماهه', 'پشتیبانی']
            },
            {
                'name': 'شش ماهه', 
                'price': 700000, 
                'discount_price': None,
                'duration_value': 6, 
                'duration_unit': 'month', 
                'is_vip': True,
                'description': ['سرویس ۶ ماهه', 'پشتیبانی']
            },
            {
                'name': 'یک ساله', 
                'price': 1500000, 
                'discount_price': None,
                'duration_value': 12, 
                'duration_unit': 'month', 
                'is_vip': True,
                'description': ['سرویس ۱۲ ماهه', 'پشتیبانی']
            },
        ]

        for p in plans:
            plan, created = Plan.objects.get_or_create(name=p['name'], defaults=p)
            if not created:
                # آپدیت فیلدها برای پلن‌های موجود
                plan.price = p['price']
                plan.discount_price = p['discount_price']
                plan.duration_value = p['duration_value']
                plan.duration_unit = p['duration_unit']
                plan.description = p['description']
                plan.is_vip = p['is_vip']
                plan.save()
            self.stdout.write(f"✓ {p['name']}")

        self.stdout.write(self.style.SUCCESS('پلن‌ها با موفقیت ساخته و به‌روزرسانی شدن'))
