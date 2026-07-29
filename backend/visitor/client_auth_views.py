"""
OTP-based auth for the public client-booking surface — mirrors api/views.py's
send/verify/register shape, but only ever creates/returns a Visitor, never a
`User`. This is what front_client's login flow should call for anyone booking
an appointment; the owner-app endpoints under /api/auth/ are untouched.
"""
import re
import secrets

from django.core.cache import cache
from rest_framework.decorators import api_view, permission_classes, throttle_classes
from rest_framework.permissions import AllowAny

from api.responses import APIResponse
from api.services.otp import send_otp as otp_send, verify_otp as otp_verify
from api.throttles import OTPRateThrottle

from .auth import sign_visitor_token
from .models import Visitor
from .serializers import VisitorSerializer

REGISTER_TOKEN_EXPIRY = 600
PHONE_RE = re.compile(r'^09\d{9}$')


def _clean_phone(request) -> str | None:
    phone = (request.data.get('phone') or '').strip()
    return phone if PHONE_RE.match(phone) else None


@api_view(['POST'])
@permission_classes([AllowAny])
@throttle_classes([OTPRateThrottle])
def client_send_otp_view(request):
    phone = _clean_phone(request)
    if not phone:
        return APIResponse.error(message='شماره موبایل معتبر نیست')

    result = otp_send(phone)
    if not result['success']:
        return APIResponse.error(result.get('error', 'خطا در ارسال کد'))

    return APIResponse.success(data={'expires_in': 180}, message='کد تأیید ارسال شد')


@api_view(['POST'])
@permission_classes([AllowAny])
def client_verify_otp_view(request):
    phone = _clean_phone(request)
    if not phone:
        return APIResponse.error(message='شماره موبایل معتبر نیست')
    code = str(request.data.get('code') or '')

    result = otp_verify(phone, code)
    if not result['success']:
        return APIResponse.error(result.get('error', 'کد نامعتبر است'))

    visitor = Visitor.objects.filter(phone_number=phone).first()
    if visitor:
        return APIResponse.success(
            data={
                'is_registered': True,
                'visitor': VisitorSerializer(visitor).data,
                'token': sign_visitor_token(visitor.id),
            },
            message='ورود موفق',
        )

    register_token = secrets.token_urlsafe(32)
    cache.set(f'visitor_register_token:{phone}', register_token, timeout=REGISTER_TOKEN_EXPIRY)
    return APIResponse.success(
        data={
            'is_registered': False,
            'register_token': register_token,
            'expires_in': REGISTER_TOKEN_EXPIRY,
        },
        message='شماره تایید شد. لطفا نام خود را وارد کنید.',
    )


@api_view(['POST'])
@permission_classes([AllowAny])
def client_register_view(request):
    phone = _clean_phone(request)
    if not phone:
        return APIResponse.error(message='شماره موبایل معتبر نیست')

    register_token = (request.data.get('register_token') or '').strip()
    name = (request.data.get('name') or '').strip()
    if not name:
        return APIResponse.error(message='نام الزامی است')

    expected = cache.get(f'visitor_register_token:{phone}')
    if not expected or not secrets.compare_digest(str(expected), register_token):
        return APIResponse.error('توکن تأیید شماره نامعتبر یا منقضی شده است')
    cache.delete(f'visitor_register_token:{phone}')

    # get_or_create rather than create: phone_number is globally unique, so a
    # visitor who was already added as a contact by some owner (or who booked
    # once before, then let their token expire) attaches to that same row
    # instead of colliding with it.
    visitor, _created = Visitor.objects.get_or_create(
        phone_number=phone,
        defaults={'full_name': name},
    )

    return APIResponse.success(
        data={
            'visitor': VisitorSerializer(visitor).data,
            'token': sign_visitor_token(visitor.id),
        },
        message='ثبت‌نام موفق',
        status=201,
    )
