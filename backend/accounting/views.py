from asgiref.sync import sync_to_async
from django.shortcuts import render
from rest_framework.permissions import IsAuthenticated, AllowAny, IsAdminUser
from adrf.decorators import api_view

from api.responses import APIResponse
from api.views import _extract_error
from .models import Plan, Subscription
from .payment.zibal_payment import ZibalPaymentService
from .payment.zibal_payment_verification import PaymentVerificationService
from .serializers import PlanSerializer, SubscriptionSerializer, BuyPlanSerializer
from rest_framework.decorators import api_view, permission_classes


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
    if plan.is_vip and user.user_type != 'vip':
        user.user_type = 'vip'
        user.save(update_fields=['user_type'])

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

    if not subscription:
        return APIResponse.success(
            data=None,
            message='اشتراک فعالی ندارید'
        )

    serializer = SubscriptionSerializer(subscription)
    return APIResponse.success(data=serializer.data)



@api_view(['GET'])
@permission_classes([IsAdminUser])
async def all_subscriptions(request):
    """همه اشتراک‌ها — فقط ادمین"""
    subs = await sync_to_async(list)(
        Subscription.objects.select_related('user', 'plan').all()
    )
    serializer = SubscriptionSerializer(subs, many=True)
    return APIResponse.success(data=serializer.data)


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
        callback_url='https://noobatyar.ir/home/payment-result'
        # callback_url=request.build_absolute_uri('/payment-result')
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
