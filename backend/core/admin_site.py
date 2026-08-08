# core/admin_site.py
"""
The project's admin site.

Installed as the *default* admin site through core.apps.NobatyarAdminConfig
(`default_site`), which means `django.contrib.admin.site` **is** an instance of
this class. That matters: every app already registers its models with the
bare `@admin.register(...)` decorator, which targets the default site. Swapping
the class instead of instantiating a second site keeps all of those working
without touching a single app's admin.py.
"""

from django.contrib import admin


class NobatyarAdminSite(admin.AdminSite):
    site_header = 'نوبت‌یار — پنل مدیریت'
    site_title = 'نوبت‌یار'
    index_title = 'مدیریت سامانه'

    # Shown on the login page and in the "view site" link. The admin panel is
    # staff-only tooling and has no public counterpart worth linking to, so the
    # link is dropped rather than pointing at the API root.
    site_url = None

    def get_urls(self):
        """
        Extension point for later phases.

        Custom admin views (dashboard KPIs, the moderation queue, exports) go in
        `extra_urls` below and are prepended so they win over the catch-all
        `^(?P<app_label>...)/$` pattern Django appends at the end of get_urls().

        Wrap every view with `self.admin_view(...)` — that is what enforces the
        staff/login check and adds the never-cache headers. A raw view here is
        world-readable.

        Note: the moderation queue is deliberately *not* built here. It hangs off
        `business/admin.py` (owned by another agent) so it can reuse the
        BusinessAdmin querysets and permissions.
        """
        extra_urls = [
            # path('dashboard/', self.admin_view(dashboard_view), name='dashboard'),
        ]
        return extra_urls + super().get_urls()

    def each_context(self, request):
        """
        Common template context for every admin page.

        Later phases can drop KPI numbers in here so base_site.html can render
        them site-wide. Keep anything added here cheap — this runs on *every*
        admin request, including the 404s from bots probing /admin/.
        """
        context = super().each_context(request)
        return context
