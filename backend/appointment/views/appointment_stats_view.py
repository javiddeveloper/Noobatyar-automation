from datetime import timedelta
from django.utils import timezone
from django.db.models import Count, Q
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from asgiref.sync import sync_to_async
import logging
from adrf.views import APIView

from appointment.models import Appointment
from api.responses import APIResponse

logger = logging.getLogger(__name__)


class AppointmentStatsView(APIView):
    """
    GET /api/appointments/stats/

    Returns today's dashboard statistics for business owners only.
    Supports filtering by business_id.
    """

    permission_classes = [IsAuthenticated]

    async def get(self, request: Request) -> Response:
        user = request.user

        business_id = request.query_params.get('business_id')

        try:
            stats = await self._get_business_stats(user, business_id)
            return APIResponse.success(data=stats, message='آمار نوبت‌ها با موفقیت دریافت شد')

        except ValueError as e:
            return APIResponse.error(str(e), code=status.HTTP_400_BAD_REQUEST)
        except Exception as e:
            logger.error(f"Stats fetch failed: {e}", exc_info=True)
            return APIResponse.error('خطا در دریافت آمار نوبت‌ها', code=status.HTTP_500_INTERNAL_SERVER_ERROR)

    @sync_to_async
    def _get_business_stats(self, user, business_id: str) -> dict:
        """Get today's stats for specific business"""

        # Today's date range
        now = timezone.now()
        today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
        today_end = today_start + timedelta(days=1)

        # Validate business_id format
        try:
            business_uuid = int(business_id)
        except ValueError:
            raise ValueError('Invalid business_id format')

        # Single query with aggregation
        stats = Appointment.objects.filter(
            business_id=business_uuid,
            # business__owner=user,
            appointment_date__gte=today_start,
            appointment_date__lt=today_end
        ).aggregate(
            total=Count('id'),
            completed=Count('id', filter=Q(status='COMPLETED')),
            no_show=Count('id', filter=Q(status='NO_SHOW')),
            unique_visitors=Count('visitor_id', distinct=True)
        )

        return {
            'total_appointments': stats['total'] or 0,
            'completed_appointments': stats['completed'] or 0,
            'no_show_appointments': stats['no_show'] or 0,
            'total_visitors': stats['unique_visitors'] or 0
        }