"""
appointment/occupancy.py

Single source of truth for "does this appointment occupy its slot?".

Before this module the two slot endpoints disagreed:
  * available_slots_view counted only WAITING / IN_PROGRESS / PENDING_APPROVAL
    — so LOCKED, PENDING_VERIFICATION and even CONFIRMED bookings left the slot
    showing as free.
  * public_slots_view counted only CONFIRMED plus LOCKED rows whose
    ``expires_at`` was still in the future — and since nothing ever set
    ``expires_at``, LOCKED never matched there either.

Both now build their queryset from :func:`blocking_q`, so availability is
consistent across endpoints and with the conflict check performed at booking
time.
"""

from datetime import timedelta

from django.db.models import Q
from django.utils import timezone

# How long a client may hold a slot while completing payment.
LOCK_TTL_MINUTES = 15

# Statuses that hold a slot unconditionally: the booking is live, whether it is
# waiting on the owner, on payment verification, or already confirmed/running.
BLOCKING_STATUSES = (
    'PENDING_APPROVAL',
    'PENDING_VERIFICATION',
    'WAITING',
    'CONFIRMED',
    'IN_PROGRESS',
)


def lock_expiry(now=None):
    """Expiry timestamp for a lock created at ``now`` (default: current time)."""
    return (now or timezone.now()) + timedelta(minutes=LOCK_TTL_MINUTES)


def blocking_q(now=None):
    """
    Q object matching appointments that currently occupy their slot.

    That is every :data:`BLOCKING_STATUSES` row, plus LOCKED rows whose payment
    window has not run out yet. An expired LOCKED row matches nothing, which is
    what frees its slot again without needing a cleanup job.
    """
    now = now or timezone.now()
    return Q(status__in=BLOCKING_STATUSES) | Q(
        status='LOCKED', expires_at__gt=now
    )


def overlapping_appointments(business_id, start, duration_minutes, now=None):
    """
    Queryset of slot-occupying appointments that overlap
    ``[start, start + duration_minutes)`` for ``business_id``.

    Overlap is computed in Python-friendly bounds rather than with a duration
    expression: candidates are limited to a window wide enough to contain any
    appointment that could reach ``start``, then filtered precisely by the
    caller-visible end time.
    """
    from .models import Appointment

    now = now or timezone.now()
    end = start + timedelta(minutes=duration_minutes)

    # A stored appointment can only overlap if it starts before our end; we
    # bound the scan on the left by the longest allowed service (8h, enforced by
    # AppointmentCreateSerializer.validate_service_duration).
    window_start = start - timedelta(hours=8)

    candidates = Appointment.objects.filter(
        blocking_q(now),
        business_id=business_id,
        appointment_date__gt=window_start,
        appointment_date__lt=end,
    )

    return candidates


def find_conflict(business_id, start, duration_minutes, exclude_id=None, now=None):
    """
    Return the first appointment overlapping the requested window, or None.

    The precise end-time comparison happens here (in Python) because
    ``service_duration`` may be null and then falls back to the business
    default, which is awkward to express in SQL.
    """
    from datetime import timedelta as _timedelta

    end = start + _timedelta(minutes=duration_minutes)
    qs = overlapping_appointments(business_id, start, duration_minutes, now=now)
    if exclude_id is not None:
        qs = qs.exclude(id=exclude_id)

    for appt in qs.select_related('business').only(
        'id', 'appointment_date', 'service_duration', 'status', 'business__default_service_duration'
    ):
        appt_duration = appt.service_duration or appt.business.default_service_duration
        appt_end = appt.appointment_date + _timedelta(minutes=appt_duration)
        if appt.appointment_date < end and start < appt_end:
            return appt

    return None
