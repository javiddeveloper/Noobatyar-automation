from django.shortcuts import render

# Create your views here.
from adrf.views import APIView
from rest_framework.permissions import IsAuthenticated
from rest_framework import status
from asgiref.sync import sync_to_async
from django.core.exceptions import ValidationError
from django.core.paginator import Paginator
from django.db.models import Q
import logging

from .models import Visitor, SmsLog, VisitorArchive, VisitorActivity
from .serializers import VisitorSerializer, SmsLogSerializer
from .activity import record_activity
from api.responses import APIResponse  # اضافه کردن import

logger = logging.getLogger(__name__)


def _positive_int(raw, default):
    """Parse a pagination param, falling back on anything that isn't a positive int."""
    try:
        value = int(raw)
    except (TypeError, ValueError):
        return default
    return value if value > 0 else default


async def get_readable_visitor(visitor_id, user):
    """Return a visitor the given user is allowed to READ.

    An owner can read a visitor they created directly, or a visitor who booked
    online (and is therefore linked to their own user account, not the owner's)
    but has at least one appointment at a business the owner owns. Read-only:
    editing/deleting still requires direct ownership.
    """
    try:
        return await sync_to_async(
            Visitor.objects.filter(
                Q(user=user) | Q(appointments__business__user=user)
            ).distinct().get
        )(id=visitor_id)
    except Visitor.DoesNotExist:
        return None


async def get_owned_visitor(visitor_id, user):
    """Return a visitor the given user is allowed to MODIFY, else None.

    Deliberately stricter than get_readable_visitor(): only a contact this owner
    created themselves. A Visitor row is global — phone_number is unique
    platform-wide, so one row is shared by every business the person books at —
    which means the read rule ("has an appointment at one of my businesses")
    would let any owner rename or re-number someone who is really another
    business's client, for the whole platform.
    """
    try:
        return await sync_to_async(Visitor.objects.get)(id=visitor_id, user=user)
    except Visitor.DoesNotExist:
        return None


