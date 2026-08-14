"""
bale/client.py

Thin HTTP layer over the Bale Bot API (https://tapi.bale.ai/bot<TOKEN>/), which
is a clone of Telegram's Bot API — same method names, same inline-keyboard and
callback_query shapes.

Follows the house convention for outbound calls (accounting/payment/*.py):
sync ``httpx.Client``, a 10s timeout, ``raise_for_status()``, catch
``httpx.HTTPError``, and return a ``{'success': bool, ...}`` dict.

**Nothing here raises.** Every caller is either a fire-and-forget notification
thread or a webhook that must answer 200 regardless, so an exception escaping
this module would only ever turn a cosmetic failure into a real one.
"""

import logging

import httpx

logger = logging.getLogger(__name__)

BASE_URL = 'https://tapi.bale.ai'
TIMEOUT = 10.0


def _call(token: str, method: str, payload: dict) -> dict:
    """POST one Bot API method. Returns ``{'success': bool, 'result'|'error'}``."""
    if not token:
        return {'success': False, 'error': 'bot token is not configured'}

    url = f'{BASE_URL}/bot{token}/{method}'
    try:
        with httpx.Client() as client:
            response = client.post(url, json=payload, timeout=TIMEOUT)
            response.raise_for_status()
            data = response.json()
    except httpx.HTTPError as e:
        # The token is in the URL, so never log the URL itself.
        logger.error('Bale %s failed: %s', method, e)
        return {'success': False, 'error': str(e)}
    except ValueError as e:
        logger.error('Bale %s returned non-JSON: %s', method, e)
        return {'success': False, 'error': 'invalid response'}

    if not data.get('ok'):
        # Bale mirrors Telegram's envelope: {"ok": false, "description": "..."}
        error = data.get('description') or 'unknown error'
        logger.warning('Bale %s rejected: %s', method, error)
        return {'success': False, 'error': error}

    return {'success': True, 'result': data.get('result')}


def send_message(token: str, chat_id: str, text: str, reply_markup: dict = None) -> dict:
    payload = {'chat_id': chat_id, 'text': text}
    if reply_markup is not None:
        payload['reply_markup'] = reply_markup
    return _call(token, 'sendMessage', payload)


def edit_message_text(token: str, chat_id: str, message_id: int, text: str,
                      reply_markup: dict = None) -> dict:
    """Rewrite an already-sent message.

    Used to swap the decision buttons for the outcome, so a message can never be
    acted on twice from the UI. Passing ``reply_markup=None`` leaves the old
    keyboard in place, so callers that want the buttons *gone* must pass an
    empty ``{'inline_keyboard': []}``.
    """
    payload = {'chat_id': chat_id, 'message_id': message_id, 'text': text}
    if reply_markup is not None:
        payload['reply_markup'] = reply_markup
    return _call(token, 'editMessageText', payload)


def answer_callback_query(token: str, callback_query_id: str, text: str = '',
                          show_alert: bool = False) -> dict:
    """Dismiss the button's spinner and optionally show a toast.

    Bale (like Telegram) leaves the button spinning for the user until this is
    called, so it runs even on the failure paths.
    """
    return _call(token, 'answerCallbackQuery', {
        'callback_query_id': callback_query_id,
        'text': text[:200],
        'show_alert': show_alert,
    })


def set_webhook(token: str, url: str) -> dict:
    return _call(token, 'setWebhook', {'url': url})


def delete_webhook(token: str) -> dict:
    return _call(token, 'deleteWebhook', {})


def get_me(token: str) -> dict:
    return _call(token, 'getMe', {})
