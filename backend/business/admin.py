# business/admin.py
"""
Staff tooling for businesses and the content-moderation workflow.

The centre of gravity here is the review queue at
``<admin>/business/business/moderation-queue/``: a card view of everything
waiting for a decision, oldest first, with any banned-keyword hits highlighted
in the owner's own copy so the reviewer sees *why* a listing surfaced without
reading every field.

Two rules shape the rest of this module:

  * **Every status change goes through business.moderation.apply_decision().**
    That is the only place a BusinessModerationLog row is written, so
    `moderation_status` and `moderation_note` are readonly on the change form —
    a hand-typed status would be a silent, unattributable edit to what the
    public sees. The queue view and the bulk actions are the supported paths.
  * **A GET never mutates.** The decision endpoints are POST-only and rely on
    Django's CSRF middleware; a reviewer's browser prefetching a link must not
    approve anything.
"""

from django.contrib import admin, messages
from django.core.exceptions import PermissionDenied
from django.core.paginator import Paginator
from django.db.models import F
from django.http import HttpResponseRedirect
from django.shortcuts import get_object_or_404
from django.template.response import TemplateResponse
from django.urls import path, reverse
from django.utils import timezone
from django.utils.decorators import method_decorator
from django.utils.html import escape, format_html
from django.utils.http import url_has_allowed_host_and_scheme
from django.utils.safestring import mark_safe
from django.views.decorators.http import require_POST

from api.jalali import format_datetime
from . import moderation
from .models import BannedKeyword, Business, BusinessModerationLog, ContentReport
# services.apply_moderation_decision() = moderation.apply_decision() + the SMS
# that tells the owner. Decisions made here are the ones an owner most needs to
# hear about, so the admin goes through the wrapper rather than the bare helper.
from .services import apply_moderation_decision

QUEUE_PAGE_SIZE = 20

# Labels for the moderated fields, so the queue card and the keyword-hit list
# name the same thing the owner sees in the app.
MODERATED_FIELD_LABELS = {
    'title': 'عنوان',
    'bio': 'معرفی کوتاه',
    'address': 'آدرس',
    'notice_message': 'اطلاعیه',
    'logo': 'لوگو',
}

# Modifier suffixes, not colours. These used to be (fg, bg) hex pairs baked
# into an inline style, which meant every badge in the changelist stayed
# light-mode-only and turned into dark-on-dark under the admin's theme toggle.
# The classes are defined once in admin_custom/css/report.css and carry a value
# for both themes; an empty string is the neutral variant.
_STATUS_BADGE = {
    Business.MODERATION_PENDING:   'warn',
    Business.MODERATION_APPROVED:  'ok',
    Business.MODERATION_REJECTED:  'danger',
    Business.MODERATION_SUSPENDED: '',
}


def _badge(label, variant=''):
    """One `.nb-badge` pill; ``variant`` is 'ok' / 'warn' / 'danger' / '' ."""
    css = 'nb-badge nb-badge--%s' % variant if variant else 'nb-badge'
    return format_html('<span class="{}">{}</span>', css, label)


def _highlight(text, terms):
    """Escaped ``text`` with every hit in ``terms`` wrapped in ``<mark>``.

    Spans come from moderation.find_spans(), which indexes the *original*
    string, so the reviewer reads the owner's own spelling — normalisation is
    only used to decide where the hits are, never to rewrite what is shown.
    """
    text = text or ''
    spans = moderation.find_spans(text, terms) if terms else []
    if not spans:
        return escape(text)

    out, cursor = [], 0
    for start, end in spans:
        out.append(escape(text[cursor:start]))
        out.append(format_html(
            '<mark class="nb-mark-hit">{}</mark>',
            text[start:end],
        ))
        cursor = end
    out.append(escape(text[cursor:]))
    return mark_safe(''.join(out))


def _safe_redirect(request, fallback):
    """Honour a ``next`` parameter, but only when it points back at this host.

    Without the host check the decision endpoints would be an open redirect
    that any page could aim a logged-in moderator at.
    """
    nxt = request.POST.get('next') or ''
    if nxt and url_has_allowed_host_and_scheme(
        nxt, allowed_hosts={request.get_host()}, require_https=request.is_secure()
    ):
        return HttpResponseRedirect(nxt)
    return HttpResponseRedirect(fallback)


