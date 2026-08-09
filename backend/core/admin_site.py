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

import csv
import io
import logging

from django.contrib import admin
from django.core.exceptions import PermissionDenied
from django.http import HttpResponse, HttpResponseRedirect
from django.shortcuts import get_object_or_404
from django.template.response import TemplateResponse
from django.urls import path, reverse
from django.utils.http import urlencode

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
            # ── Phase 4: 360 pages + audience segmentation ──────────────────
            # Same reasoning as the dashboard URLs above: wrapped in
            # admin_view() for the is_staff/login gate, prepended so nothing
            # here can be swallowed by an app's own catch-all pattern (there
            # isn't one under /core/ today, but keeping the ordering
            # convention avoids relearning this the next time one is added).
            path(
                'core/users/<int:user_id>/',
                self.admin_view(self.user_detail_view),
                name='core_user_detail',
            ),
            path(
                'core/businesses/<int:business_id>/',
                self.admin_view(self.business_detail_view),
                name='core_business_detail',
            ),
            path(
                'core/businesses/<int:business_id>/customers/',
                self.admin_view(self.business_customers_view),
                name='core_business_customers',
            ),
            path(
                'core/segments/',
                self.admin_view(self.segment_builder_view),
                name='core_segment_builder',
            ),
            path(
                'core/segments/save/',
                self.admin_view(self.segment_save_view),
                name='core_segment_save',
            ),
            path(
                'core/segments/<int:segment_id>/run/',
                self.admin_view(self.segment_run_view),
                name='core_segment_run',
            ),
            path(
                'core/segments/export.csv',
                self.admin_view(self.segment_export_view),
                name='core_segment_export',
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

    # ── 360 pages ─────────────────────────────────────────────────────────────
    # core.detail_views is the query layer (core/detail_views.py); these three
    # views do only "fetch object → gate on the page-level permission → let
    # detail_views gate every panel individually → render", the same shape
    # index() above gives the dashboard. Imported lazily for the same reason
    # `core.dashboard` is: this module loads while the app registry is still
    # populating, and detail_views pulls in models from five apps.

    def user_detail_view(self, request, user_id):
        from api.models import User
        from core import detail_views

        # Page-level gate: no api.view_user, no page at all — matches how
        # accounting.TransactionAdmin._check_access() gates the financial
        # report on the one permission that makes the page meaningful.
        # Everything *inside* the page (subscriptions, wallet, businesses,
        # activity) degrades panel-by-panel from there — see
        # detail_views.permissions_for_user().
        if not request.user.has_perm('api.view_user'):
            raise PermissionDenied
        user = get_object_or_404(User, pk=user_id)
        context = {
            **self.each_context(request),
            'title': f'{user.name} ({user.phone})',
            'opts': User._meta,
            'detail': detail_views.build_user_detail(user, request.user),
        }
        return TemplateResponse(request, 'admin/core/user_detail.html', context)

    def business_detail_view(self, request, business_id):
        from business.models import Business
        from core import detail_views

        if not request.user.has_perm('business.view_business'):
            raise PermissionDenied
        business = get_object_or_404(Business, pk=business_id)
        context = {
            **self.each_context(request),
            'title': business.title,
            'opts': Business._meta,
            'detail': detail_views.build_business_detail(business, request.user),
            'customers_url': reverse(
                'admin:core_business_customers', args=[business.id], current_app=self.name,
            ),
        }
        return TemplateResponse(request, 'admin/core/business_detail.html', context)

    def business_customers_view(self, request, business_id):
        """The full, paginated customer list for one business — the standalone
        counterpart to the short preview embedded in business_detail_view.

        Gated on `visitor.view_visitor` in addition to `business.view_business`:
        unlike the 360 page (where this is one panel among several that can
        each be hidden), this whole page *is* the customer list, so the
        equivalent of "hide the panel" here is "no page".
        """
        from business.models import Business
        from core import detail_views

        if not (request.user.has_perm('business.view_business') and request.user.has_perm('visitor.view_visitor')):
            raise PermissionDenied
        business = get_object_or_404(Business, pk=business_id)

        from django.core.paginator import Paginator

        page_number = request.GET.get('page') or 1
        paginator = Paginator(detail_views.business_customers_queryset(business), 50)
        page = paginator.get_page(page_number)

        context = {
            **self.each_context(request),
            'title': f'مراجعان {business.title}',
            'opts': Business._meta,
            'business': business,
            'page': page,
            'total': paginator.count,
        }
        return TemplateResponse(request, 'admin/core/business_customers.html', context)

    # ── Audience segment builder ─────────────────────────────────────────────
    # core.segments is the query layer (core/segments.py); this admin site owns
    # the request/permission/render side. Every one of these four views is
    # `admin_view()`-wrapped by get_urls() above, so is_staff/login is already
    # enforced before any of this runs.

    def _segment_kind(self, request):
        # segment_builder_view/segment_run_view/segment_export_view arrive as
        # GET; segment_save_view is the one POST here, and its form
        # (templates/admin/core/segment_builder.html) submits `kind` as a
        # hidden POST field, not a querystring param — the action URL carries
        # no querystring at all. Reading GET only here meant saving an
        # "owner" segment silently fell through to the 'visitor' default,
        # producing a wrong kind and an empty filter definition (POST's
        # low_wallet_below etc. were parsed as visitor filters and dropped).
        # POST takes priority so a GET param can't shadow an explicit POST.
        kind = request.POST.get('kind') or request.GET.get('kind') or 'visitor'
        return kind if kind in ('visitor', 'owner') else 'visitor'

    def segment_builder_view(self, request):
        """Filter form + live count preview, no export.

        Gate: holding `visitor.view_visitor` unlocks the visitor tab, holding
        `api.view_user` unlocks the owner tab — same "degrade per panel, never
        blanket-403 the page" idiom as everywhere else in this admin, applied
        to *tabs* instead of panels since the two kinds are otherwise
        unrelated pages sharing one template. A viewer with neither gets a
        real 403: there is nothing left on this page for them.
        """
        from core import segments

        can_visitor = request.user.has_perm('visitor.view_visitor')
        can_owner = request.user.has_perm('api.view_user')
        if not (can_visitor or can_owner):
            raise PermissionDenied

        kind = self._segment_kind(request)
        if kind == 'visitor' and not can_visitor:
            raise PermissionDenied
        if kind == 'owner' and not can_owner:
            raise PermissionDenied

        can_export = request.user.has_perm('core.export_pii')
        can_save = request.user.has_perm('core.add_audiencesegment')

        error = None
        counts = None
        preview_rows = []
        exclude_opted_out = request.GET.get('exclude_opted_out', '1') != '0'
        try:
            filters = segments.parse_filters(kind, request.GET)
            if request.GET:
                # The live COUNT is safe for anyone who can see this tab at
                # all — a number identifies nobody. Individual name+phone rows
                # are exactly the thing core.export_pii exists to gate, so
                # they must never render for a viewer who lacks it. Before
                # this check, holding only visitor.view_visitor/api.view_user
                # (which Support already has, per setup_admin_roles.py) was
                # enough to page through 20-row previews and reconstruct an
                # arbitrarily large phone-number list one filter tweak at a
                # time — entirely outside the export_pii gate and the
                # AudienceSegmentExport audit trail this whole feature is
                # built around. The query for preview_rows isn't even run
                # when the viewer can't see the result, not just hidden in
                # the template.
                counts = segments.count_segment(kind, filters, exclude_opted_out=exclude_opted_out)
                if can_export:
                    if kind == 'visitor':
                        preview_qs = segments.visitor_queryset(filters, exclude_opted_out=exclude_opted_out)
                        preview_rows = list(preview_qs.order_by('id')[:20].values('id', 'full_name', 'phone_number'))
                    else:
                        preview_rows = list(
                            segments.owner_queryset(filters).order_by('id')[:20].values('id', 'name', 'phone')
                        )
        except segments.SegmentFilterError as exc:
            error = str(exc)
            filters = {}

        from core.models import AudienceSegment
        saved_segments = AudienceSegment.objects.filter(kind=kind).select_related('created_by')[:25]

        query_for_export = request.GET.urlencode()
        context = {
            **self.each_context(request),
            'title': 'ساخت گروه مخاطب',
            'kind': kind,
            'can_visitor': can_visitor,
            'can_owner': can_owner,
            'can_export': can_export,
            'can_save': can_save,
            'error': error,
            'counts': counts,
            'preview_rows': preview_rows,
            'exclude_opted_out': exclude_opted_out,
            'saved_segments': saved_segments,
            'business_categories': segments.BUSINESS_CATEGORY_CHOICES,
            'appointment_statuses': segments.APPOINTMENT_STATUS_CHOICES,
            'plans': segments.plan_choices(),
            'query_for_export': query_for_export,
            'export_url': (
                reverse('admin:core_segment_export', current_app=self.name) + f'?{query_for_export}'
                if query_for_export else None
            ),
            'params': request.GET,
        }
        return TemplateResponse(request, 'admin/core/segment_builder.html', context)

    def segment_save_view(self, request):
        """Save the current builder filters as a named AudienceSegment.

        POST-only (mutating), gated on the model's own `add_audiencesegment`
        permission — no bespoke permission for "may save a segment definition"
        since a saved filter, unlike an export, carries no PII by itself.
        """
        from django.contrib import messages

        from core import segments
        from core.models import AudienceSegment

        if not request.user.has_perm('core.add_audiencesegment'):
            raise PermissionDenied
        if request.method != 'POST':
            raise PermissionDenied

        kind = self._segment_kind(request)
        name = (request.POST.get('name') or '').strip()
        if not name:
            messages.error(request, 'نام گروه مخاطب الزامی است.')
        else:
            try:
                segments.parse_filters(kind, request.POST)  # validate only; see raw_params() below
            except segments.SegmentFilterError as exc:
                messages.error(request, str(exc))
            else:
                definition = segments.raw_params(kind, request.POST)
                if kind == 'visitor':
                    definition['exclude_opted_out'] = request.POST.get('exclude_opted_out', '1') != '0'
                AudienceSegment.objects.create(
                    name=name, kind=kind, definition=definition, created_by=request.user,
                )
                messages.success(request, f'گروه مخاطب «{name}» ذخیره شد.')

        base = reverse('admin:core_segment_builder', current_app=self.name)
        return HttpResponseRedirect(f'{base}?{request.POST.get("return_qs", "")}')

    def segment_run_view(self, request, segment_id):
        """Re-run a saved segment: bounce to the builder pre-filled with its
        stored definition. Updates `last_run_at` — the segment itself is never
        mutated otherwise, this is the one field that records "somebody looked
        at this again", not what they saw (the filters always re-evaluate live;
        see core/segments.py's module docstring)."""
        from django.utils import timezone as dj_timezone

        from core.models import AudienceSegment

        segment = get_object_or_404(AudienceSegment, pk=segment_id)
        can = (
            request.user.has_perm('visitor.view_visitor') if segment.kind == 'visitor'
            else request.user.has_perm('api.view_user')
        )
        if not can:
            raise PermissionDenied

        segment.last_run_at = dj_timezone.now()
        segment.save(update_fields=['last_run_at'])

        # `definition` already holds the raw, stringified param values
        # (core.segments.raw_params) — the same shape a form submission
        # produces — so this is just handing them back to the same query
        # string the builder always reads, not a separate deserialize path.
        params = dict(segment.definition or {})
        exclude_opted_out = params.pop('exclude_opted_out', True)
        params['kind'] = segment.kind
        params['exclude_opted_out'] = '1' if exclude_opted_out else '0'
        base = reverse('admin:core_segment_builder', current_app=self.name)
        return HttpResponseRedirect(f'{base}?{urlencode(params)}')

    def segment_export_view(self, request):
        """CSV export of the current filter — the one action in this whole
        phase that hands out raw phone numbers.

        Gate: a dedicated `core.export_pii` permission, not `api.view_user` /
        `visitor.view_visitor`. Those two only mean "may browse this data one
        record at a time in the admin" (Support holds `api.view_user` per
        setup_admin_roles.py, for exactly that reason); none of the four
        existing roles (Superadmin/Moderator/Support/Finance) is actually the
        right fit for "may bulk-export a marketing contact list" — see
        core/models.py's Meta.permissions comment on AudienceSegment. Until a
        human decides who else should hold it, only Superadmin can export
        here, which is intentionally conservative rather than picking the
        closest existing role.

        The audit row (core.models.AudienceSegmentExport) is written *before*
        the response is returned, using a row count computed the same way the
        live-count preview computed it — never from `len()` on a materialised
        CSV — so the count in the log is trustworthy even if the client drops
        the connection while the file streams.
        """
        from core import segments
        from core.models import AudienceSegmentExport

        if not request.user.has_perm('core.export_pii'):
            raise PermissionDenied

        kind = self._segment_kind(request)
        exclude_opted_out = request.GET.get('exclude_opted_out', '1') != '0'
        try:
            filters = segments.parse_filters(kind, request.GET)
            # export_rows (not just parse_filters) has to be inside this try:
            # the low-wallet filter's cache-reachability check
            # (segments._apply_low_wallet_filter) raises the same
            # SegmentFilterError, and it can only be known once the query
            # actually runs — it must fail this export closed with a clean
            # 400, not fall through to an unhandled 500.
            header, rows, row_count = segments.export_rows(kind, filters, exclude_opted_out=exclude_opted_out)
        except segments.SegmentFilterError as exc:
            return HttpResponse(str(exc), status=400, content_type='text/plain; charset=utf-8')

        export_definition = segments.raw_params(kind, request.GET)
        export_definition['exclude_opted_out'] = exclude_opted_out
        AudienceSegmentExport.objects.create(
            kind=kind,
            definition=export_definition,
            exported_by=request.user,
            row_count=row_count,
        )

        buffer = io.StringIO()
        writer = csv.writer(buffer)
        writer.writerow(header)
        for row in rows:
            writer.writerow(row)

        # utf-8-sig — same reason accounting/admin.py's CSV export uses it:
        # without the BOM, Excel opens Persian columns as mojibake.
        content = buffer.getvalue().encode('utf-8-sig')
        response = HttpResponse(content, content_type='text/csv; charset=utf-8')
        response['Content-Disposition'] = f'attachment; filename="audience-segment-{kind}-{row_count}.csv"'
        return response

    def each_context(self, request):
        """
        Common template context for every admin page.

        Later phases can drop KPI numbers in here so base_site.html can render
        them site-wide. Keep anything added here cheap — this runs on *every*
        admin request, including the 404s from bots probing /admin/.
        """
        context = super().each_context(request)
        return context
