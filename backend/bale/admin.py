"""
bale/admin.py

Where the operator wires up the bot. Superuser-only, because the form holds a
bot token and the chat id that the webhook treats as the sole authority to
approve businesses — staff with plain admin access must not be able to point
the review bot at their own chat.
"""

from django.contrib import admin

from .models import BaleSettings


@admin.register(BaleSettings)
class BaleSettingsAdmin(admin.ModelAdmin):
    list_display = ('__str__', 'is_enabled', 'masked_token', 'chat_id', 'updated_at')
    readonly_fields = ('webhook_secret', 'updated_at', 'webhook_url')
    fieldsets = (
        (None, {
            'fields': ('is_enabled', 'bot_token', 'chat_id', 'actor'),
        }),
        ('وبهوک', {
            'fields': ('webhook_url', 'webhook_secret', 'updated_at'),
            'description': (
                'پس از ذخیره‌ی توکن، دستور <code>python manage.py bale_setup</code> '
                'را روی سرور اجرا کن تا این آدرس در بله ثبت شود.'
            ),
        }),
    )

    @admin.display(description='توکن')
    def masked_token(self, obj):
        """Never render the token in full — the changelist is the page most
        likely to end up in a screenshot."""
        token = obj.bot_token or ''
        if not token:
            return '—'
        return f'{token[:6]}…{token[-4:]}' if len(token) > 12 else '…'

    @admin.display(description='آدرس وبهوک')
    def webhook_url(self, obj):
        from .setup import build_webhook_url

        return build_webhook_url(obj) or 'SITE_URL تنظیم نشده است'

    def has_add_permission(self, request):
        # Singleton: the row is created on first access by BaleSettings.load(),
        # and a second row would silently shadow the first.
        return request.user.is_superuser and not BaleSettings.objects.exists()

    def has_delete_permission(self, request, obj=None):
        return False

    def has_view_permission(self, request, obj=None):
        return request.user.is_superuser

    def has_change_permission(self, request, obj=None):
        return request.user.is_superuser

    def changelist_view(self, request, extra_context=None):
        # Make sure the singleton exists so the list is never empty on a fresh
        # install, which would leave a superuser with no way in (add is denied
        # once a row exists, and there is no row to click).
        if request.user.is_superuser:
            BaleSettings.load()
        return super().changelist_view(request, extra_context)
