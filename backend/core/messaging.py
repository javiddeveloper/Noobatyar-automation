# core/messaging.py
"""
Platform-initiated messaging to a business owner — renewal reminders and
other ops notices staff send from the business's own admin page
(NobatyarAdminSite.business_message_view), as opposed to anything the owner
or a customer triggers themselves.

── Funding ──────────────────────────────────────────────────────────────────

SMS here goes through ``api.sms.send_sms`` directly, never
``accounting.usage.consume_sms``: that function specifically debits the
*owner's own* paid monthly quota/wallet (see appointment/management/commands/
send_appointment_reminders.py's client-SMS path for the only other place that
matters). A staff-initiated "your subscription is expiring" notice is a
platform cost, not something to bill the owner for receiving — charging their
own quota for a message they didn't choose to send would be a real billing
bug, not a rounding error.

Push is free either way, so it goes through the same
``api.services.push.send_to_user`` every other owner-push call site uses —
nothing special there.

Every attempt — one row per (business, channel), success or failure — is
logged to ``core.models.AdminMessageLog`` for audit; see that model's
docstring for why it is not folded into ``visitor.SmsLog``.
"""

import logging

from core.models import AdminMessageLog

logger = logging.getLogger(__name__)


def _personalize(text: str, business) -> str:
    """Replaces the one supported template token. Kept to a single token on
    purpose — a compose box is not a template engine, and staff already write
    these by hand per send; more tokens can be added if a real need shows up."""
    return (text or '').replace('{business_name}', business.title)


def send_admin_message(business, title: str, body: str, channels, actor=None) -> list[AdminMessageLog]:
    """
    Sends a personalized message to ``business``'s owner on each channel in
    ``channels`` (an iterable of ``'PUSH'``/``'SMS'``), logging every attempt.

    Returns the created :class:`AdminMessageLog` rows, so the calling view can
    report success/failure per channel without a second query.
    """
    personalized_title = _personalize(title, business)
    personalized_body = _personalize(body, business)
    logs = []

    if 'PUSH' in channels:
        logs.append(_send_push(business, personalized_title, personalized_body, actor))
    if 'SMS' in channels:
        logs.append(_send_sms(business, personalized_body, actor))

    return logs


def _send_push(business, title, body, actor) -> AdminMessageLog:
    from api.services import push

    if not push.is_configured():
        return AdminMessageLog.objects.create(
            business=business, sent_by=actor, channel='PUSH',
            title=title, body=body, status='FAILED',
            error_detail='سرویس اعلان (FCM) پیکربندی نشده است',
        )

    try:
        delivered = push.send_to_user(business.user_id, title=title, body=body)
    except Exception as exc:
        logger.exception('Admin push to business %s failed', business.id)
        return AdminMessageLog.objects.create(
            business=business, sent_by=actor, channel='PUSH',
            title=title, body=body, status='FAILED', error_detail=str(exc),
        )

    return AdminMessageLog.objects.create(
        business=business, sent_by=actor, channel='PUSH',
        title=title, body=body,
        status='SENT' if delivered else 'FAILED',
        error_detail='' if delivered else 'دستگاه فعالی برای این صاحب کسب‌وکار ثبت نشده است',
    )


def _send_sms(business, body, actor) -> AdminMessageLog:
    from api.sms import send_sms, signed

    phone = business.user.phone if business.user_id else ''
    if not phone:
        return AdminMessageLog.objects.create(
            business=business, sent_by=actor, channel='SMS',
            title='', body=body, status='FAILED',
            error_detail='این کسب‌وکار شماره تلفن مالک ندارد',
        )

    ok, err = send_sms(phone, signed(body))
    return AdminMessageLog.objects.create(
        business=business, sent_by=actor, channel='SMS',
        title='', body=body,
        status='SENT' if ok else 'FAILED',
        error_detail='' if ok else err,
    )
