from adrf.views import APIView
from rest_framework.permissions import IsAuthenticated
from rest_framework.parsers import MultiPartParser, FormParser, JSONParser
from asgiref.sync import sync_to_async
from django.core.exceptions import ValidationError
from django.core.paginator import Paginator
from django.utils import timezone
import logging

from . import services
from .models import Business
from .serializers import BusinessSerializer
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
                await sync_to_async(serializer.save)(
                    user=user, moderation_submitted_at=timezone.now()
                )
                logger.info(f"Business created: {serializer.data['id']} by user {user.id}")
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

        try:
            if await sync_to_async(serializer.is_valid)(raise_exception=True):
                # save() and the re-queue check run as one transaction (see
                # save_and_maybe_requeue's docstring): two separate sync_to_async
                # hops would leave a window where an edited, unreviewed business
                # is committed and still APPROVED.
                requeued = await sync_to_async(services.save_and_maybe_requeue)(
                    serializer, business, before
                )
                logger.info(f"Business updated: {business_id} by user {user.id}")

                if requeued:
                    # Re-serialise from the row rather than reusing the cached
                    # representation: submit_for_review moved the status and the
                    # submitted-at stamp after the serializer rendered, and the
                    # owner has to see that this edit took them off the public
                    # listing — that is the whole point of telling them.
                    business = await Business.objects.aget(id=business_id, user=user)
                    data = BusinessSerializer(business).data
                    return APIResponse.success(
                        data=data,
                        message=(
                            "تغییرات ذخیره شد؛ چون اطلاعات نمایش‌داده‌شده تغییر کرده، "
                            "کسب و کار دوباره برای بررسی ارسال شد و تا تأیید مجدد "
                            "برای مشتریان نمایش داده نمی‌شود"
                        )
                    )

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