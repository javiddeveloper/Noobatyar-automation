from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('accounting', '0003_plan_features_addons'),
    ]

    operations = [
        migrations.AddField(
            model_name='addonpack',
            name='appointment_amount',
            field=models.PositiveIntegerField(default=0, help_text='تعداد نوبت برای بسته‌ی نوبت'),
        ),
        migrations.AlterField(
            model_name='addonpack',
            name='kind',
            field=models.CharField(
                choices=[
                    ('sms_pack', 'بسته پیامک'),
                    ('appointment_pack', 'بسته نوبت'),
                    ('feature', 'قابلیت موقت'),
                ],
                max_length=20,
            ),
        ),
    ]