class BusinessModerationLogInline(admin.TabularInline):
    """The decision history, shown read-only on the business change page.

    An inline rather than a separate screen because the first question a
    reviewer asks about a business is "has this been through here before, and
    what did we say" — the answer has to be on the page they are already on.
    """

    model = BusinessModerationLog
    extra = 0
    can_delete = False
    fields = ('created_jalali', 'transition', 'note', 'actor')
    readonly_fields = ('created_jalali', 'transition', 'note', 'actor')
    verbose_name = 'تصمیم بررسی'
    verbose_name_plural = 'تاریخچهٔ بررسی'
    ordering = ('-created_at',)

    # The log is an audit trail: it is written by moderation.apply_decision()
    # and never by hand, so no add and no change from anywhere in the admin.
    def has_add_permission(self, request, obj=None):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    @admin.display(description='زمان')
    def created_jalali(self, obj):
        return format_datetime(obj.created_at) if obj.created_at else '—'

    @admin.display(description='تغییر وضعیت')
    def transition(self, obj):
        return f"{obj.from_status or '—'} ← {obj.to_status}"


@admin.register(Business)
class BusinessAdmin(admin.ModelAdmin):
    list_display = (
        'title', 'user', 'unique_code', 'category',
        'moderation_badge', 'lock_badge', 'created_at',
    )
    list_filter = ('moderation_status', 'category', 'is_locked')
    search_fields = ('title', 'unique_code', 'user__phone', 'bio')
    date_hierarchy = 'created_at'
    inlines = [BusinessModerationLogInline]

    # `list_editable = ('is_locked',)` was removed deliberately. `is_locked` is
    # *derived* state: business/services.py:sync_locks() recomputes it from the
    # owner's plan quota on every subscription change, so a value typed into the
    # changelist is reverted the next time that runs — with no warning and no
    # trace. Unlocking a business for real means giving the owner the quota
    # (accounting → اشتراک). The field is still editable on the change form for
    # the rare deliberate override, where the help text is visible.

    # Set by the workflow, never typed. moderation_status/moderation_note are in
    # here too, one step beyond what a "system-set field" normally means: a
    # status edited on this form would change what the public sees with no
    # BusinessModerationLog row behind it, i.e. an unattributable decision.
    # Use the review queue or the changelist actions instead — both log.
    #
    # `unique_code` used to be listed here as system-set. It no longer is: the
    # code is the public URL of the booking page and operators need to be able
    # to hand out a vanity one. It stays *generated* by default (blank on the
    # form → Business.save() makes an 8-character code), so nothing changes for
    # a business nobody edits. Editing it is a real, visible decision — the
    # field's help text says that old links stop working, and Business.clean()
    # rejects a code that collides with another one case-insensitively.
    readonly_fields = (
        'moderation_status',
        'moderation_note',
        'moderation_reviewed_by',
        'moderation_reviewed_at',
        'moderation_submitted_at',
        'created_at',
        'updated_at',
    )

    actions = ('action_approve', 'action_reject', 'action_suspend')

    # No `fieldsets`: Business is under active development by other parts of the
    # team and an explicit fieldset list silently hides any newly added field
    # from this form. Grouping is not worth losing fields over.

    def get_queryset(self, request):
        # The changelist renders `user` on every row (and search hits
        # user__phone), so join it once instead of per row.
        return super().get_queryset(request).select_related('user')

    # ── Changelist columns ────────────────────────────────────────────────
    # Two badges, deliberately different shapes and wordings, because the two
    # flags get confused constantly: moderation_status is *editorial* (has a
    # human cleared this copy?) and is_locked is *billing* (is the owner's plan
    # paying for this business?). Either one alone hides a business from the
    # public; neither implies the other.

    @admin.display(description='بررسی محتوا', ordering='moderation_status')
    def moderation_badge(self, obj):
        return _badge(
            obj.get_moderation_status_display(),
            _STATUS_BADGE.get(obj.moderation_status, ''),
        )

    @admin.display(description='وضعیت پلن', ordering='is_locked')
    def lock_badge(self, obj):
        if obj.is_locked:
            return _badge('🔒 قفل‌شده (سهمیهٔ پلن)', 'danger')
        # Not a badge: "فعال" is the unremarkable state, and pilling it would
        # give the normal case the same visual weight as the one that needs
        # somebody to act.
        return format_html('<span class="nb-quiet">فعال</span>')

    # ── Custom URLs ───────────────────────────────────────────────────────

    def get_urls(self):
        # Prepended, so they win over ModelAdmin's catch-all `<path:object_id>/`
        # pattern — otherwise "moderation-queue" is read as a primary key.
        custom = [
            path(
                'moderation-queue/',
                self.admin_site.admin_view(self.moderation_queue_view),
                name='business_business_moderation_queue',
            ),
            path(
                'moderation-queue/<int:pk>/decide/',
                self.admin_site.admin_view(self.moderation_decide_view),
                name='business_business_moderation_decide',
            ),
        ]
        return custom + super().get_urls()

    def changelist_view(self, request, extra_context=None):
        # Feeds the "صف بررسی" button in the overridden change_list template.
        extra_context = extra_context or {}
        extra_context['moderation_pending_count'] = Business.objects.filter(
            moderation_status=Business.MODERATION_PENDING
        ).count()
        extra_context['moderation_queue_url'] = reverse(
            'admin:business_business_moderation_queue', current_app=self.admin_site.name
        )
        return super().changelist_view(request, extra_context)

    # ── The review queue ──────────────────────────────────────────────────

    def _pending_queryset(self):
        return (
            Business.objects
            .filter(moderation_status=Business.MODERATION_PENDING)
            .select_related('user')
            # Oldest wait first. nulls_first because a row with no
            # submitted_at predates the moderation system (see migration 0013)
            # and has therefore been waiting the longest of all. Matches the
            # biz_moderation_queue_idx index on (status, submitted_at).
            .order_by(F('moderation_submitted_at').asc(nulls_first=True), 'created_at', 'id')
        )

    def _build_card(self, business, keywords):
        """Everything the queue template needs for one business."""
        matches = moderation.scan_keywords(business, keywords=keywords)

        # Highlight each field only with the terms that actually hit *it*, so a
        # term found in the address is not also marked up inside the title.
        terms_by_field = {}
        for match in matches:
            terms_by_field.setdefault(match['field'], []).append(match['term'])
            # scan_keywords() returns raw field names because it is shared with
            # the API layer; the reviewer sees the Persian label instead.
            match['field_label'] = MODERATED_FIELD_LABELS.get(match['field'], match['field'])

        fields = []
        for name, value in moderation.moderated_texts(business).items():
            if not value:
                continue
            fields.append({
                'name': name,
                'label': MODERATED_FIELD_LABELS.get(name, name),
                'html': _highlight(value, terms_by_field.get(name, [])),
                'flagged': name in terms_by_field,
            })

        submitted = business.moderation_submitted_at or business.created_at
        return {
            'business': business,
            'fields': fields,
            'matches': matches,
            'has_high': any(m['severity'] == BannedKeyword.SEVERITY_HIGH for m in matches),
            'submitted_jalali': format_datetime(submitted) if submitted else '—',
            'never_submitted': business.moderation_submitted_at is None,
            'change_url': reverse(
                'admin:business_business_change', args=[business.pk],
                current_app=self.admin_site.name,
            ),
            'decide_url': reverse(
                'admin:business_business_moderation_decide', args=[business.pk],
                current_app=self.admin_site.name,
            ),
        }

    def moderation_queue_view(self, request):
        # admin_view() already enforces staff+login; this adds the per-model
        # check, so a staff account without business.change_business cannot
        # reach the decision buttons by URL.
        if not self.has_change_permission(request):
            raise PermissionDenied

        paginator = Paginator(self._pending_queryset(), QUEUE_PAGE_SIZE)
        page = paginator.get_page(request.GET.get('page'))

        # One query for the whole page instead of one per card.
        keywords = list(BannedKeyword.objects.filter(is_active=True))
        cards = [self._build_card(biz, keywords) for biz in page.object_list]

        context = {
            **self.admin_site.each_context(request),
            'title': 'صف بررسی محتوا',
            'opts': self.model._meta,
            'cards': cards,
            'page_obj': page,
            'paginator': paginator,
            'total_pending': paginator.count,
            'keyword_count': len(keywords),
        }
        return TemplateResponse(request, 'admin/business/moderation_queue.html', context)

    # method_decorator, not a bare @require_POST: the plain decorator inspects
    # its first positional argument as the request, which on a bound method is
    # `self` — it would silently never reject anything.
    @method_decorator(require_POST)
    def moderation_decide_view(self, request, pk):
        """Approve or reject a single business from the queue.

        POST-only and CSRF-protected (no csrf_exempt anywhere in this module):
        a decision is a state change on public content, and link prefetchers,
        crawlers behind an authenticated session, and the browser's back button
        all issue GETs.
        """
        if not self.has_change_permission(request):
            raise PermissionDenied

        business = get_object_or_404(Business, pk=pk)
        fallback = reverse(
            'admin:business_business_moderation_queue', current_app=self.admin_site.name
        )

        decision = request.POST.get('decision')
        note = (request.POST.get('note') or '').strip()

        if decision == 'approve':
            to_status = Business.MODERATION_APPROVED
        elif decision == 'reject':
            to_status = Business.MODERATION_REJECTED
            # A rejection with no reason is unusable: the owner is told "no"
            # with nothing to fix, and nobody can answer the support ticket
            # three months later. Approval needs no note — "it was fine" is a
            # complete explanation.
            if not note:
                self.message_user(
                    request,
                    f'برای رد کردن «{business.title}» باید دلیل بنویسید.',
                    level=messages.ERROR,
                )
                return _safe_redirect(request, fallback)
        else:
            self.message_user(request, 'تصمیم نامعتبر است.', level=messages.ERROR)
            return _safe_redirect(request, fallback)

        notified = apply_moderation_decision(
            business, to_status, actor=request.user, note=note,
        )

        verb = 'تأیید' if to_status == Business.MODERATION_APPROVED else 'رد'
        # The decision is already committed at this point; a failed SMS is
        # reported, never treated as a failure of the decision itself.
        if notified:
            self.message_user(request, f'«{business.title}» {verb} شد.', level=messages.SUCCESS)
        else:
            self.message_user(
                request,
                f'«{business.title}» {verb} شد، اما پیامک اطلاع‌رسانی به مالک ارسال نشد.',
                level=messages.WARNING,
            )
        return _safe_redirect(request, fallback)

    # ── Bulk actions ──────────────────────────────────────────────────────

    def _bulk_decide(self, request, queryset, to_status, verb, note_required):
        """Shared body for the three changelist actions.

        Rejection and suspension route through a confirmation page that asks for
        a reason, for the same reason the per-card reject does — a bulk decision
        is not a licence to skip the explanation the owner receives.
        """
        if not self.has_change_permission(request):
            raise PermissionDenied

        note = (request.POST.get('moderation_note') or '').strip()

        if note_required and not request.POST.get('apply'):
            context = {
                **self.admin_site.each_context(request),
                'title': f'{verb} گروهی کسب‌وکارها',
                'opts': self.model._meta,
                'queryset': queryset,
                'verb': verb,
                'action_name': request.POST.get('action'),
                'selected': request.POST.getlist(admin.helpers.ACTION_CHECKBOX_NAME),
                'note_error': '',
            }
            return TemplateResponse(request, 'admin/business/moderation_bulk.html', context)

        if note_required and not note:
            self.message_user(request, 'نوشتن دلیل الزامی است.', level=messages.ERROR)
            context = {
                **self.admin_site.each_context(request),
                'title': f'{verb} گروهی کسب‌وکارها',
                'opts': self.model._meta,
                'queryset': queryset,
                'verb': verb,
                'action_name': request.POST.get('action'),
                'selected': request.POST.getlist(admin.helpers.ACTION_CHECKBOX_NAME),
                'note_error': 'نوشتن دلیل الزامی است.',
            }
            return TemplateResponse(request, 'admin/business/moderation_bulk.html', context)

        count = 0
        unnotified = 0
        for business in queryset:
            if not apply_moderation_decision(business, to_status, actor=request.user, note=note):
                unnotified += 1
            count += 1

        self.message_user(request, f'{count} کسب‌وکار {verb} شد.', level=messages.SUCCESS)
        if unnotified:
            self.message_user(
                request,
                f'برای {unnotified} مورد پیامک اطلاع‌رسانی ارسال نشد.',
                level=messages.WARNING,
            )
        return None

    @admin.action(description='تأیید کسب‌وکارهای انتخاب‌شده')
    def action_approve(self, request, queryset):
        return self._bulk_decide(
            request, queryset, Business.MODERATION_APPROVED, 'تأیید', note_required=False,
        )

    @admin.action(description='رد کردن کسب‌وکارهای انتخاب‌شده')
    def action_reject(self, request, queryset):
        return self._bulk_decide(
            request, queryset, Business.MODERATION_REJECTED, 'رد', note_required=True,
        )

    @admin.action(description='معلق کردن کسب‌وکارهای انتخاب‌شده')
    def action_suspend(self, request, queryset):
        return self._bulk_decide(
            request, queryset, Business.MODERATION_SUSPENDED, 'تعلیق', note_required=True,
        )


