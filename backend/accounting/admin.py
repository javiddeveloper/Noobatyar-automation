# accounting/admin.py
"""
Admin panel for the plan/entitlement system. Beyond browsing, this lets staff
manually grant benefits to a specific user without a real payment:

  * Add a Subscription row (user + plan, leave "تاریخ پایان" blank) to put a
    user on a plan tier — other active subscriptions for that user are
    auto-expired and locked businesses are re-synced against the new plan.
  * Add an AddOnPurchase row (user + pack, status="success", leave
    track_id/order_id/amount blank) to grant SMS credit or a temporary
    feature — the same benefit-granting logic used by real Zibal payments
    runs automatically on save.
"""

from uuid import uuid4

from django.contrib import admin, messages
from django.utils import timezone

from .models import Plan, Subscription, AddOnPack, AddOnPurchase
from .payment.addon_payment import grant_addon_benefit


@admin.register(Plan)
class PlanAdmin(admin.ModelAdmin):
    list_display = ['name', 'price', 'duration_value', 'duration_unit', 'is_vip', 'is_active', 'feature_count']
    list_editable = ['is_active']  # مستقیم از لیست تغییر بده
    search_fields = ['name']

    def feature_count(self, obj):
        return sum(1 for v in obj.features.values() if v is True)
    feature_count.short_description = 'قابلیت‌های فعال'


@admin.register(Subscription)
class SubscriptionAdmin(admin.ModelAdmin):
    list_display = ['user', 'plan', 'status', 'started_at', 'ends_at', 'is_valid_display', 'reminder_sent']
    list_filter = ['status', 'plan']
    search_fields = ['user__phone', 'user__name']
    autocomplete_fields = ['user', 'plan']
    readonly_fields = ['started_at']

    @admin.display(boolean=True, description='معتبر')
    def is_valid_display(self, obj):
        return obj.is_valid()

    def get_form(self, request, obj=None, **kwargs):
        form = super().get_form(request, obj, **kwargs)
        if obj is None:  # add view — this is the manual-grant path
            form.base_fields['ends_at'].required = False
            form.base_fields['ends_at'].help_text = (
                'اگر خالی بماند، بر اساس مدت پلن انتخاب‌شده محاسبه می‌شود.'
            )
        return form

    def save_model(self, request, obj, form, change):
        if obj.ends_at is None:
            obj.ends_at = obj.plan.get_end_date()
        super().save_model(request, obj, form, change)

        if obj.status == 'active':
            # Only one active subscription per user — matches the purchase flow.
            Subscription.objects.filter(user=obj.user, status='active').exclude(pk=obj.pk).update(status='expired')

        # Re-sync business locks (unlocks businesses if the new plan allows more).
        from business.services import sync_locks
        sync_locks(obj.user)

        messages.success(request, f"اشتراک «{obj.plan.name}» برای {obj.user} ثبت شد.")


@admin.register(AddOnPack)
class AddOnPackAdmin(admin.ModelAdmin):
    list_display = ['name', 'price', 'kind', 'sms_amount', 'feature_key', 'duration_days', 'is_active']
    list_editable = ['is_active']
    list_filter = ['kind', 'is_active']
    search_fields = ['name']


@admin.register(AddOnPurchase)
class AddOnPurchaseAdmin(admin.ModelAdmin):
    list_display = ['order_id', 'user', 'pack', 'amount', 'status', 'expires_at', 'created_at']
    list_filter = ['status', 'pack']
    search_fields = ['order_id', 'track_id', 'user__phone', 'user__name']
    autocomplete_fields = ['user', 'pack']
    readonly_fields = ['zibal_response', 'activated_at', 'created_at', 'updated_at']

    def get_form(self, request, obj=None, **kwargs):
        form = super().get_form(request, obj, **kwargs)
        if obj is None:  # add view — this is the manual-grant path
            for field_name in ('track_id', 'order_id', 'amount'):
                form.base_fields[field_name].required = False
                form.base_fields[field_name].help_text = 'در صورت خالی گذاشتن، خودکار پر می‌شود.'
            if 'status' in form.base_fields:
                form.base_fields['status'].initial = 'success'
                form.base_fields['status'].help_text = (
                    'برای اعطای دستی بسته به کاربر، این مقدار را روی «success» نگه دارید.'
                )
        return form

    def save_model(self, request, obj, form, change):
        if obj.pk is None:
            if not obj.amount:
                obj.amount = obj.pack.price
            if not obj.order_id:
                obj.order_id = f"MANUAL-{obj.user_id}-{obj.pack_id}-{int(timezone.now().timestamp())}"
            if not obj.track_id:
                obj.track_id = f"manual-{uuid4().hex[:12]}"

        should_grant = obj.status == 'success' and obj.activated_at is None
        super().save_model(request, obj, form, change)

        if should_grant:
            grant_addon_benefit(obj)
            messages.success(request, f"بسته «{obj.pack.name}» برای {obj.user} فعال شد.")
