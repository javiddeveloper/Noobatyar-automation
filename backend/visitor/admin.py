# visitor/admin.py
"""
Existing Visitor/SmsLog browsing, plus (phase 6) the SMS operations report
hung off ``SmsLogAdmin.get_urls()`` — the same "custom URLs on an existing
ModelAdmin" pattern ``accounting/admin.py``'s ``TransactionAdmin`` and
``business/admin.py``'s ``BusinessAdmin`` already use for their own
report/queue pages. See ``visitor/reports.py`` for the query layer and its
PII-discipline note on ``message_text``.
"""

import csv
import io

from django.contrib import admin
from django.core.exceptions import PermissionDenied
from django.http import HttpResponse
from django.template.response import TemplateResponse
from django.urls import path, reverse

from . import reports
from .models import Visitor, SmsLog


def _add_display_percentages(report):
    """Mutates ``report`` in place, adding ``*_pct`` display strings next to
    the raw 0..1 fractions ``visitor/reports.py`` returns — same split as
    ``accounting/admin.py``'s ``_add_display_percentages``: the fractions stay
    raw in the query layer because tests assert against them directly, and
    the template has no filter that both multiplies by 100 and rounds.
    """
    def pct(rate):
        return f'{rate * 100:.1f}' if rate is not None else None

    report['summary']['failure_rate_pct'] = pct(report['summary']['failure_rate'])
    for row in report['by_business']:
        row['failure_rate_pct'] = pct(row['failure_rate'])
    return report


@admin.register(Visitor)
class VisitorAdmin(admin.ModelAdmin):
    list_display = ('phone_number', 'full_name', 'created_at')
    search_fields = ('phone_number', 'full_name')

@admin.register(SmsLog)
class SmsLogAdmin(admin.ModelAdmin):
    list_display = ('visitor', 'business', 'status', 'sent_at')
    list_filter = ('status',)
    search_fields = ('visitor__phone_number',)

    def get_queryset(self, request):
        # The changelist renders `business` on every row too.
        return super().get_queryset(request).select_related('visitor', 'business')

    # ── Custom URLs ───────────────────────────────────────────────────────
    # Prepended so they win over ModelAdmin's catch-all `<path:object_id>/`
    # pattern — same reason as TransactionAdmin/BusinessAdmin's get_urls().

    def get_urls(self):
        custom = [
            path(
                'reports/',
                self.admin_site.admin_view(self.sms_report_view),
                name='visitor_smslog_report',
            ),
            path(
                'reports/export.csv',
                self.admin_site.admin_view(self.sms_report_csv_view),
                name='visitor_smslog_report_csv',
            ),
        ]
        return custom + super().get_urls()

    def changelist_view(self, request, extra_context=None):
        extra_context = extra_context or {}
        extra_context['sms_report_url'] = reverse(
            'admin:visitor_smslog_report', current_app=self.admin_site.name,
        )
        return super().changelist_view(request, extra_context)

    # ── SMS operations report ────────────────────────────────────────────

    def _check_access(self, request):
        # has_view_permission() is exactly "holds visitor.view_smslog (or is
        # a superuser)" for this ModelAdmin — admin_view() already enforced
        # is_staff/login before this runs; this adds the per-model check on
        # top of it, the same two-layer gate TransactionAdmin uses.
        if not self.has_view_permission(request):
            raise PermissionDenied

    def _params(self, request):
        get = request.GET
        return {
            'date_from': get.get('from', ''),
            'date_to': get.get('to', ''),
            'business_id': get.get('business', ''),
            'status': get.get('status', ''),
        }

    def sms_report_view(self, request):
        self._check_access(request)
        params = self._params(request)
        report = None
        error = None
        try:
            report = reports.build_report(
                params['date_from'], params['date_to'],
                business_id=params['business_id'], status=params['status'],
            )
            _add_display_percentages(report)
        except reports.ReportRangeError as exc:
            error = str(exc)

        context = {
            **self.admin_site.each_context(request),
            'title': 'گزارش عملیات پیامک',
            'opts': self.model._meta,
            'report': report,
            'error': error,
            'date_from': params['date_from'] or (report['range']['from_jalali'] if report else ''),
            'date_to': params['date_to'] or (report['range']['to_jalali'] if report else ''),
            'business_id': params['business_id'],
            'status': params['status'],
        }
        return TemplateResponse(request, 'admin/visitor/sms_report.html', context)

    def sms_report_csv_view(self, request):
        self._check_access(request)
        params = self._params(request)
        try:
            report = reports.build_report(
                params['date_from'], params['date_to'],
                business_id=params['business_id'], status=params['status'],
            )
        except reports.ReportRangeError as exc:
            return HttpResponse(str(exc), status=400, content_type='text/plain; charset=utf-8')

        buffer = io.StringIO()
        writer = csv.writer(buffer)
        writer.writerow(['گزارش عملیات پیامک نوبت‌یار'])
        writer.writerow(['بازه', f"{report['range']['from_jalali']} تا {report['range']['to_jalali']}"])
        writer.writerow([])

        writer.writerow(['خلاصه'])
        writer.writerow(['ارسال‌شده', 'ناموفق', 'جمع', 'نرخ شکست'])
        summary = report['summary']
        rate = f"{summary['failure_rate'] * 100:.1f}%" if summary['failure_rate'] is not None else '—'
        writer.writerow([summary['sent'], summary['failed'], summary['total'], rate])
        writer.writerow([])

        writer.writerow(['ناموفق‌ها به تفکیک کسب‌وکار'])
        writer.writerow(['کسب‌وکار', 'ناموفق', 'ارسال‌شده', 'جمع', 'نرخ شکست'])
        for row in report['by_business']:
            row_rate = f"{row['failure_rate'] * 100:.1f}%" if row['failure_rate'] is not None else '—'
            writer.writerow([row['business'], row['failed'], row['sent'], row['total'], row_rate])
        if not report['by_business']:
            writer.writerow(['—', 0, 0, 0, '—'])
        writer.writerow([])

        # No message_text column — see visitor/reports.py's docstring, "PII
        # discipline". error_detail is provider-side and safe to export.
        writer.writerow([f"موارد اخیر (حداکثر {reports.RECENT_LOGS_LIMIT} ردیف)"])
        writer.writerow(['کسب‌وکار', 'وضعیت', 'زمان', 'جزئیات خطا'])
        for row in report['recent_logs']:
            writer.writerow([row['business'], row['status'], row['sent_at'], row['error_detail']])
        if not report['recent_logs']:
            writer.writerow(['—', '—', '—', '—'])

        # utf-8-sig — same reason as accounting/admin.py's export: without
        # the BOM, Excel opens Persian columns as mojibake.
        content = buffer.getvalue().encode('utf-8-sig')
        response = HttpResponse(content, content_type='text/csv; charset=utf-8')
        filename = f"sms-report-{report['range']['from'].isoformat()}-{report['range']['to'].isoformat()}.csv"
        response['Content-Disposition'] = f'attachment; filename="{filename}"'
        return response
