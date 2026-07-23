import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ('accounting', '0002_initial'),
    ]

    operations = [
        migrations.AddField(
            model_name='plan',
            name='features',
            field=models.JSONField(blank=True, default=dict),
        ),
        migrations.AddField(
            model_name='subscription',
            name='reminder_sent',
            field=models.BooleanField(default=False),
        ),
        migrations.CreateModel(
            name='AddOnPack',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('name', models.CharField(max_length=100)),
                ('price', models.PositiveIntegerField(help_text='قیمت به تومان')),
                ('kind', models.CharField(choices=[('sms_pack', 'بسته پیامک'), ('feature', 'قابلیت موقت')], max_length=20)),
                ('sms_amount', models.PositiveIntegerField(default=0, help_text='تعداد پیامک برای بسته‌ی پیامکی')),
                ('feature_key', models.CharField(blank=True, default='', help_text='کلید قابلیت برای بسته‌ی قابلیتی', max_length=50)),
                ('duration_days', models.PositiveIntegerField(default=30, help_text='مدت اعتبار بسته‌ی قابلیتی (روز)')),
                ('is_active', models.BooleanField(default=True)),
            ],
        ),
        migrations.CreateModel(
            name='AddOnPurchase',
            fields=[
                ('id', models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name='ID')),
                ('amount', models.PositiveIntegerField()),
                ('track_id', models.CharField(db_index=True, max_length=100, unique=True)),
                ('order_id', models.CharField(max_length=100, unique=True)),
                ('status', models.CharField(choices=[('pending', 'Pending'), ('success', 'Success'), ('failed', 'Failed')], default='pending', max_length=20)),
                ('zibal_response', models.JSONField(blank=True, null=True)),
                ('activated_at', models.DateTimeField(blank=True, null=True)),
                ('expires_at', models.DateTimeField(blank=True, db_index=True, null=True)),
                ('created_at', models.DateTimeField(auto_now_add=True)),
                ('updated_at', models.DateTimeField(auto_now=True)),
                ('pack', models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, related_name='purchases', to='accounting.addonpack')),
                ('user', models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name='addon_purchases', to=settings.AUTH_USER_MODEL)),
            ],
            options={
                'ordering': ['-created_at'],
            },
        ),
        migrations.AddIndex(
            model_name='addonpurchase',
            index=models.Index(fields=['track_id'], name='accounting__track_i_addon_idx'),
        ),
        migrations.AddIndex(
            model_name='addonpurchase',
            index=models.Index(fields=['status'], name='accounting__status_addon_idx'),
        ),
        migrations.AddIndex(
            model_name='addonpurchase',
            index=models.Index(fields=['user', 'status'], name='accounting__user_st_addon_idx'),
        ),
    ]
