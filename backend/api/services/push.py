"""
api/services/push.py

Firebase Cloud Messaging (HTTP v1) sender for owner-facing push notifications.

Why HTTP v1 and not the old ``/fcm/send`` legacy endpoint: legacy server keys
were turned off by Google in 2024, so a server key in the environment would
simply 404. v1 authenticates with a *service account*, which means a short-lived
OAuth token minted from a private key rather than a long-lived shared secret.

Configuration (see DEPLOYMENT.md / core/settings.py):

    FCM_CREDENTIALS_FILE   absolute path to the service-account JSON downloaded
                           from Firebase console → Project settings → Service
                           accounts → Generate new private key
    FCM_PROJECT_ID         the Firebase project id; read from the JSON file when
                           left unset, so normally there is nothing to set

Everything here fails soft. A push is a convenience channel sitting next to the
SMS that actually carries the message, so a missing credentials file or a dead
FCM must never turn a reminder run into a traceback — it logs and returns False.
"""

import json
import logging
import threading

import requests
from django.conf import settings

logger = logging.getLogger(__name__)

FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging'
FCM_ENDPOINT = 'https://fcm.googleapis.com/v1/projects/{project_id}/messages:send'

# Credentials are cached process-wide: minting them re-reads the key file and
# google-auth refreshes the access token on its own when it expires.
_credentials = None
_credentials_lock = threading.Lock()


class PushNotConfigured(RuntimeError):
    """Raised when no FCM service account is configured."""


def _credentials_path() -> str:
    path = (getattr(settings, 'FCM_CREDENTIALS_FILE', '') or '').strip()
    if not path:
        raise PushNotConfigured(
            'FCM_CREDENTIALS_FILE is not set — cannot send push notifications. '
            'Download the service-account JSON from the Firebase console and '
            'point this at it (see DEPLOYMENT.md).'
        )
    return path


def _load_credentials():
    """The cached service-account credentials, minted on first use."""
    global _credentials
    if _credentials is not None:
        return _credentials
    with _credentials_lock:
        if _credentials is not None:
            return _credentials
        try:
            from google.oauth2 import service_account
        except ImportError as exc:  # pragma: no cover - deployment error
            raise PushNotConfigured(
                'google-auth is not installed — add it to requirements_prod.txt'
            ) from exc
        _credentials = service_account.Credentials.from_service_account_file(
            _credentials_path(), scopes=[FCM_SCOPE]
        )
        return _credentials


def _project_id() -> str:
    """The Firebase project id, from settings or from the key file itself."""
    configured = (getattr(settings, 'FCM_PROJECT_ID', '') or '').strip()
    if configured:
        return configured
    with open(_credentials_path(), encoding='utf-8') as handle:
        project_id = json.load(handle).get('project_id', '')
    if not project_id:
        raise PushNotConfigured(
            'FCM_PROJECT_ID is not set and the service-account file carries no '
            'project_id'
        )
    return project_id


def _access_token() -> str:
    from google.auth.transport.requests import Request

    credentials = _load_credentials()
    if not credentials.valid:
        credentials.refresh(Request())
    return credentials.token


def is_configured() -> bool:
    """True when a push could actually be attempted. Never raises."""
    try:
        _credentials_path()
    except PushNotConfigured:
        return False
    return True


