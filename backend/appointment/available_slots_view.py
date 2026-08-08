from adrf.views import APIView
from asgiref.sync import sync_to_async
from django.core.cache import cache
from rest_framework.permissions import AllowAny
from rest_framework.throttling import ScopedRateThrottle
from api.responses import APIResponse
from business.models import Business
from .models import Appointment
from .cache_utils import available_slots_key, SLOT_CACHE_TTL
from .occupancy import blocking_q
from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo
from django.conf import settings
import logging

logger = logging.getLogger(__name__)

# Business working hours are wall-clock times in the project's timezone.
LOCAL_TZ = ZoneInfo(settings.TIME_ZONE)


class AvailableSlotsView(APIView):
    """
    Returns available time slots for a business on a given date.
    GET /api/client/appointments/{business_id}/available-slots/?date=YYYY-MM-DD
    """
    permission_classes = [AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = 'public_slots'

    async def get(self, request, business_id):
        date_str = request.query_params.get('date', '')
        if not date_str:
            return APIResponse.error(message="پارامتر date الزامی است (فرمت: YYYY-MM-DD)", code=400)

        try:
            target_date = datetime.strptime(date_str, '%Y-%m-%d').date()
        except ValueError:
            return APIResponse.error(message="فرمت تاریخ نامعتبر است. از YYYY-MM-DD استفاده کنید", code=400)

        try:
            # Same gate as the public business detail endpoint: availability is
            # discovery, so a business that is locked or not editorially
            # approved reads as missing rather than as "exists but hidden".
            #
            # Resolved *before* the cache read on purpose. The cache is keyed on
            # business+date and knows nothing about moderation, so serving a hit
            # first would keep publishing a business's availability for the rest
            # of the TTL after a moderator suspended it. One indexed PK lookup on
            # a cache hit is a cheap price for the gate never going stale; the
            # expensive part (the appointment scan) is still cached.
            business = await Business.objects.aget(Business.public_filter(), id=business_id)
        except Business.DoesNotExist:
            return APIResponse.error(message="کسب‌وکار یافت نشد", code=404)

        # Serve from cache when available (high-traffic public endpoint).
        cache_key = available_slots_key(business_id, target_date)
        cached = await sync_to_async(cache.get)(cache_key)
        if cached is not None:
            return APIResponse.success(data=cached, message="ساعات خالی با موفقیت دریافت شد")

        # Build all slots for the day based on work hours and default_service_duration
        duration = business.default_service_duration  # minutes
        start_hour = business.work_start_hour
        end_hour = business.work_end_hour

        # Generate all possible slots.
        # work_start_hour/work_end_hour are wall-clock hours in the business's own
        # timezone, so they must be anchored to LOCAL_TZ. Building them as UTC
        # shifted every slot by the UTC offset (+03:30 for Tehran): the label said
        # "20:30" while the instant was 00:00 the next day, so slots outside working
        # hours were offered and `is_past` was judged against the wrong wall clock.
        all_slots = []
        current = datetime(
            target_date.year, target_date.month, target_date.day,
            start_hour, 0, tzinfo=LOCAL_TZ
        )
        day_end = datetime(
            target_date.year, target_date.month, target_date.day,
            end_hour, 0, tzinfo=LOCAL_TZ
        )

        while current + timedelta(minutes=duration) <= day_end:
            all_slots.append(current)
            current += timedelta(minutes=duration)

        # Get booked slots for this business on this local calendar day
        day_start = datetime(target_date.year, target_date.month, target_date.day, 0, 0, tzinfo=LOCAL_TZ)
        day_end_of_day = day_start + timedelta(days=1)

        booked_slots = set()
        async for appt in Appointment.objects.filter(
            blocking_q(),
            business=business,
            appointment_date__gte=day_start,
            appointment_date__lt=day_end_of_day,
        ):
            # Keep the stored instant as-is; aware datetimes hash/compare by their
            # UTC value, so a Tehran-anchored slot still matches a UTC-stored one.
            booked_slots.add(appt.appointment_date.replace(second=0, microsecond=0))

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

        data = {
            'business_id': business_id,
            'date': date_str,
            'duration_minutes': duration,
            'slots': slots,
        }
        await sync_to_async(cache.set)(cache_key, data, SLOT_CACHE_TTL)

        return APIResponse.success(data=data, message="ساعات خالی با موفقیت دریافت شد")
