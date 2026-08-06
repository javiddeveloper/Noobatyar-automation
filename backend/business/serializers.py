from rest_framework import serializers
from rest_framework.exceptions import ValidationError

from api.phone import is_iran_phone, normalize_phone
from visitor.models import SmsLog

from .models import Business, ServiceCatalogItem

# Shown to clients when the owner still has booking_enabled=True but their plan
# quota / subscription can no longer pay for a new appointment.
BOOKING_BLOCKED_NOTICE = "پذیرش نوبت جدید برای این کسب‌وکار موقتاً غیرفعال است."


def apply_notice_rules(data, instance):
    """Normalise the client-facing notice + booking flags on an outgoing payload.

    Two rules, both about never leaking state the client should not act on:

    1. ``notice_enabled=False`` ⇒ ``notice_message`` is returned as an empty
       string, never the stored text. An owner who switches the notice off
       expects it to disappear; keeping the text in the payload meant a client
       that rendered the message without also checking the flag (and every
       client did, before the flag existed) kept showing a stale "امروز تعطیلیم"
       days later.
    2. The subscription-driven booking block still fills a default notice, but
       it now also turns ``notice_enabled`` on — the payload has to stay
       self-consistent with rule 1, otherwise the one notice we actually need
       the client to display would be the one it is told to hide.

    Note that the two are independent in the other direction on purpose: an
    owner posting a notice does not close their calendar.
    """
    if not getattr(instance, 'notice_enabled', False):
        data['notice_message'] = ''
        if 'notice_enabled' in data:
            data['notice_enabled'] = False

    from accounting.usage import can_book_appointment
    if data.get('booking_enabled') and not can_book_appointment(instance.user_id):
        data['booking_enabled'] = False
        if not data.get('notice_message'):
            data['notice_message'] = BOOKING_BLOCKED_NOTICE
        if 'notice_enabled' in data:
            data['notice_enabled'] = True

    return data


