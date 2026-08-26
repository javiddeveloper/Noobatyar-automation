"""
appointment/views/service_breakdown_view.py

Per-service appointment counts for the owner dashboard's donut chart
(docs/OWNER_WEB_PLAN.md ۹.۱, gap #3): the data was already reachable by
paging through appointment_list and tallying selected_services client-side,
but that only ever sums the current page and produces a chart that is quietly
wrong. This does the tally server-side, over the whole date range in one query.

GET /api/appointment/service-breakdown/?business_id=<id>[&date_from=<ms>][&date_to=<ms>]
→ {
      "business_id": <id>,
      "total_appointments": <int>,
      "services": [{"name": <str>, "count": <int>}, ...],   // count > 0, sorted desc
      "no_service_count": <int>,
      "removed_service_count": <int>
  }

date_from/date_to are optional and share the exact parsing/semantics of
appointment_list (appointment/views/appointment_query_view.py): Unix epoch
milliseconds, each independent, date_from floored to the start of that day and
date_to pushed to the start of the next day so the given end date is
inclusive. Omitting both returns the all-time breakdown — unlike
AppointmentStatsView there is no prior "today" behaviour to stay compatible
with, since this endpoint is new.

── selected_services and the two non-catalog buckets ──────────────────────
Appointment.selected_services is a comma-separated string of names (see
_split_selected_services below for how it is parsed here), and an appointment
can legally carry more than one — the owner app's picker is a multi-select
chip sheet, not a radio button. That is in tension with "the chart total must
reconcile with the appointment count": if every selected service on an
appointment incremented its own bucket, an appointment with two services would
be counted twice and the slices would sum to more than total_appointments.

Resolved by bucketing each appointment exactly once, by its *first* selected
service (the order the owner tapped them in) — never dropped, never
double-counted:

  * no selected_services at all           → no_service_count
  * first service not in Business.services → removed_service_count
    (picked while it was still on the menu; the owner has since renamed or
    removed it — the appointment happened, so it still has to show up
    somewhere, just not attributed to a name that no longer means anything)
  * otherwise                              → services[] under that name

`sum(s["count"] for s in services) + no_service_count + removed_service_count
== total_appointments`, always. A service with zero appointments in the range
is left out of `services` rather than returned at 0 — nothing for the donut
to draw.
"""

from datetime import datetime, timedelta, timezone as dt_timezone

from asgiref.sync import sync_to_async
from django.utils import timezone
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from rest_framework.request import Request
from rest_framework.response import Response
from adrf.views import APIView

from appointment.models import Appointment
from business.models import Business
from api.responses import APIResponse


def _split_selected_services(raw: str) -> list:
    """Parse a stored Appointment.selected_services value into ordered,
    de-duplicated names.

    Deliberately not business.serializers.normalize_service_names: that
    function *validates* (raises on a >100-char name or a non-string/list
    input) because it runs on fresh client input before it is stored. This
    runs on data already in the database — AppointmentView.post
    (appointment/views/views.py) writes selected_services straight from
    request.data without ever calling normalize_service_names, so a raising
    parser here would let one odd historical row 500 the whole dashboard
    instead of just quietly landing in no_service_count/removed_service_count.
    """
    if not raw:
        return []
    names = []
    for part in raw.split(','):
        name = part.strip()
        if name and name not in names:
            names.append(name)
    return names


class ServiceBreakdownView(APIView):
    permission_classes = [IsAuthenticated]

    async def get(self, request: Request) -> Response:
        business_id = request.query_params.get('business_id')
        if not business_id:
            return APIResponse.error('پارامتر business_id الزامی است', code=status.HTTP_400_BAD_REQUEST)
        try:
            business_id = int(business_id)
        except ValueError:
            return APIResponse.error('business_id باید عدد باشد', code=status.HTTP_400_BAD_REQUEST)

        date_from = request.query_params.get('date_from')
        date_to = request.query_params.get('date_to')

        # Same Unix-ms-day parsing as appointment_list, kept out of the
        # sync_to_async body so a malformed value 400s before touching the DB.
        date_filter = {}
        if date_from:
            try:
                timestamp_ms = int(date_from)
                date_from_obj = datetime.fromtimestamp(timestamp_ms / 1000, tz=dt_timezone.utc)
                date_from_start = timezone.make_aware(datetime.combine(date_from_obj.date(), datetime.min.time()))
                date_filter['appointment_date__gte'] = date_from_start
            except (ValueError, OSError):
                return APIResponse.error('تاریخ شروع (date_from) باید یک Unix timestamp معتبر به میلی‌ثانیه باشد', code=status.HTTP_400_BAD_REQUEST)

        if date_to:
            try:
                timestamp_ms = int(date_to)
                date_to_obj = datetime.fromtimestamp(timestamp_ms / 1000, tz=dt_timezone.utc)
                date_to_end = timezone.make_aware(datetime.combine(date_to_obj.date(), datetime.min.time())) + timedelta(days=1)
                date_filter['appointment_date__lt'] = date_to_end
            except (ValueError, OSError):
                return APIResponse.error('تاریخ پایان (date_to) باید یک Unix timestamp معتبر به میلی‌ثانیه باشد', code=status.HTTP_400_BAD_REQUEST)

        data = await self._breakdown(request.user, business_id, date_filter)
        return APIResponse.success(data=data, message='تفکیک نوبت‌ها بر اساس خدمت دریافت شد')

    @sync_to_async
    def _breakdown(self, user, business_id: int, date_filter: dict) -> dict:
        # Scoped to business__user=user, same as daily_counts_view.py — a
        # business_id the caller does not own resolves to an all-zero
        # response rather than a 403/404, so this endpoint gives no signal
        # about whether the id exists at all.
        appointments = Appointment.objects.filter(
            business_id=business_id, business__user=user, **date_filter
        ).values_list('selected_services', flat=True)

        catalog = set(
            Business.objects.filter(id=business_id, user=user)
            .values_list('services', flat=True)
            .first() or []
        )

        counts: dict[str, int] = {}
        no_service_count = 0
        removed_service_count = 0
        total = 0

        for raw in appointments:
            total += 1
            names = _split_selected_services(raw)
            if not names:
                no_service_count += 1
                continue
            primary = names[0]
            if primary in catalog:
                counts[primary] = counts.get(primary, 0) + 1
            else:
                removed_service_count += 1

        services = [
            {'name': name, 'count': count}
            for name, count in sorted(counts.items(), key=lambda item: (-item[1], item[0]))
        ]

        return {
            'business_id': business_id,
            'total_appointments': total,
            'services': services,
            'no_service_count': no_service_count,
            'removed_service_count': removed_service_count,
        }
