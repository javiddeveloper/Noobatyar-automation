from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import AllowAny
from .models import AppVersion
from .serializers import AppVersionSerializer


class CheckVersionView(APIView):
    """
    POST /api/version/check/
    body: {"version_code": 100}
    """
    permission_classes = [AllowAny] 

    def post(self, request):
        client_version_code = request.data.get('version_code')

        if not client_version_code:
            return Response({
                "status": "error",
                "code": 400,
                "message": "version_code الزامی است",
                "data": None
            }, status=status.HTTP_400_BAD_REQUEST)

        # آخرین نسخه فعال
        latest = AppVersion.objects.filter(is_active=True).first()

        if not latest:
            return Response({
                "status": "error",
                "code": 404,
                "message": "نسخه‌ای یافت نشد",
                "data": None
            }, status=status.HTTP_404_NOT_FOUND)

        try:
            client_code = int(client_version_code)
        except (ValueError, TypeError):
            return Response({
                "status": "error",
                "code": 400,
                "message": "version_code باید عدد باشد",
                "data": None
            }, status=status.HTTP_400_BAD_REQUEST)

        is_outdated = client_code < latest.version_code

        return Response({
            "status": "success",
            "code": 200,
            "message": "بررسی نسخه موفق",
            "data": {
                "is_outdated": is_outdated,
                "force_update": latest.force_update if is_outdated else False,
                "latest_version": AppVersionSerializer(latest).data
            }
        })


class ChangelogListView(APIView):
    """
    GET /api/version/changelog/
    """
    permission_classes = [AllowAny] 

    def get(self, request):
        versions = AppVersion.objects.filter(is_active=True)

        changelog = [
            {
                "version_name": v.version_name,
                "version_code": v.version_code,
                "changes": v.changelog.changes if hasattr(v, 'changelog') else []
            }
            for v in versions
        ]

        return Response({
            "status": "success",
            "code": 200,
            "message": "لیست تغییرات",
            "data": changelog
        })
