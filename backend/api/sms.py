# api/sms.py
import logging
import requests
from typing import Optional

from django.conf import settings

from .phone import is_iran_phone, normalize_phone

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


# Outgoing messages are signed at the bottom rather than opened with a
# "نوبت‌یار" header: the recipient sees their own news first, and the footer
# still says who sent it.
SMS_FOOTER = 'نوبت‌یار'

# Rules for MELIPAYAMAK_FROM, which is an *advertising* line (خط تبلیغاتی), per
# their support:
#   - every message must end with this opt-out keyword
#   - links are rejected, and their filter counts a bare full stop as a link
#   - delivery only happens between 08:00 and 22:00 Tehran time
#   - recipients who blocked promotional SMS are never delivered to (shown red
#     in the panel); those are not charged
SMS_OPT_OUT = 'لغو11'
SEND_WINDOW_START_HOUR = 8
SEND_WINDOW_END_HOUR = 22


def _strip_link_like(text: str) -> str:
    """Remove what Melipayamak's filter reads as a link.

    A single full stop is enough to trip it, so sentence-ending dots go — this
    has to run over the whole assembled message, not just our own wording,
    because an interpolated business title can carry one too.
    """
    return (text or '').replace('.', '')


def prepare_text(body: str) -> str:
    """Make a message body acceptable to the advertising line.

    Strips link-like characters and guarantees the mandatory opt-out keyword is
    the last thing in the message. Idempotent, so it is safe to apply again at
    the send boundary to catch anything that skipped :func:`signed`.
    """
    text = _strip_link_like(body).rstrip()
    if not text.endswith(SMS_OPT_OUT):
        text = f'{text}\n{SMS_OPT_OUT}'
    return text


def within_send_window(now=None) -> bool:
    """True when the advertising line will actually deliver right now."""
    from django.utils import timezone

    current = now or timezone.localtime()
    return SEND_WINDOW_START_HOUR <= current.hour < SEND_WINDOW_END_HOUR


def signed(body: str) -> str:
    """Append the standard footer and the mandatory opt-out to a message body.

    Every notification goes through here so the wording lives in exactly one
    place — and so no message can accidentally ship without لغو11.
    """
    return prepare_text(f'{body}\n\n{SMS_FOOTER}')


def send_otp(phone: str) -> Optional[str]:  # به جای str | None
    phone = normalize_phone(phone)
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
    # Normalise at the boundary rather than trusting every caller: a business
    # phone stored as ۰۲۱۳۹۰۹۳۰۹۳ was rejected with «شماره گیرنده نامعتبر است».
    raw_phone = phone
    phone = normalize_phone(phone)
    if phone != raw_phone:
        logger.info("Normalised SMS recipient %r -> %r", raw_phone, phone)

    if not is_iran_phone(phone):
        # Fail before spending a credit on something the provider will reject.
        detail = f'شماره گیرنده معتبر نیست: {raw_phone!r}'
        logger.error("Refusing to send SMS: %s", detail)
        return False, detail

    # Belt and braces: apply the advertising-line rules here too, so a message
    # assembled without signed() cannot ship missing لغو11 or carrying a dot.
    message = prepare_text(message)

    if not within_send_window():
        # The line accepts these and returns «عملیات موفق» with a real recId,
        # then never delivers them. Reporting failure keeps SmsLog honest and
        # refunds the credit instead of recording a delivery that never happened.
        detail = (
            f'خارج از بازه مجاز ارسال ({SEND_WINDOW_START_HOUR}:00 تا '
            f'{SEND_WINDOW_END_HOUR}:00) — پیامک ارسال نشد'
        )
        logger.warning("Refusing to send SMS to %s****: %s", phone[-4:], detail)
        return False, detail

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
