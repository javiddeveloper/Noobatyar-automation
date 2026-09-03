from adrf.views import APIView
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.parsers import MultiPartParser, FormParser, JSONParser
from asgiref.sync import sync_to_async
from django.core.exceptions import ValidationError
from django.core.paginator import Paginator
from django.utils import timezone
import logging

from rest_framework.exceptions import ValidationError as DRFValidationError

from . import services
from .models import Business, ServiceCatalogItem
from .serializers import (
    BusinessSerializer,
    ServiceCatalogItemSerializer,
    normalize_service_names,
)
from api.responses import APIResponse
from accounting import entitlements
from accounting.permissions import validate_business_settings

logger = logging.getLogger(__name__)


class BusinessView(APIView):
    permission_classes = [IsAuthenticated]
    parser_classes = [MultiPartParser, FormParser, JSONParser]

    async def _get_business_or_404(self, business_id: int, user):
        """Helper to retrieve business with ownership check."""
        try:
            return await Business.objects.aget(id=business_id, user=user)
        except Business.DoesNotExist:
            logger.warning(f"Business {business_id} not found for user {user.id}")
            return None

    async def get(self, request, business_id=None):
        """Retrieve single business or list all businesses for user."""
        user = request.user

        if business_id:
            business = await self._get_business_or_404(business_id, user)
            if not business:
                return APIResponse.error(
                    message="کسب و کار مورد نظر یافت نشد",
                    code=404
                )

            serializer = BusinessSerializer(business)
            return APIResponse.success(
                data=serializer.data,
                message="اطلاعات کسب و کار با موفقیت دریافت شد"
            )

        # List all businesses with pagination
        page = int(request.query_params.get('page', 1))
        page_size = int(request.query_params.get('page_size', 10))
        page_size = min(page_size, 100)  # حداکثر 100 آیتم در هر صفحه

        businesses = [
            b async for b in Business.objects.filter(user=user).order_by('-created_at')
        ]

        # Paginate
        paginator = Paginator(businesses, page_size)
        page_obj = paginator.get_page(page)

        serializer = BusinessSerializer(page_obj.object_list, many=True)
        logger.info(f"Retrieved {len(businesses)} businesses for user {user.id}")

        return APIResponse.success(
            data={
                'count': paginator.count,
                'total_pages': paginator.num_pages,
                'current_page': page_obj.number,
                'next': page_obj.next_page_number() if page_obj.has_next() else None,
                'previous': page_obj.previous_page_number() if page_obj.has_previous() else None,
                'results': serializer.data
            },
            message="لیست کسب و کارها با موفقیت دریافت شد"
        )

    @sync_to_async
    def _check_business_quota(self, user):
        """Return an error message if the user has hit their max_businesses quota."""
        quota = entitlements.get_quota(user, entitlements.QUOTA_MAX_BUSINESSES)
        if entitlements.is_unlimited(quota):
            return None
        # Businesses awaiting moderation count against the quota. The argument
        # for excluding them is that the owner gets no value from a business the
        # public cannot see, so charging a slot for it feels like billing for
        # nothing. It loses to the abuse case: not counting them lets anyone
        # exceed their plan indefinitely by parking businesses in review, and
        # every one of those is a real row a human reviewer has to work through.
        # The slot is consumed the moment the record exists, so `is_locked` (the
        # billing flag) stays the only filter here — moderation state is
        # editorial and deliberately not mixed into a quota decision.
        current = Business.objects.filter(user=user, is_locked=False).count()
        if current >= quota:
            return (
                f"با پلن فعلی حداکثر می‌توانید {quota} کسب‌وکار فعال داشته باشید. "
                f"برای افزودن کسب‌وکار بیشتر، پلن خود را ارتقا دهید."
            )
        return None

    async def post(self, request):
        """Create new business."""
        user = request.user

        # Entitlement checks: business count quota + capability-gated settings.
        quota_error = await self._check_business_quota(user)
        if quota_error:
            return APIResponse.error(message=quota_error, code=403)

        settings_error = await sync_to_async(validate_business_settings)(user, request.data)
        if settings_error:
            return APIResponse.error(message=settings_error, code=403)

        serializer = BusinessSerializer(data=request.data)

        try:
            if await sync_to_async(serializer.is_valid)(raise_exception=True):
                # moderation_status already defaults to PENDING, but
                # moderation_submitted_at defaults to null and the review queue
                # orders by it — a null leaves brand-new businesses sorted
                # unpredictably against ones that were resubmitted, so the
                # oldest-waiting-first rule quietly stops holding. Creating the
                # business *is* the submission, so stamp it here.
                business = await sync_to_async(serializer.save)(
                    user=user, moderation_submitted_at=timezone.now()
                )
                logger.info(f"Business created: {serializer.data['id']} by user {user.id}")
                # Creating the business *is* the submission (see above), so this
                # is the only point where a brand-new queue entry exists to
                # announce — the two re-queue paths in services.py cover edits.
                await sync_to_async(services.notify_review_queued)(
                    business, kind='new'
                )
                return APIResponse.success(
                    data=serializer.data,
                    message=(
                        "کسب و کار با موفقیت ایجاد شد و برای بررسی ارسال شد؛ "
                        "پس از تأیید برای مشتریان نمایش داده می‌شود"
                    ),
                    status=201
                )
        except ValidationError as e:
            logger.warning(f"Validation error creating business for user {user.id}: {e}")
            return APIResponse.error(
                message="اطلاعات وارد شده معتبر نیست",
                code=400
            )

    async def put(self, request, business_id):
        """Update existing business."""
        user = request.user
        business = await self._get_business_or_404(business_id, user)

        if not business:
            return APIResponse.error(
                message="کسب و کار مورد نظر یافت نشد",
                code=404
            )

        # Capability-gated settings (gateway, deposit, promo SMS, capacity).
        settings_error = await sync_to_async(validate_business_settings)(user, request.data)
        if settings_error:
            return APIResponse.error(message=settings_error, code=403)

        serializer = BusinessSerializer(business, data=request.data, partial=True)

        # Snapshot the publicly-visible copy *before* the save: once the
        # serializer has written the instance the old values are gone, and
        # without them there is no way to tell a real edit from a form that
        # posted the whole object back unchanged.
        before = services.moderated_snapshot(business)

        # Whether this business has ever cleared review before the save below —
        # decides which of the two "your edit is under review" messages is
        # true afterwards (its own moderation_status is about to change).
        had_prior_approval = business.first_approved_at is not None

        try:
            if await sync_to_async(serializer.is_valid)(raise_exception=True):
                # save() and the re-queue check run as one transaction (see
                # save_with_moderation's docstring): two separate sync_to_async
                # hops would leave a window where an edited, unreviewed business
                # is committed and still APPROVED.
                requeued = await sync_to_async(services.save_with_moderation)(
                    serializer, business, before
                )
                logger.info(f"Business updated: {business_id} by user {user.id}")

                if requeued:
                    # Re-serialise from the row rather than reusing the cached
                    # representation: submit_for_review moved the status and the
                    # submitted-at stamp after the serializer rendered, and the
                    # owner has to see that this edit is under review.
                    business = await Business.objects.aget(id=business_id, user=user)
                    data = BusinessSerializer(business).data
                    if had_prior_approval:
                        # Staged path (business/services.py): the live, public
                        # copy is untouched — customers keep seeing the last-
                        # approved version until this edit is cleared.
                        message = (
                            "تغییرات برای بررسی ارسال شد؛ تا تأیید، نسخه‌ی قبلی "
                            "همچنان برای مشتریان نمایش داده می‌شود"
                        )
                    else:
                        message = (
                            "تغییرات ذخیره شد؛ چون اطلاعات نمایش‌داده‌شده تغییر کرده، "
                            "کسب و کار دوباره برای بررسی ارسال شد و تا تأیید مجدد "
                            "برای مشتریان نمایش داده نمی‌شود"
                        )
                    return APIResponse.success(data=data, message=message)

                return APIResponse.success(
                    data=serializer.data,
                    message="کسب و کار با موفقیت بروزرسانی شد"
                )
        except ValidationError as e:
            logger.warning(f"Validation error updating business {business_id}: {e}")
            return APIResponse.error(
                message="اطلاعات وارد شده معتبر نیست",
                code=400
            )

    async def delete(self, request, business_id):
        """Delete business."""
        user = request.user
        business = await self._get_business_or_404(business_id, user)

        if not business:
            return APIResponse.error(
                message="کسب و کار مورد نظر یافت نشد",
                code=404
            )

        try:
            business_title = business.title
            await sync_to_async(business.delete)()
            logger.info(f"Business '{business_title}' (ID: {business_id}) deleted by user {user.id}")
            return APIResponse.success(
                message=f"کسب و کار '{business_title}' با موفقیت حذف شد",
                status=200,
                data=None
            )
        except Exception as e:
            logger.error(f"Error deleting business {business_id}: {e}", exc_info=True)
            return APIResponse.error(
                message="خطا در حذف کسب و کار، لطفا مجددا تلاش کنید",
                code=500
            )


