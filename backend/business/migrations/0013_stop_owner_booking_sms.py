from django.db import migrations


def stop_owner_booking_sms(apps, schema_editor):
    """Switch every existing business off owner-notification SMS.

    ``notify_owner_by_sms`` shipped defaulting to True, so every business
    created so far has it on — and almost none of those owners ever chose it,
    they simply got the default. The product decision is that an owner learns
    about their own booking from the app, not from a message they pay for out of
    their own SMS quota, so changing the field default alone would have fixed
    nothing: it only affects rows created from here on.

    This is deliberately not reversible. Restoring True for everyone would
    switch the SMS back on for owners who never asked for it in the first place,
    which is the exact bill we are trying to stop; an owner who does want it can
    turn it back on from the app.
    """
    Business = apps.get_model('business', 'Business')
    Business.objects.filter(notify_owner_by_sms=True).update(notify_owner_by_sms=False)


class Migration(migrations.Migration):

    dependencies = [
        ('business', '0012_business_notice_enabled_business_reminder_delivery_and_more'),
    ]

    operations = [
        migrations.RunPython(stop_owner_booking_sms, migrations.RunPython.noop),
    ]
