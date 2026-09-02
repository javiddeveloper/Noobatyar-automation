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
from dataclasses import dataclass

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


def send_to_token(
    token: str,
    title: str,
    body: str,
    data: dict | None = None,
    image: str | None = None,
    link: str | None = None,
) -> tuple[bool, str]:
    """
    Deliver one notification to one device.

    Returns ``(ok, detail)``. ``detail`` carries the FCM error code on failure,
    so callers can react to ``UNREGISTERED`` (the app was uninstalled or the
    token was rotated) by deactivating the row rather than retrying forever.

    ``data`` values must be strings — FCM rejects a data payload containing
    numbers or booleans — so everything is stringified here rather than at each
    call site.

    ``image`` and ``link`` exist for promotional campaigns
    (``core.campaigns.PushCampaign``); every transactional call site omits both
    and gets exactly the payload it got before they were added.
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
                    # A campaign's own link wins; everything else keeps landing
                    # on the panel, as it did before campaigns existed.
                    'link': link or getattr(settings, 'FCM_WEBPUSH_CLICK_URL', 'https://panel.noobatyar.ir/'),
                },
            },
        }
    }

    if image:
        # Set on the platform-independent `notification` (covers Android and,
        # via FCM's own mapping, APNs) *and* explicitly on webpush: the
        # top-level image is not applied to web push by FCM, so a campaign
        # image would silently vanish for exactly the audience — front_client
        # customers — that most campaigns target.
        message['message']['notification']['image'] = image
        message['message']['webpush']['notification']['image'] = image

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


#: Error codes that mean "this address will never work again", as opposed to a
#: transient failure worth leaving the token active for.
DEAD_TOKEN_CODES = ('UNREGISTERED', 'INVALID_ARGUMENT', 'NOT_FOUND')


@dataclass
class SendResult:
    """Outcome of fanning one notification out to a set of device tokens.

    Exists because a bare "delivered" count cannot answer the question a
    campaign report actually asks: a zero could mean nobody had the app
    installed, or that every single send errored. Keeping ``failed`` and
    ``dead`` separate distinguishes "FCM refused it" from "that install is
    gone", which is the difference between a broken campaign and normal
    attrition.

    Note ``delivered`` means *accepted by FCM*, never "shown on a phone" —
    the send API reports nothing beyond acceptance. Every label rendered from
    this in the admin panel is worded accordingly.
    """

    delivered: int = 0
    failed: int = 0
    dead: int = 0

    def __iadd__(self, other: 'SendResult') -> 'SendResult':
        self.delivered += other.delivered
        self.failed += other.failed
        self.dead += other.dead
        return self


def _fan_out(token_rows, model, owner_label: str, title, body, data, image=None, link=None) -> SendResult:
    """Send to every ``(row_id, token)`` in ``token_rows``, reaping dead ones.

    Shared by the owner and visitor paths below: the two differ only in which
    model holds the tokens, and duplicating the reaping logic once per
    identity type is how the two quietly drift apart.
    """
    result = SendResult()
    dead_ids = []

    for row_id, token in token_rows:
        ok, detail = send_to_token(token, title, body, data, image=image, link=link)
        if ok:
            result.delivered += 1
        else:
            result.failed += 1
            if detail in DEAD_TOKEN_CODES:
                dead_ids.append(row_id)

    if dead_ids:
        model.objects.filter(id__in=dead_ids).update(is_active=False)
        result.dead = len(dead_ids)
        logger.info('Deactivated %d dead FCM token(s) for %s', len(dead_ids), owner_label)

    return result


def send_to_user(user_id: int, title: str, body: str, data: dict | None = None) -> int:
    """
    Push to every active device the owner has registered.

    Returns how many devices accepted the message. Tokens FCM reports as dead
    are deactivated in place, so the table does not grow a tail of addresses
    that can never be delivered to.
    """
    return deliver_to_user(user_id, title, body, data).delivered


def deliver_to_user(
    user_id: int, title: str, body: str, data: dict | None = None,
    image: str | None = None, link: str | None = None,
) -> SendResult:
    """:func:`send_to_user` with the full :class:`SendResult`, plus the
    campaign-only ``image``/``link``. Kept as a separate entry point so the
    dozen existing ``send_to_user`` call sites keep their simple int return."""
    from api.models import DeviceToken

    tokens = list(
        DeviceToken.objects.filter(user_id=user_id, is_active=True)
        .values_list('id', 'token')
    )
    if not tokens:
        return SendResult()

    return _fan_out(tokens, DeviceToken, f'user {user_id}', title, body, data, image=image, link=link)


def send_to_visitor(visitor_id: int, title: str, body: str, data: dict | None = None) -> int:
    """
    Push to every active device a visitor (customer, not an owner/staff
    ``User``) has registered. Mirrors ``send_to_user`` exactly, against
    ``visitor.models.VisitorDeviceToken`` instead of ``api.models.DeviceToken``
    — kept as a separate function rather than a shared one taking a queryset,
    so each call site stays obviously scoped to the identity it means.
    """
    return deliver_to_visitor(visitor_id, title, body, data).delivered


def deliver_to_visitor(
    visitor_id: int, title: str, body: str, data: dict | None = None,
    image: str | None = None, link: str | None = None,
) -> SendResult:
    """:func:`send_to_visitor` with the full :class:`SendResult`, plus the
    campaign-only ``image``/``link``."""
    from visitor.models import VisitorDeviceToken

    tokens = list(
        VisitorDeviceToken.objects.filter(visitor_id=visitor_id, is_active=True)
        .values_list('id', 'token')
    )
    if not tokens:
        return SendResult()

    return _fan_out(
        tokens, VisitorDeviceToken, f'visitor {visitor_id}',
        title, body, data, image=image, link=link,
    )


def send_visitor_appointment_push(business_id, visitor_id, appointment_id, title, body):
    """
    Push to a visitor for an appointment-lifecycle event — booked, confirmed,
    or rejected by the owner (see appointment/client_views.py's
    _fire_booking_sms/_fire_deposit_paid_sms and appointment/views/views.py's
    _notify_client_of_decision, the SMS-only functions this complements).

    Gated by the SAME entitlement as the reminder push
    (accounting.entitlements.FEATURE_AUTO_REMINDER_SMS — پرو/پرو پلاس and
    up), and logged to visitor.models.PushLog exactly like the reminder push
    is (send_appointment_reminders._push_visitor).

    Silently does nothing — no PushLog row — when the business isn't
    entitled or FCM isn't configured: neither is an error, there is just
    nothing to send, same reasoning _push_owner already uses for "not
    configured".
    """
    from accounting.entitlements import FEATURE_AUTO_REMINDER_SMS, has_feature
    from business.models import Business
    from visitor.models import PushLog

    if not is_configured():
        return

    try:
        owner_id = Business.objects.values_list('user_id', flat=True).get(id=business_id)
    except Business.DoesNotExist:
        return
    if not has_feature(owner_id, FEATURE_AUTO_REMINDER_SMS):
        return

    try:
        delivered = send_to_visitor(visitor_id, title=title, body=body)
        PushLog.objects.create(
            business_id=business_id, visitor_id=visitor_id, appointment_id=appointment_id,
            title=title, body=body,
            status='SENT' if delivered else 'FAILED',
            error_detail='' if delivered else 'دستگاه فعالی برای این مراجع ثبت نشده است',
        )
    except Exception:
        logger.exception(
            'Visitor appointment push failed (business=%s visitor=%s appointment=%s)',
            business_id, visitor_id, appointment_id,
        )
