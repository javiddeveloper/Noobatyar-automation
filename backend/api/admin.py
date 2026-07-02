from django.contrib import admin
from .models import User


@admin.register(User)
class UserAdmin(admin.ModelAdmin):
    list_display = ['phone', 'name', 'role', 'is_employee', 'joined_at']
    list_filter = ['role', 'is_employee']
    search_fields = ['phone', 'name']
