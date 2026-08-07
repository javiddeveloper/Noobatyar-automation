from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('business', '0014_servicecatalogitem_and_more'),
    ]

    operations = [
        migrations.AddField(
            model_name='business',
            name='services',
            field=models.JSONField(
                blank=True,
                default=list,
                help_text="Service names this business offers, e.g. ['کوتاهی مو', 'رنگ مو']",
            ),
        ),
        migrations.AddField(
            model_name='business',
            name='allow_client_add_service',
            field=models.BooleanField(
                default=False,
                help_text="If True, clients may add a service name that is not on the business's menu",
            ),
        ),
    ]
