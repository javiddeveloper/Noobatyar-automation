# api/views.py
from .responses import APIResponse
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAdminUser, AllowAny, IsAuthenticated
from .sms import send_otp
from rest_framework_simplejwt.tokens import RefreshToken
from django.contrib.auth.hashers import check_password
from .serializers import (
    RegisterSerializer, LoginSerializer, UserSerializer, 
    UpdateUserSerializer, ForgotPasswordSendOTPSerializer,
    ForgotPasswordVerifyOTPSerializer, ResetPasswordSerializer,
    LogoutSerializer
)
from .models import User
from .permissions import IsVIPUser, HasActiveSubscription
from django.core.cache import cache
from accounting.models import Plan, Subscription # اضافه شد
import secrets


OTP_EXPIRY = 120
RESET_TOKEN_EXPIRY = 300


def get_tokens(user):
    refresh = RefreshToken.for_user(user)
    return {
        'refresh': str(refresh),
        'access': str(refresh.access_token),
    }


def _extract_error(errors: dict) -> str:
    """اولین پیام خطا رو از serializer errors بیرون میکشه"""
    for field, messages in errors.items():
        if isinstance(messages, list) and messages:
            return f"{messages[0]}"
        if isinstance(messages, str):
            return messages
    return 'اطلاعات نامعتبر'


@api_view(['POST'])
@permission_classes([AllowAny])
def register_view(request):
    serializer = RegisterSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    user = User.objects.create_user(
        phone=serializer.validated_data['phone'],
        password=serializer.validated_data['password'],
        name=serializer.validated_data['name']
    )

    # فعال‌سازی خودکار پلن آزمایشی برای کاربر جدید
    trial_plan = Plan.objects.filter(price=0, is_active=True).first()
    if trial_plan:
        Subscription.objects.create(
            user=user,
            plan=trial_plan,
            ends_at=trial_plan.get_end_date()
        )

    return APIResponse.success(
        data={
            'user': UserSerializer(user).data,
            'tokens': get_tokens(user)
        },
        message='ثبت‌نام موفق',
        status=201
    )


@api_view(['POST'])
@permission_classes([AllowAny])
def login_view(request):
    serializer = LoginSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    user = User.objects.filter(phone=serializer.validated_data['phone']).first()
    if not user or not check_password(serializer.validated_data['password'], user.password):
        return APIResponse.unauthorized('شماره یا رمز اشتباه است')

    return APIResponse.success(
        data={
            'user': UserSerializer(user).data,
            'tokens': get_tokens(user)
        },
        message='ورود موفق'
    )


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def logout_view(request):
    serializer = LogoutSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    try:
        token = RefreshToken(serializer.validated_data['refresh'])
        token.blacklist()
        return APIResponse.success(message='خروج موفق')
    except Exception:
        return APIResponse.error('توکن نامعتبر است')


@api_view(['POST'])
@permission_classes([AllowAny])
def forgot_password_send_otp(request):
    serializer = ForgotPasswordSendOTPSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    phone = serializer.validated_data['phone']

    if not User.objects.filter(phone=phone).exists():
        return APIResponse.success(message='اگر شماره ثبت شده باشد، کد ارسال می‌شود')

    code = send_otp(phone)
    if not code:
        return APIResponse.error('خطا در ارسال پیامک')

    cache.set(f'reset_otp:{phone}', str(code), timeout=OTP_EXPIRY)

    return APIResponse.success(
        data={'expires_in': OTP_EXPIRY},
        message='کد تأیید ارسال شد'
    )


@api_view(['POST'])
@permission_classes([AllowAny])
def forgot_password_verify_otp(request):
    serializer = ForgotPasswordVerifyOTPSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    phone = serializer.validated_data['phone']
    code = str(serializer.validated_data['code'])

    cached_code = cache.get(f'reset_otp:{phone}')

    if not cached_code or cached_code != code:
        return APIResponse.error('کد نامعتبر یا منقضی شده است')

    reset_token = secrets.token_urlsafe(32)
    cache.set(f'reset_token:{phone}', reset_token, timeout=RESET_TOKEN_EXPIRY)
    cache.delete(f'reset_otp:{phone}')

    return APIResponse.success(
        data={'reset_token': reset_token, 'expires_in': RESET_TOKEN_EXPIRY},
        message='کد تأیید شد'
    )


@api_view(['POST'])
@permission_classes([AllowAny])
def forgot_password_reset(request):
    serializer = ResetPasswordSerializer(data=request.data)
    if not serializer.is_valid():
        return APIResponse.error(
            message=_extract_error(serializer.errors)
        )

    phone = serializer.validated_data['phone']
    reset_token = serializer.validated_data['reset_token']
    new_password = serializer.validated_data['new_password']

    cached_token = cache.get(f'reset_token:{phone}')
    if not cached_token or cached_token != reset_token:
        return APIResponse.error('توکن نامعتبر یا منقضی شده است')

    user = User.objects.filter(phone=phone).first()
    if not user:
        return APIResponse.error('کاربر یافت نشد', code=404)

    user.set_password(new_password)
    user.save()
    cache.delete(f'reset_token:{phone}')

    return APIResponse.success(message='رمز عبور با موفقیت تغییر کرد')


@api_view(['GET'])
@permission_classes([IsAdminUser])
def user_list(request):
    users = User.objects.all()
    return APIResponse.success(
        data=UserSerializer(users, many=True).data
    )


@api_view(['GET', 'PATCH', 'DELETE'])
@permission_classes([IsAuthenticated])
def user_detail(request, pk):
    # Check permission
    if not request.user.is_staff and request.user.pk != pk:
        return APIResponse.error('دسترسی ندارید', code=403)
    
    user = User.objects.filter(pk=pk).first()
    if not user:
        return APIResponse.error('کاربر پیدا نشد', code=404)

    if request.method == 'GET':
        return APIResponse.success(data=UserSerializer(user).data)

    if request.method == 'PATCH':
        serializer = UpdateUserSerializer(user, data=request.data, partial=True)
        if not serializer.is_valid():
            return APIResponse.error(
                message=_extract_error(serializer.errors)
            )
        serializer.save()
        return APIResponse.success(
            data=UserSerializer(user).data,
            message='پروفایل به‌روز شد'
        )

    if request.method == 'DELETE':
        user.delete()
        return APIResponse.success(message='کاربر حذف شد')


@api_view(['GET'])
@permission_classes([IsAuthenticated, HasActiveSubscription, IsVIPUser])
def vip_content(request):
    return APIResponse.success(
        data={'content': f'سلام {request.user.name}، به بخش VIP خوش اومدی'}
    )
