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

import logging

from django.contrib import admin
from django.http import HttpResponseRedirect
from django.urls import path, reverse

# core.dashboard is imported inside the views, never here. This module is
# imported while the app registry is still populating (AdminConfig resolves
# `default_site` during autodiscover), and core.dashboard pulls in models from
# five apps — importing it at module level fails with a misleading
# 'does not define NobatyarAdminSite' ImportError.

logger = logging.getLogger(__name__)


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
            path(
                'dashboard/refresh/',
                self.admin_view(self.dashboard_refresh_view),
                name='dashboard_refresh',
            ),
        ]
        return extra_urls + super().get_urls()

    # ── Dashboard ─────────────────────────────────────────────────────────────

    def index(self, request, extra_context=None):
        """The admin landing page: KPI dashboard *above* the usual app list.

        The app list is not replaced. Staff navigate by model far more often
        than they read metrics, and admin/index.html still renders the stock
        `admin/app_list.html` below the dashboard — the dashboard is an addition
        to this page, not a different page.

        The dashboard is built here rather than in `each_context()` because
        each_context runs on *every* admin request, including bot 404s; a dozen
        aggregate queries there would be paid for on pages that never show them.

        A failure to compute metrics must not cost staff the admin index, which
        is the entry point to everything else. So the whole thing is wrapped:
        on error the page renders as the plain app list, with the traceback in
        the logs rather than on the screen.
        """
        from core import dashboard

        context = dict(extra_context or {})
        try:
            context.update(dashboard.context_for(request))
        except Exception:
            logger.exception('admin dashboard failed to build; falling back to the app list')
            context['dashboard_any'] = False
            context['dashboard_failed'] = True
        return super().index(request, extra_context=context)

    def dashboard_refresh_view(self, request):
        """Drop the cached payload and bounce back to the index.

        The numbers are cached for a minute and a half, which is the wrong
        answer exactly when somebody is watching a payment land. This gives them
        a way to force a recompute without waiting out the TTL.
        """
        from core import dashboard

        dashboard.cache.invalidate()
        return HttpResponseRedirect(reverse('admin:index', current_app=self.name))

    def each_context(self, request):
        """
        Common template context for every admin page.

        Later phases can drop KPI numbers in here so base_site.html can render
        them site-wide. Keep anything added here cheap — this runs on *every*
        admin request, including the 404s from bots probing /admin/.
        """
        context = super().each_context(request)
        return context
