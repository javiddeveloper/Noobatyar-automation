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

    # Card-to-card belongs to the paid (deposit/commitment) tier — basic plans
    # are cash-only. A CARD method or a card number both imply it.
    card_number = (data.get("card_number") or "").strip() if "card_number" in data else ""
    wants_card = (payment_method == "CARD") or ("CARD" in accepted) or bool(card_number)
    if wants_card and not entitlements.has_feature(user, entitlements.FEATURE_DEPOSIT):
        return need(entitlements.FEATURE_DEPOSIT)

    # Deposit
    if "deposit_mode" in data:
        deposit_mode = data.get("deposit_mode")
        if deposit_mode in ("MANDATORY", "OPTIONAL"):
            if not entitlements.has_feature(user, entitlements.FEATURE_DEPOSIT):
                return need(entitlements.FEATURE_DEPOSIT)
            # A deposit with no amount leaves the client staring at
            # "مبلغ بیعانه: نامشخص" with nothing meaningful to pay. Only checked
            # when the amount is part of this payload, so partial updates that
            # do not touch it keep working (see the docstring contract).
            if "deposit_amount" in data:
                try:
                    amount = int(data.get("deposit_amount") or 0)
                except (TypeError, ValueError):
                    amount = 0
                if amount <= 0:
                    return "برای فعال کردن بیعانه، مبلغ بیعانه را وارد کنید."

    # Promotional SMS
    if "enable_promotional_sms" in data and _as_bool(data.get("enable_promotional_sms")):
        if not entitlements.has_feature(user, entitlements.FEATURE_PROMOTIONAL_SMS):
            return need(entitlements.FEATURE_PROMOTIONAL_SMS)

    # Automatic reminder delivery from the SMS panel.
    # MANUAL costs nothing (the message leaves the owner's own SIM from the
    # owner app), PANEL bills every reminder to the owner's plan quota — so
    # only the tier that pays for panel sending may switch it on. Switching
    # back to MANUAL is always allowed, including for a user whose plan just
    # expired, otherwise a downgrade would strand them on a paid setting they
    # can no longer turn off.
    if "reminder_delivery" in data and data.get("reminder_delivery") == "PANEL":
        if not entitlements.has_feature(user, entitlements.FEATURE_AUTO_REMINDER_SMS):
            return need(entitlements.FEATURE_AUTO_REMINDER_SMS)

    # Hourly capacity control
    if "max_appointments_per_hour" in data:
        value = data.get("max_appointments_per_hour")
        has_value = value not in (None, "", "null")
        if has_value and not entitlements.has_feature(user, entitlements.FEATURE_CAPACITY_CONTROL):
            return need(entitlements.FEATURE_CAPACITY_CONTROL)

    return None
