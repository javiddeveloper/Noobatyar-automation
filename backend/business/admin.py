from django.contrib import admin
from .models import Business

@admin.register(Business)
class BusinessAdmin(admin.ModelAdmin):
    list_display = ('title', 'user', 'unique_code', 'category', 'created_at')
    list_filter = ('category',)
    search_fields = ('title', 'unique_code', 'user__phone')
