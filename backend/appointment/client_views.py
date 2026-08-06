from adrf.views import APIView
from asgiref.sync import sync_to_async
from rest_framework.permissions import AllowAny
from api.responses import APIResponse
from visitor.auth import VisitorTokenAuthentication, IsVisitorAuthenticated
from visitor.activity import record_activity
from visitor.models import VisitorActivity
from .models import Appointment
from .client_serializers import ClientAppointmentSerializer
from .cache_utils import invalidate_slots_cache
from .occupancy import find_conflict, lock_expiry, refund_quota
from api.pagination import StandardPagination
from accounting import usage
from django.utils import timezone
import logging

logger = logging.getLogger(__name__)

class ClientAppointmentListView(APIView):
    # request.user is a Visitor here (see visitor/auth.py), never a `User`.
    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def get(self, request):
        # Paginate + serialize in one sync block: the serializer's
        # queue_position/estimated_turn_time fields run ORM .count() queries, so
        # this must not touch the async ORM. Pagination also bounds those
        # per-row queries to one page instead of the user's entire history.
        return await sync_to_async(self._list_paginated)(request)

    def _list_paginated(self, request):
        queryset = (
            Appointment.objects
            .filter(visitor=request.user)
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

        # request.user is the Visitor resolved by VisitorTokenAuthentication —
        # already exists, no account/get_or_create needed here.
        visitor = request.user

        app_date = datetime.fromtimestamp(serializer.validated_data['appointment_date'] / 1000.0, tz=timezone.utc)
        duration = serializer.validated_data.get('service_duration') or business.default_service_duration

        # A payment step only applies when the business actually collects money
        # at booking time — a deposit, or a card/online method. Those are all
        # premium capabilities, so they are re-checked against the owner's
        # *current* plan: a downgraded owner whose business row still carries a
        # stale CARD/deposit config must not push clients into a payment step.
        requires_payment = await sync_to_async(_requires_payment)(business)

        # Create the row inside a transaction that first re-checks the slot, so
        # two clients racing for the same time cannot both win.
        appointment, conflict = await sync_to_async(self._create_if_free)(
            business, visitor, app_date, duration,
            serializer.validated_data.get('description', ''), requires_payment,
        )
        if conflict:
            return APIResponse.error(
                message="این زمان به‌تازگی رزرو شد. لطفاً زمان دیگری انتخاب کنید",
                code=409,
            )

        # Count this booking against the owner's monthly appointment quota, and
        # remember which bucket paid so a cancellation can refund that one.
        quota_source = await sync_to_async(usage.record_appointment)(business.user_id)
        if quota_source:
            appointment.quota_source = quota_source
            await appointment.asave(update_fields=['quota_source'])

        # Slot occupancy changed → drop cached slot views for this business/date.
        await sync_to_async(invalidate_slots_cache)(business_id, app_date)

        await sync_to_async(record_activity)(
            visitor,
            'APPOINTMENT_BOOKED',
            actor_type=VisitorActivity.ACTOR_VISITOR,
            business=business,
            appointment=appointment,
            status=appointment.status,
        )

        if requires_payment:
            # SMS is deferred until payment is completed (ClientAppointmentPaymentView).
            return APIResponse.success(
                data={'id': appointment.id, 'requires_payment': True},
                message="نوبت با موفقیت قفل شد. لطفا پرداخت را انجام دهید."
            )

        # No payment needed → confirm the booking and notify client + owner now.
        appointment.business = business
        appointment.visitor = visitor
        _fire_booking_sms(appointment, visitor.phone_number)
        return APIResponse.success(
            data={'id': appointment.id, 'requires_payment': False},
            message="نوبت شما با موفقیت ثبت شد و در انتظار تایید کسب‌وکار است."
        )

    @staticmethod
    def _create_if_free(business, visitor, app_date, duration, description, requires_payment):
        """
        Re-check the slot and create the appointment atomically.

        Returns ``(appointment, conflict)``. The row-level lock on the business
        serialises concurrent bookings for the same business, so the conflict
        check cannot be overtaken between the SELECT and the INSERT.
        """
        from django.db import transaction
        from business.models import Business as _Business

        with transaction.atomic():
            # Serialise bookings per business.
            _Business.objects.select_for_update().only('id').get(id=business.id)

            conflict = find_conflict(business.id, app_date, duration)
            if conflict is not None:
                return None, conflict

            now = timezone.now()
            appointment = Appointment.objects.create(
                # No `User` involved in a self-booked appointment — only the
                # Visitor identifies who this is for.
                user=None,
                business=business,
                visitor=visitor,
                appointment_date=app_date,
                service_duration=duration,
                description=description,
                status='LOCKED' if requires_payment else 'PENDING_APPROVAL',
                # Hold the slot only while the client is paying; an abandoned
                # lock expires on its own and frees the slot (see occupancy.py).
                locked_at=now if requires_payment else None,
                expires_at=lock_expiry(now) if requires_payment else None,
            )
            return appointment, None


def _requires_payment(business):
    """
    Whether booking this business must go through the payment step.

    Deposit and card/online collection are plan-gated capabilities, so the
    owner's live entitlements decide — not just the stored business config,
    which can be left over from a plan the owner no longer has.
    """
    from accounting import entitlements

    owner_id = business.user_id
    accepted = business.accepted_payment_methods or []

    wants_deposit = business.deposit_mode in ('MANDATORY', 'OPTIONAL')
    if wants_deposit and entitlements.has_feature(owner_id, entitlements.FEATURE_DEPOSIT):
        return True

    if 'CARD' in accepted and entitlements.has_feature(owner_id, entitlements.FEATURE_DEPOSIT):
        return True

    if 'ONLINE' in accepted and entitlements.has_feature(owner_id, entitlements.FEATURE_ONLINE_GATEWAY):
        return True

    return False


class ClientAppointmentPaymentView(APIView):
    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def post(self, request, pk):
        try:
            appointment = await Appointment.objects.select_related('business', 'visitor').aget(
                id=pk, visitor=request.user
            )
        except Appointment.DoesNotExist:
            return APIResponse.error(message="نوبت یافت نشد", code=404)

        if appointment.status != 'LOCKED':
            return APIResponse.error(message="این نوبت قابل پرداخت نیست", code=400)

        # Lazy lock expiry — no cleanup job needed. Once the payment window has
        # passed the hold is released (the slot already reads as free via
        # occupancy.blocking_q) and the row is closed out as cancelled.
        if appointment.expires_at and appointment.expires_at <= timezone.now():
            appointment.status = 'CANCELLED'
            # The client never paid, so the owner keeps the credit.
            await sync_to_async(refund_quota)(appointment, appointment.business.user_id)
            await appointment.asave(update_fields=['status', 'quota_source', 'updated_at'])
            await sync_to_async(invalidate_slots_cache)(
                appointment.business_id, appointment.appointment_date
            )
            return APIResponse.error(
                message="مهلت پرداخت این نوبت به پایان رسیده است. لطفاً دوباره نوبت بگیرید",
                code=400,
            )

        payment_reference = (request.data.get('payment_reference') or '').strip()
        payment_receipt = request.FILES.get('payment_receipt', None)
        # The client states how they are paying. Older clients that predate this
        # field submitted a receipt/reference, so default to CARD for them.
        method = (request.data.get('method') or 'CARD').upper()

        if method not in ('CARD', 'ONLINE', 'CASH'):
            return APIResponse.error(message="روش پرداخت نامعتبر است", code=400)

        business = appointment.business

        if method == 'CASH':
            # Paying in person: nothing to verify, so the booking goes straight
            # to the owner's approval queue instead of the receipt queue.
            if business.deposit_mode == 'MANDATORY':
                return APIResponse.error(
                    message="پرداخت بیعانه برای این کسب‌وکار الزامی است",
                    code=400,
                )
            appointment.status = 'PENDING_APPROVAL'
            appointment.deposit_payment_method = 'NONE'
            success_message = "نوبت شما ثبت شد و در انتظار تایید کسب‌وکار است"
        else:
            # Card / online transfers must come with proof, otherwise the owner
            # sees a "verify the receipt" item with no receipt to verify.
            if not payment_receipt and not payment_reference:
                return APIResponse.error(
                    message="لطفاً شماره پیگیری را وارد کنید یا تصویر فیش را آپلود نمایید",
                    code=400,
                )
            appointment.status = 'PENDING_VERIFICATION'
            appointment.payment_reference = payment_reference
            appointment.deposit_payment_method = 'CARD'
            if payment_receipt:
                appointment.payment_receipt = payment_receipt
            success_message = "پرداخت با موفقیت ثبت شد و نوبت در انتظار تایید است"

        # The payment window is over once payment is submitted: the slot is now
        # held by the status itself, so the lock timestamps are cleared.
        appointment.locked_at = None
        appointment.expires_at = None
        await appointment.asave()

        # Slot occupancy changed → drop cached slot views for this business/date.
        await sync_to_async(invalidate_slots_cache)(
            appointment.business_id, appointment.appointment_date
        )

        # ── SMS Notifications via Melipayamak ─────────────────────────
        _fire_booking_sms(appointment, request.user.phone_number)
        # ─────────────────────────────────────────────────────────────

        return APIResponse.success(
            data={'id': appointment.id},
            message=success_message
        )


class ClientAppointmentOnlinePaymentView(APIView):
    """Opens a Zibal payment for the deposit and hands back the gateway URL.

    The money goes to the business owner's own Zibal merchant, so this is only
    available when the owner both configured a merchant id and holds the
    online-gateway entitlement.
    """

    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def post(self, request, pk):
        from django.urls import reverse

        from accounting import entitlements
        from .payment.zibal_deposit import create_deposit_payment

        try:
            appointment = await Appointment.objects.select_related('business').aget(
                id=pk, visitor=request.user
            )
        except Appointment.DoesNotExist:
            return APIResponse.error(message="نوبت یافت نشد", code=404)

        if appointment.status != 'LOCKED':
            return APIResponse.error(message="این نوبت قابل پرداخت نیست", code=400)

        # Same lazy lock expiry as the manual payment path: an abandoned hold is
        # released rather than sending the client to a gateway for a dead slot.
        if appointment.expires_at and appointment.expires_at <= timezone.now():
            appointment.status = 'CANCELLED'
            await sync_to_async(refund_quota)(appointment, appointment.business.user_id)
            await appointment.asave(update_fields=['status', 'quota_source', 'updated_at'])
            await sync_to_async(invalidate_slots_cache)(
                appointment.business_id, appointment.appointment_date
            )
            return APIResponse.error(
                message="مهلت پرداخت این نوبت به پایان رسیده است. لطفاً دوباره نوبت بگیرید",
                code=400,
            )

        business = appointment.business
        if 'ONLINE' not in (business.accepted_payment_methods or []):
            return APIResponse.error(message="این کسب‌وکار پرداخت آنلاین ندارد", code=400)

        has_gateway = await sync_to_async(entitlements.has_feature)(
            business.user_id, entitlements.FEATURE_ONLINE_GATEWAY
        )
        if not has_gateway:
            return APIResponse.error(message="پرداخت آنلاین برای این کسب‌وکار فعال نیست", code=400)

        callback_url = request.build_absolute_uri(
            reverse('client-deposit-callback')
        )
        result = await sync_to_async(create_deposit_payment)(appointment, callback_url)
        if not result['success']:
            return APIResponse.error(message=result['error'], code=400)

        # The trackId is how the callback finds this appointment again; the query
        # string it arrives with is not trusted for identification.
        appointment.payment_reference = result['track_id']
        await appointment.asave(update_fields=['payment_reference', 'updated_at'])

        return APIResponse.success(
            data={'payment_url': result['payment_url'], 'track_id': result['track_id']},
            message="در حال انتقال به درگاه پرداخت",
        )


class ClientDepositCallbackView(APIView):
    """Where Zibal returns the client's browser after a deposit payment.

    Unauthenticated on purpose — this is a browser redirect from the bank, with
    no Authorization header. Nothing here trusts the query string beyond the
    trackId lookup: whether the payment happened is decided by Zibal's verify
    endpoint, so a hand-crafted ``?success=1`` confirms nothing.
    """

    permission_classes = [AllowAny]

    async def get(self, request):
        from django.conf import settings
        from django.shortcuts import redirect

        from .payment.zibal_deposit import verify_deposit_payment

        client_web = getattr(settings, 'CLIENT_WEB_URL', '').rstrip('/')
        track_id = (request.GET.get('trackId') or '').strip()

        def _back(state: str, appointment_id=None):
            suffix = f"&id={appointment_id}" if appointment_id else ""
            return redirect(f"{client_web}/appointments?payment={state}{suffix}")

        if not track_id:
            return _back('failed')

        try:
            appointment = await Appointment.objects.select_related('business', 'visitor').aget(
                payment_reference=track_id
            )
        except Appointment.DoesNotExist:
            logger.warning("Deposit callback for unknown trackId %s", track_id)
            return _back('failed')

        # Replayed callback for an appointment already settled — treat as success
        # rather than re-running the side effects.
        if appointment.status != 'LOCKED':
            return _back('success', appointment.id)

        result = await sync_to_async(verify_deposit_payment)(appointment, track_id)
        if not result['success']:
            return _back('failed', appointment.id)

        # Verified by the bank, so there is no receipt for the owner to eyeball:
        # the booking is confirmed outright instead of queueing for approval.
        appointment.status = 'WAITING'
        appointment.locked_at = None
        appointment.expires_at = None
        appointment.deposit_payment_method = 'GATEWAY'
        await appointment.asave(
            update_fields=[
                'status', 'locked_at', 'expires_at',
                'deposit_payment_method', 'updated_at',
            ]
        )
        await sync_to_async(invalidate_slots_cache)(
            appointment.business_id, appointment.appointment_date
        )

        _fire_deposit_paid_sms(appointment)
        return _back('success', appointment.id)


class ClientAppointmentCancelView(APIView):
    """Lets a client cancel their own appointment before it starts."""

    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    # Anything still ahead of the appointment time can be dropped by the client.
    CANCELLABLE_STATUSES = (
        'LOCKED',
        'PENDING_APPROVAL',
        'PENDING_VERIFICATION',
        'WAITING',
        'CONFIRMED',
    )

    async def post(self, request, pk):
        try:
            appointment = await Appointment.objects.select_related('business', 'visitor').aget(
                id=pk, visitor=request.user
            )
        except Appointment.DoesNotExist:
            return APIResponse.error(message="نوبت یافت نشد", code=404)

        if appointment.status not in self.CANCELLABLE_STATUSES:
            return APIResponse.error(message="این نوبت قابل لغو نیست", code=400)

        if appointment.appointment_date <= timezone.now():
            return APIResponse.error(
                message="زمان این نوبت گذشته است و قابل لغو نیست",
                code=400,
            )

        previous_status = appointment.status
        appointment.status = 'CANCELLED'
        appointment.locked_at = None
        appointment.expires_at = None
        # A slot the owner never served should not keep costing them a credit.
        await sync_to_async(refund_quota)(appointment, appointment.business.user_id)
        await appointment.asave(
            update_fields=['status', 'locked_at', 'expires_at', 'quota_source', 'updated_at']
        )

        # Freeing the slot changes availability for everyone.
        await sync_to_async(invalidate_slots_cache)(
            appointment.business_id, appointment.appointment_date
        )

        await sync_to_async(record_activity)(
            appointment.visitor,
            'APPOINTMENT_CANCELLED',
            actor_type=VisitorActivity.ACTOR_VISITOR,
            business=appointment.business,
            appointment=appointment,
            old=previous_status,
            new='CANCELLED',
        )

        _fire_cancellation_sms(appointment)

        return APIResponse.success(
            data={'id': appointment.id},
            message="نوبت شما لغو شد",
        )


def _fire_cancellation_sms(appointment):
    """Tell the owner their client cancelled, so the freed slot is not a surprise."""
    import threading

    from api.jalali import format_datetime
    from api.sms import signed

    time_str = format_datetime(appointment.appointment_date)

    owner_msg = signed(
        f"❌ لغو نوبت توسط مشتری\n"
        f"مشتری: {appointment.visitor.full_name}\n"
        f"تاریخ: {time_str}"
    )

    threading.Thread(
        target=_send_booking_sms,
        kwargs=dict(
            client_phone=None,     # the client initiated this; no confirmation SMS
            client_msg=None,
            owner_msg=owner_msg,
            business_id=appointment.business_id,
            visitor_id=appointment.visitor_id,
        ),
        daemon=True,
        name=f"cancel-sms-{appointment.id}",
    ).start()


def _fire_booking_sms(appointment, client_phone):
    """Build the client/owner confirmation messages for a booked appointment and
    dispatch the fire-and-forget SMS thread. ``appointment.business`` and
    ``appointment.visitor`` must already be loaded (no DB access here)."""
    import threading

    from api.jalali import format_datetime
    from api.sms import signed

    time_str = format_datetime(appointment.appointment_date)

    client_msg = signed(
        f"✅ نوبت شما در {appointment.business.title}\n"
        f"تاریخ: {time_str}\n"
        f"در انتظار تایید کسب‌وکار"
    )
    owner_msg = signed(
        f"📋 درخواست نوبت جدید\n"
        f"مشتری: {appointment.visitor.full_name}\n"
        f"تاریخ: {time_str}"
    )

    # Fire-and-forget on a daemon thread so it is independent of the request's
    # event loop (an asyncio task tied to the request loop can be dropped once
    # the response is returned). Pass only primitive ids/values into the thread.
    threading.Thread(
        target=_send_booking_sms,
        kwargs=dict(
            client_phone=client_phone,
            client_msg=client_msg,
            owner_msg=owner_msg,
            business_id=appointment.business_id,
            visitor_id=appointment.visitor_id,
        ),
        daemon=True,
        name=f"booking-sms-{appointment.id}",
    ).start()


def _fire_deposit_paid_sms(appointment):
    """Messages for a booking settled through the gateway.

    Separate from ``_fire_booking_sms`` because that one tells the client the
    booking is "در انتظار تایید کسب‌وکار" — untrue here, where the bank already
    confirmed the deposit and the appointment goes straight into the queue.
    ``appointment.business`` and ``appointment.visitor`` must already be loaded.
    """
    import threading

    from api.jalali import format_datetime
    from api.sms import signed

    time_str = format_datetime(appointment.appointment_date)

    client_msg = signed(
        f"✅ نوبت شما در {appointment.business.title} قطعی شد\n"
        f"تاریخ: {time_str}\n"
        f"بیعانه پرداخت شد"
    )
    owner_msg = signed(
        f"💳 نوبت جدید با پرداخت آنلاین\n"
        f"مشتری: {appointment.visitor.full_name}\n"
        f"تاریخ: {time_str}"
    )

    threading.Thread(
        target=_send_booking_sms,
        kwargs=dict(
            client_phone=appointment.visitor.phone_number,
            client_msg=client_msg,
            owner_msg=owner_msg,
            business_id=appointment.business_id,
            visitor_id=appointment.visitor_id,
        ),
        daemon=True,
        name=f"deposit-sms-{appointment.id}",
    ).start()


def _send_booking_sms(client_phone, client_msg, owner_msg, business_id, visitor_id):
    """
    Background daemon-thread target: sends SMS to client and business owner via
    Melipayamak and logs the client SMS result in SmsLog.

    Runs fully synchronously in its own thread (own DB connection), so it never
    depends on the request's asyncio event loop still being alive.
    """
    from api.sms import send_sms
    from visitor.models import SmsLog
    from business.models import Business

    # Resolve the owner whose plan pays for these SMS, and the number that
    # actually reaches them. business.phone is a display number for
    # customers — often a landline — and was never the owner's own contact;
    # owner notifications go to the phone their account was registered with.
    owner_id = None
    owner_phone = None
    notify_owner = True
    try:
        owner_id, owner_phone, notify_owner = Business.objects.values_list(
            'user_id', 'user__phone', 'notify_owner_by_sms'
        ).get(id=business_id)
    except Business.DoesNotExist:
        pass

    # Send to client (consumes one SMS credit from the owner's plan/wallet).
    # Callers pass client_msg=None when the client triggered the action
    # themselves and does not need to be told about it (e.g. self-cancellation).
    try:
        if not client_phone or not client_msg:
            pass
        else:
            receipt = usage.consume_sms(owner_id) if owner_id is not None else None
            if owner_id is not None and not receipt:
                logger.warning(f"SMS→client skipped for business {business_id}: SMS quota exhausted")
            else:
                client_ok, client_err = send_sms(client_phone, client_msg)
                if not client_ok:
                    # The provider never accepted it, so the owner must not pay
                    # for it — put the credit back where it came from.
                    usage.refund_sms(receipt)
                SmsLog.objects.create(
                    business_id=business_id,
                    visitor_id=visitor_id,
                    message_text=client_msg,
                    status='SENT' if client_ok else 'FAILED',
                    error_detail=client_err if not client_ok else ""
                )
                logger.info(f"SMS→client {client_phone}: {'✓' if client_ok else '✗'}")
    except Exception as e:
        logger.error(f"SMS→client error: {e}")

    # Send to business owner — off unless the owner explicitly asked for it.
    # Business.notify_owner_by_sms now defaults to False (and existing rows were
    # switched off by business migration 0013): an owner learning about their own
    # booking should not be paying, out of their own SMS quota, for a message
    # that repeats what the owner app already shows them. The intended
    # replacement is an app push notification, which this backend cannot send
    # yet — there is no device-token model, no FCM/APNs credentials and no
    # dispatch path anywhere in the project. Until that exists, an owner who
    # still wants to be told by SMS can turn this back on and keep paying for it.
    try:
        if not owner_phone or not owner_msg:
            pass
        elif not notify_owner:
            logger.info(f"SMS→owner disabled for business {business_id}")
        else:
            receipt = usage.consume_sms(owner_id) if owner_id is not None else None
            if owner_id is not None and not receipt:
                logger.warning(f"SMS→owner skipped for business {business_id}: SMS quota exhausted")
            else:
                owner_ok, owner_err = send_sms(owner_phone, owner_msg)
                if not owner_ok:
                    usage.refund_sms(receipt)
                # Logged like the client message: this one is billed too, so
                # leaving it out made the SMS report undercount every booking by
                # one and never reconcile against the quota the owner was charged.
                # visitor is null — the recipient is the owner, not a client.
                SmsLog.objects.create(
                    business_id=business_id,
                    visitor_id=None,
                    message_text=owner_msg,
                    status='SENT' if owner_ok else 'FAILED',
                    error_detail=owner_err if not owner_ok else ""
                )
                logger.info(f"SMS→owner {owner_phone}: {'✓' if owner_ok else '✗'}")
    except Exception as e:
        logger.error(f"SMS→owner error: {e}")
