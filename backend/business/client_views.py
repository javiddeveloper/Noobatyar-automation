from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import AllowAny
from rest_framework.throttling import AnonRateThrottle
from django.core.paginator import Paginator
from django.db.models import Q
import logging

from api.phone import is_iran_phone, normalize_phone
from visitor.auth import VisitorTokenAuthentication
from visitor.models import Visitor

from .models import Business, ContentReport
from .serializers import PublicBusinessSerializer
from api.responses import APIResponse

logger = logging.getLogger(__name__)

MAX_REPORT_DETAIL_LEN = 2000
_VALID_REPORT_REASONS = {code for code, _ in ContentReport.REASON_CHOICES}


class ContentReportRateThrottle(AnonRateThrottle):
    """Per-IP throttle for the abuse-report submission endpoint.

    Rate comes from THROTTLE_RATES['content_report'] (core/settings.py) —
    same subclassing pattern as api/throttles.py's OTPRateThrottle, defined
    here instead of there because api/ is outside this phase's scope.

    AnonRateThrottle (not the shared 'anon' scope every other endpoint uses)
    deliberately: this is a no-account, no-authentication endpoint with
    nothing else limiting it, so a spam/DoS-minded caller filing hundreds of
    fake reports would otherwise only be bounded by the generous general-
    purpose anon rate (60/min) — nowhere near tight enough for something that
    creates a database row and lands in a human reviewer's queue every time.

    get_cache_key is overridden because plain AnonRateThrottle exempts any
    request whose ``request.user.is_authenticated`` is true — and
    ``visitor.models.Visitor.is_authenticated`` is hard-coded ``True`` (see
    that property's own docstring: it exists so DRF's generic
    "is this user logged in" checks work, not to mark a Visitor as a trusted,
    unthrottled account tier). Without this override, a signed-in visitor's
    requests skipped the throttle entirely — verified by hand while wiring
    this up: six requests with a valid ``Authorization: Visitor <token>``
    header all returned 201 with the base class. This endpoint always keys by
    IP, authenticated visitor or not, since nothing about holding a Visitor
    token makes repeated report filing legitimate.
    """
    scope = 'content_report'

    def get_cache_key(self, request, view):
        return self.cache_format % {
            'scope': self.scope,
            'ident': self.get_ident(request),
        }

class ClientBusinessListView(APIView):
    permission_classes = [AllowAny]

    async def get(self, request):
        page = int(request.query_params.get('page', 1))
        page_size = int(request.query_params.get('page_size', 10))
        page_size = min(page_size, 100)
        
        search_query = request.query_params.get('search', '').strip()
        category_query = request.query_params.get('category', '').strip()

        # Build Q objects for filtering. public_filter() carries both reasons a
        # business stays out of public browsing — locked (subscription
        # downgrade) and not editorially approved — so this listing can never
        # drift from the detail/slots endpoints by checking only one of them.
        filters = Business.public_filter()
        if search_query:
            filters &= (Q(title__icontains=search_query) | Q(unique_code__iexact=search_query) | Q(address__icontains=search_query))
        if category_query:
            filters &= Q(category=category_query)

        businesses = [
            b async for b in Business.objects.filter(filters).order_by('-created_at')
        ]

        paginator = Paginator(businesses, page_size)
        page_obj = paginator.get_page(page)

        serializer = PublicBusinessSerializer(
            page_obj.object_list,
            many=True,
            context={'request': request}
        )

        # PublicBusinessSerializer.to_representation() checks the owner's
        # subscription (can_book_appointment -> sync ORM queries) to decide
        # whether to gray out booking, so accessing .data raises
        # SynchronousOnlyOperation unless it's pushed off this async view's
        # event loop thread.
        results = await sync_to_async(lambda: serializer.data)()

        return APIResponse.success(
            data={
                'count': paginator.count,
                'total_pages': paginator.num_pages,
                'current_page': page_obj.number,
                'next': page_obj.next_page_number() if page_obj.has_next() else None,
                'previous': page_obj.previous_page_number() if page_obj.has_previous() else None,
                'results': results
            },
            message="لیست کسب و کارها با موفقیت دریافت شد"
        )