@admin.register(BusinessModerationLog)
class BusinessModerationLogAdmin(admin.ModelAdmin):
    """Browse-only view of the audit trail.

    No add, no change, no delete — including for superusers. If a moderation
    log can be edited from the admin it stops being evidence, and the whole
    reason this table exists separately from LogEntry is that it has to answer
    "who decided this, and why" long after the fact.
    """

    list_display = ('created_jalali', 'business_link', 'transition', 'actor', 'short_note')
    list_filter = ('to_status', 'from_status')
    search_fields = ('business__title', 'business__unique_code', 'note', 'actor__phone')
    date_hierarchy = 'created_at'

    def get_readonly_fields(self, request, obj=None):
        return [f.name for f in self.model._meta.fields]

    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False

    def get_queryset(self, request):
        return super().get_queryset(request).select_related('business', 'actor')

    @admin.display(description='زمان', ordering='created_at')
    def created_jalali(self, obj):
        return format_datetime(obj.created_at) if obj.created_at else '—'

    @admin.display(description='کسب‌وکار', ordering='business__title')
    def business_link(self, obj):
        url = reverse('admin:business_business_change', args=[obj.business_id])
        return format_html('<a href="{}">{}</a>', url, obj.business.title)

    @admin.display(description='تغییر وضعیت')
    def transition(self, obj):
        return f"{obj.from_status or '—'} ← {obj.to_status}"

    @admin.display(description='دلیل')
    def short_note(self, obj):
        return (obj.note[:80] + '…') if len(obj.note) > 80 else (obj.note or '—')


