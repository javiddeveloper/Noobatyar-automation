from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import IsAuthenticated
from api.responses import APIResponse
from .models import Appointment
from .client_serializers import ClientAppointmentSerializer
from .cache_utils import invalidate_slots_cache
from api.pagination import StandardPagination
import logging

logger = logging.getLogger(__name__)

class ClientAppointmentListView(APIView):
    permission_classes = [IsAuthenticated]

    async def get(self, request):
        # Paginate + serialize in one sync block: the serializer's
        # queue_position/estimated_turn_time fields run ORM .count() queries, so
        # this must not touch the async ORM. Pagination also bounds those
        # per-row queries to one page instead of the user's entire history.
        return await sync_to_async(self._list_paginated)(request)

    def _list_paginated(self, request):
        queryset = (
            Appointment.objects
            .filter(user=request.user)
            .select_related('business', 'visitor')
            .order_by('-appointment_date')
        )

        paginator = StandardPagination()
        page = paginator.paginate_queryset(queryset, request, view=self)
        serializer = ClientAppointmentSerializer(
            page,
            many=True,
            context={'request': request},
        )
        paginated = paginator.get_paginated_response(serializer.data)

        return APIResponse.success(
            data=paginated.data,
            message="لیست نوبت‌های شما با موفقیت دریافت شد",
        )

    async def post(self, request):
        from datetime import datetime, timezone
        from business.models import Business
        from visitor.models import Visitor
        from .client_serializers import ClientAppointmentCreateSerializer

        serializer = ClientAppointmentCreateSerializer(data=request.data)
        if not serializer.is_valid():
            return APIResponse.error(message="اطلاعات نامعتبر است", code=400)

        business_id = serializer.validated_data['business_id']
        try:
            business = await Business.objects.aget(id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(message="کسب و کار یافت نم‌شد", code=404)

        # Ensure visitor exists for this user
        # Visitor model fields: user, full_name, phone_number
        visitor, created = await Visitor.objects.aget_or_create(
            user=request.user,
            phone_number=request.user.phone,
            defaults={
                'full_name': request.user.name or request.user.phone,
            }
        )

        app_date = datetime.fromtimestamp(serializer.validated_data['appointment_date'] / 1000.0, tz=timezone.utc)

        appointment = await Appointment.objects.acreate(
            user=request.user,
            business=business,
            visitor=visitor,
            appointment_date=app_date,
            service_duration=serializer.validated_data.get('service_duration', business.default_service_duration),
            description=serializer.validated_data.get('description', ''),
            status='LOCKED'
        )

        # Slot occupancy changed → drop cached slot views for this business/date.
        await sync_to_async(invalidate_slots_cache)(business_id, app_date)

        # ── SMS Notifications are deferred until payment is completed ──


        return APIResponse.success(
            data={'id': appointment.id},
            message="نوبت با موفقیت قفل شد. لطفا پرداخت را انجام دهید."
        )

class ClientAppointmentPaymentView(APIView):
    permission_classes = [IsAuthenticated]

    async def post(self, request, pk):
        from zoneinfo import ZoneInfo
        
        try:
            appointment = await Appointment.objects.select_related('business', 'visitor').aget(
                id=pk, user=request.user
            )
        except Appointment.DoesNotExist:
            return APIResponse.error(message="نوبت یافت نشد", code=404)

        if appointment.status != 'LOCKED':
            return APIResponse.error(message="این نوبت قابل پرداخت نیست", code=400)

        payment_reference = request.data.get('payment_reference', '')
        payment_receipt = request.FILES.get('payment_receipt', None)
        
        appointment.status = 'PENDING_VERIFICATION'
        appointment.payment_reference = payment_reference
        if payment_receipt:
            appointment.payment_receipt = payment_receipt
        await appointment.asave()

        # Slot occupancy changed → drop cached slot views for this business/date.
        await sync_to_async(invalidate_slots_cache)(
            appointment.business_id, appointment.appointment_date
        )

        # ── SMS Notifications via Melipayamak ─────────────────────────
        tehran_tz = ZoneInfo('Asia/Tehran')
        local_time = appointment.appointment_date.astimezone(tehran_tz)
        time_str = local_time.strftime('%Y/%m/%d ساعت %H:%M')

        client_msg = (
            f"نوبت‌یار ✅\n"
            f"نوبت شما در {appointment.business.title}\n"
            f"تاریخ: {time_str}\n"
            f"در انتظار تایید کسب‌وکار\n"
            f"کد نوبت: {appointment.id}"
        )
        owner_msg = (
            f"نوبت‌یار 📋\n"
            f"درخواست نوبت جدید\n"
            f"مشتری: {appointment.visitor.full_name}\n"
            f"تاریخ: {time_str}\n"
            f"کد: {appointment.id}"
        )

        # Fire-and-forget on a daemon thread so it is independent of the request's
        # event loop (an asyncio task tied to the request loop can be dropped once
        # the response is returned). Pass only primitive ids/values into the thread.
        import threading
        threading.Thread(
            target=_send_booking_sms,
            kwargs=dict(
                client_phone=request.user.phone,
                owner_phone=appointment.business.phone,
                client_msg=client_msg,
                owner_msg=owner_msg,
                business_id=appointment.business_id,
                visitor_id=appointment.visitor_id,
            ),
            daemon=True,
            name=f"booking-sms-{appointment.id}",
        ).start()
        # ─────────────────────────────────────────────────────────────

        return APIResponse.success(
            data={'id': appointment.id},
            message="پرداخت با موفقیت ثبت شد و نوبت در انتظار تایید است"
        )


def _send_booking_sms(client_phone, owner_phone, client_msg, owner_msg, business_id, visitor_id):
    """
    Background daemon-thread target: sends SMS to client and business owner via
    Melipayamak and logs the client SMS result in SmsLog.

    Runs fully synchronously in its own thread (own DB connection), so it never
    depends on the request's asyncio event loop still being alive.
    """
    from api.sms import send_sms
    from visitor.models import SmsLog

    # Send to client
    try:
        client_ok = send_sms(client_phone, client_msg)
        SmsLog.objects.create(
            business_id=business_id,
            visitor_id=visitor_id,
            message_text=client_msg,
            status='SENT' if client_ok else 'FAILED',
        )
        logger.info(f"SMS→client {client_phone}: {'✓' if client_ok else '✗'}")
    except Exception as e:
        logger.error(f"SMS→client error: {e}")

    # Send to business owner
    try:
        owner_ok = send_sms(owner_phone, owner_msg)
        logger.info(f"SMS→owner {owner_phone}: {'✓' if owner_ok else '✗'}")
    except Exception as e:
        logger.error(f"SMS→owner error: {e}")
