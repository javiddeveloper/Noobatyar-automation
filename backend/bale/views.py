"""
bale/views.py

The inbound half: Bale POSTs an Update here when the operator taps a button or
sends the bot a message.

Authentication, and why it looks like this
------------------------------------------
Bale does not sign webhook deliveries — there is no HMAC header to verify, and
this project has no existing signing pattern to reuse. So the endpoint stacks
two independent checks:

1. **An unguessable path segment.** ``webhook_secret`` is 48 random characters
   minted by BaleSettings.save() and only ever travels inside the HTTPS URL
   handed to setWebhook. A wrong secret is a 404, not a 403, so probing cannot
   confirm the endpoint exists.
2. **Sender identity.** The update must come from the configured ``chat_id``.
   This is the check that still holds if the URL leaks — through a proxy log, a
   backup, or a screenshot — because leaking the URL does not give an attacker
   the operator's Bale account.

Neither check alone is enough: (1) protects against someone who knows the chat
id, (2) against someone who knows the URL.

**Everything on this path must be fast.** Bale keeps the tapped button in a
loading state until answerCallbackQuery arrives, and retries the whole delivery
if the webhook is slow to return — so the owner's notification SMS is fired on a
background thread rather than awaited inline. It used to be awaited, and a
Melipayamak round trip with a 10s timeout was long enough to leave the button
spinning and the update redelivered.

Always answers 200, so a handler bug cannot turn into an infinite retry loop.
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

from .client import answer_callback_query, edit_message_text, send_message
from .keyboards import (
    main_keyboard,
    no_keyboard,
    parse_callback,
    reason_keyboard,
)
from .models import BaleSettings, PendingReason
from .notify import KIND_EDIT, KIND_NEW, build_text, fire_owner_sms, send_card

logger = logging.getLogger(__name__)

# One message per business, so each keeps its own buttons. Beyond this the chat
# becomes unusable and the operator should work in the admin queue instead.
MAX_LISTED = 10

_STATUS_LABEL = {
    Business.MODERATION_APPROVED: '✅ تأیید شد',
    Business.MODERATION_REJECTED: '❌ رد شد',
    Business.MODERATION_SUSPENDED: '⛔ تعلیق شد',
}

_PROMPT = {
    Business.MODERATION_REJECTED: 'دلیل رد را بنویس و همین‌جا بفرست:',
    Business.MODERATION_SUSPENDED: 'دلیل تعلیق را بنویس و همین‌جا بفرست:',
}

HELP_TEXT = (
    'ربات بررسی نوبت‌یار\n\n'
    '/pending — لیست کسب‌وکارهای در انتظار تأیید\n'
    '/help — همین راهنما\n\n'
    'هر کسب‌وکار جدید یا ویرایش‌شده خودکار همین‌جا اعلام می‌شود'
)


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


def _authorised(config, sender_id) -> bool:
    return bool(sender_id) and hmac.compare_digest(str(sender_id), str(config.chat_id))


def _handle(config, payload):
    if not isinstance(payload, dict):
        return

    query = payload.get('callback_query')
    if isinstance(query, dict):
        return _handle_callback(config, query)

    message = payload.get('message')
    if isinstance(message, dict):
        return _handle_message(config, message)


# ── button taps ─────────────────────────────────────────────────────────────

def _handle_callback(config, query):
    query_id = query.get('id')
    sender_id = str((query.get('from') or {}).get('id', ''))

    if not _authorised(config, sender_id):
        logger.warning('Bale callback from unauthorised sender %r ignored', sender_id)
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

    message_id = (query.get('message') or {}).get('message_id')

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
    elif action == 'ask_reason':
        _ask_reason(config, query_id, message_id, business, parsed['status'])
    elif action == 'decide':
        _decide(config, query_id, message_id, business, parsed['status'], parsed['note'])


def _show_reasons(config, query_id, message_id, business, letter):
    if query_id:
        answer_callback_query(config.bot_token, query_id)
    if message_id:
        edit_message_text(
            config.bot_token, config.chat_id, message_id,
            build_text(business, _kind_for(business)) + '\n\nدلیل را انتخاب کن:',
            reply_markup=reason_keyboard(business.pk, letter),
        )


def _show_main(config, query_id, message_id, business):
    if query_id:
        answer_callback_query(config.bot_token, query_id)
    # Backing out abandons any half-finished typed reason, or the next message
    # the operator sends would be swallowed as a reason for a decision they
    # just cancelled.
    PendingReason.objects.filter(chat_id=str(config.chat_id)).delete()
    if message_id:
        edit_message_text(
            config.bot_token, config.chat_id, message_id,
            build_text(business, _kind_for(business)),
            reply_markup=main_keyboard(business.pk),
        )


def _ask_reason(config, query_id, message_id, business, to_status):
    """Park the decision and wait for the operator to type the note."""
    if query_id:
        answer_callback_query(config.bot_token, query_id, 'دلیل را بنویس')

    PendingReason.open(config.chat_id, business, to_status, message_id)

    send_message(
        config.bot_token, config.chat_id,
        f'{_PROMPT.get(to_status, "دلیل را بنویس:")}\n'
        f'«{business.title}»\n\n'
        'برای انصراف /cancel را بفرست',
    )


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

    # notify=False: the owner's SMS is fired below on its own thread. Awaiting
    # it here is what used to leave the button spinning.
    apply_moderation_decision(business, to_status, config.actor, note=note, notify=False)

    if query_id:
        answer_callback_query(
            config.bot_token, query_id, _STATUS_LABEL.get(to_status, to_status)
        )

    fire_owner_sms(business.pk, to_status)
    _finalise_message(config, message_id, business, to_status, note)


def _finalise_message(config, message_id, business, to_status, note, already=False):
    """Replace the buttons with the outcome, so the message cannot be re-tapped
    and the chat reads as a decision log."""
    if not message_id:
        return

    lines = [f'{_STATUS_LABEL.get(to_status, to_status)} — {business.title}']
    if note:
        lines.append(f'دلیل: {note}')
    if already:
        lines.append('(این تصمیم قبلاً ثبت شده بود)')

    edit_message_text(
        config.bot_token, config.chat_id, message_id,
        '\n'.join(lines),
        reply_markup=no_keyboard(),
    )


# ── plain messages ──────────────────────────────────────────────────────────

def _handle_message(config, message):
    sender_id = str((message.get('from') or {}).get('id', ''))
    if not _authorised(config, sender_id):
        logger.warning('Bale message from unauthorised sender %r ignored', sender_id)
        return

    text = (message.get('text') or '').strip()
    if not text:
        return

    command = text.split()[0].lower().split('@')[0]

    if command in ('/start', '/help'):
        PendingReason.objects.filter(chat_id=str(config.chat_id)).delete()
        send_message(config.bot_token, config.chat_id, HELP_TEXT)
        return

    if command == '/cancel':
        cancelled = PendingReason.take(config.chat_id)
        send_message(
            config.bot_token, config.chat_id,
            'انصراف داده شد' if cancelled else 'چیزی برای انصراف نبود',
        )
        return

    if command in ('/pending', '/list'):
        _send_pending_list(config)
        return

    # Not a command: the only thing a free message can mean is the reason for a
    # decision the operator parked with "دلیل دلخواه".
    _apply_typed_reason(config, text)


def _apply_typed_reason(config, text):
    pending = PendingReason.take(config.chat_id)
    if pending is None:
        send_message(
            config.bot_token, config.chat_id,
            'دستور را نشناختم\n\n' + HELP_TEXT,
        )
        return

    business = Business.objects.select_related('user').filter(
        pk=pending.business_id
    ).first()
    if business is None:
        send_message(config.bot_token, config.chat_id, 'این کسب‌وکار دیگر وجود ندارد')
        return

    if business.moderation_status == pending.to_status:
        send_message(
            config.bot_token, config.chat_id, 'این تصمیم قبلاً ثبت شده است'
        )
        return

    apply_moderation_decision(
        business, pending.to_status, config.actor, note=text, notify=False
    )
    fire_owner_sms(business.pk, pending.to_status)
    _finalise_message(config, pending.message_id, business, pending.to_status, text)

    send_message(
        config.bot_token, config.chat_id,
        f'{_STATUS_LABEL.get(pending.to_status, pending.to_status)} — {business.title}\n'
        f'دلیل: {text}',
    )


def _send_pending_list(config):
    """Every business waiting on a decision, oldest first.

    Same ordering as the admin queue (business/admin.py): whoever has been
    waiting longest for *this* decision comes first.
    """
    queryset = Business.objects.select_related('user').filter(
        moderation_status=Business.MODERATION_PENDING
    ).order_by('moderation_submitted_at', 'pk')

    total = queryset.count()
    if not total:
        send_message(
            config.bot_token, config.chat_id, '✅ هیچ کسب‌وکاری در انتظار بررسی نیست'
        )
        return

    header = f'📋 {total} کسب‌وکار در انتظار بررسی'
    if total > MAX_LISTED:
        header += f'\n({MAX_LISTED} مورد قدیمی‌تر نمایش داده می‌شود)'
    send_message(config.bot_token, config.chat_id, header)

    for business in queryset[:MAX_LISTED]:
        send_card(config, business, _kind_for(business))


def _kind_for(business):
    """Rebuilding the original text needs the same kind it was sent with, which
    is not stored. ``first_approved_at`` is the closest honest proxy: a business
    that has cleared review before can only be here because of an edit."""
    return KIND_EDIT if business.first_approved_at else KIND_NEW
