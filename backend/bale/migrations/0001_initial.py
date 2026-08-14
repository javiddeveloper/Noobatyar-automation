# Hand-written: the local interpreter is Python 3.9 and cannot run manage.py,
# so this mirrors what makemigrations would emit for bale/models.py.

from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):

    initial = True

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
    ]

    operations = [
        migrations.CreateModel(
            name='BaleSettings',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('bot_token', models.CharField(blank=True, default='', help_text='توکنی که BotFather بله می‌دهد', max_length=255)),
                ('chat_id', models.CharField(blank=True, default='', help_text='شناسه‌ی چتی که اعلان‌ها به آن می‌رود؛ تنها فرستنده‌ای که اجازه‌ی تصمیم‌گیری دارد هم همین است', max_length=64)),
                ('webhook_secret', models.CharField(blank=True, default='', help_text='بخش مخفی مسیر وبهوک — خودکار ساخته می‌شود، دستی عوضش نکن', max_length=64)),
                ('is_enabled', models.BooleanField(default=False, help_text='تا وقتی خاموش است هیچ پیامی ارسال و هیچ تصمیمی پذیرفته نمی‌شود')),
                ('updated_at', models.DateTimeField(auto_now=True)),
                ('actor', models.ForeignKey(blank=True, help_text='کاربری که تصمیم‌های گرفته‌شده از بله به نامش در لاگ ثبت می‌شود', null=True, on_delete=django.db.models.deletion.SET_NULL, related_name='+', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'verbose_name': 'تنظیمات ربات بله',
                'verbose_name_plural': 'تنظیمات ربات بله',
                'db_table': 'bale_settings',
            },
        ),
    ]
