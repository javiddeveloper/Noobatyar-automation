from datetime import datetime, timedelta, timezone as dt_timezone
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

    Returns dashboard statistics for business owners only. Supports filtering
    by business_id, and optionally by date_from/date_to.

    date_from / date_to use the exact same parameter names and semantics as
    appointment_list (appointment/views/appointment_query_view.py): Unix epoch
    milliseconds, each independently optional, date_from floored to the start
    of that day and date_to pushed to the start of the *next* day so the given
    end date is inclusive. Kept identical on purpose — the owner web dashboard
    (docs/OWNER_WEB_PLAN.md ۹.۱) needs "this month" numbers from the same
    range a caller already used to fetch the matching appointment list, and a
    second date convention on the same resource would be its own bug source.

    With neither param the window defaults to today, unchanged from before
    date filtering existed — mobile's AppointmentApiService only ever sends
    business_id, and that response must stay byte-identical.
    """

    permission_classes = [IsAuthenticated]

    async def get(self, request: Request) -> Response:
        user = request.user

        business_id = request.query_params.get('business_id')
        date_from = request.query_params.get('date_from')
        date_to = request.query_params.get('date_to')

        try:
            stats = await self._get_business_stats(user, business_id, date_from, date_to)
            return APIResponse.success(data=stats, message='آمار نوبت‌ها با موفقیت دریافت شد')

        except ValueError as e:
            return APIResponse.error(str(e), code=status.HTTP_400_BAD_REQUEST)
        except Exception as e:
            logger.error(f"Stats fetch failed: {e}", exc_info=True)
            return APIResponse.error('خطا در دریافت آمار نوبت‌ها', code=status.HTTP_500_INTERNAL_SERVER_ERROR)

    @sync_to_async
    def _get_business_stats(self, user, business_id: str, date_from: str = None, date_to: str = None) -> dict:
        """Get stats for a specific business, over a date range or (default) today"""

        # Validate business_id format
        try:
            business_uuid = int(business_id)
        except ValueError:
            raise ValueError('Invalid business_id format')

        date_filter = {}
        if date_from or date_to:
            # A range was asked for — no implicit "today" bound on top of it,
            # same as appointment_list: date_from/date_to are independent and
            # each optional on their own.
            if date_from:
                try:
                    timestamp_ms = int(date_from)
                    date_from_obj = datetime.fromtimestamp(timestamp_ms / 1000, tz=dt_timezone.utc)
                    date_from_start = timezone.make_aware(datetime.combine(date_from_obj.date(), datetime.min.time()))
                    date_filter['appointment_date__gte'] = date_from_start
                except (ValueError, OSError):
                    raise ValueError('تاریخ شروع (date_from) باید یک Unix timestamp معتبر به میلی‌ثانیه باشد')

            if date_to:
                try:
                    timestamp_ms = int(date_to)
                    date_to_obj = datetime.fromtimestamp(timestamp_ms / 1000, tz=dt_timezone.utc)
                    date_to_end = timezone.make_aware(datetime.combine(date_to_obj.date(), datetime.min.time())) + timedelta(days=1)
                    date_filter['appointment_date__lt'] = date_to_end
                except (ValueError, OSError):
                    raise ValueError('تاریخ پایان (date_to) باید یک Unix timestamp معتبر به میلی‌ثانیه باشد')
        else:
            # No range given — today's window, exactly as this endpoint has
            # always behaved.
            now = timezone.now()
            today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
            today_end = today_start + timedelta(days=1)
            date_filter['appointment_date__gte'] = today_start
            date_filter['appointment_date__lt'] = today_end

        # Single query with aggregation
        stats = Appointment.objects.filter(
            business_id=business_uuid,
            # business__owner=user,
            **date_filter
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