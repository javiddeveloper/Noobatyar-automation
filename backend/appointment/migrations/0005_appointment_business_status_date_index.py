from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('appointment', '0004_appointment_payment_receipt'),
    ]

    operations = [
        migrations.AddIndex(
            model_name='appointment',
            index=models.Index(
                fields=['business', 'status', 'appointment_date'],
                name='appt_biz_status_date_idx',
            ),
        ),
    ]
