"""
Visitor-scoped authentication for the public client-booking surface.

A Visitor never has a `User` account, so it cannot use simplejwt's
JWTAuthentication (which always resolves to `api.User`). This issues and
verifies its own signed token instead, and deliberately uses the `Visitor`
auth scheme (not `Bearer`) so it never collides with JWTAuthentication, which
only reacts to `Bearer` — the two can sit side by side on the same project
without either one mistakenly trying to decode the other's token.
"""
from django.core import signing
from rest_framework.authentication import BaseAuthentication
from rest_framework.exceptions import AuthenticationFailed
from rest_framework.permissions import BasePermission

from .models import Visitor

VISITOR_TOKEN_SALT = 'visitor-auth'
# No refresh mechanism (yet) — a token just lasts long enough that a customer
# isn't re-verifying OTP on every visit. Once it expires, the client's normal
# 401 handling sends them back through OTP verify, which finds their existing
# Visitor row and issues a fresh token.
VISITOR_TOKEN_MAX_AGE = 60 * 60 * 24 * 90  # 90 days


def sign_visitor_token(visitor_id: int) -> str:
    return signing.dumps({'visitor_id': visitor_id}, salt=VISITOR_TOKEN_SALT)


class VisitorTokenAuthentication(BaseAuthentication):
    keyword = 'Visitor'

    def authenticate_header(self, request):
        """Return a WWW-Authenticate value so failures stay 401, not 403.

        Without this DRF downgrades every AuthenticationFailed raised below to
        403 Forbidden (it only keeps 401 when some authenticator advertises a
        challenge). The client treats 401 as "session is gone → clear the token
        and re-run OTP", so a 403 left anyone with an unusable token — an expired
        one, or a visitor an owner had deleted — permanently stuck: no redirect,
        no way to sign out, just a "مشتری یافت نشد" toast on every action.
        """
        return self.keyword

    def authenticate(self, request):
        auth = request.headers.get('Authorization', '')
        if not auth.startswith(f'{self.keyword} '):
            return None

        token = auth[len(self.keyword) + 1:].strip()
        try:
            data = signing.loads(token, salt=VISITOR_TOKEN_SALT, max_age=VISITOR_TOKEN_MAX_AGE)
        except signing.SignatureExpired:
            raise AuthenticationFailed('نشست شما منقضی شده است')
        except signing.BadSignature:
            raise AuthenticationFailed('توکن نامعتبر است')

        try:
            visitor = Visitor.objects.get(id=data['visitor_id'])
        except Visitor.DoesNotExist:
            raise AuthenticationFailed('مشتری یافت نشد')

        # DRF assigns whatever we return here to request.user — there is no
        # `User` in this flow, so request.user is a Visitor instance for any
        # view using this authentication class. IsVisitorAuthenticated below
        # is what enforces that expectation at the permission layer.
        return (visitor, token)


class IsVisitorAuthenticated(BasePermission):
    def has_permission(self, request, view):
        return isinstance(getattr(request, 'user', None), Visitor)