class BusinessSerializer(serializers.ModelSerializer):
    """
    Full serializer for the Business OWNER.
    Includes ALL fields, including sensitive payment and SMS config.
    NEVER expose this to unauthenticated clients.
    """
    class Meta:
        model = Business
        fields = [
            # Identity
            'id', 'title', 'category', 'unique_code', 'phone', 'address', 'logo',
            # Schedule
            'default_service_duration', 'work_start_hour', 'work_end_hour',
            # Notifications (legacy)
            'notification_enabled', 'notification_types', 'notification_minutes_before',
            # Payment config
            'payment_method', 'accepted_payment_methods', 'merchant_id',
            'payment_link', 'card_number', 'card_owner_name',
            # SMS preferences
            'enable_reminder_sms', 'enable_promotional_sms', 'notify_owner_by_sms',
            'reminder_delivery',
            # Misc
            'allow_anonymous_view', 'bio', 'created_at', 'updated_at',
            # Booking control + emergency notice
            'notice_message', 'notice_enabled', 'booking_enabled',
            # Subscription lock (graceful downgrade) — read-only status flag
            'is_locked',
            # Advanced capacity & deposit settings. These were validated by
            # validate_business_settings() and gated behind plan entitlements, but
            # were missing here — so the owner app's advanced settings passed
            # validation, returned 200, and were then silently dropped by the
            # serializer instead of being saved.
            'max_appointments_per_hour', 'deposit_mode', 'deposit_amount',
        ]
        read_only_fields = ['id', 'unique_code', 'created_at', 'updated_at', 'owner', 'is_locked']
        extra_kwargs = {
            # Restated even though the model field is already CharField(300):
            # the cap is part of the API contract the owner app validates
            # against, and stating it here keeps it true if the model column is
            # ever widened for some unrelated reason.
            'notice_message': {'max_length': 300, 'allow_blank': True},
        }

    def validate_phone(self, value):
        """Normalise and check the business contact number.

        This field had no validation at all, which is how a phone typed on a
        Persian keyboard (۰۲۱۳۹۰۹۳۰۹۳) reached Melipayamak and came back as
        «شماره گیرنده نامعتبر است». Persian/Arabic digits and the usual
        separators are accepted and converted rather than rejected — the owner
        typed a perfectly good number, it just wasn't in ASCII.

        Landlines are allowed: this is the number clients call, not necessarily
        one that can receive SMS.
        """
        if not value:
            return value
        normalized = normalize_phone(value)
        if not is_iran_phone(normalized):
            raise serializers.ValidationError(
                "شماره باید ۱۱ رقم و با ۰ شروع شود (مثل ۰۲۱۱۲۳۴۵۶۷۸ یا ۰۹۱۲۱۲۳۴۵۶۷)"
            )
        return normalized

    def validate_work_start_hour(self, value):
        if not 0 <= value <= 23:
            raise serializers.ValidationError("Must be between 0-23")
        return value

    def validate_work_end_hour(self, value):
        if not 0 <= value <= 23:
            raise serializers.ValidationError("Must be between 0-23")
        return value

    def to_internal_value(self, data):
        ret = super().to_internal_value(data)
        
        # DRF's HTML form parser maps missing booleans to False.
        # Since the mobile app doesn't send some boolean fields during creation,
        # we remove them from validated data if they weren't explicitly provided,
        # so the model defaults (e.g. True) take effect.
        for field in ['booking_enabled', 'enable_reminder_sms', 'enable_promotional_sms', 'allow_anonymous_view', 'notification_enabled', 'notify_owner_by_sms', 'notice_enabled']:
            if field not in data and field in ret:
                ret.pop(field)
                
        return ret

    def validate(self, data):
        if data.get('work_start_hour') and data.get('work_end_hour'):
            if data['work_start_hour'] >= data['work_end_hour']:
                raise serializers.ValidationError(
                    "work_end_hour must be greater than work_start_hour"
                )

        # A payment method must be *usable*, not merely switched on. Storing CARD
        # with no card number, or ONLINE with neither a Zibal merchant nor a
        # payment link, leaves the client on a checkout screen with nothing to
        # pay to — which read as «شماره کارت ثبت نشده» and dead-ended the booking.
        def current(field):
            """Effective value after this write: partial updates keep the rest."""
            if field in data:
                return data[field]
            return getattr(self.instance, field, None)

        accepted = current('accepted_payment_methods') or []

        if 'CARD' in accepted and not str(current('card_number') or '').strip():
            raise serializers.ValidationError({
                'card_number': 'برای پرداخت کارت به کارت، شماره کارت الزامی است'
            })

        if 'ONLINE' in accepted and not (
            str(current('merchant_id') or '').strip()
            or str(current('payment_link') or '').strip()
        ):
            raise serializers.ValidationError({
                'merchant_id': 'برای پرداخت آنلاین، مرچنت آیدی زیبال یا لینک پرداخت الزامی است'
            })

        if current('deposit_mode') in ('MANDATORY', 'OPTIONAL') and not current('deposit_amount'):
            raise serializers.ValidationError({
                'deposit_amount': 'برای دریافت بیعانه، مبلغ آن باید بیشتر از صفر باشد'
            })

        return data

    def validate_logo(self, value):
        if value.size > 500 * 1024:  # 500KB
            raise ValidationError("حجم عکس نباید بیشتر از 500 کیلوبایت باشد.")
        return value


class ServiceCatalogItemSerializer(serializers.ModelSerializer):
    """A single pickable service-name chip, shared across a category."""
    class Meta:
        model = ServiceCatalogItem
        fields = ['id', 'category', 'name']
        read_only_fields = ['id']


class SmsLogVisitorSerializer(serializers.Serializer):
    """The recipient shown next to a logged SMS.

    Nested rather than flattened because the field is nullable: owner
    notifications are logged with ``visitor=None`` (they are billed to the same
    quota, so they belong in the report), and a null object says "this one went
    to you" far more clearly than three independently-null columns.
    """
    id = serializers.IntegerField()
    full_name = serializers.CharField()
    phone_number = serializers.CharField()


