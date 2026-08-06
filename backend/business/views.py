from adrf.views import APIView
from rest_framework.permissions import IsAuthenticated
from rest_framework.parsers import MultiPartParser, FormParser, JSONParser
from asgiref.sync import sync_to_async
from django.core.exceptions import ValidationError
from django.core.paginator import Paginator
import logging

from .models import Business, ServiceCatalogItem
from .serializers import BusinessSerializer, ServiceCatalogItemSerializer
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
                await sync_to_async(serializer.save)(user=user)
                logger.info(f"Business created: {serializer.data['id']} by user {user.id}")
                return APIResponse.success(
                    data=serializer.data,
                    message="کسب و کار با موفقیت ایجاد شد",
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

        try:
            if await sync_to_async(serializer.is_valid)(raise_exception=True):
                await sync_to_async(serializer.save)()
                logger.info(f"Business updated: {business_id} by user {user.id}")
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
        name = (request.data.get('name') or '').strip()

        valid_categories = {choice[0] for choice in Business.CATEGORY_CHOICES}
        if category not in valid_categories:
            return APIResponse.error(message="دسته‌بندی معتبر نیست", code=400)
        if not name:
            return APIResponse.error(message="نام خدمت الزامی است", code=400)
        if len(name) > 100:
            return APIResponse.error(message="نام خدمت نباید بیشتر از ۱۰۰ کاراکتر باشد", code=400)

        item, created = await sync_to_async(ServiceCatalogItem.objects.get_or_create)(
            category=category, name=name
        )
        serializer = ServiceCatalogItemSerializer(item)
        return APIResponse.success(
            data=serializer.data,
            message="خدمت با موفقیت اضافه شد",
            status=201 if created else 200
        )