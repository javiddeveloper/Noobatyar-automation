from django.contrib import admin
from .models import DeviceToken, User


@admin.register(User)
class UserAdmin(admin.ModelAdmin):
    list_display = ['phone', 'name', 'role', 'is_employee', 'joined_at']
    list_filter = ['role', 'is_employee']
    search_fields = ['phone', 'name']


@admin.register(DeviceToken)
class DeviceTokenAdmin(admin.ModelAdmin):
    list_display = ['user', 'platform', 'device_name', 'is_active', 'updated_at']
    list_filter = ['platform', 'is_active']
    search_fields = ['user__phone', 'user__name', 'token']
    readonly_fields = ['created_at', 'updated_at']
