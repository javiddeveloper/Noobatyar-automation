from asgiref.sync import sync_to_async
from django.shortcuts import render
from django.utils import timezone
from rest_framework.permissions import IsAuthenticated, AllowAny, IsAdminUser
from adrf.decorators import api_view

from api.responses import APIResponse
from api.views import _extract_error
from .models import Plan, Subscription, AddOnPack
from .payment.zibal_payment import ZibalPaymentService
from .payment.zibal_payment_verification import PaymentVerificationService
from .payment.addon_payment import AddOnPaymentService, AddOnVerificationService
from .serializers import (
    PlanSerializer, SubscriptionSerializer, BuyPlanSerializer,
    AddOnPackSerializer, BuyAddOnSerializer,
)
from . import entitlements, usage
from rest_framework.decorators import api_view, permission_classes


def _client_callback(path: str) -> str:
    """Absolute URL on the customer web app for a payment gateway to return to.

    These were hardcoded to https://noobatyar.ir/... — the bare domain, which
    resolves to a *different server* than the one running this app. Zibal sent
    every payer there after paying, so the page that calls the verify endpoint
    never ran: transactions stayed `pending` with an empty zibal_response and
    the plan or add-on was never applied.

    CLIENT_WEB_URL already existed for exactly this (the deposit flow uses it)
    and already defaults to the right host.
    """
    from django.conf import settings

    base = (getattr(settings, 'CLIENT_WEB_URL', '') or '').rstrip('/')
    return f'{base}{path}'


@api_view(['GET'])
@permission_classes([AllowAny])
def plan_list(request):
    """لیست پلن‌ها — برای همه قابل مشاهده"""
    plans = Plan.objects.filter(is_active=True)
    serializer = PlanSerializer(plans, many=True)
    return APIResponse.success(
        data=serializer.data,
        message=None
    )


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def buy_plan(request):
    """
    خرید مستقیم پلن (بدون درگاه):
    - اشتراک قبلی expire
    - اشتراک جدید
    - VIP upgrade
    """
    serializer = BuyPlanSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(message=_extract_error(serializer.errors))

    plan_id = serializer.validated_data['plan_id']

    try:
        plan = Plan.objects.get(id=plan_id, is_active=True)
    except Plan.DoesNotExist:
        return APIResponse.error(message='پلن پیدا نشد', code=404)

    user = request.user

    # بررسی اشتراک فعلی برای تمدید زمان
    active_sub = Subscription.objects.filter(user=user, status='active').first()
    
    # اگر اشتراک فعال و معتبری وجود دارد، از تاریخ پایان آن شروع می‌کنیم
    # در غیر این صورت از همین الان شروع می‌کنیم
    if active_sub and active_sub.is_valid():
        start_date = active_sub.ends_at
        active_sub.status = 'expired'
        active_sub.save(update_fields=['status'])
    else:
        # اگر اشتراک منقضی شده هم وجود داشته باشد، آن را غیرفعال می‌کنیم
        Subscription.objects.filter(user=user, status='active').update(status='expired')
        start_date = timezone.now()

    # Create new subscription
    subscription = Subscription.objects.create(
        user=user,
        plan=plan,
        ends_at=plan.get_end_date(start_date=start_date)
    )

    # VIP upgrade
    if plan.is_vip and user.role != 'BUSINESS_OWNER':
        user.role = 'BUSINESS_OWNER'
        user.save(update_fields=['role'])

    # Unlock businesses that were locked under a smaller/expired plan.
    from business.services import sync_locks
    sync_locks(user)

    serializer = SubscriptionSerializer(subscription)
    return APIResponse.success(
        data=serializer.data,
        message=f'پلن {plan.name} با موفقیت فعال شد',
        status=201
    )



@api_view(['GET'])
@permission_classes([IsAuthenticated])
def my_subscription(request):  # ✅ sync
    """اشتراک فعال یا آخرین وضعیت اشتراک کاربر"""
    
    # ابتدا به دنبال اشتراک فعال می‌گردیم
    subscription = Subscription.objects.filter(
        user=request.user,
        status='active'
    ).select_related('plan').first()

    # اگر اشتراک فعال نداشت، آخرین اشتراک ثبت شده (حتی منقضی) را نشان می‌دهیم
    if not subscription:
        subscription = Subscription.objects.filter(
            user=request.user
        ).select_related('plan').order_by('-started_at').first()

    # NOTE: kept as a flat SubscriptionDto for backward compatibility with the
    # released clients. Entitlements + usage are served by /my-entitlements/.
    if not subscription:
        return APIResponse.success(
            data=None,
            message='اشتراک فعالی ندارید'
        )

    serializer = SubscriptionSerializer(subscription)
    return APIResponse.success(data=serializer.data)


