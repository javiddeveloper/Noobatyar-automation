"""
api/throttles.py

HTTP-layer rate limiting for the OTP / SMS-triggering endpoints.

The OTP service (api/services/otp.py) already enforces a per-phone 3-minute
cooldown and a 24h ban, but that is keyed on the phone *inside* the request.
This throttle adds an outer, per-client-IP limit so a single source cannot
sweep across many phone numbers to trigger a flood of SMS (SMS-bombing) — the
counter is shared across workers via the Redis cache backend.
"""

from rest_framework.throttling import AnonRateThrottle


class OTPRateThrottle(AnonRateThrottle):
    """Per-IP throttle for OTP/SMS endpoints. Rate comes from THROTTLE_RATES['otp']."""
    scope = 'otp'
