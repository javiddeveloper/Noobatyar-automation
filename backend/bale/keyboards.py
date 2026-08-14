"""
bale/keyboards.py

Inline keyboards and the ``callback_data`` wire format, kept in one module so
the sender (notify.py) and the receiver (views.py) can never drift apart.

``callback_data`` is capped at 64 bytes by the Bot API, so the encoding is
terse rather than readable:

    m:d:<business_id>:<A|R|S>[:<reason_index>]   apply a decision
    m:q:<business_id>:<R|S>                      open the reason menu
    m:b:<business_id>                            back to the decision buttons

A rejection or suspension always carries a reason index: the admin queue refuses
a rejection with an empty note (business/admin.py), the note is what the owner
receives by SMS (business/sms_moderation.py), and a bot that could bypass that
would leave owners rejected with no stated reason — the exact failure the SMS
notice exists to prevent. Canned reasons buy that guarantee without a
free-text conversation and the short-lived state it would need.
"""

from business.models import Business

# Index is part of the wire format: only ever append, never reorder. A message
# sent before a reorder is still sitting in the chat with the old indices.
REJECT_REASONS = [
    'اطلاعات کسب‌وکار ناقص یا نادرست است',
    'محتوای واردشده نامناسب است',
    'عنوان یا توضیحات نامعتبر است',
    'تصویر یا لوگو نامناسب است',
]

SUSPEND_REASONS = [
    'گزارش تخلف از سوی کاربران',
    'محتوای نامناسب پس از تأیید',
    'درخواست پشتیبانی',
]

STATUS_BY_LETTER = {
    'A': Business.MODERATION_APPROVED,
    'R': Business.MODERATION_REJECTED,
    'S': Business.MODERATION_SUSPENDED,
}

LETTER_BY_STATUS = {v: k for k, v in STATUS_BY_LETTER.items()}

REASONS_BY_LETTER = {
    'R': REJECT_REASONS,
    'S': SUSPEND_REASONS,
}


def main_keyboard(business_id) -> dict:
    """Approve outright, or step into a reason menu for the negative decisions."""
    return {'inline_keyboard': [[
        {'text': '✅ تأیید', 'callback_data': f'm:d:{business_id}:A'},
        {'text': '❌ رد', 'callback_data': f'm:q:{business_id}:R'},
        {'text': '⛔ تعلیق', 'callback_data': f'm:q:{business_id}:S'},
    ]]}


def reason_keyboard(business_id, letter) -> dict:
    """One button per canned reason, plus a way out.

    Reasons go one per row: they are full Persian sentences and three of them
    side by side get elided to nothing on a phone.
    """
    reasons = REASONS_BY_LETTER.get(letter, [])
    rows = [
        [{'text': reason, 'callback_data': f'm:d:{business_id}:{letter}:{index}'}]
        for index, reason in enumerate(reasons)
    ]
    rows.append([{'text': '↩️ انصراف', 'callback_data': f'm:b:{business_id}'}])
    return {'inline_keyboard': rows}


def no_keyboard() -> dict:
    """Explicitly empty — client.edit_message_text keeps the old keyboard on None."""
    return {'inline_keyboard': []}


def parse_callback(data: str):
    """Decode ``callback_data`` into a dict, or None if it is not ours.

    Returns one of::

        {'action': 'decide', 'business_id': int, 'status': str, 'note': str}
        {'action': 'menu',   'business_id': int, 'letter': str}
        {'action': 'back',   'business_id': int}

    Everything is validated here — the payload arrives from the network, and a
    malformed or hand-crafted one must fall out as None rather than reach a
    moderation call.
    """
    if not data or not isinstance(data, str):
        return None

    parts = data.split(':')
    if len(parts) < 3 or parts[0] != 'm':
        return None

    try:
        business_id = int(parts[2])
    except (TypeError, ValueError):
        return None

    kind = parts[1]

    if kind == 'b':
        return {'action': 'back', 'business_id': business_id}

    if kind == 'q':
        letter = parts[3] if len(parts) > 3 else ''
        if letter not in REASONS_BY_LETTER:
            return None
        return {'action': 'menu', 'business_id': business_id, 'letter': letter}

    if kind == 'd':
        letter = parts[3] if len(parts) > 3 else ''
        status = STATUS_BY_LETTER.get(letter)
        if status is None:
            return None

        if letter == 'A':
            return {
                'action': 'decide',
                'business_id': business_id,
                'status': status,
                'note': '',
            }

        reasons = REASONS_BY_LETTER[letter]
        try:
            index = int(parts[4])
            note = reasons[index]
        except (IndexError, TypeError, ValueError):
            # A stale message whose reason list has since shrunk, or a
            # hand-crafted index. Refusing beats rejecting someone with the
            # wrong reason attached.
            return None

        return {
            'action': 'decide',
            'business_id': business_id,
            'status': status,
            'note': note,
        }

    return None
