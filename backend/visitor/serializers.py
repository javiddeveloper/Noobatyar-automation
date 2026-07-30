from rest_framework import serializers
from api.phone import is_iran_mobile, normalize_phone
from .models import Visitor, SmsLog, VisitorActivity


class VisitorSerializer(serializers.ModelSerializer):
    class Meta:
        model = Visitor
        fields = ['id', 'full_name', 'phone_number', 'created_at', 'updated_at']
        read_only_fields = ['id', 'created_at', 'updated_at']

    def validate_phone_number(self, value):
        """Normalise and validate an Iranian mobile number.

        Normalising first means a number typed on a Persian keyboard, or pasted
        with dashes or a +98 prefix, is converted instead of rejected. Mobile
        only here (unlike Business.phone): this is the number we send SMS to.
        """
        normalized = normalize_phone(value)
        if not is_iran_mobile(normalized):
            raise serializers.ValidationError(
                "شماره تلفن باید با 09 شروع شده و 11 رقم باشد"
            )
        return normalized

    def validate_full_name(self, value):
        """Ensure name is not empty or whitespace"""
        if not value or not value.strip():
            raise serializers.ValidationError("نام نمی‌تواند خالی باشد")
        return value.strip()


class SmsLogSerializer(serializers.ModelSerializer):
    visitor_name = serializers.CharField(source='visitor.full_name', read_only=True)
    visitor_phone = serializers.CharField(source='visitor.phone_number', read_only=True)

    class Meta:
        model = SmsLog
        fields = ['id', 'visitor', 'visitor_name', 'visitor_phone', 'business', 'message_text', 'status', 'sent_at']
        read_only_fields = ['id', 'sent_at']


class VisitorActivitySerializer(serializers.ModelSerializer):
    """Read-only activity row for the visitor's own profile page.

    Ships human-readable Persian labels alongside the raw codes so the client
    does not have to keep its own copy of the choice tables.
    """

    action_label = serializers.CharField(source='get_action_display', read_only=True)
    actor_label = serializers.CharField(source='get_actor_type_display', read_only=True)
    business_title = serializers.CharField(source='business.title', read_only=True, default=None)

    class Meta:
        model = VisitorActivity
        fields = [
            'id', 'action', 'action_label', 'actor_type', 'actor_label',
            'business', 'business_title', 'appointment', 'detail', 'created_at',
        ]
        read_only_fields = fields