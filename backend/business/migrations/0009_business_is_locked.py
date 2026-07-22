from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('business', '0008_business_bio'),
    ]

    operations = [
        migrations.AddField(
            model_name='business',
            name='is_locked',
            field=models.BooleanField(
                default=False,
                db_index=True,
                help_text='If True, this business is locked due to subscription limits (data kept, but hidden/read-only).',
            ),
        ),
    ]