@admin.register(BannedKeyword)
class BannedKeywordAdmin(admin.ModelAdmin):
    """Full CRUD — this list is meant to be tuned by the people using it.

    `is_active` is inline-editable because turning off a term that is flagging
    every legitimate salon needs to take one click during a review session, not
    a trip through a change form. Unlike Business.is_locked nothing recomputes
    this field, so an edit here sticks.
    """

    list_display = ('term', 'severity_badge', 'note', 'is_active', 'created_at')
    list_editable = ('is_active',)
    list_filter = ('severity', 'is_active')
    search_fields = ('term', 'note')

    @admin.display(description='شدت', ordering='severity')
    def severity_badge(self, obj):
        if obj.severity == BannedKeyword.SEVERITY_HIGH:
            return _badge('زیاد', 'danger')
        return _badge('کم', 'warn')


@admin.register(ContentReport)
class ContentReportAdmin(admin.ModelAdmin):
    """Queue-style view of reports filed against businesses.

    Reports arrive from the public booking page, so the working order is
    "newest NEW first, triage, resolve" — hence the status filter, the status
    actions, and the link straight through to the reported business rather than
    a report detail page nobody would read twice.
    """

    list_display = (
        'created_jalali', 'business_link', 'reason', 'status_badge',
        'reporter_display', 'short_detail', 'resulting_decision_link',
    )
    list_filter = ('status', 'reason')
    search_fields = (
        'business__title', 'business__unique_code', 'detail',
        'reporter_phone', 'reporter_user__phone',
    )
    date_hierarchy = 'created_at'
    # Stamped by the actions below, never typed — same rule as the moderation
    # fields on BusinessAdmin. resulting_moderation_log is stamped too, but only
    # by apply_moderation_decision() (business/services.py) when a suspension or
    # rejection auto-resolves this report — never by an admin action here.
    readonly_fields = ('resolved_by', 'resolved_at', 'resulting_moderation_log', 'created_at')
    actions = ('mark_reviewing', 'mark_actioned', 'mark_dismissed')

    def get_queryset(self, request):
        return super().get_queryset(request).select_related(
            'business', 'reporter_user', 'reporter_visitor', 'resulting_moderation_log',
        )

    @admin.display(description='تصمیم مرتبط')
    def resulting_decision_link(self, obj):
        if not obj.resulting_moderation_log_id:
            return '—'
        url = reverse(
            'admin:business_businessmoderationlog_change',
            args=[obj.resulting_moderation_log_id],
        )
        return format_html(
            '<a href="{}">{}</a>', url, obj.resulting_moderation_log.get_to_status_display(),
        )

    @admin.display(description='زمان', ordering='created_at')
    def created_jalali(self, obj):
        return format_datetime(obj.created_at) if obj.created_at else '—'

    @admin.display(description='کسب‌وکار گزارش‌شده', ordering='business__title')
    def business_link(self, obj):
        url = reverse('admin:business_business_change', args=[obj.business_id])
        return format_html(
            '<a href="{}">{}</a> <span class="nb-quiet">({})</span>',
            url, obj.business.title, obj.business.get_moderation_status_display(),
        )

    @admin.display(description='وضعیت', ordering='status')
    def status_badge(self, obj):
        variants = {
            ContentReport.STATUS_NEW:       'danger',
            ContentReport.STATUS_REVIEWING: 'warn',
            ContentReport.STATUS_ACTIONED:  'ok',
            ContentReport.STATUS_DISMISSED: '',
        }
        return _badge(obj.get_status_display(), variants.get(obj.status, ''))

    @admin.display(description='گزارش‌دهنده')
    def reporter_display(self, obj):
        # Anonymous visitors are the common case, so fall back through the
        # three identities rather than showing a blank column.
        if obj.reporter_user_id:
            return str(obj.reporter_user)
        if obj.reporter_visitor_id:
            return str(obj.reporter_visitor)
        return obj.reporter_phone or 'ناشناس'

    @admin.display(description='شرح')
    def short_detail(self, obj):
        return (obj.detail[:60] + '…') if len(obj.detail) > 60 else (obj.detail or '—')

    def _set_status(self, request, queryset, status, label, resolving):
        """Shared body for the three changelist actions.

        Resolving (actioned/dismissed) routes through a confirmation page that
        asks for a reason — same rule as BusinessAdmin's reject/suspend bulk
        actions: a decision that closes a report needs a recoverable "why" for
        whoever reads it later. `mark_reviewing` is not a resolution, so it
        skips this and updates in place.
        """
        if not self.has_change_permission(request):
            raise PermissionDenied

        if not resolving:
            updated = queryset.update(status=status)
            self.message_user(request, f'{updated} گزارش «{label}» شد.', level=messages.SUCCESS)
            return None

        note = (request.POST.get('resolution_note') or '').strip()

        if not request.POST.get('apply'):
            context = {
                **self.admin_site.each_context(request),
                'title': f'{label} گزارش‌ها',
                'opts': self.model._meta,
                'queryset': queryset,
                'verb': label,
                'action_name': request.POST.get('action'),
                'selected': request.POST.getlist(admin.helpers.ACTION_CHECKBOX_NAME),
                'note_error': '',
            }
            return TemplateResponse(request, 'admin/business/content_report_bulk.html', context)

        if not note:
            context = {
                **self.admin_site.each_context(request),
                'title': f'{label} گزارش‌ها',
                'opts': self.model._meta,
                'queryset': queryset,
                'verb': label,
                'action_name': request.POST.get('action'),
                'selected': request.POST.getlist(admin.helpers.ACTION_CHECKBOX_NAME),
                'note_error': 'نوشتن دلیل الزامی است.',
            }
            return TemplateResponse(request, 'admin/business/content_report_bulk.html', context)

        updated = queryset.update(
            status=status,
            resolution_note=note,
            resolved_by=request.user,
            resolved_at=timezone.now(),
        )
        self.message_user(request, f'{updated} گزارش «{label}» شد.', level=messages.SUCCESS)
        return None

    @admin.action(description='علامت‌گذاری به‌عنوان «در حال بررسی»')
    def mark_reviewing(self, request, queryset):
        # Not a resolution — resolved_by/resolved_at stay empty so the report
        # is still visibly open.
        return self._set_status(request, queryset, ContentReport.STATUS_REVIEWING, 'در حال بررسی', False)

    @admin.action(description='علامت‌گذاری به‌عنوان «اقدام شد»')
    def mark_actioned(self, request, queryset):
        return self._set_status(request, queryset, ContentReport.STATUS_ACTIONED, 'اقدام شد', True)

    @admin.action(description='علامت‌گذاری به‌عنوان «رد شد»')
    def mark_dismissed(self, request, queryset):
        return self._set_status(request, queryset, ContentReport.STATUS_DISMISSED, 'رد شد', True)
