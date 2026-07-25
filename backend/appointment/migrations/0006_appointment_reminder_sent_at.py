from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('appointment', '0005_appointment_business_status_date_index'),
    ]

    operations = [
        migrations.AddField(
            model_name='appointment',
            name='reminder_sent_at',
            field=models.DateTimeField(
                blank=True,
                db_index=True,
                help_text='When the pre-appointment reminder SMS was sent (null = not yet)',
                null=True,
            ),
        ),
    ]
