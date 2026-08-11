# Generated manually (local toolchain can't run `makemigrations` — see
# noobatyar-local-toolchain memory), following the style of prior migrations
# in this app.

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('business', '0016_merge_20260810_0954'),
    ]

    operations = [
        migrations.AddField(
            model_name='business',
            name='first_approved_at',
            field=models.DateTimeField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name='business',
            name='pending_title',
            field=models.CharField(blank=True, max_length=255, null=True),
        ),
        migrations.AddField(
            model_name='business',
            name='pending_bio',
            field=models.CharField(blank=True, max_length=50, null=True),
        ),
        migrations.AddField(
            model_name='business',
            name='pending_address',
            field=models.TextField(blank=True, null=True),
        ),
        migrations.AddField(
            model_name='business',
            name='pending_logo',
            field=models.ImageField(blank=True, null=True, upload_to='business_logos/'),
        ),
    ]
