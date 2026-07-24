from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import IsAuthenticated
from api.responses import APIResponse
from .models import Appointment
from .client_serializers import ClientAppointmentSerializer
from .cache_utils import invalidate_slots_cache
from api.pagination import StandardPagination
from accounting import usage
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

        # A locked business (subscription downgrade) does not accept new bookings.
        if business.is_locked:
            return APIResponse.error(
                message="این کسب‌وکار در حال حاضر نوبت جدید نمی‌پذیرد",
                code=403,
            )

        # Monthly appointment quota belongs to the business owner's plan.
        if not await sync_to_async(usage.can_book_appointment)(business.user_id):
            return APIResponse.error(
                message="ظرفیت نوبت‌های این کسب‌وکار برای این ماه تکمیل شده است",
                code=409,
            )

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

        # A payment step only applies when the business actually collects money
        # at booking time — a deposit, or a card/online method (all premium
        # features). On the basic plan (cash-only, no deposit) there is nothing
        # to pay, so the appointment is booked directly for the owner to approve.
        accepted = business.accepted_payment_methods or []
        requires_payment = (
            business.deposit_mode in ('MANDATORY', 'OPTIONAL')
            or 'CARD' in accepted
            or 'ONLINE' in accepted
        )

        appointment = await Appointment.objects.acreate(
            user=request.user,
            business=business,
            visitor=visitor,
            appointment_date=app_date,
            service_duration=serializer.validated_data.get('service_duration', business.default_service_duration),
            description=serializer.validated_data.get('description', ''),
            status='LOCKED' if requires_payment else 'PENDING_APPROVAL',
        )

        # Count this booking against the owner's monthly appointment quota.
        await sync_to_async(usage.record_appointment)(business.user_id)

        # Slot occupancy changed → drop cached slot views for this business/date.
        await sync_to_async(invalidate_slots_cache)(business_id, app_date)

        if requires_payment:
            # SMS is deferred until payment is completed (ClientAppointmentPaymentView).
            return APIResponse.success(
                data={'id': appointment.id, 'requires_payment': True},
                message="نوبت با موفقیت قفل شد. لطفا پرداخت را انجام دهید."
            )

        # No payment needed → confirm the booking and notify client + owner now.
        appointment.business = business
        appointment.visitor = visitor
        _fire_booking_sms(appointment, request.user.phone, business.phone)
        return APIResponse.success(
            data={'id': appointment.id, 'requires_payment': False},
            message="نوبت شما با موفقیت ثبت شد و در انتظار تایید کسب‌وکار است."
        )

class ClientAppointmentPaymentView(APIView):
    permission_classes = [IsAuthenticated]

    async def post(self, request, pk):
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
        _fire_booking_sms(appointment, request.user.phone, appointment.business.phone)
        # ─────────────────────────────────────────────────────────────

        return APIResponse.success(
            data={'id': appointment.id},
            message="پرداخت با موفقیت ثبت شد و نوبت در انتظار تایید است"
        )


def _fire_booking_sms(appointment, client_phone, owner_phone):
    """Build the client/owner confirmation messages for a booked appointment and
    dispatch the fire-and-forget SMS thread. ``appointment.business`` and
    ``appointment.visitor`` must already be loaded (no DB access here)."""
    from zoneinfo import ZoneInfo
    import threading

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
    threading.Thread(
        target=_send_booking_sms,
        kwargs=dict(
            client_phone=client_phone,
            owner_phone=owner_phone,
            client_msg=client_msg,
            owner_msg=owner_msg,
            business_id=appointment.business_id,
            visitor_id=appointment.visitor_id,
        ),
        daemon=True,
        name=f"booking-sms-{appointment.id}",
    ).start()


def _send_booking_sms(client_phone, owner_phone, client_msg, owner_msg, business_id, visitor_id):
    """
    Background daemon-thread target: sends SMS to client and business owner via
    Melipayamak and logs the client SMS result in SmsLog.

    Runs fully synchronously in its own thread (own DB connection), so it never
    depends on the request's asyncio event loop still being alive.
    """
    from api.sms import send_sms
    from visitor.models import SmsLog
    from business.models import Business

    # Resolve the owner whose plan pays for these SMS.
    try:
        owner_id = Business.objects.values_list('user_id', flat=True).get(id=business_id)
    except Business.DoesNotExist:
        owner_id = None

    # Send to client (consumes one SMS credit from the owner's plan/wallet)
    try:
        if owner_id is not None and not usage.consume_sms(owner_id):
            logger.warning(f"SMS→client skipped for business {business_id}: SMS quota exhausted")
        else:
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

    # Send to business owner (also consumes one credit)
    try:
        if owner_id is not None and not usage.consume_sms(owner_id):
            logger.warning(f"SMS→owner skipped for business {business_id}: SMS quota exhausted")
        else:
            owner_ok = send_sms(owner_phone, owner_msg)
            logger.info(f"SMS→owner {owner_phone}: {'✓' if owner_ok else '✗'}")
    except Exception as e:
        logger.error(f"SMS→owner error: {e}")