class BusinessCategoriesView(APIView):
    """
    The business-category vocabulary, grouped.

    GET -> {"groups": [{"key", "label", "categories": [{"value", "label"}]}],
            "categories": [{"value", "label", "group"}]}

    Exists so a client never has to ship its own copy of the ~35 categories:
    before this, adding one meant a mobile release, and until that release
    every business in a new category rendered as "سایر" (the app's enum falls
    back to OTHER for anything it does not know). Clients should render this
    and keep their local list only as an offline fallback.

    Both shapes are returned from one call rather than making the client
    reshape: `groups` is what a sectioned picker renders, `categories` is what
    a flat filter or a label lookup wants, and computing the second from the
    first in every client is duplicated work over a payload this small.

    ``AllowAny``: this is a static vocabulary with no user data in it, it is
    already visible on every public booking page (the category label is
    rendered there), and the sign-up flow needs it *before* an account exists.
    """
    permission_classes = [AllowAny]

    async def get(self, request):
        groups = [
            {
                'key': key,
                'label': label,
                'categories': [{'value': value, 'label': text} for value, text in pairs],
            }
            for key, label, pairs in Business.CATEGORY_GROUPS
        ]
        flat = [
            {'value': value, 'label': text, 'group': key}
            for key, _label, pairs in Business.CATEGORY_GROUPS
            for value, text in pairs
        ]
        return APIResponse.success(data={'groups': groups, 'categories': flat})


