# core/admin.py
"""
Changelist registration for core's two models.

The actual segment-building UI (live count, filter form, CSV export) is the
custom pages under NobatyarAdminSite (core/admin_site.py + core/segments.py),
not this file — these ModelAdmin registrations exist so saved segments and
past exports are ordinary, browsable/searchable admin objects too (list,
search, permission-gated visibility), matching how business/admin.py keeps
BusinessModerationLog registered *and* linked into a bespoke queue view.
"""

from django.contrib import admin

from .models import AudienceSegment, AudienceSegmentExport


@admin.register(AudienceSegment)
class AudienceSegmentAdmin(admin.ModelAdmin):
    list_display = ['name', 'kind', 'created_by', 'created_at', 'last_run_at']
    list_filter = ['kind']
    search_fields = ['name']
    readonly_fields = ['created_by', 'created_at', 'last_run_at']

    def save_model(self, request, obj, form, change):
        if not change:
            obj.created_by = request.user
        super().save_model(request, obj, form, change)

    def get_queryset(self, request):
        return super().get_queryset(request).select_related('created_by')


@admin.register(AudienceSegmentExport)
class AudienceSegmentExportAdmin(admin.ModelAdmin):
    """Read-only: this is an audit trail, not something staff edit.

    Same rationale as accounting.TransactionAdmin — a record with no
    legitimate reason to ever be changed after creation gets no edit
    controls, so an accidental edit can't quietly rewrite the audit trail
    it exists to provide.
    """
    list_display = ['created_at', 'kind', 'segment', 'exported_by', 'row_count']
    list_filter = ['kind']
    search_fields = ['exported_by__phone', 'exported_by__name', 'segment__name']
    readonly_fields = [f.name for f in AudienceSegmentExport._meta.fields]

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    def get_queryset(self, request):
        return super().get_queryset(request).select_related('segment', 'exported_by')
