"""
bale/views.py

The inbound half: Bale POSTs an Update here when the operator taps a button.

Authentication, and why it looks like this
------------------------------------------
Bale does not sign webhook deliveries — there is no HMAC header to verify, and
this project has no existing signing pattern to reuse. So the endpoint stacks
two independent checks:

1. **An unguessable path segment.** ``webhook_secret`` is 48 random characters
   minted by BaleSettings.save() and only ever travels inside the HTTPS URL
   handed to setWebhook. A wrong secret is a 404, not a 403, so probing cannot
   confirm the endpoint exists.
2. **Sender identity.** The tap must come from the configured ``chat_id``.
   This is the check that still holds if the URL leaks — through a proxy log, a
   backup, or a screenshot — because leaking the URL does not give an attacker
   the operator's Bale account.

Neither check alone is enough: (1) protects against someone who knows the chat
id, (2) against someone who knows the URL.

Always answers 200. Bale, like Telegram, retries a failing webhook and would
otherwise replay a decision that already landed.
"""

import hmac
import json
import logging

from django.http import Http404
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from business.models import Business
from business.services import apply_moderation_decision

from .client import answer_callback_query, edit_message_text
from .keyboards import (
    LETTER_BY_STATUS,
    main_keyboard,
    no_keyboard,
    parse_callback,
    reason_keyboard,
)
from .models import BaleSettings
from .notify import build_text

logger = logging.getLogger(__name__)

_STATUS_LABEL = {
    Business.MODERATION_APPROVED: '✅ تأیید شد',
    Business.MODERATION_REJECTED: '❌ رد شد',
    Business.MODERATION_SUSPENDED: '⛔ تعلیق شد',
}


@api_view(['POST'])
@permission_classes([AllowAny])
def webhook(request, secret):
    """Entry point for Bale updates. Never raises, never returns non-200."""
    config = BaleSettings.load()

    if not config.is_configured:
        raise Http404

    # compare_digest rather than == : the comparison is against a secret, and a
    # timing oracle on a 48-char value is worth closing even though the attack
    # is impractical over the network.
    if not hmac.compare_digest(str(secret), str(config.webhook_secret)):
        logger.warning('Bale webhook called with a bad secret')
        raise Http404

    try:
        _handle(config, request.data)
    except Exception:
        logger.exception('Bale webhook handler failed')

    return Response({'ok': True})


def _handle(config, payload):
    if not isinstance(payload, dict):
        return

    query = payload.get('callback_query')
    if not isinstance(query, dict):
        # Plain messages, joins, etc. The bot is not conversational.
        return

    query_id = query.get('id')
    sender_id = str((query.get('from') or {}).get('id', ''))

    if not sender_id or not hmac.compare_digest(sender_id, str(config.chat_id)):
        logger.warning(
            'Bale callback from unauthorised sender %r ignored', sender_id
        )
        if query_id:
            answer_callback_query(
                config.bot_token, query_id, 'شما اجازه‌ی این کار را ندارید', True
            )
        return

    parsed = parse_callback(query.get('data'))
    if parsed is None:
        if query_id:
            answer_callback_query(config.bot_token, query_id, 'دستور نامعتبر است', True)
        return

    message = query.get('message') or {}
    message_id = message.get('message_id')

    business = Business.objects.select_related('user').filter(
        pk=parsed['business_id']
    ).first()
    if business is None:
        if query_id:
            answer_callback_query(
                config.bot_token, query_id, 'این کسب‌وکار دیگر وجود ندارد', True
            )
        return

    action = parsed['action']
    if action == 'menu':
        _show_reasons(config, query_id, message_id, business, parsed['letter'])
    elif action == 'back':
        _show_main(config, query_id, message_id, business)
    elif action == 'decide':
        _decide(config, query_id, message_id, business, parsed['status'], parsed['note'])


def _show_reasons(config, query_id, message_id, business, letter):
    if message_id:
        edit_message_text(
            config.bot_token, config.chat_id, message_id,
            build_text(business, _kind_for(business)) + '\n\nدلیل را انتخاب کنید:',
            reply_markup=reason_keyboard(business.pk, letter),
        )
    if query_id:
        answer_callback_query(config.bot_token, query_id)


def _show_main(config, query_id, message_id, business):
    if message_id:
        edit_message_text(
            config.bot_token, config.chat_id, message_id,
            build_text(business, _kind_for(business)),
            reply_markup=main_keyboard(business.pk),
        )
    if query_id:
        answer_callback_query(config.bot_token, query_id)


def _decide(config, query_id, message_id, business, to_status, note):
    # Replay guard. Deliberately based on the row's own status rather than a
    # cache key: django-redis runs with IGNORE_EXCEPTIONS=True, so during a
    # Redis outage every cache.get returns None and a cache-based guard would
    # fail open — the one moment it needs to hold.
    if business.moderation_status == to_status:
        if query_id:
            answer_callback_query(
                config.bot_token, query_id, 'این تصمیم قبلاً ثبت شده است', True
            )
        _finalise_message(config, message_id, business, to_status, note, already=True)
        return

    notified = apply_moderation_decision(
        business, to_status, config.actor, note=note, notify=True
    )

    label = _STATUS_LABEL.get(to_status, to_status)
    toast = label if notified else f'{label} (پیامک ارسال نشد)'
    if query_id:
        answer_callback_query(config.bot_token, query_id, toast)

    _finalise_message(config, message_id, business, to_status, note, sms_sent=notified)


def _finalise_message(config, message_id, business, to_status, note,
                      already=False, sms_sent=True):
    """Replace the buttons with the outcome, so the message cannot be re-tapped
    and the chat reads as a decision log."""
    if not message_id:
        return

    lines = [f'{_STATUS_LABEL.get(to_status, to_status)} — {business.title}']
    if note:
        lines.append(f'دلیل: {note}')
    if already:
        lines.append('(این تصمیم قبلاً ثبت شده بود)')
    elif not sms_sent:
        lines.append('(پیامک اطلاع‌رسانی به مالک ارسال نشد)')

    edit_message_text(
        config.bot_token, config.chat_id, message_id,
        '\n'.join(lines),
        reply_markup=no_keyboard(),
    )


def _kind_for(business):
    """Rebuilding the original text needs the same kind it was sent with, which
    is not stored. ``first_approved_at`` is the closest honest proxy: a business
    that has cleared review before can only be here because of an edit."""
    from .notify import KIND_EDIT, KIND_NEW

    return KIND_EDIT if business.first_approved_at else KIND_NEW
