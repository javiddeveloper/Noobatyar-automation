# core/dashboard/cache.py
"""
Short-TTL caching for the dashboard payload.

The aggregates in metrics.py touch every large table on the platform. The admin
index is the first page every staff member lands on and the one they leave open,
so without this a handful of people refreshing produces a steady stream of full
table scans. Ninety seconds is short enough that nobody watching a payment come
in doubts the number, and long enough that a shift's worth of refreshes costs
one computation.

The cache is shared (Redis in production), not per-process: the payload is
identical for every viewer by design — permission filtering happens after the
cache, in panels.py — so one entry serves everyone.

Nothing here may raise. `django_redis` is configured with IGNORE_EXCEPTIONS, so
a Redis outage turns get/set into None/no-op rather than an error, but the
locmem fallback in DEBUG and any future backend get the same treatment from the
try/except: a cache problem degrades this page to a live query, never to a 500.
"""

import logging

from django.core.cache import cache

from . import metrics

logger = logging.getLogger(__name__)

# Versioned so a change to the payload's shape cannot serve a stale dict to a
# template that no longer matches it — bump the suffix instead of flushing.
CACHE_KEY = 'admin:dashboard:v1'
CACHE_TTL = 90


def get_payload(force_refresh=False):
    """The dashboard payload, from cache when possible.

    Returns ``(payload, cached)`` so the page can tell staff how fresh the
    numbers are.
    """
    if not force_refresh:
        try:
            cached = cache.get(CACHE_KEY)
        except Exception:
            logger.warning('dashboard cache read failed; falling back to a live query',
                           exc_info=True)
            cached = None
        if cached is not None:
            return cached, True

    payload = metrics.collect()
    try:
        cache.set(CACHE_KEY, payload, CACHE_TTL)
    except Exception:
        logger.warning('dashboard cache write failed; the next load recomputes',
                       exc_info=True)
    return payload, False


def invalidate():
    """Drop the cached payload. Used by the page's "بروزرسانی" action."""
    try:
        cache.delete(CACHE_KEY)
    except Exception:
        logger.warning('dashboard cache delete failed', exc_info=True)
