from django.contrib import admin
from .models import User


@admin.register(User)
class UserAdmin(admin.ModelAdmin):
    list_display = ['phone', 'name', 'user_type', 'is_employee', 'joined_at']
    list_filter = ['user_type', 'is_employee']
    search_fields = ['phone', 'name']
