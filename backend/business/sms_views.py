"""
business/sms_views.py

Owner-facing SMS report for a single business:

    GET /api/business/<business_id>/sms-logs/?page=1&page_size=20&status=SENT
        &search=<name or phone>&date_from=YYYY-MM-DD&date_to=YYYY-MM-DD
    GET /api/business/<business_id>/sms-logs/summary/

Every SMS this platform sends on an owner's behalf is billed to that owner's
plan quota or wallet, so the owner needs to be able to see what was actually
sent and what failed — until now ``SmsLog`` rows were written but never read
back by anything, which made "چرا اعتبار پیامکم تموم شد؟" impossible to answer.

These two views deliberately return the plain DRF pagination envelope
(``count``/``next``/``previous``/``results``) and a flat summary object rather
than the project's ``APIResponse`` wrapper: the client apps consume them through
generated paging code that expects the standard shape.

Ownership is enforced by filtering on ``user=request.user`` rather than by
fetching-then-comparing, so a business belonging to somebody else is
indistinguishable from one that does not exist (404, never a 403 that confirms
the id is real).
"""

from datetime import datetime, timezone as dt_timezone

from django.db.models import Q
from django.utils.dateparse import parse_date
from rest_framework.exceptions import NotFound
from rest_framework.pagination import PageNumberPagination
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from accounting import entitlements, usage
from visitor.models import SmsLog

from .models import Business
from .serializers import SmsLogSerializer


class SmsLogPagination(PageNumberPagination):
    """``?page_size=`` is client-controlled but capped, matching the other
    paginated owner endpoints (see BusinessView.get)."""
    page_size = 20
    page_size_query_param = 'page_size'
    max_page_size = 100


def _owned_business(request, business_id):
    """Fetch the business only if the caller owns it.

    DRF's ``NotFound`` is raised rather than ``get_object_or_404``: the latter
    raises Django's ``Http404``, which slips past the ``isinstance(exc, NotFound)``
    branch in ``api.exceptions.custom_exception_handler`` and reaches the client
    as the raw English «No Business matches the given query.» instead of the
    Persian 404 every other endpoint returns.
    """
    try:
        return Business.objects.get(id=business_id, user=request.user)
    except Business.DoesNotExist:
        raise NotFound("کسب و کار مورد نظر یافت نشد")


def _month_start_utc():
    """Start of the current UTC calendar month.

    Must match ``accounting.usage._period()``, which buckets counters by
    ``datetime.now(utc).strftime("%Y-%m")``. Using the local/Tehran month here
    instead would make ``sent_this_month`` disagree with ``monthly_used`` for a
    few hours around every month boundary, and the report is the one place an
    owner goes to reconcile the two.
    """
    now = datetime.now(dt_timezone.utc)
    return now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)


class SmsLogListView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, business_id):
        business = _owned_business(request, business_id)

        queryset = SmsLog.objects.filter(business=business).select_related('visitor')

        # Owner notifications are logged with visitor=None, so select_related is
        # what keeps this from firing one query per row for the rest.
        status_filter = (request.query_params.get('status') or '').upper()
        if status_filter in ('SENT', 'FAILED', 'SKIPPED_QUOTA'):
            queryset = queryset.filter(status=status_filter)

        # Customer filter: match either identifying field SmsLog actually has
        # access to (through visitor). An owner-notification row (visitor=None)
        # never matches a search, which is correct — there is no customer to
        # search by on those rows.
        search = (request.query_params.get('search') or '').strip()
        if search:
            queryset = queryset.filter(
                Q(visitor__full_name__icontains=search) |
                Q(visitor__phone_number__icontains=search)
            )

        # Date range on sent_at. Bad/unparseable values are ignored rather than
        # raising 400s — a malformed date shouldn't blank the whole report, and
        # the summary card already prevents the quota numbers from claiming to
        # match a filtered slice.
        date_from = parse_date(request.query_params.get('date_from') or '')
        if date_from:
            queryset = queryset.filter(sent_at__date__gte=date_from)
        date_to = parse_date(request.query_params.get('date_to') or '')
        if date_to:
            queryset = queryset.filter(sent_at__date__lte=date_to)

        # Model Meta already orders by -sent_at (newest first); not re-stating it
        # here keeps a single definition of "newest first".
        paginator = SmsLogPagination()
        page = paginator.paginate_queryset(queryset, request, view=self)
        serializer = SmsLogSerializer(page, many=True)
        return paginator.get_paginated_response(serializer.data)


class SmsLogSummaryView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, business_id):
        business = _owned_business(request, business_id)

        month_start = _month_start_utc()
        this_month = SmsLog.objects.filter(business=business, sent_at__gte=month_start)

        # Quota/usage/wallet are read straight from the accounting modules
        # instead of being recomputed from SmsLog rows. The two are not the same
        # number and must not be made to look like they are: quota is keyed on
        # the *owner* across all of their businesses, while the counts above are
        # this one business's log. Deriving "used" from the log would also miss
        # refunds (a failed send returns its credit) and every SMS charged
        # outside this business.
        owner_id = business.user_id
        return Response({
            'sent_this_month': this_month.filter(status='SENT').count(),
            'failed_this_month': this_month.filter(status='FAILED').count(),
            'monthly_quota': entitlements.get_quota(owner_id, entitlements.QUOTA_MONTHLY_SMS),
            'monthly_used': usage.get_usage(owner_id, usage.METRIC_SMS),
            'wallet_balance': usage.get_wallet(owner_id),
        })