class ClientBusinessDetailView(APIView):
    permission_classes = [AllowAny]

    async def get(self, request, business_id):
        try:
            # public_filter() is a plain Q, so it composes with aget(). A
            # non-approved business is reported as simply missing: telling an
            # anonymous caller "this exists but was rejected" would leak the
            # moderation decision to anyone holding a stale link.
            business = await Business.objects.aget(Business.public_filter(), id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(
                message="کسب و کار مورد نظر یافت نشد",
                code=404
            )

        serializer = PublicBusinessSerializer(business, context={'request': request})
        # Same reason as ClientBusinessListView: to_representation() does a
        # sync ORM lookup to check the owner's subscription.
        data = await sync_to_async(lambda: serializer.data)()
        return APIResponse.success(
            data=data,
            message="اطلاعات کسب و کار با موفقیت دریافت شد"
        )


class ClientContentReportView(APIView):
    """File an abuse/content report against a business — public booking page,
    "report this listing".

    Client identity handling mirrors ClientBusinessListView/DetailView's own
    stance: this must not require the caller to already be a Visitor with an
    account, since most people who notice a problem on a public page are
    anonymous browsers, not signed-in customers (business.models.ContentReport's
    own docstring). VisitorTokenAuthentication.authenticate() already returns
    ``None`` (not a failure) when no ``Authorization: Visitor …`` header is
    present, so pairing it with AllowAny — the same pairing
    ClientAppointmentListView uses for its *required* case, used here for the
    optional case — gives three outcomes without any extra branching:

      * A valid Visitor token → request.user is that Visitor; reporter_visitor
        is set automatically, no phone field needed from the client.
      * No token at all → request.user is AnonymousUser; the caller may
        optionally supply reporter_phone so staff have a way to follow up.
      * An expired/invalid token → VisitorTokenAuthentication raises
        AuthenticationFailed itself (401), same as every other endpoint using
        it — this view does not special-case that away, a stale token should
        surface to the client like anywhere else.

    Throttled by IP (ContentReportRateThrottle above) — the only defence this
    endpoint has against being spammed, since it accepts anonymous callers by
    design and therefore cannot rely on a per-user limit.
    """
    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [AllowAny]
    throttle_classes = [ContentReportRateThrottle]

    async def post(self, request, business_id):
        try:
            # public_filter(), same as ClientBusinessDetailView — reporting a
            # business the caller cannot otherwise see would both 404-leak its
            # existence to a stale/guessed id and let a report be filed against
            # a listing nobody on the public site can currently reach.
            business = await Business.objects.aget(Business.public_filter(), id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(message="کسب و کار مورد نظر یافت نشد", code=404)

        reason = str(request.data.get('reason') or '').strip().upper()
        if reason not in _VALID_REPORT_REASONS:
            return APIResponse.error(message="دلیل گزارش نامعتبر است", code=400)

        detail = str(request.data.get('detail') or '').strip()[:MAX_REPORT_DETAIL_LEN]

        reporter_visitor = None
        reporter_phone = ''
        if isinstance(request.user, Visitor):
            reporter_visitor = request.user
        else:
            raw_phone = str(request.data.get('reporter_phone') or '').strip()
            if raw_phone:
                normalized = normalize_phone(raw_phone)
                if not is_iran_phone(normalized):
                    return APIResponse.error(message="شماره تماس نامعتبر است", code=400)
                reporter_phone = normalized

        await ContentReport.objects.acreate(
            business=business,
            reason=reason,
            detail=detail,
            reporter_visitor=reporter_visitor,
            reporter_phone=reporter_phone,
        )

        return APIResponse.success(
            message="گزارش شما ثبت شد و توسط تیم بررسی خواهد شد",
            status=201,
        )
