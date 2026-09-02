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

from core.models import AdminMessageLog, MarketingPushLog

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


# ── Promotional campaigns ────────────────────────────────────────────────────
# One code path for both audiences. The *only* thing that differs between
# pushing to customers and pushing to business owners is which table the
# recipients and their device tokens come from, so that difference is isolated
# to _visitor_recipients/_owner_recipients below and everything after it — the
# payload, the personalization, the loop, the counters, the audit row — is
# literally the same code. Two parallel implementations is exactly how the two
# audiences would end up supporting different features.


def _visitor_recipients(filters, exclude_opted_out):
    """``(matched, reachable)`` for the customer audience.

    ``matched`` is everyone the filter selected; ``reachable`` is the subset
    with at least one active device token, as ``(id, display_name)`` pairs.
    Both are returned because the gap between them — people who never granted
    notification permission — is the most useful number on the report, and it
    is unrecoverable once you only count the ones you could reach.
    """
    from core import segments

    qs = segments.visitor_queryset(filters, exclude_opted_out=exclude_opted_out)
    matched = qs.count()
    reachable = list(
        qs.filter(device_tokens__is_active=True)
        .distinct()
        .values_list('id', 'full_name')
    )
    return matched, reachable


def _owner_recipients(filters, exclude_opted_out=None):
    """``(matched, reachable)`` for the business-owner audience.

    ``exclude_opted_out`` is accepted and ignored so the two recipient
    functions stay interchangeable: ``Visitor.marketing_opt_out`` has no
    owner-side equivalent (see core/segments.py — owners have no marketing
    consent flag), and silently accepting the argument is clearer than making
    the caller special-case which audience takes it.
    """
    from api.models import User
    from core import segments

    owner_ids = segments.owner_ids_for_segment(filters)
    reachable = list(
        User.objects.filter(id__in=owner_ids, device_tokens__is_active=True)
        .distinct()
        .values_list('id', 'name')
    )
    return len(owner_ids), reachable


#: audience key → (recipient lookup, push delivery function). Adding a third
#: audience later means adding a row here, not a third branch in the sender.
_AUDIENCE_DISPATCH = {
    MarketingPushLog.AUDIENCE_VISITOR: _visitor_recipients,
    MarketingPushLog.AUDIENCE_OWNER: _owner_recipients,
}


def _deliver(audience, recipient_id, campaign):
    from api.services import push

    if audience == MarketingPushLog.AUDIENCE_VISITOR:
        return push.deliver_to_visitor(
            recipient_id, title=campaign.title, body=campaign.body,
            data=campaign.data_payload(), image=campaign.image_url or None,
            link=campaign.link or None,
        )
    return push.deliver_to_user(
        recipient_id, title=campaign.title, body=campaign.body,
        data=campaign.data_payload(), image=campaign.image_url or None,
        link=campaign.link or None,
    )


def send_push_campaign(campaign, audience, filters, definition, *,
                       group_id=None, exclude_opted_out=True, actor=None) -> MarketingPushLog:
    """
    Send ``campaign`` (a :class:`core.campaigns.PushCampaign`) to everyone in
    one audience matching ``filters``, and record the result.

    Recipients without an active device token are skipped rather than failed —
    there is nothing to send to, same as any other channel someone hasn't
    opted into — but they are still counted in ``recipient_count`` so the
    report can show how much of the audience is simply unreachable.

    ``{full_name}`` in the title/body is replaced per recipient; see
    :meth:`PushCampaign.personalized`.

    A synchronous loop, same as the reminder job and every other admin bulk
    action in this codebase — acceptable for the segment sizes this admin
    panel deals with today. A genuinely large campaign (tens of thousands of
    recipients) would need this moved to a background task instead of a
    request/response cycle; that is a real scaling note, not something this
    function tries to solve.
    """
    import uuid

    from api.services import push
    from api.services.push import SendResult

    lookup = _AUDIENCE_DISPATCH[audience]
    matched, reachable = lookup(filters, exclude_opted_out)

    totals = SendResult()
    if not push.is_configured():
        if reachable:
            logger.warning(
                'Campaign skipped for %d reachable %s — FCM not configured',
                len(reachable), audience,
            )
    else:
        for recipient_id, display_name in reachable:
            try:
                totals += _deliver(audience, recipient_id, campaign.personalized(display_name))
            except Exception:
                logger.exception('Campaign push to %s %s failed', audience, recipient_id)
                totals.failed += 1

    return MarketingPushLog.objects.create(
        group_id=group_id or uuid.uuid4(),
        audience=audience,
        definition=definition,
        title=campaign.title,
        body=campaign.body,
        image_url=campaign.image_url,
        link=campaign.link,
        deep_link=campaign.deep_link,
        recipient_count=matched,
        reachable_count=len(reachable),
        delivered_count=totals.delivered,
        failed_count=totals.failed,
        dead_token_count=totals.dead,
        sent_by=actor,
    )