class VisitorView(APIView):
    permission_classes = [IsAuthenticated]

    async def _get_visitor_or_404(self, visitor_id: int, user) -> Visitor:
        """Fetch a visitor this owner may act on at all (read-level access).

        An owner curated this contact directly, or the visitor has an
        appointment at one of the owner's businesses. Visitor.user is optional
        (most self-booked visitors never set it), so restricting this to
        `user=owner` would hide almost every online booking from the owner it
        belongs to. Writes that change shared data use get_owned_visitor().
        """
        return await get_readable_visitor(visitor_id, user)

    async def get(self, request, visitor_id=None):
        """List all visitors or retrieve single visitor"""
        try:
            if visitor_id:
                # Retrieve single visitor (readable if owned OR linked to one of
                # the owner's businesses through an appointment).
                visitor = await get_readable_visitor(visitor_id, request.user)
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

            # Same readability rule as get_readable_visitor(): include visitors
            # who booked online under their own account but have an appointment
            # at one of this owner's businesses, not just visitors the owner
            # created directly. Visitors this owner archived are hidden from
            # their list only — the row itself is untouched and still visible to
            # every other business the person books at.
            visitors = [
                b async for b in Visitor.objects.filter(
                    Q(user=user) | Q(appointments__business__user=user)
                ).exclude(archives__owner=user).distinct().order_by('-created_at')
            ]

            # Paginate
            paginator = Paginator(visitors, page_size)
            page_obj = paginator.get_page(page)

            serializer = VisitorSerializer(page_obj.object_list, many=True)

            return APIResponse.success(
                data={
                    'count': paginator.count,
                    'total_pages': paginator.num_pages,
                    'current_page': page_obj.number,
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
        """Create new visitor, or attach to an existing one by phone number.

        phone_number is globally unique on Visitor now (it's one real person
        across the whole platform, not a per-owner record), so a blind create
        would 500 on a visitor who already exists — whether from their own
        online booking or another owner having added them first.
        """
        try:
            serializer = VisitorSerializer(data=request.data)

            if await sync_to_async(serializer.is_valid)():
                phone = serializer.validated_data['phone_number']
                full_name = serializer.validated_data['full_name']

                visitor, created = await sync_to_async(Visitor.objects.get_or_create)(
                    phone_number=phone,
                    defaults={'full_name': full_name, 'user': request.user},
                )
                if not created and visitor.user_id is None:
                    visitor.user = request.user
                    await sync_to_async(visitor.save)(update_fields=['user'])

                return APIResponse.success(
                    data=VisitorSerializer(visitor).data,
                    message="مشتری با موفقیت ایجاد شد" if created else "این مشتری از قبل ثبت شده بود",
                    status=201 if created else 200,
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
        """Update a visitor this owner created.

        Editing is restricted to contacts the owner added themselves, because the
        Visitor row is shared platform-wide — see get_owned_visitor().
        """
        try:
            visitor = await get_owned_visitor(visitor_id, request.user)
            if not visitor:
                # Distinguish "not yours to edit" from "does not exist", so the
                # owner is not left guessing why their own list rejects an edit.
                if await self._get_visitor_or_404(visitor_id, request.user):
                    return APIResponse.error(
                        message="این مراجع توسط شما ثبت نشده و اطلاعاتش بین همه کسب‌وکارها مشترک است؛ ویرایش آن مجاز نیست",
                        code=403
                    )
                return APIResponse.error(
                    message="مشتری یافت نشد",
                    code=404
                )

            before = {'full_name': visitor.full_name, 'phone_number': visitor.phone_number}
            serializer = VisitorSerializer(visitor, data=request.data, partial=True)

            if await sync_to_async(serializer.is_valid)():
                updated_visitor = await sync_to_async(serializer.save)()

                changed = {
                    field: {'from': old, 'to': getattr(updated_visitor, field)}
                    for field, old in before.items()
                    if getattr(updated_visitor, field) != old
                }
                if changed:
                    await sync_to_async(record_activity)(
                        updated_visitor,
                        'PROFILE_UPDATED',
                        actor_type=VisitorActivity.ACTOR_OWNER,
                        actor_user=request.user,
                        changed=changed,
                    )

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
        """Archive a visitor from this owner's list — never delete the row.

        The method and URL are unchanged so existing clients keep working, but
        the semantics are now "hide from my list". Deleting the row would take
        the person's appointments at *other* businesses with it and lock them out
        of their own account permanently — see VisitorArchive's docstring.
        """
        try:
            visitor = await self._get_visitor_or_404(visitor_id, request.user)
            if not visitor:
                return APIResponse.error(
                    message="مشتری یافت نشد",
                    code=404
                )

            visitor_name = visitor.full_name or visitor.phone_number or f"ID:{visitor.id}"

            _, created = await sync_to_async(VisitorArchive.objects.get_or_create)(
                owner=request.user, visitor=visitor
            )

            if created:
                await sync_to_async(record_activity)(
                    visitor,
                    'ARCHIVED_BY_OWNER',
                    actor_type=VisitorActivity.ACTOR_OWNER,
                    actor_user=request.user,
                )
                logger.info(f"Visitor {visitor_id} archived by user {request.user.id}")

            return APIResponse.success(
                message=f"مشتری '{visitor_name}' از لیست شما بایگانی شد",
                status=204,
                data=None
            )

        except Exception as e:
            logger.error(f"Error archiving visitor: {str(e)}")
            return APIResponse.error(
                message="خطا در بایگانی مشتری",
                code=500
            )


class VisitorRestoreView(APIView):
    """Undo an archive, putting the visitor back in this owner's list."""

    permission_classes = [IsAuthenticated]

    async def post(self, request, visitor_id):
        try:
            visitor = await get_readable_visitor(visitor_id, request.user)
            if not visitor:
                return APIResponse.error(message="مشتری یافت نشد", code=404)

            deleted, _ = await sync_to_async(
                VisitorArchive.objects.filter(owner=request.user, visitor=visitor).delete
            )()

            if deleted:
                await sync_to_async(record_activity)(
                    visitor,
                    'RESTORED_BY_OWNER',
                    actor_type=VisitorActivity.ACTOR_OWNER,
                    actor_user=request.user,
                )
                logger.info(f"Visitor {visitor_id} restored by user {request.user.id}")
                message = "مشتری به لیست شما بازگردانده شد"
            else:
                message = "این مشتری در بایگانی شما نبود"

            return APIResponse.success(
                data=VisitorSerializer(visitor).data,
                message=message,
            )

        except Exception as e:
            logger.error(f"Error restoring visitor: {str(e)}")
            return APIResponse.error(message="خطا در بازگردانی مشتری", code=500)


class VisitorMessageHistoryView(APIView):
    permission_classes = [IsAuthenticated]

    async def get(self, request, visitor_id):
        visitor = await get_readable_visitor(visitor_id, request.user)
        if not visitor:
            return APIResponse.error("مشتری یافت نشد", code=404)

        # Paginate and serialize in one sync block. SmsLogSerializer exposes
        # visitor_name/visitor_phone, so building `.data` walks the visitor
        # relation — a DB hit that raises SynchronousOnlyOperation if it happens
        # on the async side. Same pattern as ClientAppointmentListView.
        return await sync_to_async(self._list_messages)(request, visitor)

    def _list_messages(self, request, visitor):
        page = _positive_int(request.query_params.get('page'), default=1)
        page_size = min(_positive_int(request.query_params.get('page_size'), default=10), 100)

        # Kept as a queryset (not a list) so Paginator slices at the DB instead
        # of pulling the visitor's entire SMS history into memory.
        logs = (
            SmsLog.objects
            .filter(visitor=visitor, business__user=request.user)
            .select_related('business', 'visitor')
            .order_by('-sent_at')
        )

        paginator = Paginator(logs, page_size)
        page_obj = paginator.get_page(page)

        serializer = SmsLogSerializer(page_obj.object_list, many=True)

        return APIResponse.success(
            data={
                'count': paginator.count,
                'total_pages': paginator.num_pages,
                'current_page': page_obj.number,
                'next': page_obj.next_page_number() if page_obj.has_next() else None,
                'previous': page_obj.previous_page_number() if page_obj.has_previous() else None,
                'results': serializer.data
            },
            message="تاریخچه پیام‌ها با موفقیت دریافت شد"
        )