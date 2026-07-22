"""
accounting/permissions.py

Entitlement-aware building blocks used to gate features behind the user's plan.

  * RequiresFeature('online_gateway')  — a DRF permission class factory.
  * validate_business_settings(...)     — checks the settings an owner is trying
    to enable on a Business against their plan, returning a Persian error string
    (or None if everything is allowed).
"""

from rest_framework.permissions import BasePermission

from . import entitlements


def RequiresFeature(feature_key, message=None):
    """
    Build a DRF permission class that allows the request only if the user's plan
    unlocks ``feature_key``. Staff always pass.

        permission_classes = [IsAuthenticated, RequiresFeature('advanced_reports')]
    """
    label = entitlements.FEATURE_LABELS.get(feature_key, feature_key)
    default_message = message or f"قابلیت «{label}» در پلن فعلی شما فعال نیست. برای استفاده، پلن خود را ارتقا دهید."

    class _RequiresFeature(BasePermission):
        message = default_message

        def has_permission(self, request, view):
            user = request.user
            if not user or not user.is_authenticated:
                return False
            if user.is_staff:
                return True
            return entitlements.has_feature(user, feature_key)

    _RequiresFeature.__name__ = f"RequiresFeature_{feature_key}"
    return _RequiresFeature


def _as_bool(value):
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        return value.strip().lower() in ("1", "true", "yes", "on")
    return bool(value)


def _as_list(value):
    if isinstance(value, (list, tuple)):
        return list(value)
    if isinstance(value, str) and value:
        # multipart sends JSON arrays as a string, or a single value
        cleaned = value.strip()
        if cleaned.startswith("["):
            import json
            try:
                return json.loads(cleaned)
            except ValueError:
                return [cleaned]
        return [p.strip() for p in cleaned.split(",") if p.strip()]
    return []


def validate_business_settings(user, data):
    """
    Return a Persian error message if ``data`` tries to enable a capability the
    user's plan does not include; otherwise None.

    ``data`` is the raw request payload (values may be strings under multipart).
    Only keys that are present are checked, so partial updates are fine.
    """
    if getattr(user, "is_staff", False):
        return None

    def need(feature_key):
        label = entitlements.FEATURE_LABELS.get(feature_key, feature_key)
        return f"قابلیت «{label}» در پلن فعلی شما فعال نیست. برای استفاده، پلن خود را ارتقا دهید."

    # Online gateway
    payment_method = data.get("payment_method")
    accepted = _as_list(data.get("accepted_payment_methods")) if "accepted_payment_methods" in data else []
    wants_gateway = (payment_method in ("GATEWAY", "ONLINE")) or ("ONLINE" in accepted) or ("GATEWAY" in accepted)
    if wants_gateway and not entitlements.has_feature(user, entitlements.FEATURE_ONLINE_GATEWAY):
        return need(entitlements.FEATURE_ONLINE_GATEWAY)

    # Deposit
    if "deposit_mode" in data:
        deposit_mode = data.get("deposit_mode")
        if deposit_mode in ("MANDATORY", "OPTIONAL") and not entitlements.has_feature(user, entitlements.FEATURE_DEPOSIT):
            return need(entitlements.FEATURE_DEPOSIT)

    # Promotional SMS
    if "enable_promotional_sms" in data and _as_bool(data.get("enable_promotional_sms")):
        if not entitlements.has_feature(user, entitlements.FEATURE_PROMOTIONAL_SMS):
            return need(entitlements.FEATURE_PROMOTIONAL_SMS)

    # Hourly capacity control
    if "max_appointments_per_hour" in data:
        value = data.get("max_appointments_per_hour")
        has_value = value not in (None, "", "null")
        if has_value and not entitlements.has_feature(user, entitlements.FEATURE_CAPACITY_CONTROL):
            return need(entitlements.FEATURE_CAPACITY_CONTROL)

    return None
