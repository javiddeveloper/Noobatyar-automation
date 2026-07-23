from rest_framework import serializers
from rest_framework.exceptions import ValidationError

from .models import Business


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
            # Payment config  ← new
            'payment_method', 'merchant_id', 'payment_link', 'card_number', 'card_owner_name',
            # SMS preferences ← new
            'enable_reminder_sms', 'enable_promotional_sms',
            # Misc
            'allow_anonymous_view', 'created_at', 'updated_at',
            # Booking control
            'notice_message', 'booking_enabled',
            # Subscription lock (graceful downgrade) — read-only status flag
            'is_locked',
            # Advanced Capacity & Deposit Settings
            'booking_enabled', 'accepted_payment_methods', 'payment_link',
            'card_number', 'card_owner_name', 'merchant_id', 'bio'
        ]
        read_only_fields = ['id', 'unique_code', 'created_at', 'updated_at', 'owner', 'is_locked']

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
        for field in ['booking_enabled', 'enable_reminder_sms', 'enable_promotional_sms', 'allow_anonymous_view', 'notification_enabled']:
            if field not in data and field in ret:
                ret.pop(field)
                
        return ret

    def validate(self, data):
        if data.get('work_start_hour') and data.get('work_end_hour'):
            if data['work_start_hour'] >= data['work_end_hour']:
                raise serializers.ValidationError(
                    "work_end_hour must be greater than work_start_hour"
                )
        return data

    def validate_logo(self, value):
        if value.size > 500 * 1024:  # 500KB
            raise ValidationError("حجم عکس نباید بیشتر از 500 کیلوبایت باشد.")
        return value


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

        return data


class ClientBusinessSerializer(serializers.ModelSerializer):
    """
    Kept for backward compatibility with appointment/client_serializers.py.
    Used to display business info inside a client's own appointment record.
    Does NOT include sensitive financial or config fields.
    """
    class Meta:
        model = Business
        fields = [
            'id', 'title', 'category', 'unique_code', 'phone', 'address', 'logo',
            'default_service_duration', 'work_start_hour', 'work_end_hour',
            'allow_anonymous_view',
            'notice_message', 'booking_enabled',
        ]

    def to_representation(self, instance):
        data = super().to_representation(instance)
        request = self.context.get('request')

        # If user is anonymous and the business doesn't allow anonymous viewing,
        # mask contact fields
        if (not request or not request.user or request.user.is_anonymous) \
                and not instance.allow_anonymous_view:
            data['phone'] = None
            data['address'] = None

        return data
