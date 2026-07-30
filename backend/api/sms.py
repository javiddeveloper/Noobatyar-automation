# api/sms.py
import logging
import requests
from typing import Optional

from django.conf import settings

logger = logging.getLogger(__name__)

class SmsNotConfigured(RuntimeError):
    """Raised when no provider token is configured."""


def _token() -> str:
    """The Melipayamak API token, from MELIPAYAMAK_OTP_TOKEN only.

    There used to be a hardcoded token here as a fallback "so OTP keeps working
    when the env var is unset". That was a live credential committed to a public
    repository, and because it silently papered over missing configuration it is
    also why a broken SMS setup went unnoticed for so long. Failing loudly is the
    point: a missing token should be a deployment error, not a silent downgrade.
    """
    token = (getattr(settings, 'MELIPAYAMAK_OTP_TOKEN', '') or '').strip()
    if not token:
        raise SmsNotConfigured(
            'MELIPAYAMAK_OTP_TOKEN is not set — cannot send SMS. '
            'Set it in the environment (see DEPLOYMENT.md).'
        )
    return token


def _dev_mode() -> bool:
    """Dev bypass: log messages instead of dispatching them (see settings.SMS_DEV_MODE).

    settings forces this off whenever DEBUG is off, so production always sends.
    """
    return bool(getattr(settings, 'SMS_DEV_MODE', False))


# Outgoing messages are signed at the bottom with the site address rather than
# opened with a "نوبت‌یار" header: the recipient sees their own news first, and
# the footer still says who sent it while doubling as a way back to the site.
SMS_FOOTER = 'نوبت‌یار'


def signed(body: str) -> str:
    """Append the standard footer to a message body.

    Every notification goes through here so the wording (and the domain, should
    it ever change) lives in exactly one place.
    """
    return f'{body}\n\n{SMS_FOOTER}'


def send_otp(phone: str) -> Optional[str]:  # به جای str | None
    if _dev_mode():
        logger.warning('SMS dev bypass — OTP not sent to %s****', phone[-4:])
        return None
    try:
        url = f'https://console.melipayamak.com/api/send/otp/{_token()}'
    except SmsNotConfigured as e:
        logger.error("%s", e)
        return None
    try:
        response = requests.post(url, json={'to': phone}, timeout=10)
        result = response.json()
        if result.get('status') == 'عملیات موفق':
            return result.get('code')
        return None
    except Exception:
        return None


def send_sms(phone: str, message: str) -> tuple[bool, str]:
    """Send a standard text message using Melipayamak's simple-send API.

    Two bugs were fixed here relative to the previous prototype:
      1. The required ``from`` sender line (settings.MELIPAYAMAK_FROM) was never
         sent, so Melipayamak rejected every message.
      2. Success detection compared ``status`` against strings that the
         simple-send endpoint never returns, so a *sent* SMS was still reported
         as failed. Simple-send returns ``{"recId": <positive int>, "status": ...}``
         on success, so we key off ``recId``.
    """
    if _dev_mode():
        logger.warning(
            'SMS dev bypass — not sending to %s****. Message:\n%s', phone[-4:], message
        )
        return True, "dev mode bypass"

    try:
        url = f'https://console.melipayamak.com/api/send/simple/{_token()}'
    except SmsNotConfigured as e:
        # Surfaced to the caller so it lands in SmsLog.error_detail and the
        # owner's credit is refunded, rather than raising a 500.
        logger.error("%s", e)
        return False, str(e)

    sender = getattr(settings, 'MELIPAYAMAK_FROM', '') or ''

    if not sender:
        logger.warning(
            "MELIPAYAMAK_FROM is not configured; SMS to %s may be rejected by the provider",
            phone,
        )

    payload = {
        'from': sender,  # required by Melipayamak simple-send
        'to': phone,
        'text': message,
    }

    try:
        response = requests.post(url, json=payload, timeout=10)
        result = response.json()
        # On success simple-send returns a positive recId; some responses also
        # carry a Persian success message in `status`.
        rec_id = result.get('recId') or result.get('recID') or 0
        status_text = str(result.get('status', ''))
        success = bool(rec_id) or ('موفق' in status_text)
        
        error_detail = ""
        if not success:
            error_detail = str(result)
            logger.error("SMS send failed to %s: %s", phone, result)
        return success, error_detail
    except Exception as e:
        logger.error("Error sending SMS to %s: %s", phone, e)
        return False, str(e)
