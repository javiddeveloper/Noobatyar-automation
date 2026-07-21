from django.contrib import admin
from .models import Visitor, SmsLog

@admin.register(Visitor)
class VisitorAdmin(admin.ModelAdmin):
    list_display = ('phone', 'name', 'created_at')
    search_fields = ('phone', 'name')

@admin.register(SmsLog)
class SmsLogAdmin(admin.ModelAdmin):
    list_display = ('phone', 'status', 'created_at')
    list_filter = ('status',)
    search_fields = ('phone',)
