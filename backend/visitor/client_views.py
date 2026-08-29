"""
Visitor-facing (client) endpoints for a visitor's own account.

Mirrors the shape of appointment/client_views.py: request.user is a Visitor
instance here, not a `User` (see visitor/auth.py), so ownership scoping is
always `request.user` itself rather than a lookup.
"""

from adrf.views import APIView
from asgiref.sync import sync_to_async

from api.pagination import StandardPagination
from api.responses import APIResponse

from .auth import IsVisitorAuthenticated, VisitorTokenAuthentication
from .models import VisitorActivity, VisitorDeviceToken
from .serializers import VisitorActivitySerializer, VisitorSerializer


class ClientMeView(APIView):
    """The signed-in visitor's own identity.

    Until this existed the client only received the visitor object once, in the
    OTP verify response, and never persisted it — so a page reload lost the
    person's own name.
    """

    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def get(self, request):
        return APIResponse.success(
            data=VisitorSerializer(request.user).data,
            message="اطلاعات حساب شما با موفقیت دریافت شد",
        )


class ClientActivityView(APIView):
    """The visitor's own activity log, newest first.

    This is what makes owner-side actions visible to the person they affect —
    notably being archived from a business's list, or having their contact
    details edited.
    """

    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def get(self, request):
        # Paginate and serialize in one sync block: DRF's paginator and the
        # related-field lookups below are sync ORM calls.
        return await sync_to_async(self._list_paginated)(request)

    def _list_paginated(self, request):
        queryset = (
            VisitorActivity.objects
            .filter(visitor=request.user)
            .select_related('business')
        )

        paginator = StandardPagination()
        page = paginator.paginate_queryset(queryset, request, view=self)
        serializer = VisitorActivitySerializer(page, many=True)
        paginated = paginator.get_paginated_response(serializer.data)

        return APIResponse.success(
            data=paginated.data,
            message="تاریخچه فعالیت شما با موفقیت دریافت شد",
        )


class ClientDeviceRegisterView(APIView):
    """
    Register/refresh an FCM web-push token for the signed-in visitor.

    Mirrors api/device_views.py's register_device exactly (same
    upsert-on-token reasoning — see VisitorDeviceToken's docstring), scoped
    to the visitor instead of an owner User.
    """

    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def post(self, request):
        return await sync_to_async(self._register)(request)

    def _register(self, request):
        token = (request.data.get('token') or '').strip()
        if not token:
            return APIResponse.error("توکن دستگاه ارسال نشده است", code=400)
        if len(token) > 255:
            return APIResponse.error("توکن دستگاه نامعتبر است", code=400)

        VisitorDeviceToken.objects.update_or_create(
            token=token,
            defaults={
                'visitor': request.user,
                'platform': 'WEB',
                'is_active': True,
            },
        )
        return APIResponse.success(message="دستگاه ثبت شد")


class ClientDeviceUnregisterView(APIView):
    """Deactivate one of the visitor's own device tokens (e.g. on logout)."""

    authentication_classes = [VisitorTokenAuthentication]
    permission_classes = [IsVisitorAuthenticated]

    async def post(self, request):
        return await sync_to_async(self._unregister)(request)

    def _unregister(self, request):
        token = (request.data.get('token') or '').strip()
        if not token:
            return APIResponse.error("توکن دستگاه ارسال نشده است", code=400)

        # Scoped to the caller so one visitor cannot silence another's device
        # by guessing (or replaying) its token.
        VisitorDeviceToken.objects.filter(visitor=request.user, token=token).update(is_active=False)
        return APIResponse.success(message="دستگاه حذف شد")
