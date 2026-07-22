"""
appointment/views/public_slots_view.py

Public endpoint — returns ONLY the occupied time ranges for a given business
and date.  No visitor names, phones, or any personal data are ever serialised.

Red Line #1  : Only PublicBusinessSerializer is used; no owner data leaks.
Red Line #4  : Response contains exactly {start_time, end_time, status}.
               The client device calculates free slots itself.

Endpoint
--------
GET /api/client/businesses/<business_id>/slots/?date=YYYY-MM-DD

Query params
------------
date  (required)  ISO date string, e.g. 2026-07-14
"""

from datetime import datetime, timedelta, timezone as dt_timezone

from django.core.cache import cache
from django.utils import timezone
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView
from rest_framework import status

from business.models import Business
from ..models import Appointment
from ..cache_utils import public_slots_key, SLOT_CACHE_TTL


class PublicAvailableSlotsView(APIView):
    """
    Returns occupied/locked time slots for one business on one date.
    Completely public — no authentication required.

    The slot list contains ONLY:
      - start_time  (ISO 8601)
      - end_time    (ISO 8601, derived from service_duration or business default)
      - status      ("CONFIRMED" | "LOCKED")

    A slot is considered "occupied" if:
      - status == "CONFIRMED", OR
      - status == "LOCKED"  AND  expires_at > now()   (active lock, not expired)

    Expired locks (expires_at <= now()) are intentionally excluded so those
    slots appear free again to new visitors.
    """

    permission_classes = [AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "public_slots"

    def get(self, request, business_id: int) -> Response:
        # ── 1. Parse & validate the date query param ──────────────────────
        date_str = request.query_params.get("date")
        if not date_str:
            return Response(
                {"detail": "پارامتر date الزامی است. فرمت: YYYY-MM-DD"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        try:
            query_date = datetime.strptime(date_str, "%Y-%m-%d").date()
        except ValueError:
            return Response(
                {"detail": "فرمت تاریخ نامعتبر است. از YYYY-MM-DD استفاده کنید."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # ── 2. Serve from cache when available (high-traffic public endpoint) ─
        cache_key = public_slots_key(business_id, query_date)
        cached = cache.get(cache_key)
        if cached is not None:
            return Response(cached, status=status.HTTP_200_OK)

        # ── 3. Resolve business ───────────────────────────────────────────
        try:
            business = Business.objects.get(pk=business_id, is_locked=False)
        except Business.DoesNotExist:
            return Response(
                {"detail": "کسب و کار یافت نشد."},
                status=status.HTTP_404_NOT_FOUND,
            )

        # ── 3. Build day boundaries (aware datetimes) ─────────────────────
        tz = timezone.get_current_timezone()
        day_start = datetime(
            query_date.year, query_date.month, query_date.day, 0, 0, 0, tzinfo=tz
        )
        day_end = datetime(
            query_date.year, query_date.month, query_date.day, 23, 59, 59, tzinfo=tz
        )
        now = timezone.now()

        # ── 4. Query only what we need — NO select_related on visitor ─────
        #
        # We deliberately do NOT call select_related('visitor') so that
        # visitor data never enters memory.
        #
        # The queryset returns only CONFIRMED slots, plus LOCKED slots whose
        # lock has NOT yet expired.
        appointments = (
            Appointment.objects
            .filter(
                business_id=business_id,
                appointment_date__range=(day_start, day_end),
            )
            .filter(
                # CONFIRMED — always occupied
                status="CONFIRMED",
            )
            | Appointment.objects
            .filter(
                business_id=business_id,
                appointment_date__range=(day_start, day_end),
                status="LOCKED",
                expires_at__gt=now,   # only active locks
            )
        ).only(                        # ← pull minimum columns from DB
            "appointment_date",
            "service_duration",
            "status",
        ).order_by("appointment_date")

        # ── 5. Serialise — manual projection, no ModelSerializer ──────────
        #
        # We intentionally skip DRF serializers here to make it impossible
        # for a future developer to accidentally add a visitor field.
        default_duration: int = business.default_service_duration or 30

        slots = []
        for appt in appointments:
            duration = appt.service_duration or default_duration
            start = appt.appointment_date
            end = start + timedelta(minutes=duration)
            slots.append(
                {
                    "start_time": start.isoformat(),
                    "end_time": end.isoformat(),
                    "status": appt.status,
                }
            )

        payload = {
            "business_id": business_id,
            "date": date_str,
            "occupied_slots": slots,
        }
        cache.set(cache_key, payload, SLOT_CACHE_TTL)

        return Response(payload, status=status.HTTP_200_OK)