def send_to_token(token: str, title: str, body: str, data: dict | None = None) -> tuple[bool, str]:
    """
    Deliver one notification to one device.

    Returns ``(ok, detail)``. ``detail`` carries the FCM error code on failure,
    so callers can react to ``UNREGISTERED`` (the app was uninstalled or the
    token was rotated) by deactivating the row rather than retrying forever.

    ``data`` values must be strings — FCM rejects a data payload containing
    numbers or booleans — so everything is stringified here rather than at each
    call site.
    """
    try:
        url = FCM_ENDPOINT.format(project_id=_project_id())
        headers = {
            'Authorization': f'Bearer {_access_token()}',
            'Content-Type': 'application/json; UTF-8',
        }
    except PushNotConfigured as exc:
        logger.error('%s', exc)
        return False, str(exc)
    except Exception as exc:
        logger.error('FCM credentials could not be loaded: %s', exc)
        return False, str(exc)

    message = {
        'message': {
            'token': token,
            'notification': {'title': title, 'body': body},
            'data': {str(k): str(v) for k, v in (data or {}).items()},
            # Persian bodies wrap badly in a single-line notification, and the
            # owner app needs a channel that exists on Android 8+ or the system
            # drops the notification silently.
            'android': {
                'priority': 'high',
                'notification': {
                    'channel_id': getattr(settings, 'FCM_ANDROID_CHANNEL_ID', 'appointment_reminders'),
                },
            },
            'apns': {
                'payload': {'aps': {'sound': 'default'}},
            },
            # Owner web panel (docs/OWNER_WEB_PLAN.md ۱۰.۱): DeviceToken already
            # accepts platform=WEB and send_to_user() fans out to every active
            # token regardless of platform, so a WEB row reaches this branch too.
            # Without this block the notification still shows — the top-level
            # `notification` above covers that — but bare, with the browser's
            # generic icon and no click destination.
            # FCM_ANDROID_CHANNEL_ID is Android-only (it names a channel created
            # by ProQueueApp.onCreate, see NOTIFICATIONS.md ۴); webpush has no
            # notion of a channel, so it is not reused here.
            # Icon/badge are given as paths, not full URLs: the browser resolves
            # them against the service worker's own origin when it calls
            # `showNotification()`, which is where the web panel's PWA manifest
            # (docs/OWNER_WEB_PLAN.md ۸.۲) is expected to serve them from.
            'webpush': {
                'notification': {
                    'icon': '/icons/icon-192.png',
                    'badge': '/icons/badge-72.png',
                },
                'fcm_options': {
                    'link': getattr(settings, 'FCM_WEBPUSH_CLICK_URL', 'https://panel.noobatyar.ir/'),
                },
            },
        }
    }

    try:
        response = requests.post(url, headers=headers, json=message, timeout=10)
    except Exception as exc:
        logger.error('FCM request failed: %s', exc)
        return False, str(exc)

    if response.status_code == 200:
        return True, ''

    # FCM reports a dead token as 404 UNREGISTERED or 400 INVALID_ARGUMENT; both
    # mean "stop sending to this one", which the caller keys off.
    detail = _error_status(response)
    logger.warning('FCM send failed (%s): %s', response.status_code, response.text[:500])
    return False, detail


def _error_status(response) -> str:
    try:
        payload = response.json()
    except ValueError:
        return f'HTTP {response.status_code}'
    error = payload.get('error', {})
    for detail in error.get('details', []):
        if detail.get('@type', '').endswith('FcmError'):
            return detail.get('errorCode', '') or error.get('status', '')
    return error.get('status', '') or f'HTTP {response.status_code}'


def send_to_user(user_id: int, title: str, body: str, data: dict | None = None) -> int:
    """
    Push to every active device the owner has registered.

    Returns how many devices accepted the message. Tokens FCM reports as dead
    are deactivated in place, so the table does not grow a tail of addresses
    that can never be delivered to.
    """
    from api.models import DeviceToken

    tokens = list(
        DeviceToken.objects.filter(user_id=user_id, is_active=True)
        .values_list('id', 'token')
    )
    if not tokens:
        return 0

    delivered = 0
    dead = []
    for row_id, token in tokens:
        ok, detail = send_to_token(token, title, body, data)
        if ok:
            delivered += 1
        elif detail in ('UNREGISTERED', 'INVALID_ARGUMENT', 'NOT_FOUND'):
            dead.append(row_id)

    if dead:
        DeviceToken.objects.filter(id__in=dead).update(is_active=False)
        logger.info('Deactivated %d dead FCM token(s) for user %s', len(dead), user_id)

    return delivered


def send_to_visitor(visitor_id: int, title: str, body: str, data: dict | None = None) -> int:
    """
    Push to every active device a visitor (customer, not an owner/staff
    ``User``) has registered. Mirrors ``send_to_user`` exactly, against
    ``visitor.models.VisitorDeviceToken`` instead of ``api.models.DeviceToken``
    — kept as a separate function rather than a shared one taking a queryset,
    so each call site stays obviously scoped to the identity it means.
    """
    from visitor.models import VisitorDeviceToken

    tokens = list(
        VisitorDeviceToken.objects.filter(visitor_id=visitor_id, is_active=True)
        .values_list('id', 'token')
    )
    if not tokens:
        return 0

    delivered = 0
    dead = []
    for row_id, token in tokens:
        ok, detail = send_to_token(token, title, body, data)
        if ok:
            delivered += 1
        elif detail in ('UNREGISTERED', 'INVALID_ARGUMENT', 'NOT_FOUND'):
            dead.append(row_id)

    if dead:
        VisitorDeviceToken.objects.filter(id__in=dead).update(is_active=False)
        logger.info('Deactivated %d dead FCM token(s) for visitor %s', len(dead), visitor_id)

    return delivered
