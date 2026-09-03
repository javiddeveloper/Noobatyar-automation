"""
appointment/views/daily_counts_view.py

Per-day appointment counts for the owner's home chart.

GET /api/appointment/daily-counts/?business_id=<id>&days=7&days_ahead=0
→ [{ "date": "YYYY-MM-DD", "count": <int> }, ...]  (oldest → newest, gap-filled)

`days` counts backwards from today (inclusive); `days_ahead` extends the window
into the future. The home screen's month card needs both sides — an owner
looking at "next month" is asking about bookings that have not happened yet —
while the 7-day trend chart keeps calling this with `days` alone.
"""

from datetime import timedelta

from asgiref.sync import sync_to_async
from django.db.models import Count
from django.db.models.functions import TruncDate
from django.utils import timezone
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response
from adrf.views import APIView

from appointment.models import Appointment
from api.responses import APIResponse

# Per side, so a full window can reach ~6 months. Raised from 60 because the
# home month card asks for a whole Jalali month either side of today, and on
# the last day of a 31-day month that alone is 62 days back.
MAX_DAYS = 93


class DailyCountsView(APIView):
    permission_classes = [IsAuthenticated]

    async def get(self, request: Request) -> Response:
        business_id = request.query_params.get('business_id')
        if not business_id:
            return APIResponse.error('پارامتر business_id الزامی است', code=status.HTTP_400_BAD_REQUEST)
        try:
            business_id = int(business_id)
        except ValueError:
            return APIResponse.error('business_id باید عدد باشد', code=status.HTTP_400_BAD_REQUEST)

        try:
            days = int(request.query_params.get('days', 7))
        except ValueError:
            days = 7
        days = max(1, min(days, MAX_DAYS))

        try:
            days_ahead = int(request.query_params.get('days_ahead', 0))
        except ValueError:
            days_ahead = 0
        days_ahead = max(0, min(days_ahead, MAX_DAYS))

        data = await self._daily_counts(request.user, business_id, days, days_ahead)
        return APIResponse.success(data=data, message='آمار روزانه نوبت‌ها دریافت شد')

    @sync_to_async
    def _daily_counts(self, user, business_id: int, days: int, days_ahead: int = 0):
        tz = timezone.get_current_timezone()
        today = timezone.localdate()
        start_date = today - timedelta(days=days - 1)
        # Exclusive upper bound on the query so a forward window does not pull
        # in every future booking the business has ever taken.
        end_date = today + timedelta(days=days_ahead)
        start_dt = timezone.make_aware(
            timezone.datetime(start_date.year, start_date.month, start_date.day, 0, 0), tz
        )
        end_dt = timezone.make_aware(
            timezone.datetime(end_date.year, end_date.month, end_date.day, 0, 0), tz
        ) + timedelta(days=1)

        rows = (
            Appointment.objects
            .filter(
                business_id=business_id, business__user=user,
                appointment_date__gte=start_dt, appointment_date__lt=end_dt,
            )
            .annotate(day=TruncDate('appointment_date'))
            .values('day')
            .annotate(count=Count('id'))
        )
        counts = {row['day'].isoformat(): row['count'] for row in rows if row['day']}

        # Gap-fill every day in the window so the chart always has a point per day.
        result = []
        for i in range(days + days_ahead):
            d = (start_date + timedelta(days=i)).isoformat()
            result.append({'date': d, 'count': counts.get(d, 0)})
        return result
