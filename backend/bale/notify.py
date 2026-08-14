"""
bale/notify.py

Pushes a moderation-queue item into the operator's Bale chat.

Threading follows the house pattern for "must not block the response and must
not be able to fail it" (appointment/client_views.py): a ``daemon=True`` thread
handed nothing but primitive ids, re-reading the database on its own connection.
The public entry point wraps that in ``transaction.on_commit``, because two of
its three call sites sit inside ``transaction.atomic`` and a thread that started
early would query a row that is not committed yet — and, on a rollback, would
announce a business that does not exist.

Nothing here raises. A moderation queue entry is the source of truth; the Bale
message is a convenience on top of it, and a chat outage must never cost someone
their business registration.
"""

import logging
import os
import threading

from django.conf import settings
from django.db import transaction

from api.jalali import format_datetime

from .client import send_message, send_photo
from .keyboards import main_keyboard
from .models import BaleSettings

logger = logging.getLogger(__name__)

KIND_NEW = 'new'
KIND_EDIT = 'edit'

_MAX_ADDRESS_CHARS = 80


def notify_review_queued(business, kind=KIND_NEW, changed=None):
    """Announce that ``business`` is (back) in the review queue.

    Safe to call from inside a transaction and from a ``sync_to_async`` hop.
    Returns nothing — callers must not branch on whether the chat got the
    message.
    """
    try:
        business_id = business.pk
        changed = list(changed or [])
        transaction.on_commit(
            lambda: _spawn(business_id, kind, changed)
        )
    except Exception:
        logger.exception('Failed to schedule Bale review notification')


def _spawn(business_id, kind, changed):
    try:
        threading.Thread(
            target=_send,
            args=(business_id, kind, changed),
            daemon=True,
            name=f'bale-review-{business_id}',
        ).start()
    except Exception:
        logger.exception('Failed to start Bale notification thread for %s', business_id)


def _send(business_id, kind, changed):
    """Thread body: load everything fresh, build the message, send it."""
    try:
        config = BaleSettings.load()
        if not config.is_configured:
            return

        from business.models import Business

        business = (
            Business.objects.select_related('user')
            .filter(pk=business_id)
            .first()
        )
        if business is None:
            # Created and deleted inside one request, or rolled back after the
            # on_commit hook was queued.
            logger.info('Bale notification skipped: business %s is gone', business_id)
            return

        send_card(config, business, kind, changed)
    except Exception:
        logger.exception('Bale review notification failed for business %s', business_id)


def send_card(config, business, kind=KIND_NEW, changed=None):
    """Send one reviewable business: its logo, then its details and buttons.

    The logo goes as a separate photo message rather than as a caption on the
    card, because the decision buttons live on the card and a photo message's
    text can only be changed with editMessageCaption — so folding the two
    together would mean the "✅ تأیید شد" rewrite silently fails and the buttons
    stay live after a decision.
    """
    photo = _logo_path(business)
    if photo:
        result = send_photo(config.bot_token, config.chat_id, photo)
        if not result['success']:
            logger.warning(
                'Bale logo not delivered for business %s: %s',
                business.pk, result.get('error'),
            )

    result = send_message(
        config.bot_token,
        config.chat_id,
        build_text(business, kind, changed),
        reply_markup=main_keyboard(business.pk),
    )
    if not result['success']:
        logger.warning(
            'Bale review notification not delivered for business %s: %s',
            business.pk, result.get('error'),
        )
    return result


def _logo_path(business):
    """Local filesystem path of the logo under review, or None.

    Prefers ``pending_logo``: on a staged edit that is the image actually
    awaiting a decision, while ``logo`` still holds the last-approved one.
    """
    for field in ('pending_logo', 'logo'):
        image = getattr(business, field, None)
        if not image:
            continue
        try:
            path = image.path
        except (NotImplementedError, ValueError):
            # Non-filesystem storage, or a name that resolves to nothing.
            continue
        if os.path.exists(path):
            return path
    return None


