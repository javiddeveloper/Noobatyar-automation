# core/campaigns.py
"""
The one payload shape every promotional push shares, whoever it is aimed at.

A campaign aimed at customers and one aimed at business owners differ only in
*who* is looked up — the notification itself (title, body, optional image, an
optional web link, an optional in-app deep link) is identical. That shared
shape lives here as :class:`PushCampaign` so the two audiences cannot drift
into two subtly different payloads: ``core.messaging.send_push_campaign``
builds one of these and hands the same object to both the visitor and the
owner delivery path.

── Link vs deep link ────────────────────────────────────────────────────────

They are deliberately two separate fields rather than one "url":

* ``link`` is a normal ``https://`` address. On web push it becomes the
  notification's click target; in the mobile app it opens the browser.
* ``deep_link`` is a ``noobatyar://`` address that routes *inside* the owner
  app (the scheme is already registered — see the payment-result intent
  filter in mobile_owner/composeApp/src/androidMain/AndroidManifest.xml).

Sending both is allowed and useful: the app follows ``deep_link``, while a
browser that has no idea what ``noobatyar://`` means still has ``link`` to
fall back on.

── Why the data payload is all strings ──────────────────────────────────────

FCM rejects a ``data`` object containing anything but strings, and silently
drops keys whose value is empty in some client SDKs. :meth:`data_payload`
therefore stringifies everything and omits empty keys entirely, rather than
sending ``"image": ""`` for the (common) case of a campaign with no image.
"""

from __future__ import annotations

from dataclasses import dataclass
from urllib.parse import urlparse

# Kept well under FCM's own limits, which are generous; these are the point at
# which a notification stops being readable on a phone rather than the point
# at which Google refuses it.
TITLE_MAX = 120
BODY_MAX = 1000

# The custom scheme already registered by the owner app's manifest. A deep
# link on any other scheme would simply not resolve on the device, so it is
# rejected at compose time instead of being sent and silently doing nothing.
DEEP_LINK_SCHEME = 'noobatyar'


class CampaignError(ValueError):
    """Raised for a payload a human still has to fix (empty body, bad URL)."""


def _validate_https(url: str, field_label: str) -> str:
    """Accepts an ``https://`` URL, or ``http://`` only for localhost.

    Plain http is refused because both consumers reject it in practice: FCM
    requires an https image URL, and a browser on an https page will not open
    an http notification target without a mixed-content warning. Localhost is
    allowed through so this is usable against a dev backend.
    """
    parsed = urlparse(url)
    if parsed.scheme not in ('http', 'https') or not parsed.netloc:
        raise CampaignError(f'{field_label} باید یک آدرس کامل و معتبر باشد (با https:// شروع شود).')
    if parsed.scheme == 'http' and parsed.hostname not in ('localhost', '127.0.0.1'):
        raise CampaignError(f'{field_label} باید با https:// باشد.')
    return url


def _validate_deep_link(url: str) -> str:
    parsed = urlparse(url)
    if parsed.scheme != DEEP_LINK_SCHEME:
        # The scheme is written as one contiguous `noobatyar://` run rather
        # than assembled around the punctuation: split across the RTL sentence
        # the bidi algorithm reorders the `//:` and shows it backwards.
        raise CampaignError(
            f'دیپ‌لینک نامعتبر است — باید با {DEEP_LINK_SCHEME}:// شروع شود '
            f'(مثال: {DEEP_LINK_SCHEME}://home).'
        )
    return url


@dataclass(frozen=True)
class PushCampaign:
    """One promotional notification, independent of who receives it.

    Immutable on purpose: :func:`core.messaging.send_push_campaign` hands the
    same instance to every recipient across both audiences, and a payload that
    could be mutated mid-loop is a bug waiting to happen (recipient 900 of a
    campaign getting different text than recipient 1).

    Build one through :meth:`create`, which validates; the constructor itself
    is left permissive so a stored log row can be rehydrated verbatim.
    """

    title: str
    body: str
    image_url: str = ''
    link: str = ''
    deep_link: str = ''

    @classmethod
    def create(cls, *, title: str, body: str, image_url: str = '',
               link: str = '', deep_link: str = '') -> PushCampaign:
        """Normalizes and validates operator input, or raises CampaignError."""
        title = (title or '').strip()
        body = (body or '').strip()
        image_url = (image_url or '').strip()
        link = (link or '').strip()
        deep_link = (deep_link or '').strip()

        if not body:
            raise CampaignError('متن اعلان نمی‌تواند خالی باشد.')
        if len(title) > TITLE_MAX:
            raise CampaignError(f'عنوان نباید بیشتر از {TITLE_MAX} کاراکتر باشد.')
        if len(body) > BODY_MAX:
            raise CampaignError(f'متن نباید بیشتر از {BODY_MAX} کاراکتر باشد.')

        if image_url:
            image_url = _validate_https(image_url, 'آدرس تصویر')
        if link:
            link = _validate_https(link, 'لینک')
        if deep_link:
            deep_link = _validate_deep_link(deep_link)

        return cls(title=title, body=body, image_url=image_url, link=link, deep_link=deep_link)

    def personalized(self, full_name: str) -> PushCampaign:
        """A copy with ``{full_name}`` substituted.

        The one supported token, same "a compose box is not a template engine"
        reasoning as ``core.messaging._personalize``. Returns a new instance
        rather than mutating, so the campaign passed into the send loop stays
        the pristine original.
        """
        name = full_name or ''
        return PushCampaign(
            title=self.title.replace('{full_name}', name),
            body=self.body.replace('{full_name}', name),
            image_url=self.image_url,
            link=self.link,
            deep_link=self.deep_link,
        )

    def data_payload(self) -> dict[str, str]:
        """The FCM ``data`` block clients read to route a tap.

        ``type`` lets a client tell a marketing push apart from the
        transactional ones (``NEW_BOOKING`` etc. — see
        appointment/client_views.py) without parsing the text.
        """
        data = {'type': 'MARKETING'}
        if self.link:
            data['link'] = self.link
        if self.deep_link:
            data['deep_link'] = self.deep_link
        return data

    @property
    def has_media(self) -> bool:
        return bool(self.image_url)
