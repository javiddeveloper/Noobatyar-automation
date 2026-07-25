from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('appointment', '0006_appointment_reminder_sent_at'),
    ]

    operations = [
        migrations.AddField(
            model_name='appointment',
            name='quota_source',
            field=models.CharField(
                blank=True,
                default='',
                help_text=(
                    "Which credit paid for this booking ('monthly'/'wallet'), so a "
                    "cancellation refunds the right one. Empty = nothing was charged."
                ),
                max_length=10,
            ),
        ),
    ]
