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
from .models import VisitorActivity
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
