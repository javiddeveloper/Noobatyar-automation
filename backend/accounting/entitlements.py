"""
accounting/entitlements.py

Single source of truth for what each subscription plan unlocks.

A plan stores its capabilities in ``Plan.features`` (a JSON object). This module
defines the canonical keys, the commitment-ladder tier bundles, and the helpers
the rest of the app uses to answer two questions:

    * has_feature(user, key)  → is this capability unlocked?
    * get_quota(user, key)    → how many of this resource are allowed? (-1 = unlimited)

Resolution order for a user's effective entitlements:
    DEFAULT_ENTITLEMENTS  ← baseline (no / expired plan)
        ↑ overlaid by the active plan's ``features``
        ↑ overlaid by any active *feature* add-on purchases (temporary unlocks)

SMS add-on packs are NOT entitlements — they top up a wallet handled in
``accounting/usage.py``.
"""

from django.utils import timezone


# ── Feature flags (booleans) ──────────────────────────────────────────────────
FEATURE_ONLINE_GATEWAY   = "online_gateway"     # درگاه آنلاین زیبال
FEATURE_DEPOSIT          = "deposit"            # دریافت بیعانه
FEATURE_PROMOTIONAL_SMS  = "promotional_sms"    # پیامک تبلیغاتی/کمپین
FEATURE_CAPACITY_CONTROL = "capacity_control"   # کنترل ظرفیت ساعتی
FEATURE_ADVANCED_REPORTS = "advanced_reports"   # گزارش‌های پیشرفته
FEATURE_MULTI_CHANNEL    = "multi_channel"      # واتساپ/تلگرام
FEATURE_BRANDED_PAGE     = "branded_page"       # صفحه‌ی نوبت‌دهی برندشده
FEATURE_PRIORITY_SUPPORT = "priority_support"   # پشتیبانی اولویت‌دار
FEATURE_AUTO_REMINDER_SMS = "auto_reminder_sms" # یادآوری خودکار از پنل پیامکی

# ── Quotas (ints; UNLIMITED = -1) ─────────────────────────────────────────────
QUOTA_MAX_BUSINESSES       = "max_businesses"
QUOTA_MONTHLY_APPOINTMENTS = "monthly_appointments"
QUOTA_MONTHLY_SMS          = "monthly_sms"

UNLIMITED = -1

FEATURE_KEYS = (
    FEATURE_ONLINE_GATEWAY, FEATURE_DEPOSIT, FEATURE_PROMOTIONAL_SMS,
    FEATURE_CAPACITY_CONTROL, FEATURE_ADVANCED_REPORTS, FEATURE_MULTI_CHANNEL,
    FEATURE_BRANDED_PAGE, FEATURE_PRIORITY_SUPPORT, FEATURE_AUTO_REMINDER_SMS,
)
QUOTA_KEYS = (QUOTA_MAX_BUSINESSES, QUOTA_MONTHLY_APPOINTMENTS, QUOTA_MONTHLY_SMS)

# Features that are sold in a plan but that no code path actually implements yet.
# They stay in the bundles (so the pricing table and any already-sold plan keep
# their shape) but every client is told, in the entitlements payload, to render
# them as disabled/"coming soon" — otherwise an owner on the ۶ ماهه plan taps a
# switch that resolves to True and then waits for a WhatsApp message that no
# part of this backend has ever been able to send. Delete a key from here the
# moment its feature ships; this list is the single source of truth.
COMING_SOON_FEATURES = (
    FEATURE_PROMOTIONAL_SMS,
    FEATURE_MULTI_CHANNEL,
)

# Human-readable labels (used in error messages / API output).
FEATURE_LABELS = {
    FEATURE_ONLINE_GATEWAY:   "درگاه پرداخت آنلاین",
    FEATURE_DEPOSIT:          "دریافت بیعانه",
    FEATURE_PROMOTIONAL_SMS:  "پیامک تبلیغاتی",
    FEATURE_CAPACITY_CONTROL: "کنترل ظرفیت ساعتی",
    FEATURE_ADVANCED_REPORTS: "گزارش‌های پیشرفته",
    FEATURE_MULTI_CHANNEL:    "اعلان واتساپ/تلگرام",
    FEATURE_BRANDED_PAGE:     "صفحه‌ی نوبت‌دهی برندشده",
    FEATURE_PRIORITY_SUPPORT: "پشتیبانی اولویت‌دار",
    FEATURE_AUTO_REMINDER_SMS: "ارسال خودکار یادآوری از پنل پیامکی",
}


