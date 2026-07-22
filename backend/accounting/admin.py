# accounting/admin.py

from django.contrib import admin
from .models import Plan, Subscription, AddOnPack, AddOnPurchase


@admin.register(Plan)
class PlanAdmin(admin.ModelAdmin):
    list_display = ['name', 'price', 'duration_value', 'duration_unit', 'is_vip', 'is_active']
    list_editable = ['is_active']  # مستقیم از لیست تغییر بده


@admin.register(Subscription)
class SubscriptionAdmin(admin.ModelAdmin):
    list_display = ['user', 'plan', 'status', 'started_at', 'ends_at', 'reminder_sent']
    list_filter = ['status', 'plan']


@admin.register(AddOnPack)
class AddOnPackAdmin(admin.ModelAdmin):
    list_display = ['name', 'price', 'kind', 'sms_amount', 'feature_key', 'duration_days', 'is_active']
    list_editable = ['is_active']
    list_filter = ['kind', 'is_active']


@admin.register(AddOnPurchase)
class AddOnPurchaseAdmin(admin.ModelAdmin):
    list_display = ['order_id', 'user', 'pack', 'amount', 'status', 'expires_at', 'created_at']
    list_filter = ['status', 'pack']
    search_fields = ['order_id', 'track_id']
