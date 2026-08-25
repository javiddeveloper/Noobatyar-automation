"""
api/device_views.py

Registration of FCM device tokens for the owner app.

    POST /api/devices/register/    {"token": "...", "platform": "ANDROID"}
    POST /api/devices/unregister/  {"token": "..."}

The register call is an upsert keyed on the token, not on the user: FCM issues
one token per app installation, so signing in with a second account on the same
phone has to *move* the row rather than create a duplicate — otherwise the
previous owner keeps receiving push notifications about a business they no
longer have open.

Unregister is what logout calls. It deactivates rather than deletes, matching
how ``api.services.push`` retires tokens FCM reports as dead.
"""

import logging

from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import IsAuthenticated

from .models import DeviceToken
from .responses import APIResponse

logger = logging.getLogger(__name__)

VALID_PLATFORMS = {choice for choice, _ in DeviceToken.PLATFORM_CHOICES}


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def register_device(request):
    token = (request.data.get('token') or '').strip()
    if not token:
        return APIResponse.error("توکن دستگاه ارسال نشده است", code=400)
    if len(token) > 255:
        return APIResponse.error("توکن دستگاه نامعتبر است", code=400)

    platform = (request.data.get('platform') or 'ANDROID').upper()
    if platform not in VALID_PLATFORMS:
        platform = 'ANDROID'

    DeviceToken.objects.update_or_create(
        token=token,
        defaults={
            'user': request.user,
            'platform': platform,
            'device_name': (request.data.get('device_name') or '')[:100],
            'app_version': (request.data.get('app_version') or '')[:30],
            # A token being re-registered is by definition alive again.
            'is_active': True,
        },
    )
    return APIResponse.success(message="دستگاه ثبت شد")


@api_view(['POST'])
@permission_classes([IsAuthenticated])
def unregister_device(request):
    token = (request.data.get('token') or '').strip()
    if not token:
        return APIResponse.error("توکن دستگاه ارسال نشده است", code=400)

    # Scoped to the caller so one account cannot silence another's device by
    # guessing (or replaying) its token.
    DeviceToken.objects.filter(user=request.user, token=token).update(is_active=False)
    return APIResponse.success(message="دستگاه حذف شد")
