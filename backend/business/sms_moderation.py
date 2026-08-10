"""
business/sms_moderation.py

Owner-directed SMS for moderation decisions.

**These are never billed to the owner's SMS quota, and that is deliberate.**
Every other client-facing message in the app goes through
``accounting.usage.consume_sms`` first, because it is a message the owner chose
to send to their own customers on their own plan. A moderation notice is the
opposite: it is platform-initiated, the owner never asked for it, and it is the
only channel telling them why their business went offline. Charging it would
mean (a) billing someone for the privilege of being rejected, and (b) an owner
with an exhausted quota silently never finding out — which is exactly the
failure this whole feature exists to prevent.

So: do not "fix" these senders into calling consume_sms.

For the same reason they are not written to ``visitor.SmsLog``. That log exists
to reconcile against what the owner was charged; unbilled platform notices in it
would make the SMS report overcount every decision.
"""

import logging

from api.sms import send_sms, signed, within_send_window

logger = logging.getLogger(__name__)

# The advertising line rejects links and counts a bare full stop as one, and
# every message must carry لغو11 — signed() handles both, so all wording below
# goes through it and none of it contains a '.'.

# Melipayamak bills per 70-character Persian part, and the reviewer's note is
# free text an admin typed into a TextField. Left unbounded, one long rejection
# reason turns a one-part notice into six. The owner gets the gist plus a
# pointer to the app, which holds the full text via moderation_note in the API.
MAX_NOTE_CHARS = 120


def _trim_note(note: str) -> str:
    note = (note or '').strip()
    if len(note) <= MAX_NOTE_CHARS:
        return note
    return note[:MAX_NOTE_CHARS].rstrip() + '…'


def _owner_phone(business):
    """The number that actually reaches the owner.

    Not ``business.phone`` — that is the display number clients call and is
    routinely a landline. Owner notices go to the phone the account was
    registered with, same rule as the booking notification.
    """
    return getattr(getattr(business, 'user', None), 'phone', None)


def approved_text(business) -> str:
    return signed(
        f'✅ کسب‌وکار «{business.title}» تأیید شد\n'
        f'از این پس در نوبت‌یار قابل مشاهده است و مشتریان می‌توانند نوبت بگیرند'
    )


def rejected_text(business) -> str:
    reason = _trim_note(business.moderation_note)
    body = f'❌ کسب‌وکار «{business.title}» تأیید نشد'
    if reason:
        body += f'\nدلیل: {reason}'
    body += '\nمی‌توانید اطلاعات را در اپلیکیشن اصلاح کنید تا دوباره بررسی شود'
    return signed(body)


def suspended_text(business) -> str:
    reason = _trim_note(business.moderation_note)
    body = f'⛔ کسب‌وکار «{business.title}» موقتاً از دسترس عموم خارج شد'
    if reason:
        body += f'\nدلیل: {reason}'
    body += '\nبرای پیگیری با پشتیبانی نوبت‌یار تماس بگیرید'
    return signed(body)


_TEXT_BUILDERS = {
    'APPROVED': approved_text,
    'REJECTED': rejected_text,
    'SUSPENDED': suspended_text,
}


def notify_moderation_decision(business, to_status) -> bool:
    """Text the owner about a decision. Returns True if the provider took it.

    Never raises: a provider outage must not roll back or block a moderation
    decision that is already committed. Everything is caught and logged.

    PENDING is intentionally not notified — it is either "we received your
    business" (already obvious from the app, which now shows the status) or the
    automatic re-review after the owner edited a moderated field, and texting
    someone about their own edit is noise.
    """
    builder = _TEXT_BUILDERS.get(to_status)
    if builder is None:
        return False

    phone = _owner_phone(business)
    if not phone:
        logger.warning(
            'Moderation SMS skipped for business %s: owner has no phone', business.pk
        )
        return False

    try:
        message = builder(business)
    except Exception:
        logger.exception('Moderation SMS text build failed for business %s', business.pk)
        return False

    if not within_send_window():
        # send_sms refuses outside 08:00–22:00 anyway; logging it here names the
        # business, so a decision made at midnight is traceable to a notice the
        # owner never got rather than looking like a silent drop.
        logger.warning(
            'Moderation SMS for business %s (%s) falls outside the send window',
            business.pk, to_status,
        )

    try:
        ok, err = send_sms(phone, message)
    except Exception:
        logger.exception('Moderation SMS failed for business %s', business.pk)
        return False

    if not ok:
        # No refund call here on purpose — nothing was consumed to refund.
        logger.warning(
            'Moderation SMS (%s) not delivered for business %s: %s',
            to_status, business.pk, err,
        )
    return bool(ok)