# ── Baseline: user with no active plan (most restrictive) ─────────────────────
DEFAULT_ENTITLEMENTS = {
    QUOTA_MAX_BUSINESSES:       1,
    QUOTA_MONTHLY_APPOINTMENTS: 0,
    QUOTA_MONTHLY_SMS:          0,
    FEATURE_ONLINE_GATEWAY:     False,
    FEATURE_DEPOSIT:            False,
    FEATURE_PROMOTIONAL_SMS:    False,
    FEATURE_CAPACITY_CONTROL:   False,
    FEATURE_ADVANCED_REPORTS:   False,
    FEATURE_MULTI_CHANNEL:      False,
    FEATURE_BRANDED_PAGE:       False,
    FEATURE_PRIORITY_SUPPORT:   False,
    FEATURE_AUTO_REMINDER_SMS:  False,
}


# ── Commitment-ladder tier bundles ────────────────────────────────────────────
# Longer commitment unlocks strictly more. Referenced by seed_plans.py.
# بیعانه و کنترل ظرفیت از همان روز اول باز هستند: بیعانه‌ی کارت‌به‌کارت
# اصلی‌ترین دلیل خرید محصول است و نباید پشت پلن‌های بالاتر قفل باشد.
#
# ── منطق سقف پیامک در کل نردبان ──────────────────────────────────────────────
# پیامک تنها هزینه‌ی متغیر پلتفرم است (۱۷۰ تومان هر عدد)؛ نوبت عملاً رایگان است.
# پس سقف پیامک باید با «مبلغ ماهانه‌ی پرداختی» بالا برود، نه با «طول تعهد».
# اگر پلن سالانه (ماهی ~۱۴۹) پیامک بیشتری از پلن ماهانه (۱۹۹) بگیرد، حاشیه‌ی
# سود در بلندترین تعهد به کمترین مقدار می‌رسد — یعنی وفادارترین مشتری
# کم‌سودترین مشتری می‌شود. سقف‌های زیر عمداً ملایم بالا می‌روند تا این وارونگی
# رخ ندهد؛ مصرف بیشتر از طریق بسته‌های افزودنی (کیف‌پول پیامک) تأمین می‌شود.
BUNDLE_TRIAL = {
    QUOTA_MAX_BUSINESSES:       1,
    QUOTA_MONTHLY_APPOINTMENTS: 100,
    QUOTA_MONTHLY_SMS:          20,
    FEATURE_ONLINE_GATEWAY:     False,
    FEATURE_DEPOSIT:            True,
    FEATURE_PROMOTIONAL_SMS:    False,
    FEATURE_CAPACITY_CONTROL:   True,
    FEATURE_ADVANCED_REPORTS:   False,
    FEATURE_MULTI_CHANNEL:      False,
    FEATURE_BRANDED_PAGE:       False,
    FEATURE_PRIORITY_SUPPORT:   False,
    FEATURE_AUTO_REMINDER_SMS:  False,
}

# ۱ ماهه «شروع»
BUNDLE_1M = {
    QUOTA_MAX_BUSINESSES:       1,
    QUOTA_MONTHLY_APPOINTMENTS: 500,
    QUOTA_MONTHLY_SMS:          200,
    FEATURE_ONLINE_GATEWAY:     False,
    FEATURE_DEPOSIT:            True,
    FEATURE_PROMOTIONAL_SMS:    False,
    FEATURE_CAPACITY_CONTROL:   True,
    FEATURE_ADVANCED_REPORTS:   False,
    FEATURE_MULTI_CHANNEL:      False,
    FEATURE_BRANDED_PAGE:       False,
    FEATURE_PRIORITY_SUPPORT:   False,
    FEATURE_AUTO_REMINDER_SMS:  False,
}