class ServiceCatalogView(APIView):
    """
    Service-name chips, shared across every business in a category.

    GET  ?category=BEAUTY_SALON  -> existing names an owner can pick from.
    POST {category, name}        -> add one, visible to every business in
                                     that category from then on (get_or_create,
                                     no moderation — see ServiceCatalogItem's
                                     docstring for why).
    """
    permission_classes = [IsAuthenticated]
    parser_classes = [JSONParser, FormParser, MultiPartParser]

    async def get(self, request):
        category = request.query_params.get('category')
        valid_categories = {choice[0] for choice in Business.CATEGORY_CHOICES}
        if not category or category not in valid_categories:
            return APIResponse.error(message="دسته‌بندی معتبر نیست", code=400)

        items = [
            i async for i in ServiceCatalogItem.objects.filter(category=category).order_by('name')
        ]
        serializer = ServiceCatalogItemSerializer(items, many=True)
        return APIResponse.success(
            data=serializer.data,
            message="لیست خدمات با موفقیت دریافت شد"
        )

    async def post(self, request):
        category = (request.data.get('category') or '').strip()
        # Same normalisation the business menu uses, so a chip added here can be
        # stored in a business's `services` list and in an appointment's
        # comma-separated `selected_services` without changing shape.
        try:
            names = normalize_service_names([request.data.get('name') or ''])
        except DRFValidationError as exc:
            return APIResponse.error(message=str(exc.detail[0]), code=400)
        name = names[0] if names else ''

        valid_categories = {choice[0] for choice in Business.CATEGORY_CHOICES}
        if category not in valid_categories:
            return APIResponse.error(message="دسته‌بندی معتبر نیست", code=400)
        if not name:
            return APIResponse.error(message="نام خدمت الزامی است", code=400)

        item, created = await sync_to_async(ServiceCatalogItem.objects.get_or_create)(
            category=category, name=name
        )
        serializer = ServiceCatalogItemSerializer(item)
        return APIResponse.success(
            data=serializer.data,
            message="خدمت با موفقیت اضافه شد",
            status=201 if created else 200
        )