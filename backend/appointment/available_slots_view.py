from adrf.views import APIView
from rest_framework.permissions import AllowAny
from api.responses import APIResponse
from business.models import Business
from .models import Appointment
from datetime import datetime, timedelta, timezone
import logging

logger = logging.getLogger(__name__)


class AvailableSlotsView(APIView):
    """
    Returns available time slots for a business on a given date.
    GET /api/client/appointments/{business_id}/available-slots/?date=YYYY-MM-DD
    """
    permission_classes = [AllowAny]

    async def get(self, request, business_id):
        date_str = request.query_params.get('date', '')
        if not date_str:
            return APIResponse.error(message="پارامتر date الزامی است (فرمت: YYYY-MM-DD)", code=400)

        try:
            target_date = datetime.strptime(date_str, '%Y-%m-%d').date()
        except ValueError:
            return APIResponse.error(message="فرمت تاریخ نامعتبر است. از YYYY-MM-DD استفاده کنید", code=400)

        try:
            business = await Business.objects.aget(id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(message="کسب‌وکار یافت نشد", code=404)

        # Build all slots for the day based on work hours and default_service_duration
        duration = business.default_service_duration  # minutes
        start_hour = business.work_start_hour
        end_hour = business.work_end_hour

        # Generate all possible slots
        all_slots = []
        current = datetime(
            target_date.year, target_date.month, target_date.day,
            start_hour, 0, tzinfo=timezone.utc
        )
        day_end = datetime(
            target_date.year, target_date.month, target_date.day,
            end_hour, 0, tzinfo=timezone.utc
        )

        while current + timedelta(minutes=duration) <= day_end:
            all_slots.append(current)
            current += timedelta(minutes=duration)

        # Get booked slots for this business on this date
        day_start_utc = datetime(target_date.year, target_date.month, target_date.day, 0, 0, tzinfo=timezone.utc)
        day_end_utc = datetime(target_date.year, target_date.month, target_date.day, 23, 59, tzinfo=timezone.utc)

        booked_slots = set()
        async for appt in Appointment.objects.filter(
            business=business,
            appointment_date__gte=day_start_utc,
            appointment_date__lte=day_end_utc,
            status__in=['WAITING', 'IN_PROGRESS', 'PENDING_APPROVAL']
        ):
            booked_slots.add(appt.appointment_date.replace(second=0, microsecond=0, tzinfo=timezone.utc))

        # Build response
        now = datetime.now(tz=timezone.utc)
        slots = []
        for slot in all_slots:
            slot_ts = int(slot.timestamp() * 1000)
            is_booked = slot.replace(second=0, microsecond=0) in booked_slots
            is_past = slot < now

            slots.append({
                'time': slot.strftime('%H:%M'),
                'timestamp': slot_ts,
                'available': not is_booked and not is_past,
                'status': 'PAST' if is_past else ('BOOKED' if is_booked else 'AVAILABLE'),
            })

        return APIResponse.success(
            data={
                'business_id': business_id,
                'date': date_str,
                'duration_minutes': duration,
                'slots': slots,
            },
            message="ساعات خالی با موفقیت دریافت شد"
        )
