from adrf.views import APIView
from rest_framework.permissions import AllowAny
from django.core.paginator import Paginator
from django.db.models import Q
import logging

from .models import Business
from .serializers import ClientBusinessSerializer
from api.responses import APIResponse

logger = logging.getLogger(__name__)

class ClientBusinessListView(APIView):
    permission_classes = [AllowAny]

    async def get(self, request):
        page = int(request.query_params.get('page', 1))
        page_size = int(request.query_params.get('page_size', 10))
        page_size = min(page_size, 100)
        
        search_query = request.query_params.get('search', '').strip()
        category_query = request.query_params.get('category', '').strip()

        # Build Q objects for filtering
        filters = Q()
        if search_query:
            filters &= (Q(title__icontains=search_query) | Q(unique_code__iexact=search_query) | Q(address__icontains=search_query))
        if category_query:
            filters &= Q(category=category_query)

        businesses = [
            b async for b in Business.objects.filter(filters).order_by('-created_at')
        ]

        paginator = Paginator(businesses, page_size)
        page_obj = paginator.get_page(page)

        serializer = ClientBusinessSerializer(
            page_obj.object_list, 
            many=True,
            context={'request': request}
        )

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


class ClientBusinessDetailView(APIView):
    permission_classes = [AllowAny]

    async def get(self, request, business_id):
        try:
            business = await Business.objects.aget(id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(
                message="کسب و کار مورد نظر یافت نشد",
                code=404
            )

        serializer = ClientBusinessSerializer(business, context={'request': request})
        return APIResponse.success(
            data=serializer.data,
            message="اطلاعات کسب و کار با موفقیت دریافت شد"
        )
