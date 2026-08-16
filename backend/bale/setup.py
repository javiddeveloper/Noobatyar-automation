"""
bale/setup.py

Building the webhook URL, shared by the admin (which displays it) and the
``bale_setup`` command (which registers it). Kept out of both so the address
shown in the panel is provably the one that gets registered.
"""

from django.conf import settings
from django.urls import reverse


def build_webhook_url(config) -> str:
    """Absolute https URL Bale should POST updates to, or '' if unbuildable.

    Bale refuses a plain-http or localhost webhook, so a SITE_URL still on its
    development default yields '' rather than a URL that would be rejected with
    a confusing provider-side error.
    """
    site = (getattr(settings, 'SITE_URL', '') or '').rstrip('/')
    if not site or not config.webhook_secret:
        return ''
    if site.startswith('http://') or 'localhost' in site or '127.0.0.1' in site:
        return ''
    path = reverse('bale:webhook', kwargs={'secret': config.webhook_secret})
    return f'{site}{path}'