def fire_owner_sms(business_id, to_status):
    """Send the owner's moderation SMS off the request path.

    ``apply_moderation_decision(notify=True)`` sends it inline, which costs the
    caller a round trip to Melipayamak with a 10s timeout. In the admin that is
    merely slow; in the Bale webhook it is the difference between a button that
    resolves instantly and one that spins until Bale gives up on the delivery
    and retries it.
    """
    def _run():
        try:
            from business.models import Business
            from business.sms_moderation import notify_moderation_decision

            business = Business.objects.select_related('user').filter(
                pk=business_id).first()
            if business is not None:
                notify_moderation_decision(business, to_status)
        except Exception:
            logger.exception('Owner moderation SMS failed for business %s', business_id)

    try:
        threading.Thread(
            target=_run, daemon=True, name=f'bale-sms-{business_id}',
        ).start()
    except Exception:
        logger.exception('Could not start owner SMS thread for %s', business_id)


def build_text(business, kind, changed=None) -> str:
    """The message body. Plain text — no parse_mode, so a business title
    containing ``*`` or ``_`` cannot break the formatting or, worse, be used to
    forge markup in the operator's chat."""
    if kind == KIND_EDIT:
        lines = ['✏️ ویرایش در انتظار بررسی', '']
    else:
        lines = ['🆕 کسب‌وکار جدید در صف بررسی', '']

    lines.append(f'عنوان: {business.title}')
    lines.append(f'دسته: {business.get_category_display()}')
    if business.unique_code:
        lines.append(f'کد: {business.unique_code}')

    owner = getattr(business, 'user', None)
    owner_name = getattr(owner, 'name', '') or ''
    owner_phone = getattr(owner, 'phone', '') or ''
    if owner_name or owner_phone:
        lines.append(f'مالک: {" — ".join(x for x in (owner_name, owner_phone) if x)}')
    if business.phone:
        lines.append(f'تلفن: {business.phone}')

    address = (business.address or '').strip().replace('\n', ' ')
    if address:
        if len(address) > _MAX_ADDRESS_CHARS:
            address = address[:_MAX_ADDRESS_CHARS].rstrip() + '…'
        lines.append(f'آدرس: {address}')

    bio = (business.bio or '').strip().replace('\n', ' ')
    if bio:
        lines.append(f'توضیحات: {bio}')

    lines.append(
        f'ساعت کاری: {business.work_start_hour}–{business.work_end_hour}'
        f'  |  مدت نوبت: {business.default_service_duration} دقیقه'
    )
    lines.append('لوگو: دارد' if _logo_path(business) else 'لوگو: ندارد')

    if changed:
        lines.append(f'تغییرات: {"، ".join(_FIELD_LABELS.get(f, f) for f in changed)}')

    # Staged edits: the live columns still hold the last-approved copy, so the
    # values above are not what needs reviewing. Show the draft too, or the
    # operator would be approving text they never saw.
    staged = _staged_values(business)
    if staged:
        lines.append('')
        lines.append('— متن جدید در انتظار تأیید —')
        lines.extend(staged)

    if business.moderation_submitted_at:
        lines.append('')
        lines.append(f'زمان ثبت در صف: {format_datetime(business.moderation_submitted_at)}')

    admin_url = _admin_url(business.pk)
    if admin_url:
        lines.append(admin_url)

    return '\n'.join(lines)


# Callers hand over Business.MODERATED_FIELDS names, which are English column
# names; showing "تغییرات: title" to the operator would be a leak of schema
# detail into a chat message.
_FIELD_LABELS = {
    'title': 'عنوان',
    'bio': 'توضیحات',
    'address': 'آدرس',
    'logo': 'لوگو',
}

_STAGED_LABELS = {f'pending_{k}': v for k, v in _FIELD_LABELS.items()}


def _staged_values(business):
    out = []
    for field, label in _STAGED_LABELS.items():
        value = getattr(business, field, None)
        if not value:
            continue
        value = str(value).strip().replace('\n', ' ')
        if len(value) > _MAX_ADDRESS_CHARS:
            value = value[:_MAX_ADDRESS_CHARS].rstrip() + '…'
        out.append(f'{label}: {value}')
    return out


def _admin_url(business_id):
    """Deep link to the business's admin change page.

    Returns '' rather than a broken link when SITE_URL is still the localhost
    default, since a link to localhost in a phone chat is worse than none.
    """
    site = (getattr(settings, 'SITE_URL', '') or '').rstrip('/')
    if not site or 'localhost' in site or '127.0.0.1' in site:
        return ''
    admin_path = getattr(settings, 'ADMIN_URL', 'admin/')
    return f'{site}/{admin_path}business/business/{business_id}/change/'