class SmsLogSerializer(serializers.ModelSerializer):
    visitor = SmsLogVisitorSerializer(read_only=True)

    class Meta:
        model = SmsLog
        fields = ['id', 'message_text', 'status', 'error_detail', 'sent_at', 'visitor']
        read_only_fields = fields


class PublicBusinessSerializer(serializers.ModelSerializer):
    """
    STRICTLY ISOLATED public-facing serializer.

    This is the ONLY serializer that must ever be used when returning
    business data to an unauthenticated end-client (the booking page).

    Explicitly whitelisted fields — any new field added to the model
    must be CONSCIOUSLY added here. It must NEVER include:
      - phone, merchant_id, card_owner_name, card_number (shown separately via logic)
      - notification_*, enable_*_sms, allow_anonymous_view
      - created_at, updated_at, unique_code (internal)
    """
    class Meta:
        model = Business
        fields = [
            'id',
            'unique_code',
            'category',
            'title',
            'logo',
            'address',
            'phone',
            'work_start_hour',
            'work_end_hour',
            'default_service_duration',
            'payment_method',   # clients need to know which flow to show (Free/Card/Gateway)
            'accepted_payment_methods',
            'deposit_mode',
            'deposit_amount',
            'card_number',
            'card_owner_name',
            'payment_link',
            'bio',
            'booking_enabled',
            'notice_enabled',
            'notice_message',
            'allow_anonymous_view',
        ]
        # All fields are read-only for this serializer
        read_only_fields = fields

    def to_representation(self, instance):
        data = super().to_representation(instance)
        request = self.context.get('request')

        # If user is anonymous and the business doesn't allow anonymous viewing,
        # mask contact fields
        if (not request or not request.user or request.user.is_anonymous) \
                and not instance.allow_anonymous_view:
            data['phone'] = None
            data['address'] = None

        # Emergency-notice gating + the subscription-driven booking block.
        return apply_notice_rules(data, instance)


class ClientBusinessSerializer(serializers.ModelSerializer):
    """
    Kept for backward compatibility with appointment/client_serializers.py.
    Used to display business info inside a client's own appointment record.

    Includes the payment fields the checkout screen needs (card number, owner
    name, deposit, accepted methods). Without them the client was shown
    «شماره کارت ثبت نشده» and «مبلغ را با کسب‌وکار هماهنگ کنید» and had no way to
    pay at all. These are the same fields PublicBusinessSerializer already
    returns on the public booking page, so nothing new is exposed here.

    Still excluded: merchant_id, notification_*, enable_*_sms, created_at/updated_at.
    ``online_gateway_enabled`` is how the checkout screen learns a real Zibal
    gateway is available without the merchant id itself crossing the wire.
    """
    online_gateway_enabled = serializers.SerializerMethodField()

    class Meta:
        model = Business
        fields = [
            'id', 'title', 'category', 'unique_code', 'phone', 'address', 'logo',
            'default_service_duration', 'work_start_hour', 'work_end_hour',
            'allow_anonymous_view',
            'notice_message', 'notice_enabled', 'booking_enabled',
            'payment_method', 'accepted_payment_methods',
            'deposit_mode', 'deposit_amount',
            'card_number', 'card_owner_name', 'payment_link',
            'online_gateway_enabled',
        ]

    def get_online_gateway_enabled(self, obj) -> bool:
        """True when the owner configured a merchant, i.e. the client can be sent
        to a real gateway instead of the manual link-and-tracking-number flow."""
        return bool((obj.merchant_id or '').strip())

    def to_representation(self, instance):
        data = super().to_representation(instance)
        request = self.context.get('request')

        # If user is anonymous and the business doesn't allow anonymous viewing,
        # mask contact fields
        if (not request or not request.user or request.user.is_anonymous) \
                and not instance.allow_anonymous_view:
            data['phone'] = None
            data['address'] = None

        # Emergency-notice gating + the subscription-driven booking block.
        return apply_notice_rules(data, instance)
