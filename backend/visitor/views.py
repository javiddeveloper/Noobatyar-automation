from django.shortcuts import render

# Create your views here.
from adrf.views import APIView
from rest_framework.permissions import IsAuthenticated
from rest_framework import status
from asgiref.sync import sync_to_async
from django.core.exceptions import ValidationError
from django.core.paginator import Paginator
import logging

from .models import Visitor
from .serializers import VisitorSerializer
from api.responses import APIResponse  # اضافه کردن import

logger = logging.getLogger(__name__)


class VisitorView(APIView):
    permission_classes = [IsAuthenticated]

    async def _get_visitor_or_404(self, visitor_id: int, user) -> Visitor:
        """Helper to fetch visitor by ID and check ownership"""
        try:
            visitor = await sync_to_async(Visitor.objects.get)(
                id=visitor_id,
                user=user
            )
            return visitor
        except Visitor.DoesNotExist:
            return None

    async def get(self, request, visitor_id=None):
        """List all visitors or retrieve single visitor"""
        try:
            if visitor_id:
                # Retrieve single visitor
                visitor = await self._get_visitor_or_404(visitor_id, request.user)
                if not visitor:
                    return APIResponse.error(
                        message="مشتری یافت نشد",
                        code=404
                    )
                serializer = VisitorSerializer(visitor)
                return APIResponse.success(
                    data=serializer.data,
                    message="اطلاعات مشتری با موفقیت دریافت شد"
                )

            # List all visitors with pagination
            user = request.user

            # Get pagination parameters
            page = int(request.query_params.get('page', 1))
            page_size = int(request.query_params.get('page_size', 10))
            page_size = min(page_size, 100)  # حداکثر 100 آیتم در هر صفحه

            visitors = [
                b async for b in Visitor.objects.filter(user=user).order_by('-created_at')
            ]

            # Paginate
            paginator = Paginator(visitors, page_size)
            page_obj = paginator.get_page(page)

            serializer = VisitorSerializer(page_obj.object_list, many=True)

            return APIResponse.success(
                data={
                    'count': paginator.count,
                    'next': page_obj.next_page_number() if page_obj.has_next() else None,
                    'previous': page_obj.previous_page_number() if page_obj.has_previous() else None,
                    'results': serializer.data
                },
                message="لیست مشتریان با موفقیت دریافت شد"
            )

        except Exception as e:
            logger.error(f"Error retrieving visitor(s): {str(e)}")
            return APIResponse.error(
                message="خطا در دریافت اطلاعات مشتری",
                code=500
            )

    async def post(self, request):
        """Create new visitor"""
        try:
            serializer = VisitorSerializer(data=request.data)

            if await sync_to_async(serializer.is_valid)():
                visitor = await sync_to_async(serializer.save)(user=request.user)
                return APIResponse.success(
                    data=VisitorSerializer(visitor).data,
                    message="مشتری با موفقیت ایجاد شد",
                    status=201
                )

            return APIResponse.error(
                message="اطلاعات وارد شده معتبر نیست",
                code=400,
                data=serializer.errors  # ارسال جزئیات خطا برای دیباگ
            )

        except ValidationError as e:
            return APIResponse.error(
                message=str(e),
                code=400
            )
        except Exception as e:
            logger.error(f"Error creating visitor: {str(e)}")
            return APIResponse.error(
                message="خطا در ایجاد مشتری",
                code=500
            )

    async def put(self, request, visitor_id):
        """Update existing visitor"""
        try:
            visitor = await self._get_visitor_or_404(visitor_id, request.user)
            if not visitor:
                return APIResponse.error(
                    message="مشتری یافت نشد",
                    code=404
                )

            serializer = VisitorSerializer(visitor, data=request.data, partial=True)

            if await sync_to_async(serializer.is_valid)():
                updated_visitor = await sync_to_async(serializer.save)()
                return APIResponse.success(
                    data=VisitorSerializer(updated_visitor).data,
                    message="مشتری با موفقیت بروزرسانی شد"
                )

            return APIResponse.error(
                message="اطلاعات وارد شده معتبر نیست",
                code=400,
                data=serializer.errors
            )

        except ValidationError as e:
            return APIResponse.error(
                message=str(e),
                code=400
            )
        except Exception as e:
            logger.error(f"Error updating visitor: {str(e)}")
            return APIResponse.error(
                message="خطا در به‌روزرسانی مشتری",
                code=500
            )

    async def delete(self, request, visitor_id):
        """Delete visitor"""
        try:
            visitor = await self._get_visitor_or_404(visitor_id, request.user)
            if not visitor:
                return APIResponse.error(
                    message="مشتری یافت نشد",
                    code=404
                )

            # ذخیره نام مشتری برای پیام حذف
            visitor_name = f"{visitor.first_name} {visitor.last_name}" if visitor.first_name or visitor.last_name else visitor.phone_number or visitor.email or f"ID:{visitor.id}"
            
            await sync_to_async(visitor.delete)()
            
            logger.info(f"Visitor {visitor_id} deleted by user {request.user.id}")
            
            return APIResponse.success(
                message=f"مشتری '{visitor_name}' با موفقیت حذف شد",
                status=204,
                data=None
            )

        except Exception as e:
            logger.error(f"Error deleting visitor: {str(e)}")
            return APIResponse.error(
                message="خطا در حذف مشتری",
                code=500
            )