from django.contrib import admin
from .models import Appointment

@admin.register(Appointment)
class AppointmentAdmin(admin.ModelAdmin):
    list_display = ('id', 'business', 'visitor_phone', 'status', 'created_at')
    list_filter = ('status', 'created_at')
    search_fields = ('visitor_phone', 'business__title')
