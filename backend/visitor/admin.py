from django.contrib import admin
from .models import Visitor, SmsLog

@admin.register(Visitor)
class VisitorAdmin(admin.ModelAdmin):
    list_display = ('phone_number', 'full_name', 'created_at')
    search_fields = ('phone_number', 'full_name')

@admin.register(SmsLog)
class SmsLogAdmin(admin.ModelAdmin):
    list_display = ('visitor', 'status', 'sent_at')
    list_filter = ('status',)
    search_fields = ('visitor__phone_number',)