@api_view(['GET'])
@permission_classes([IsAuthenticated])
def my_entitlements(request):
    """قابلیت‌ها و سقف‌های پلن فعلی کاربر + میزان مصرف این ماه.

    ``coming_soon`` lists feature keys that a plan may well resolve to ``True``
    but that nothing in this backend implements yet. It is sent as a separate
    list rather than by forcing those keys to ``False``, because the entitlement
    genuinely was sold with the plan — the client's job is to render the row
    disabled with a «به‌زودی» badge, not to pretend the user did not buy it.
    """
    user = request.user
    ent = entitlements.get_entitlements(user)
    return APIResponse.success(data={
        'entitlements': ent,
        'coming_soon': list(entitlements.COMING_SOON_FEATURES),
        'usage': {
            'appointments': usage.appointment_balance(user),
            'sms': usage.sms_balance(user),
        },
    })


@api_view(['GET'])
@permission_classes([AllowAny])
def addon_list(request):
    """لیست بسته‌های افزودنی فعال."""
    packs = AddOnPack.objects.filter(is_active=True)
    return APIResponse.success(data=AddOnPackSerializer(packs, many=True).data)


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def buy_addon(request):
    """خرید بسته‌ی افزودنی از طریق درگاه زیبال."""
    serializer = BuyAddOnSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(message=_extract_error(serializer.errors))

    try:
        pack = AddOnPack.objects.get(id=serializer.validated_data['pack_id'], is_active=True)
    except AddOnPack.DoesNotExist:
        return APIResponse.error(message='بسته پیدا نشد', code=404)

    result = AddOnPaymentService().create_payment(
        user=request.user,
        pack=pack,
        callback_url=_client_callback('/home/payment-result-addon'),
    )
    if not result['success']:
        return APIResponse.error(message=result.get('error', 'خطا در ایجاد درخواست پرداخت'))

    return APIResponse.success(
        data={'payment_url': result['payment_url'], 'track_id': result.get('track_id')},
        message='درخواست پرداخت ایجاد شد'
    )


@api_view(['GET'])
@permission_classes([AllowAny])
def addon_payment_callback(request):
    """Zibal callback for add-on purchases."""
    track_id = request.GET.get('trackId')
    if not track_id:
        return render(request, 'payment_result.html', {
            'success': False, 'message': 'شناسه پرداخت یافت نشد', 'data': {}
        })

    result = AddOnVerificationService(track_id).verify_and_grant()
    return render(request, 'payment_result.html', {
        'success': result['success'],
        'message': result['message'],
        'data': result.get('data', {}),
    })





# ============ PAYMENT ============
# accounting/views.py - pay_for_plan همون کد قبلیت sync بمونه
@api_view(['POST'])
@permission_classes([IsAuthenticated])
def pay_for_plan(request):  # ✅ sync
    serializer = BuyPlanSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(message=_extract_error(serializer.errors))

    plan_id = serializer.validated_data['plan_id']

    try:
        plan = Plan.objects.get(id=plan_id, is_active=True)  # ✅ بدون await
    except Plan.DoesNotExist:
        return APIResponse.error(message='پلن پیدا نشد', code=404)

    # جلوگیری از خرید پلن‌های رایگان/آزمایشی از طریق درگاه
    price = plan.discount_price if plan.discount_price is not None else plan.price
    if price <= 0:
        return APIResponse.error(message='پلن‌های رایگان را نمی‌توان از طریق درگاه خریداری کرد')

    user = request.user

    payment_service = ZibalPaymentService()
    result = payment_service.create_payment(  # ✅ بدون await
        user=user,
        plan=plan,
        callback_url=_client_callback('/home/payment-result'),
    )

    if not result['success']:
        return APIResponse.error(
            message=result.get('error', 'خطا در ایجاد درخواست پرداخت')
        )

    return APIResponse.success(
        data={
            'payment_url': result['payment_url'],
            'track_id': result.get('track_id')
        },
        message='درخواست پرداخت ایجاد شد'
    )

@api_view(['GET'])
@permission_classes([AllowAny])
def payment_callback(request):
    """
    Zibal callback — render HTML page
    Expected params: trackId, success, status, orderId
    """
    track_id = request.GET.get('trackId')

    if not track_id:
        return render(request, 'payment_result.html', {
            'success': False,
            'message': 'شناسه پرداخت یافت نشد',
            'data': {}
        })

    service = PaymentVerificationService(track_id)
    result = service.verify_and_activate()

    return render(request, 'payment_result.html', {
        'success': result['success'],
        'message': result['message'],
        'data': result.get('data', {})
    })
