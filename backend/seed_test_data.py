"""
Script to seed test business data for front_client development.
Run with: python3 manage.py shell < seed_test_data.py
"""
import django
import os
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')

from django.contrib.auth import get_user_model
from business.models import Business

User = get_user_model()

# Create or get test user
user, created = User.objects.get_or_create(
    phone='09100000001',
    defaults={
        'name': 'تست اونر',
        'is_active': True,
    }
)
if created:
    user.set_password('testpass123')
    user.save()
    print(f"✅ User created: {user.phone}")
else:
    print(f"ℹ️  User exists: {user.phone}")

# Create test businesses
businesses_data = [
    {
        'title': 'سالن زیبایی زنانه پارسیان',
        'category': 'BEAUTY_SALON',
        'phone': '02188123456',
        'address': 'تهران، خیابان انقلاب، پلاک ۱۲',
        'default_service_duration': 45,
        'work_start_hour': 9,
        'work_end_hour': 21,
        'notification_enabled': True,
        'notification_types': 'SMS',
        'notification_minutes_before': 30,
        'allow_anonymous_view': True,
    },
    {
        'title': 'کلینیک دکتر احمدی',
        'category': 'DOCTOR',
        'phone': '02144567890',
        'address': 'تهران، شریعتی، نرسیده به پل صدر',
        'default_service_duration': 20,
        'work_start_hour': 8,
        'work_end_hour': 18,
        'notification_enabled': True,
        'notification_types': 'SMS,WHATSAPP',
        'notification_minutes_before': 60,
        'allow_anonymous_view': True,
    },
    {
        'title': 'مشاوره کسب‌وکار ستاره',
        'category': 'CONSULTANT',
        'phone': '02166789012',
        'address': 'تهران، ولیعصر، بالاتر از پارک ساعی',
        'default_service_duration': 60,
        'work_start_hour': 10,
        'work_end_hour': 20,
        'notification_enabled': True,
        'notification_types': 'SMS',
        'notification_minutes_before': 45,
        'allow_anonymous_view': True,
    },
]

for data in businesses_data:
    biz, created = Business.objects.get_or_create(
        title=data['title'],
        user=user,
        defaults=data
    )
    status = "✅ Created" if created else "ℹ️  Exists"
    print(f"{status}: {biz.title} | Code: {biz.unique_code} | ID: {biz.id}")

print("\n🎉 Seed data complete!")
print("\n📋 Business list:")
for b in Business.objects.all():
    print(f"  - [{b.id}] {b.title} | unique_code: {b.unique_code} | URL: /b/Noobatyar-{b.unique_code}")
