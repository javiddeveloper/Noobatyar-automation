from django.contrib import admin
from .models import Business

@admin.register(Business)
class BusinessAdmin(admin.ModelAdmin):
    list_display = ('title', 'user', 'unique_code', 'category', 'is_locked', 'created_at')
    list_filter = ('category', 'is_locked')
    list_editable = ('is_locked',)  # آزادسازی/قفل دستی یک کسب‌وکار
    search_fields = ('title', 'unique_code', 'user__phone')