# ۳ ماهه «حرفه‌ای»
BUNDLE_3M = {
    QUOTA_MAX_BUSINESSES:       2,
    QUOTA_MONTHLY_APPOINTMENTS: 1000,
    QUOTA_MONTHLY_SMS:          250,
    FEATURE_ONLINE_GATEWAY:     True,
    FEATURE_DEPOSIT:            True,
    FEATURE_PROMOTIONAL_SMS:    True,
    FEATURE_CAPACITY_CONTROL:   True,
    FEATURE_ADVANCED_REPORTS:   True,
    FEATURE_MULTI_CHANNEL:      False,
    FEATURE_BRANDED_PAGE:       True,
    FEATURE_PRIORITY_SUPPORT:   True,
    FEATURE_AUTO_REMINDER_SMS:  True,
}

# ۶ ماهه «ویژه»
BUNDLE_6M = {
    QUOTA_MAX_BUSINESSES:       3,
    QUOTA_MONTHLY_APPOINTMENTS: 2000,
    QUOTA_MONTHLY_SMS:          300,
    FEATURE_ONLINE_GATEWAY:     True,
    FEATURE_DEPOSIT:            True,
    FEATURE_PROMOTIONAL_SMS:    True,
    FEATURE_CAPACITY_CONTROL:   True,
    FEATURE_ADVANCED_REPORTS:   True,
    FEATURE_MULTI_CHANNEL:      True,
    FEATURE_BRANDED_PAGE:       True,
    FEATURE_PRIORITY_SUPPORT:   True,
    FEATURE_AUTO_REMINDER_SMS:  True,
}

# ۱۲ ماهه — بالاترین سطح، مثل ۶ ماهه با نوبت نامحدود و پیامک بیشتر.
# نوبت برای ما تقریباً هزینه‌ای ندارد؛ محدودکننده‌ی واقعی هزینه، پیامک است.
BUNDLE_12M = {
    **BUNDLE_6M,
    QUOTA_MAX_BUSINESSES:       5,
    QUOTA_MONTHLY_APPOINTMENTS: UNLIMITED,
    QUOTA_MONTHLY_SMS:          400,
}


# ── Resolution ────────────────────────────────────────────────────────────────

def _user_id(user_or_id):
    return user_or_id.id if hasattr(user_or_id, "id") else user_or_id


def resolve_entitlements(plan_features):
    """Overlay a plan's ``features`` dict on top of the baseline."""
    merged = dict(DEFAULT_ENTITLEMENTS)
    if plan_features:
        merged.update(plan_features)
    return merged


def get_active_subscription(user_or_id):
    """Return the user's current, still-valid Subscription, or None."""
    from accounting.models import Subscription
    sub = (
        Subscription.objects
        .filter(user_id=_user_id(user_or_id), status="active")
        .select_related("plan")
        .first()
    )
    if sub and sub.is_valid():
        return sub
    return None


def _active_feature_addons(user_id):
    """Set of feature keys temporarily unlocked by active feature add-ons."""
    from accounting.models import AddOnPurchase, AddOnPack
    now = timezone.now()
    rows = AddOnPurchase.objects.filter(
        user_id=user_id,
        status="success",
        pack__kind=AddOnPack.KIND_FEATURE,
        expires_at__gt=now,
    ).values_list("pack__feature_key", flat=True)
    return {key for key in rows if key}


def get_entitlements(user_or_id):
    """
    Effective entitlements for a user: plan features overlaid on the baseline,
    then any active feature add-ons forced on.
    """
    user_id = _user_id(user_or_id)
    sub = get_active_subscription(user_id)
    ent = resolve_entitlements(sub.plan.features if sub else None)

    for feature_key in _active_feature_addons(user_id):
        ent[feature_key] = True

    return ent


def has_feature(user_or_id, key):
    return bool(get_entitlements(user_or_id).get(key, False))


def get_quota(user_or_id, key):
    """Quota value for a resource. -1 means unlimited."""
    return int(get_entitlements(user_or_id).get(key, 0))


def is_unlimited(quota_value):
    return quota_value == UNLIMITED
