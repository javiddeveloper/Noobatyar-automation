# core/dashboard/__init__.py
"""
The admin dashboard's data layer.

  * ``metrics``  — the aggregate queries. No request, no permissions, no HTML.
  * ``cache``    — short-TTL sharing of the payload between staff.
  * ``panels``   — shapes the payload for the template and filters it by what
                   the viewer is allowed to see.

The view (``core/admin_site.NobatyarAdminSite.index``) does nothing but chain
the three.
"""

from . import cache, metrics, panels  # noqa: F401


def context_for(request, force_refresh=False):
    """Dashboard context for ``request.user``, cached where possible."""
    payload, cached = cache.get_payload(force_refresh=force_refresh)
    context = panels.build(payload, request.user)
    context['dashboard_cached'] = cached
    return context
