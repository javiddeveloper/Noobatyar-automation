# versions/admin.py
from django.contrib import admin
from .models import AppVersion, VersionChangelog

class ChangelogInline(admin.StackedInline):
    model = VersionChangelog

@admin.register(AppVersion)
class AppVersionAdmin(admin.ModelAdmin):
    inlines = [ChangelogInline]
    list_display = ['version_name', 'version_code', 'force_update', 'is_active']
