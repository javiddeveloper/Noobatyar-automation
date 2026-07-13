"""
api/services/otp.py

Redis-backed OTP service.  All state lives in Django's cache framework
(configured as django-redis in production), making it stateless across
multiple workers and restarts.

Cache keys
----------
otp:code:{phone}        OTP value          TTL = 3 min   (OTP_TTL)
otp:rate:{phone}        Rate-limit flag    TTL = 3 min   (OTP_RATE_TTL)
otp:daily_fail:{phone}  Daily fail counter TTL = 24 h    (OTP_DAILY_FAIL_TTL)

Red Line #3 rules
-----------------
1. Max 1 OTP request per phone per 3 minutes.
2. After 5 wrong verifications in 24 h  → phone banned for the rest of that 24 h.
3. Sending SMS is ALWAYS non-blocking — the HTTP call to Melipayamak runs in a
   background daemon thread so the API response is instant.
"""

import random
import threading
import logging

from django.core.cache import cache
from django.conf import settings

logger = logging.getLogger(__name__)

# ── Configurable constants (override via settings if needed) ──────────────────
OTP_TTL           = getattr(settings, "OTP_TTL_SECONDS",      180)   # 3 min
OTP_RATE_TTL      = getattr(settings, "OTP_RATE_TTL_SECONDS",  180)  # 3 min cooldown
OTP_DAILY_FAIL_TTL = getattr(settings, "OTP_DAILY_FAIL_TTL",  86400) # 24 h ban window
OTP_MAX_DAILY_FAIL = getattr(settings, "OTP_MAX_DAILY_FAIL",     5)  # failures before ban
OTP_MAX_ATTEMPTS  = getattr(settings, "OTP_MAX_ATTEMPTS",         5)  # per-code verify tries


# ── Key helpers ────────────────────────────────────────────────────────────────

def _key_code(phone: str) -> str:
    return f"otp:code:{phone}"

def _key_rate(phone: str) -> str:
    return f"otp:rate:{phone}"

def _key_daily_fail(phone: str) -> str:
    return f"otp:daily_fail:{phone}"

def _key_attempts(phone: str) -> str:
    return f"otp:attempts:{phone}"


# ── Public API ────────────────────────────────────────────────────────────────

def send_otp(phone: str) -> dict:
    """
    Generate an OTP and dispatch it asynchronously.

    Returns
    -------
    {"success": True}
        OTP was generated and SMS dispatch started.
    {"success": False, "error": str}
        Request rejected (rate limit or daily ban).
    """
    # 1. Daily ban check
    fail_count = cache.get(_key_daily_fail(phone), 0)
    if fail_count >= OTP_MAX_DAILY_FAIL:
        logger.warning("OTP daily ban active for %s (fails=%d)", phone, fail_count)
        return {
            "success": False,
            "error": "این شماره به دلیل تلاش‌های ناموفق مکرر تا ۲۴ ساعت مسدود شده است",
        }

    # 2. Rate limit check (3-minute cooldown)
    if cache.get(_key_rate(phone)):
        return {
            "success": False,
            "error": "لطفاً ۳ دقیقه صبر کنید و سپس دوباره درخواست کنید",
        }

    # 3. Generate code and store it atomically
    code = _generate_otp()
    cache.set(_key_code(phone),    code, timeout=OTP_TTL)
    cache.set(_key_rate(phone),    True, timeout=OTP_RATE_TTL)
    cache.set(_key_attempts(phone), 0,   timeout=OTP_TTL)

    # 4. Fire-and-forget — do NOT await or join; response returns immediately
    thread = threading.Thread(
        target=_dispatch_sms,
        args=(phone, code),
        daemon=True,          # won't block server shutdown
        name=f"otp-sms-{phone[-4:]}",
    )
    thread.start()

    logger.info("OTP generated and SMS thread started for %s", phone[-4:] + "****")
    return {"success": True}


def verify_otp(phone: str, code: str) -> dict:
    """
    Verify a submitted OTP code.

    Side effects
    ------------
    - On success  : clears code, rate, and attempt keys.
    - On failure  : increments daily fail counter; increments attempt counter.
      If attempt count reaches OTP_MAX_ATTEMPTS the code is invalidated early.
    """
    stored_code = cache.get(_key_code(phone))
    if not stored_code:
        return {"success": False, "error": "کد منقضی شده یا ارسال نشده است"}

    attempts = cache.get(_key_attempts(phone), 0)

    if attempts >= OTP_MAX_ATTEMPTS:
        # Too many wrong tries — kill the code
        _invalidate_otp(phone)
        return {"success": False, "error": "تعداد تلاش بیش از حد مجاز — لطفاً کد جدید دریافت کنید"}

    if stored_code != str(code):
        # Wrong code — increment counters
        cache.set(_key_attempts(phone), attempts + 1, timeout=OTP_TTL)
        _increment_daily_fail(phone)
        remaining = OTP_MAX_ATTEMPTS - attempts - 1
        return {
            "success": False,
            "error": f"کد اشتباه است — {remaining} تلاش باقی مانده",
        }

    # ✓ Correct — clean up all related keys
    _invalidate_otp(phone)
    return {"success": True}


# ── Internal helpers ───────────────────────────────────────────────────────────

def _generate_otp() -> str:
    return str(random.randint(100000, 999999))


def _invalidate_otp(phone: str) -> None:
    """Remove all per-OTP keys (called on success or max-attempt breach)."""
    cache.delete(_key_code(phone))
    cache.delete(_key_rate(phone))
    cache.delete(_key_attempts(phone))


def _increment_daily_fail(phone: str) -> None:
    """
    Atomically increment the daily failure counter.
    We use add+incr because Django's cache.incr raises ValueError if the
    key doesn't exist; cache.add is a no-op if it already exists.
    """
    if not cache.add(_key_daily_fail(phone), 1, timeout=OTP_DAILY_FAIL_TTL):
        try:
            cache.incr(_key_daily_fail(phone))
        except ValueError:
            cache.set(_key_daily_fail(phone), 1, timeout=OTP_DAILY_FAIL_TTL)


def _dispatch_sms(phone: str, code: str) -> None:
    """
    Background thread target — calls Melipayamak API.
    Any exception is logged and swallowed; the main request has already returned.
    """
    import requests

    try:
        if phone.startswith("98"):
            phone = "0" + phone[2:]

        otp_token = getattr(settings, "MELIPAYAMAK_OTP_TOKEN", None)
        if not otp_token:
            logger.error("MELIPAYAMAK_OTP_TOKEN is not configured — OTP SMS not sent")
            return

        url = f"https://console.melipayamak.com/api/send/simple/{otp_token}"
        payload = {
            "from": getattr(settings, "MELIPAYAMAK_FROM", ""),
            "to": phone,
            "text": f"کد تأیید نوبت‌یار: {code}\nاعتبار: ۳ دقیقه",
        }
        response = requests.post(url, json=payload, timeout=10)
        result = response.json() if response.text.strip() else {}

        if result.get("code") == 0 or response.status_code == 200:
            logger.info("OTP SMS delivered to %s****", phone[-4:])
        else:
            logger.warning("OTP SMS failed for %s****: %s", phone[-4:], result)

    except requests.Timeout:
        logger.error("OTP SMS timeout for %s****", phone[-4:])
    except Exception as exc:
        logger.exception("OTP SMS unexpected error for %s****: %s", phone[-4:], exc)
