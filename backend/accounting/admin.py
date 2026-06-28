# accounting/admin.py

from django.contrib import admin
from .models import Plan, Subscription


@admin.register(Plan)
class PlanAdmin(admin.ModelAdmin):
    list_display = ['name', 'price', 'duration_value', 'duration_unit', 'is_vip', 'is_active']
    list_editable = ['is_active']  # مستقیم از لیست تغییر بده


@admin.register(Subscription)
class SubscriptionAdmin(admin.ModelAdmin):
    list_display = ['user', 'plan', 'status', 'started_at', 'ends_at']
    list_filter = ['status', 'plan']
