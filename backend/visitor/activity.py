"""
visitor/activity.py

Writing to the visitor activity log.

This project has no signals — side effects are called explicitly at each
mutation site (see the SMS helpers in appointment/client_views.py and the
invalidate_slots_cache calls). Activity recording follows the same convention.

Everything here fails open, in the same spirit as accounting/usage.py: an
audit-trail write must never be the reason a booking or a cancellation breaks.
"""

import logging

from .models import VisitorActivity

logger = logging.getLogger(__name__)


def record_activity(
    visitor,
    action,
    *,
    actor_type,
    actor_user=None,
    business=None,
    appointment=None,
    **detail,
):
    """Append one row to the visitor's activity log.

    ``visitor`` may be a Visitor instance or an id. ``business`` / ``appointment``
    / ``actor_user`` likewise accept an instance or an id (or None). Any extra
    keyword arguments are stored together in the ``detail`` JSON field — that is
    where old/new values belong, e.g.
    ``record_activity(v, 'APPOINTMENT_STATUS_CHANGED', ..., old='WAITING', new='COMPLETED')``.

    Returns the created row, or None if recording failed. Never raises.
    """
    try:
        return VisitorActivity.objects.create(
            **_ref('visitor', visitor),
            **_ref('business', business),
            **_ref('appointment', appointment),
            **_ref('actor_user', actor_user),
            action=action,
            actor_type=actor_type,
            detail=detail,
        )
    except Exception:
        # Deliberately swallowed: see the module docstring.
        logger.exception(
            "Failed to record visitor activity %s (visitor=%s)", action, _pk(visitor)
        )
        return None


def _ref(field, value):
    """Build the kwarg for a FK that may be given as an instance or a raw id."""
    if value is None:
        return {}
    if isinstance(value, int):
        return {f'{field}_id': value}
    return {field: value}


def _pk(value):
    return value if isinstance(value, int) or value is None else getattr(value, 'pk', value)
